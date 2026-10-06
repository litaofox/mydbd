"""Kafka 消费循环：按 topic 分发、GPS 批量入库、手动提交、毒消息死信隔离。

- 客户端 kafka-python（纯 Python，适配 slim 镜像与 384M 内存限制）；
- 单线程顺序消费：warnId 去重的前置检查在单线程下可靠；
- 处理异常 → 写 gateway_dlq 并跳过该消息，绝不阻塞分区；
- 网关报警固定 event_source='jt808'，绕过 CEP 二次判定（GPS 点仍过点级规则）；
- 热路径优化：消费统计内存聚合按轮落库、终端保活写库 30s 节流、
  终端解析全量预热（详见 terminal_resolver）。
"""

import json
import threading
import time

from kafka import KafkaConsumer

from app import db
from app.config import settings
from app.gateway import mappers, media_store
from app.gateway.terminal_resolver import resolver
from app.services import cep_engine

GPS_FLUSH_SIZE = 200
# 终端保活（GPS 携带的存活信号 / 心跳 topic）写库节流间隔：
# 在线状态 30s 内最多落库一次；online/offline 状态切换不节流、立即写
_HEARTBEAT_THROTTLE_SEC = 30.0

# 消费统计内存缓冲：{topic: [ok, err]}，每轮 poll 后批量落库
_stat_buf: dict[str, list[int]] = {}
_stat_lock = threading.Lock()
# terminal_id -> 上次保活/状态写库 monotonic 时间
_last_online_write: dict[int, float] = {}


def _bump_stat(topic: str, ok: int = 0, err: int = 0) -> None:
    with _stat_lock:
        cell = _stat_buf.setdefault(topic, [0, 0])
        cell[0] += ok
        cell[1] += err


def _flush_stats() -> None:
    global _stat_buf
    with _stat_lock:
        if not _stat_buf:
            return
        buf = _stat_buf
        _stat_buf = {}  # 引用替换：旧计数交给本次落库，新计数进入新字典
    try:
        db.flush_ingest_stats(buf)
    except Exception as exc:
        print(f"[gateway] flush stats error: {exc}")
        with _stat_lock:
            for topic, counts in buf.items():
                cell = _stat_buf.setdefault(topic, [0, 0])
                cell[0] += counts[0]
                cell[1] += counts[1]


def _touch_online(terminal_id, event: str, ts) -> None:
    """终端在线态写库：heartbeat 节流，online/offline 状态切换立即写。"""
    now = time.monotonic()
    if event == "heartbeat":
        if now - _last_online_write.get(terminal_id, 0.0) < _HEARTBEAT_THROTTLE_SEC:
            return
    _last_online_write[terminal_id] = now
    db.upsert_terminal_online(terminal_id, event, ts)


def create_consumer() -> KafkaConsumer:
    return KafkaConsumer(
        *settings.gateway_topics,
        bootstrap_servers=settings.kafka_servers,
        group_id=settings.kafka_group_id,
        enable_auto_commit=False,
        auto_offset_reset="latest",
        value_deserializer=lambda b: json.loads(b.decode("utf-8")),
    )


def run_loop(stop_event, on_error) -> None:
    """消费主循环；异常由 runner 捕获后重连。"""
    # 启动（及重连）先全量预热终端索引，单次查询替代逐台 SELECT
    try:
        n = resolver.prewarm()
        print(f"[gateway] 终端索引预热完成：{n} 台")
    except Exception as exc:
        print(f"[gateway] terminal prewarm error: {exc}")
    consumer = create_consumer()
    gps_buffer: list[dict] = []
    try:
        while not stop_event.is_set():
            records = consumer.poll(
                timeout_ms=settings.kafka_poll_timeout_ms, max_records=500)
            for tp, msgs in records.items():
                for msg in msgs:
                    try:
                        row = _dispatch(tp.topic, msg.value)
                        if row is not None:
                            gps_buffer.append(row)
                        _bump_stat(tp.topic, ok=1)
                    except Exception as exc:
                        _dead_letter(tp.topic, msg, exc)
                    if len(gps_buffer) >= GPS_FLUSH_SIZE:
                        _flush_gps(gps_buffer)
                consumer.commit_async()
            if gps_buffer and not records:
                _flush_gps(gps_buffer)
            _flush_stats()
    finally:
        _flush_stats()
        try:
            consumer.close()
        except Exception:
            pass


def _flush_gps(buffer: list[dict]) -> None:
    rows, buffer[:] = buffer[:], []
    db.insert_gps_points_ext(rows)
    # CEP 点级规则旁路评估（与 HTTP 接入一致）；引擎异常不阻断主链路
    try:
        cep_engine.evaluate_points(rows)
    except Exception as exc:
        print(f"[cep] gateway points evaluate error: {exc}")


def _dead_letter(topic: str, msg, exc: Exception) -> None:
    try:
        payload = msg.value
        text = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False, default=str)
        db.insert_dlq(topic, msg.partition, msg.offset, text[:4000], str(exc)[:1000])
        _bump_stat(topic, err=1)
    except Exception as dlq_exc:
        print(f"[gateway] dlq write error: {dlq_exc}; original: {exc}")


def _dispatch(topic: str, payload) -> dict | None:
    """返回待批量入库的 GPS 行；其余消息即时处理返回 None。"""
    if not isinstance(payload, dict):
        raise ValueError("消息体不是 JSON 对象")
    if topic == settings.gateway_topic_gps:
        return _handle_gps(payload)
    if topic == settings.gateway_topic_warn:
        _handle_warn(payload)
    elif topic == settings.gateway_topic_warn_media:
        _handle_media(payload)
    elif topic == settings.gateway_topic_heartbeat:
        _handle_heartbeat(payload)
    elif topic == settings.gateway_topic_offline:
        _handle_offline(payload)
    elif topic == settings.gateway_topic_command:
        _handle_command(payload)
    return None


def _handle_gps(payload: dict) -> dict | None:
    head = payload.get("gpsHead") or {}
    row = mappers.map_gps(payload)
    if row is None:
        return None
    term = resolver.resolve(
        phone=head.get("phoneNumber"), truck_id=head.get("truckId"),
        plate_no=head.get("plateNo"))
    if term is None:
        return None
    row["identity_code"] = term["identity_code"]
    if not row.get("plate_no"):
        row["plate_no"] = term.get("plate_no")
    # 有定位即视为存活信号（30s 节流，避免每条 GPS 一次 UPDATE）
    try:
        _touch_online(term["id"], "heartbeat", row["gps_time"])
    except Exception as exc:
        print(f"[gateway] heartbeat by gps error: {exc}")
    return row


def _handle_warn(payload: dict) -> None:
    event_map = db.load_gateway_event_map()
    warn = mappers.map_warn(payload, event_map)
    if not warn.get("warn_id"):
        raise ValueError("报警消息缺少 warnId")
    term = resolver.resolve(
        phone=warn.get("identity_code"), truck_id=warn.get("truck_id"),
        plate_no=warn.get("plate_no"))
    if term is None:
        return
    warn["identity_code"] = term["identity_code"]
    if not warn.get("plate_no"):
        warn["plate_no"] = term.get("plate_no")
    # 报警开闭环：开始插入、结束回填（按 warnId）
    db.upsert_warn_lifecycle(warn)
    # 风险事件 + 自动工单：source_id=warnId 幂等，重复消息（含结束包）不会重复建单
    if warn.get("event_code"):
        db.insert_gateway_risk_event({
            "event_code": warn["event_code"],
            "event_source": "jt808",
            "plate_no": warn.get("plate_no"),
            "identity_code": warn["identity_code"],
            "event_time": warn.get("start_warn_time"),
            "lng": warn.get("lng"),
            "lat": warn.get("lat"),
            "speed": warn.get("speed"),
            "risk_level": warn["risk_level"],
            "confidence": 1.00,
            "title": f'{warn["event_name"]}·{warn.get("plate_no") or ""}',
            "source_id": warn["warn_id"],
        })


def _handle_media(payload: dict) -> None:
    media = mappers.map_media(payload)
    if not media.get("warn_id") or not media.get("file_name"):
        raise ValueError("附件消息缺少 warnId/fileName")
    term = resolver.resolve(phone=media.get("imei"), truck_id=media.get("truck_id"))
    if term is None:
        return
    local_path = None
    if media.get("file_status") == 2:  # 仅转存已完成的附件
        local_path = media_store.store(
            media["warn_id"], media["file_name"], media.get("url"), media.get("file_type"))
    db.insert_warn_media({
        "warn_id": media["warn_id"],
        "identity_code": term["identity_code"],
        "truck_id": media.get("truck_id"),
        "file_name": media["file_name"],
        "file_type": media.get("file_type") or 0,
        "file_size": media.get("file_size"),
        "url": media.get("url"),
        "local_path": local_path,
        "file_status": media.get("file_status"),
        "receive_time": media.get("receive_time"),
    })


def _handle_heartbeat(payload: dict) -> None:
    term = resolver.resolve(
        phone=payload.get("phoneNumber") or payload.get("phone"),
        truck_id=payload.get("truckId"))
    if term is None:
        return
    _touch_online(term["id"], "heartbeat",
                  payload.get("receiveTime") or payload.get("gpsTime"))


def _handle_offline(payload: dict) -> None:
    """离线事件仅携带 truckId，按 truckId 反查。"""
    term = resolver.resolve_by_truck(payload.get("truckId"))
    if term is None:
        db.upsert_unknown_terminal(None, payload.get("truckId"), None)
        return
    _touch_online(term["id"], "offline",
                  payload.get("offlineTime") or payload.get("receiveTime"))


def _handle_command(payload: dict) -> None:
    """0102 鉴权应答近似"上线"事件（网关只发离线不发上线）。"""
    head = payload.get("gpsHead") or payload
    msg_id = str(head.get("messageId") or "")
    if msg_id.lstrip("0x") not in ("0102", "102"):
        return
    term = resolver.resolve(
        phone=head.get("phoneNumber"), truck_id=head.get("truckId"))
    if term is None:
        return
    _touch_online(term["id"], "online",
                  head.get("receiveTime") or head.get("createTime"))

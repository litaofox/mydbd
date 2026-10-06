"""仿真网关数据投递器（仅 GATEWAY_MODE=mock 时启用）。

按 vps 网关 Kafka 契约（GATEWAY-REF-001）向本地 dev-infra Kafka 投递
与正式环境完全同构的 JSON 载荷，让 consumer 走真实解析/入库/工单/附件
全链路；切换到正式网关只需改 GATEWAY_MODE 与 KAFKA_BOOTSTRAP_SERVERS。

仿真内容：
- 每 5s 每车一条定位（gpsType=10，速度/里程按网关口径：m/h、米）；
- 每 30s 心跳；启动时 0102 鉴权（上线）；随机下线/上线循环；
- 报警全局限频：每 60s 窗口最多 2 起（窗口内随机错开触发时刻），
  开始包 + 附件 fileStatus=2 + 结束包；GPS 报警位置 0，不喂 CEP 随机事件。
"""

import json
import random
import time
import uuid
from datetime import datetime

from app import db
from app.config import settings

_FMT = "%Y-%m-%d %H:%M:%S"
_TICK_SEC = 5
# 报警全局限频：每窗口最多触发起数（风险事件/工单/终端报警同源联动受限）
_WARN_WINDOW_SEC = 60
_WARN_PER_WINDOW = 2
# 报警类型（与 18 号脚本 gateway_event_map 种子一致）
_WARN_TYPES = [
    (101001, 1), (101002, 2), (101003, 2), (102001, 1), (102002, 2),
    (102003, 2), (102004, 2), (104001, 2), (105001, 2), (105002, 2),
]

# 模块级运行状态：供 runner.status 展示 / 清除模拟数据接口重置在途报警
stats = {"ticks": 0, "warns": 0}
_state: dict = {"vehicles": []}


def reset_mock_state() -> None:
    """清空在途报警，避免清除模拟数据后结束包按旧 warnId 补建事件。"""
    for v in _state.get("vehicles", []):
        v["pending_warns"] = []


def _now() -> str:
    return datetime.now().strftime(_FMT)


def _load_vehicles() -> list[dict]:
    """复用平台已建档并绑定的车辆/终端（手机号优先 sim_account）。"""
    sql = """
        SELECT t.id, COALESCE(t.sim_account, t.identity_code) AS phone,
               t.identity_code, v.vehicle_no
        FROM traj.traj_terminal t
        JOIN traj.traj_vehicle_terminal vt
          ON vt.terminal_id = t.id AND vt.valid_mark = 1
        JOIN traj.traj_vehicle v
          ON v.id = vt.vehicle_id AND v.valid_mark = 1
        WHERE t.valid_mark = 1
        ORDER BY t.id
    """
    with db.get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            return [
                {"term_id": r[0], "phone": r[1], "truck_id": 900000 + r[0],
                 "plate_no": r[3],
                 "lng": round(116.30 + random.random() * 0.25, 6),
                 "lat": round(39.85 + random.random() * 0.15, 6),
                 "direction": random.randint(0, 359),
                 "mileage_m": random.randint(10000, 90000) * 1000,
                 "online": True, "pending_warns": []}
                for r in cur.fetchall()
            ]


def _gps_payload(v: dict, gps_type: int = 10) -> dict:
    now = _now()
    speed_kmh = random.randint(30, 95)
    v["mileage_m"] += int(speed_kmh * _TICK_SEC / 3.6)
    return {
        "gpsHead": {
            "trackId": uuid.uuid4().hex,
            "receiveTime": now, "sendTime": now, "createTime": now,
            "protocolType": "jt808", "protocolVersion": 2019,
            "messageId": "0200", "phoneNumber": v["phone"],
            "truckId": v["truck_id"], "plateNo": v["plate_no"],
            "plateColor": 2, "delay": False, "gpsType": gps_type,
        },
        "gpsInfo": {
            "alarmFlag": 0,  # 报警位恒 0：报警统一走限频 warn 链路，不喂 CEP 随机事件
            "status": 2, "latitude": v["lat"], "longitude": v["lng"],
            "altitude": random.randint(20, 60),
            "speed": speed_kmh * 100,  # 网关口径 m/h
            "recordSpeed": speed_kmh * 100,
            "direction": v["direction"], "gpsTime": now,
            "mileage": v["mileage_m"],  # 网关口径 米
            "additions": [],
        },
    }


def _warn_start(v: dict) -> dict:
    type_id, level = random.choice(_WARN_TYPES)
    warn_id = uuid.uuid4().hex
    v["pending_warns"].append({"warnId": warn_id, "typeId": type_id, "left": 2})
    now = _now()
    return {
        "warnId": warn_id, "startWarnTime": now, "truckId": v["truck_id"],
        "identityCode": v["phone"], "plateNo": v["plate_no"],
        "typeId": type_id, "rawType": "0x64_0x01", "alarmLevel": level,
        "startLongitude": v["lng"], "startLatitude": v["lat"],
        "startSpeed": random.randint(40, 90) * 100,
        "startAltitude": 40, "startGpsTime": now,
        "warnIdentity": uuid.uuid4().hex[:16], "fileNum": 2,
        "receiveNum": 0, "transferNum": 0, "fromSource": 10,
        "trackIds": uuid.uuid4().hex,
    }


def _warn_end(v: dict, pending: dict) -> dict:
    now = _now()
    return {
        "warnId": pending["warnId"], "startWarnTime": now,
        "endWarnTime": now, "endGpsTime": now,
        "truckId": v["truck_id"], "identityCode": v["phone"],
        "plateNo": v["plate_no"], "typeId": pending["typeId"],
        "alarmLevel": 2,
        "endLongitude": v["lng"], "endLatitude": v["lat"],
        "endSpeed": random.randint(20, 60) * 100, "endAltitude": 40,
        "fromSource": 10,
    }


def _media_payloads(v: dict, warn_id: str) -> list[dict]:
    now = _now()
    return [
        {
            "warnId": warn_id, "truckId": v["truck_id"],
            "fileName": f"{warn_id}_{i}.jpg", "imei": v["phone"],
            "fileSize": 1024, "fileType": 0, "fileStatus": 2,
            "lazy": False,
            "url": f"mock://{warn_id}/{warn_id}_{i}.jpg",
            "storeType": "local", "fileSource": 0,
            "receiveTime": now, "createTime": now,
        }
        for i in range(2)
    ]


def run_mock(stop_event) -> None:
    """mock 投递主循环（由 runner 以守护线程启动）。"""
    from kafka import KafkaProducer
    try:
        producer = KafkaProducer(
            bootstrap_servers=settings.kafka_servers,
            value_serializer=lambda d: json.dumps(d, ensure_ascii=False).encode("utf-8"),
            key_serializer=lambda k: str(k).encode("utf-8"),
        )
    except Exception as exc:
        print(f"[mock] KafkaProducer 初始化失败：{exc}")
        return

    vehicles: list[dict] = []
    _state["vehicles"] = vehicles
    tick = 0
    # 报警全局限频调度：每个窗口随机错开 N 个触发时刻（绝对时间）
    now_m = time.monotonic()
    window_end = now_m + _WARN_WINDOW_SEC
    warn_times = sorted(random.uniform(now_m, window_end)
                        for _ in range(_WARN_PER_WINDOW))
    while not stop_event.is_set():
        try:
            if not vehicles:
                vehicles = _load_vehicles()
                _state["vehicles"] = vehicles
                if not vehicles:
                    print("[mock] 无已绑定终端的车辆，30s 后重试")
                    time.sleep(30)
                    continue
                # 启动即上线：0102 鉴权
                for v in vehicles:
                    producer.send(settings.gateway_topic_command, key=v["truck_id"], value={
                        "gpsHead": {"messageId": "0102", "phoneNumber": v["phone"],
                                    "truckId": v["truck_id"], "receiveTime": _now()},
                    })
                    producer.flush()

            tick += 1
            for v in vehicles:
                # 随机下线/上线循环（每车约 3% 概率切状态）
                if random.random() < 0.01:
                    v["online"] = not v["online"]
                    if v["online"]:
                        producer.send(settings.gateway_topic_command, key=v["truck_id"], value={
                            "gpsHead": {"messageId": "0102", "phoneNumber": v["phone"],
                                        "truckId": v["truck_id"], "receiveTime": _now()},
                        })
                    else:
                        producer.send(settings.gateway_topic_offline, key=v["truck_id"], value={
                            "truckId": v["truck_id"], "type": 1, "offlineTime": _now(),
                        })
                if not v["online"]:
                    continue
                # 移动 + 定位
                v["lng"] = round(v["lng"] + random.uniform(-0.002, 0.002), 6)
                v["lat"] = round(v["lat"] + random.uniform(-0.002, 0.002), 6)
                producer.send(settings.gateway_topic_gps, key=v["truck_id"],
                              value=_gps_payload(v))
                # 心跳（每 6 tick ≈ 30s）
                if tick % 6 == 0:
                    producer.send(settings.gateway_topic_heartbeat, key=v["truck_id"], value={
                        "phoneNumber": v["phone"], "truckId": v["truck_id"],
                        "receiveTime": _now(),
                    })
                # 报警结束包（延迟 2 tick）
                for p in list(v["pending_warns"]):
                    p["left"] -= 1
                    if p["left"] <= 0:
                        producer.send(settings.gateway_topic_warn, key=v["truck_id"],
                                      value=_warn_end(v, p))
                        v["pending_warns"].remove(p)
            # 报警全局限频：到达窗口内预定时刻才触发（≤2 起/分钟）
            now_m = time.monotonic()
            while warn_times and warn_times[0] <= now_m:
                warn_times.pop(0)
                online = [v for v in vehicles if v["online"]]
                if online:
                    v = random.choice(online)
                    start = _warn_start(v)
                    producer.send(settings.gateway_topic_warn, key=v["truck_id"], value=start)
                    for m in _media_payloads(v, start["warnId"]):
                        producer.send(settings.gateway_topic_warn_media,
                                      key=v["truck_id"], value=m)
                    stats["warns"] += 1
            # 窗口滚动：生成下一窗口的触发时刻
            if now_m >= window_end:
                window_end = now_m + _WARN_WINDOW_SEC
                warn_times = sorted(random.uniform(now_m, window_end)
                                    for _ in range(_WARN_PER_WINDOW))
            producer.flush()
            stats["ticks"] += 1
        except Exception as exc:
            print(f"[mock] 投递异常：{exc}")
        stop_event.wait(_TICK_SEC)
    try:
        producer.close()
    except Exception:
        pass
    print("[mock] 仿真投递已停止")

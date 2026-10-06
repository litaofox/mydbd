"""网关消息映射（纯函数，可离线表驱动单测）

契约来源：GATEWAY-REF-001。
- 速度：网关 m/h（=km/h×100）→ ÷100 得 km/h（平台 integer）
- 里程：网关 米 → ÷1000 得 km（平台 numeric，保留 2 位）
- 报警级别：苏标 1高/2低 → 平台 risk_level 3高/2中
- 时间："yyyy-MM-dd HH:mm:ss"（GMT+8），直接透传给 psycopg2
- 坐标：已为 WGS-84 度，原样入库（4326）
"""

_TIME_FMT = "%Y-%m-%d %H:%M:%S"


def _pick(d: dict, *keys):
    for k in keys:
        v = d.get(k)
        if v is not None:
            return v
    return None


def _num(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _valid_time(value) -> bool:
    from datetime import datetime
    try:
        datetime.strptime(str(value)[:19], _TIME_FMT)
        return True
    except (TypeError, ValueError):
        return False


def map_gps(payload: dict) -> dict | None:
    """PositionInfo{gpsHead,gpsInfo} → traj_gps_point 行；无效点返回 None。"""
    head = payload.get("gpsHead") or {}
    info = payload.get("gpsInfo") or {}
    lng = _num(info.get("longitude"))
    lat = _num(info.get("latitude"))
    if lng is None or lat is None:
        return None
    if not (-180 <= lng <= 180 and -90 <= lat <= 90) or (lng == 0 and lat == 0):
        return None
    gps_time = _pick(info, "gpsTime") or head.get("receiveTime")
    if not _valid_time(gps_time):
        return None

    speed_raw = _num(info.get("speed"))
    mileage_raw = _num(info.get("mileage"))
    row = {
        "identity_code": head.get("phoneNumber"),  # 由 resolver 覆盖为平台标识
        "plate_no": head.get("plateNo"),
        "gps_time": str(gps_time)[:19],
        "lng": round(lng, 6),
        "lat": round(lat, 6),
        "speed": int(round(speed_raw / 100)) if speed_raw is not None else None,
        "direction": info.get("direction"),
        "altitude": info.get("altitude"),
        "alarm_flag": int(info.get("alarmFlag") or 0),
        "mileage": round(mileage_raw / 1000, 2) if mileage_raw is not None else None,
        "ext": {
            "trackId": head.get("trackId"),
            "gpsType": head.get("gpsType"),
            "delay": head.get("delay"),
            "truckId": head.get("truckId"),
            "phoneNumber": head.get("phoneNumber"),
            "status": info.get("status"),
            "recordSpeed": info.get("recordSpeed"),
        },
    }
    return row


def map_level(alarm_level, default_level: int) -> int:
    """苏标 1高/2低 → 平台 3高/2中；其他/缺失 → 字典默认级别。"""
    try:
        lv = int(alarm_level)
    except (TypeError, ValueError):
        return default_level
    return {1: 3, 2: 2}.get(lv, default_level)


def map_warn(payload: dict, event_map: dict[int, dict]) -> dict:
    """WarnInfo → 规范化报警结构（映射缺失时 event_code 为 None，由调用方跳过建事件）。

    event_map: {type_id: {event_code, default_level, name}}
    """
    type_id = payload.get("typeId")
    m = event_map.get(type_id) or {}
    start_lng = _num(_pick(payload, "startLongitude", "startLng"))
    start_lat = _num(_pick(payload, "startLatitude", "startLat"))
    end_lng = _num(_pick(payload, "endLongitude", "endLng"))
    end_lat = _num(_pick(payload, "endLatitude", "endLat"))
    start_speed = _num(_pick(payload, "startSpeed"))
    end_speed = _num(_pick(payload, "endSpeed"))
    return {
        "warn_id": payload.get("warnId"),
        "truck_id": payload.get("truckId"),
        "identity_code": payload.get("identityCode"),  # 手机号，由 resolver 覆盖
        "plate_no": payload.get("plateNo"),
        "type_id": type_id,
        "raw_type": payload.get("rawType"),
        "event_code": m.get("event_code"),
        "event_name": m.get("name") or f"类型 {type_id}",
        "risk_level": map_level(payload.get("alarmLevel"), m.get("default_level", 2)),
        "start_warn_time": _pick(payload, "startWarnTime"),
        "end_warn_time": _pick(payload, "endWarnTime"),
        "start_gps_time": _pick(payload, "startGpsTime", "startWarnTime"),
        "end_gps_time": _pick(payload, "endGpsTime", "endWarnTime"),
        # traj_warn_info 坐标列为 varchar，按原文透传
        "start_lng": str(start_lng) if start_lng is not None else None,
        "start_lat": str(start_lat) if start_lat is not None else None,
        "end_lng": str(end_lng) if end_lng is not None else None,
        "end_lat": str(end_lat) if end_lat is not None else None,
        "start_speed": int(round(start_speed / 100)) if start_speed is not None else None,
        "end_speed": int(round(end_speed / 100)) if end_speed is not None else None,
        # risk_event 用数值坐标
        "lng": start_lng,
        "lat": start_lat,
        "speed": int(round(start_speed / 100)) if start_speed is not None else None,
    }


def map_media(payload: dict) -> dict:
    """MediaInfoDto → 规范化附件结构（fileType 0图1音2视；fileStatus 2完成）。"""
    return {
        "warn_id": payload.get("warnId"),
        "truck_id": payload.get("truckId"),
        "imei": payload.get("imei"),
        "file_name": payload.get("fileName"),
        "file_size": payload.get("fileSize"),
        "file_type": payload.get("fileType"),
        "file_status": payload.get("fileStatus"),
        "url": payload.get("url"),
        "receive_time": _pick(payload, "receiveTime", "createTime"),
    }

"""网关映射器表驱动断言（无需 Kafka/数据库，直接运行：python tests/test_gateway_mappers.py）"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from app.gateway import mappers  # noqa: E402

EVENT_MAP = {
    101001: {"event_code": "ADAS_FCW", "default_level": 3, "name": "前向碰撞报警"},
    102001: {"event_code": "DSM_FATIGUE", "default_level": 3, "name": "疲劳驾驶报警"},
}


def _gps_payload(**over):
    payload = {
        "gpsHead": {
            "trackId": "t-001", "receiveTime": "2026-10-06 10:00:01",
            "phoneNumber": "13800100001", "truckId": 900001,
            "plateNo": "京A12345", "gpsType": 10, "delay": False,
        },
        "gpsInfo": {
            "alarmFlag": 2 ** 31 + 5, "status": 2,
            "latitude": 39.904200, "longitude": 116.407400,
            "altitude": 45, "speed": 8000, "recordSpeed": 7900,
            "direction": 180, "gpsTime": "2026-10-06 10:00:00",
            "mileage": 150000, "additions": [],
        },
    }
    for k, v in over.items():
        head, _, key = k.partition(".")
        payload[head][key] = v
    return payload


def test_gps_units_and_ext():
    row = mappers.map_gps(_gps_payload())
    assert row is not None
    assert row["speed"] == 80, "speed m/h ÷100"
    assert row["mileage"] == 150.0, "mileage 米 ÷1000"
    assert row["alarm_flag"] == 2 ** 31 + 5, "32 位位图原值保留"
    assert row["gps_time"] == "2026-10-06 10:00:00"
    assert row["ext"]["trackId"] == "t-001"
    assert row["ext"]["truckId"] == 900001
    assert row["ext"]["gpsType"] == 10


def test_gps_invalid_points():
    assert mappers.map_gps(_gps_payload(**{"gpsInfo.longitude": 0, "gpsInfo.latitude": 0})) is None
    assert mappers.map_gps(_gps_payload(**{"gpsInfo.latitude": 91})) is None
    assert mappers.map_gps(_gps_payload(**{"gpsInfo.longitude": -181})) is None
    assert mappers.map_gps(_gps_payload(**{"gpsInfo.gpsTime": "bad"})) is None
    assert mappers.map_gps({}) is None
    # gpsTime 缺失时回退 receiveTime
    row = mappers.map_gps(_gps_payload(**{"gpsInfo.gpsTime": None}))
    assert row["gps_time"] == "2026-10-06 10:00:01"


def test_map_level():
    assert mappers.map_level(1, 2) == 3, "苏标 1 高 → 平台 3 高"
    assert mappers.map_level(2, 2) == 2, "苏标 2 低 → 平台 2 中"
    assert mappers.map_level(None, 2) == 2
    assert mappers.map_level(9, 1) == 1, "未知级别回退字典默认"


def test_warn_mapping():
    payload = {
        "warnId": "w-001", "startWarnTime": "2026-10-06 10:00:00",
        "truckId": 900001, "identityCode": "13800100001",
        "plateNo": "京A12345", "typeId": 101001, "alarmLevel": 1,
        "startLongitude": 116.4074, "startLatitude": 39.9042,
        "startSpeed": 6500, "startAltitude": 40,
        "startGpsTime": "2026-10-06 09:59:58",
    }
    w = mappers.map_warn(payload, EVENT_MAP)
    assert w["event_code"] == "ADAS_FCW"
    assert w["risk_level"] == 3
    assert w["speed"] == 65 and w["start_speed"] == 65
    assert w["start_lng"] == "116.4074"  # warn_info 坐标列为 varchar
    assert w["lng"] == 116.4074          # risk_event 用数值
    # 未登记类型：event_code 为 None，名称退化；级别按 alarmLevel 反转
    w2 = mappers.map_warn({**payload, "typeId": 999999}, EVENT_MAP)
    assert w2["event_code"] is None
    assert w2["event_name"] == "类型 999999"
    assert w2["risk_level"] == 3  # alarmLevel=1 → 平台 3
    w2b = mappers.map_warn({**payload, "typeId": 999999, "alarmLevel": None}, EVENT_MAP)
    assert w2b["risk_level"] == 2  # 无级别回退默认 2
    # 结束包字段
    w3 = mappers.map_warn({**payload, "endWarnTime": "2026-10-06 10:00:30",
                           "endLongitude": 116.5, "endLatitude": 39.9,
                           "endSpeed": 3000}, EVENT_MAP)
    assert w3["end_warn_time"] == "2026-10-06 10:00:30"
    assert w3["end_speed"] == 30


def test_media_mapping():
    m = mappers.map_media({
        "warnId": "w-001", "truckId": 900001, "fileName": "w-001_0.jpg",
        "imei": "13800100001", "fileSize": 2048, "fileType": 0,
        "fileStatus": 2, "url": "mock://w-001/w-001_0.jpg",
        "receiveTime": "2026-10-06 10:00:05",
    })
    assert m["warn_id"] == "w-001"
    assert m["file_type"] == 0 and m["file_status"] == 2
    assert m["imei"] == "13800100001"


if __name__ == "__main__":
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for fn in fns:
        fn()
        print(f"PASS {fn.__name__}")
    print(f"\n全部通过：{len(fns)} 项")

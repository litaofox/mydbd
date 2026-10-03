"""轨迹数据接入：终端实时推送（服务令牌）+ 样例 CSV 导入"""

import csv
import json
import os

from fastapi import APIRouter, Depends, HTTPException

from app import db
from app.config import settings
from app.security import verify_service_token
from app.services import cep_engine

router = APIRouter(prefix="/api/ingest", tags=["ingest"])

INSERT_CHUNK = 5000


@router.post("/traj", dependencies=[Depends(verify_service_token)])
def ingest_traj(batch: dict):
    """终端/第三方系统实时推送轨迹点"""
    identity = batch.get("identityCode")
    plate = batch.get("plateNo")
    points = batch.get("points", [])
    if not identity or not points:
        raise HTTPException(status_code=400, detail="identityCode 与 points 不能为空")
    rows = [{
        "identity_code": identity,
        "plate_no": plate,
        "gps_time": p["gpsTime"],
        "lng": p["lng"],
        "lat": p["lat"],
        "speed": p.get("speed"),
        "direction": p.get("direction"),
        "altitude": p.get("altitude"),
        "alarm_flag": p.get("alarmFlag", 0),
        "mileage": p.get("mileage"),
    } for p in points]
    inserted = 0
    for i in range(0, len(rows), INSERT_CHUNK):
        inserted += db.insert_gps_points(rows[i:i + INSERT_CHUNK])
    # F18 CEP 旁路评估：引擎异常不阻断轨迹入库
    try:
        cep_engine.evaluate_points(rows)
    except Exception as exc:
        print(f"[cep] evaluate error: {exc}")
    return {"inserted": inserted}


@router.post("/load-sample")
def load_sample(payload: dict | None = None):
    """将 samples/ 下的样例轨迹导入 PostGIS，并同步导入当日司机事件。

    幂等：重导前按设备 + 时间范围清理旧点；同一天事件只导一次。
    """
    filename = (payload or {}).get("file", "vehicle_gps_20260901.csv")
    path = os.path.join(settings.samples_dir, os.path.basename(filename))
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail=f"样例文件不存在: {filename}")

    rows = []
    times = []
    identity = None
    with open(path, encoding="utf-8") as f:
        for rec in csv.DictReader(f):
            identity = rec["vehicle_id"]
            times.append(rec["gps_time"])
            rows.append({
                "identity_code": rec["vehicle_id"],
                "plate_no": rec["plate_no"],
                "gps_time": rec["gps_time"],
                "lng": float(rec["longitude"]),
                "lat": float(rec["latitude"]),
                "speed": int(rec["speed"]),
                "direction": int(rec["direction"]),
                "altitude": int(rec["altitude"]),
                "alarm_flag": int(rec["alarm_flag"]),
                "mileage": float(rec["mileage"]),
            })
    if not rows:
        raise HTTPException(status_code=400, detail="CSV 无有效数据")

    times.sort()
    start, end = times[0], times[-1]
    db.delete_gps_points(identity, start, end)
    inserted = 0
    for i in range(0, len(rows), INSERT_CHUNK):
        inserted += db.insert_gps_points(rows[i:i + INSERT_CHUNK])

    # 同步导入当日司机事件（DSM/ADAS 风险事件），同一天仅导一次
    date_str = start[:10]
    events_inserted = 0
    events_path = os.path.join(settings.samples_dir, "driver_events.json")
    if db.count_risk_events_on(date_str) == 0 and os.path.exists(events_path):
        with open(events_path, encoding="utf-8") as f:
            events = json.load(f).get("events", [])
        event_rows = []
        for e in events:
            event_time = e.get("event_time", "")
            if not event_time.startswith(date_str):
                continue
            level = (e.get("extra") or {}).get("dsm_level", 2)
            event_rows.append({
                "event_code": str(e.get("event_code")),
                "event_source": e.get("event_source"),
                "plate_no": e.get("plate_no"),
                "event_time": event_time,
                "lng": e.get("lon"),
                "lat": e.get("lat"),
                "speed": e.get("speed"),
                "risk_level": level,
            })
        events_inserted = db.insert_risk_events(event_rows)

    return {
        "file": filename,
        "identityCode": identity,
        "timeRange": [start, end],
        "insertedPoints": inserted,
        "insertedEvents": events_inserted,
    }

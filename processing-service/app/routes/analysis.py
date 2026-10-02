"""驾驶员监控视频分析接口（服务令牌）"""

import uuid

from fastapi import APIRouter, Depends

from app import db
from app.security import verify_service_token
from app.services.risk_engine import analyze

router = APIRouter(
    prefix="/api/analysis",
    tags=["analysis"],
    dependencies=[Depends(verify_service_token)],
)


@router.post("/video")
def analyze_video(req: dict):
    task_id = req.get("taskId") or f"VA-{uuid.uuid4().hex[:10]}"
    plate = req.get("plateNo", "")
    channel = req.get("channel", "DSM")
    clip_url = req.get("clipUrl")

    db.upsert_video_analysis({
        "task_id": task_id,
        "plate_no": plate,
        "channel": channel,
        "clip_url": clip_url,
        "status": "RUNNING",
        "result_summary": None,
        "event_count": 0,
    })
    events, summary = analyze(task_id, plate, channel)
    db.insert_risk_events(events)
    db.update_video_analysis(task_id, "SUCCESS", summary, len(events))
    return {
        "taskId": task_id,
        "status": "SUCCESS",
        "eventCount": len(events),
        "summary": summary,
    }

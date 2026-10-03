"""驾驶员监控视频分析接口（服务令牌）

演示链路：占位"识别"出信号候选 → F18 cep_engine 按启用规则分级/去重/落事件。
真实终端接入后替换识别来源，规则与事件结构不变。
"""

import uuid

from fastapi import APIRouter, Depends

from app import db
from app.security import verify_service_token
from app.services.risk_engine import recognize
from app.services import cep_engine

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
    event_code = req.get("eventCode")

    db.upsert_video_analysis({
        "task_id": task_id,
        "plate_no": plate,
        "channel": channel,
        "clip_url": clip_url,
        "status": "RUNNING",
        "result_summary": None,
        "event_count": 0,
    })

    candidate = recognize(task_id, channel, event_code)
    event = cep_engine.evaluate_signal(
        channel=candidate["channel"],
        event_code=candidate["event_code"],
        plate_no=plate,
        identity_code=req.get("identityCode"),
        lng=req.get("lng"),
        lat=req.get("lat"),
        speed=req.get("speed"),
        media_url=clip_url,
        confidence=candidate["confidence"],
    )

    if event is not None:
        summary = (
            f"检出 {candidate['event_name']}（{candidate['event_code']}），"
            f"按规则定级 {event['risk_level']} 级，已生成风险事件"
        )
        event_count = 1
    else:
        summary = (
            f"信号 {candidate['event_code']} 未匹配启用规则或处于冷却窗口，"
            f"本次不落风险事件"
        )
        event_count = 0

    db.update_video_analysis(task_id, "SUCCESS", summary, event_count)
    return {
        "taskId": task_id,
        "status": "SUCCESS",
        "eventCode": candidate["event_code"],
        "eventCount": event_count,
        "summary": summary,
    }

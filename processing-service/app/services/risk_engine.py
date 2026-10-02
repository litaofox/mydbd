"""驾驶员监控视频风险分析（骨架占位）。

正式版本将接入 DSM/ADAS 视觉模型：疲劳（闭眼/打哈欠/长时驾驶）、
分心（左顾右盼/低头）、抽烟、接打电话、前向碰撞、车道偏离等。
当前提供确定性规则占位，保证 video_analysis → risk_event 链路可演示。
"""

# 通道 → 可能产出的占位事件（代码，名称，风险等级）
_PROFILES = {
    "DSM": [("DSM_FATIGUE", "疲劳驾驶", 3), ("DSM_DISTRACTION", "分心驾驶", 2)],
    "ADAS": [("ADAS_FCW", "前向碰撞风险", 3), ("ADAS_LDW", "车道偏离", 2)],
}


def analyze(task_id: str, plate_no: str, channel: str):
    channel = (channel or "DSM").upper()
    profiles = _PROFILES.get(channel, _PROFILES["DSM"])
    # 由任务号派生确定性选择，保证同一任务重复执行结果一致
    idx = sum(ord(c) for c in task_id) % len(profiles)
    code, name, level = profiles[idx]
    events = [{
        "event_code": code,
        "event_source": channel,
        "plate_no": plate_no,
        "event_time": _now(),
        "risk_level": level,
        "confidence": 0.88,
    }]
    summary = f"占位分析：检出 {name}（{code}），风险等级 {level}"
    return events, summary


def _now() -> str:
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

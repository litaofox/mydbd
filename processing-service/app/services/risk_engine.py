"""驾驶员监控视频风险识别（骨架占位，仅"识别"环节）。

正式版本将接入终端侧 DSM/ADAS 视觉模型：疲劳（闭眼/打哈欠/长时驾驶）、
分心（左顾右盼/低头）、抽烟、接打电话、前向碰撞、车道偏离等。
按既定"边缘识别 + 按需调阅"原则，平台不做全量视频分析；
本模块只产出一个信号候选（信号码 + 置信度），
是否落事件、定什么等级由 F18 cep_engine 按启用规则决定。
"""

# 通道 → 可能产出的占位信号（代码，名称，置信度）
_PROFILES = {
    "DSM": [("DSM_FATIGUE", "疲劳驾驶", 0.91), ("DSM_DISTRACTION", "分心驾驶", 0.84)],
    "ADAS": [("ADAS_FCW", "前向碰撞风险", 0.88), ("ADAS_LDW", "车道偏离", 0.82)],
}


def recognize(task_id: str, channel: str, event_code: str | None = None) -> dict:
    """返回候选信号 {event_code, event_name, confidence, channel}。

    event_code 显式传入时优先（真实终端信号路径）；
    未传时由任务号派生确定性选择，保证演示同一任务重复执行结果一致。
    """
    channel = (channel or "DSM").upper()
    profiles = _PROFILES.get(channel, _PROFILES["DSM"])
    if event_code:
        match = next((p for p in profiles if p[0] == event_code), None)
        code, name, conf = match or (event_code, event_code, 0.80)
    else:
        idx = sum(ord(c) for c in (task_id or "")) % len(profiles)
        code, name, conf = profiles[idx]
    return {"event_code": code, "event_name": name,
            "confidence": conf, "channel": channel}

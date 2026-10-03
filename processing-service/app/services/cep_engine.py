"""F18 风控规则引擎（CEP）。

在北斗点写入后旁路评估启用中的规则/围栏，命中且通过冷却去重后写 mon.risk_event。
- 规则/围栏配置走 TTL 缓存（settings.risk_cache_sec），配置改动热生效；
- 引擎异常由调用方吞掉，绝不阻断轨迹主链路；
- 视频危险动作的"识别"仍在终端侧（本期为 risk_engine.recognize 占位），
  本引擎只负责信号的规则分级与去重。
"""

import time
from datetime import datetime, timedelta

from app import db
from app.config import settings

_TIME_FMT = "%Y-%m-%d %H:%M:%S"

_rules_cache = {"ts": 0.0, "rows": []}
_fences_cache = {"ts": 0.0, "rows": []}


def _rules() -> list[dict]:
    now = time.time()
    if now - _rules_cache["ts"] > settings.risk_cache_sec:
        _rules_cache["rows"] = db.load_enabled_rules()
        _rules_cache["ts"] = now
    return _rules_cache["rows"]


def _fences() -> list[dict]:
    now = time.time()
    if now - _fences_cache["ts"] > settings.risk_cache_sec:
        _fences_cache["rows"] = db.load_enabled_fences()
        _fences_cache["ts"] = now
    return _fences_cache["rows"]


def _parse_ts(value) -> datetime | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.replace(tzinfo=None)
    try:
        return datetime.strptime(str(value)[:19], _TIME_FMT)
    except (TypeError, ValueError):
        return None


def _num(params: dict, key: str):
    try:
        return float(params.get(key))
    except (TypeError, ValueError):
        return None


def _rule_event(rule: dict, plate: str, identity: str, point: dict) -> dict:
    return {
        "event_code": rule["event_code"],
        "event_source": "北斗",
        "plate_no": plate,
        "identity_code": identity,
        "event_time": point.get("gps_time"),
        "lng": point.get("lng"),
        "lat": point.get("lat"),
        "speed": point.get("speed"),
        "risk_level": rule["risk_level"],
        "confidence": 1.00,
        "rule_id": rule["id"],
        "title": rule["rule_name"],
    }


def _cooled(last_event_time, point_time: datetime, cooldown_sec: int) -> bool:
    """冷却窗口内已有事件 → True（应抑制）"""
    last = _parse_ts(last_event_time)
    return last is not None and (point_time - last).total_seconds() < cooldown_sec


# =====================================================================
# 北斗点批评估
# =====================================================================

def evaluate_points(rows: list[dict]) -> int:
    """对一批已入库的定位点做规则评估，返回新产生的事件数。"""
    if not rows:
        return 0
    rules = _rules()
    if not rules:
        return 0
    speed_rules = [r for r in rules if r["rule_type"] == "SPEED"]
    fatigue_rules = [r for r in rules if r["rule_type"] == "FATIGUE"]
    combo_rules = [r for r in rules if r["rule_type"] == "COMBO"]
    fatigue_def = next((r for r in rules if r["rule_code"] == "FATIGUE_DRIVE"), None)
    fences = _fences()
    if not (speed_rules or fatigue_rules or combo_rules or fences):
        return 0

    # 按车分组
    groups: dict[str, list[dict]] = {}
    for row in rows:
        groups.setdefault(row.get("identity_code") or row.get("plate_no") or "", []).append(row)

    produced = 0
    # 本批次内存事件，供 COMBO 判定"同一批刚产生的疲劳"
    batch_events: list[dict] = []

    for identity, points in groups.items():
        if not identity:
            continue
        points.sort(key=lambda p: str(p.get("gps_time")))
        last = points[-1]
        plate = next((p.get("plate_no") for p in points if p.get("plate_no")), None) or identity
        last_time = _parse_ts(last.get("gps_time"))
        if last_time is None:
            continue

        candidates: list[tuple[dict, dict]] = []

        # ---- SPEED：取本批最超速点作为代表 ----
        if speed_rules:
            pmax = max(points, key=lambda p: p.get("speed") or 0)
            max_speed = pmax.get("speed") or 0
            for rule in speed_rules:
                threshold = _num(rule.get("params") or {}, "speedKmh")
                if threshold is None:
                    continue  # 脏配置只跳过该规则
                if max_speed >= threshold:
                    candidates.append((rule, pmax))

        # ---- FATIGUE：连续行驶会话回溯（每车每批最多每规则 1 候选）----
        if fatigue_rules and (last.get("speed") or 0) > 0:
            for rule in fatigue_rules:
                params = rule.get("params") or {}
                cont = _num(params, "continuousMin")
                gap = _num(params, "gapMin")
                if cont is None or gap is None:
                    continue
                session = db.latest_driving_session(
                    identity, last.get("gps_time"), int(cont), int(gap))
                if not session:
                    continue
                s0, s1 = _parse_ts(session[0]), _parse_ts(session[1])
                if not s0 or not s1:
                    continue
                if (last_time - s1).total_seconds() > gap * 60:
                    continue  # 会话已中断
                if (s1 - s0).total_seconds() >= cont * 60:
                    candidates.append((rule, last))

        # ---- 规则类冷却去重（一次 SQL）----
        accepted: list[tuple[dict, dict]] = []
        if candidates:
            last_times = db.last_rule_event_times(plate, [r["id"] for r, _ in candidates])
            for rule, point in candidates:
                point_time = _parse_ts(point.get("gps_time")) or last_time
                if _cooled(last_times.get(rule["id"]), point_time, int(rule["cooldown_sec"])):
                    continue
                accepted.append((rule, point))
            if accepted:
                events = [_rule_event(r, plate, identity, p) for r, p in accepted]
                produced += db.insert_risk_events(events)
                batch_events.extend(events)

        # ---- 围栏进出状态机 ----
        if fences:
            try:
                produced += _evaluate_fences(identity, plate, last)
            except Exception as exc:
                print(f"[cep] fence evaluate error: {exc}")

        # ---- COMBO：窗口内疲劳 + 当前超速 ----
        if combo_rules and (last.get("speed") or 0) >= 0:
            pmax = max(points, key=lambda p: p.get("speed") or 0)
            for rule in combo_rules:
                params = rule.get("params") or {}
                window = _num(params, "windowMin") or 30
                speed_threshold = _num(params, "speedKmh")
                if speed_threshold is None:
                    continue
                if (pmax.get("speed") or 0) < speed_threshold:
                    continue
                # 疲劳来源：库内窗口事件 + 本批次刚产生的疲劳事件
                fatigue_hit = False
                if fatigue_def is not None:
                    since = last_time - timedelta(minutes=window)
                    if db.has_recent_rule_event(plate, fatigue_def["id"], since):
                        fatigue_hit = True
                    elif any(
                        e.get("plate_no") == plate and e.get("rule_id") == fatigue_def["id"]
                        and last_time - (_parse_ts(e.get("event_time")) or last_time)
                        <= timedelta(minutes=window)
                        for e in batch_events
                    ):
                        fatigue_hit = True
                if not fatigue_hit:
                    continue
                last_times = db.last_rule_event_times(plate, [rule["id"]])
                if _cooled(last_times.get(rule["id"]), last_time, int(rule["cooldown_sec"])):
                    continue
                event = _rule_event(rule, plate, identity, pmax)
                produced += db.insert_risk_events([event])
                batch_events.append(event)

    return produced


def _evaluate_fences(identity: str, plate: str, point: dict) -> int:
    lng, lat = point.get("lng"), point.get("lat")
    if lng is None or lat is None:
        return 0
    statuses = db.fences_inside_status(float(lng), float(lat))
    if not statuses:
        return 0
    states = db.get_fence_states(identity, [s["id"] for s in statuses])
    point_time = _parse_ts(point.get("gps_time"))
    transitions: list[dict] = []

    for row in statuses:
        fid = row["id"]
        inside = 1 if row["inside"] else 0
        state = states.get(fid)
        transition = {
            "fence_id": fid, "inside": inside, "event": None,
            "risk_level": row["risk_level"], "fence_name": row["fence_name"],
        }
        if state is None:
            # 首见只初始化，不补报
            transitions.append(transition)
            continue
        old = int(state["inside"])
        if old == inside:
            transitions.append(transition)
            continue
        cooldown = int(row["cooldown_sec"])
        if inside == 1 and row["trigger_dir"] in (1, 3):
            if not _cooled(state.get("last_enter_time"), point_time, cooldown):
                transition["event"] = "GEO_ENTER"
        elif inside == 0 and row["trigger_dir"] in (2, 3):
            if not _cooled(state.get("last_exit_time"), point_time, cooldown):
                transition["event"] = "GEO_EXIT"
        transitions.append(transition)

    return db.apply_fence_transitions(identity, plate, point, transitions)


# =====================================================================
# 终端 DSM/ADAS 信号分级
# =====================================================================

def evaluate_signal(channel: str, event_code: str, plate_no: str,
                    identity_code: str | None = None, lng=None, lat=None,
                    speed=None, media_url: str | None = None,
                    confidence: float | None = None) -> dict | None:
    """终端预警信号按启用中的 SIGNAL 规则定级落事件；无匹配/冷却中返回 None。"""
    if not event_code or not plate_no:
        return None
    rule = next(
        (r for r in _rules()
         if r["rule_type"] == "SIGNAL" and r["event_code"] == event_code),
        None,
    )
    if rule is None:
        return None

    now = datetime.now()
    last_times = db.last_rule_event_times(plate_no, [rule["id"]])
    if _cooled(last_times.get(rule["id"]), now, int(rule["cooldown_sec"])):
        return None

    event = {
        "event_code": rule["event_code"],
        "event_source": (channel or "DSM").upper()[:16],
        "plate_no": plate_no,
        "identity_code": identity_code,
        "event_time": now.strftime(_TIME_FMT),
        "lng": lng,
        "lat": lat,
        "speed": speed,
        "risk_level": rule["risk_level"],
        "confidence": confidence if confidence is not None else 0.88,
        "media_url": media_url,
        "rule_id": rule["id"],
        "title": rule["rule_name"],
    }
    db.insert_risk_events([event])
    return event

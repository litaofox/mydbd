"""北斗轨迹模拟器（标准数据集版）：多区域市内运行 + 跨省高速专线模拟。

车辆清单不再硬编码：启动时从标准测试数据集中选取
  · 10 辆市内车：10 个车队各取第 1 辆，在所属城市围栏内平滑随机游走
  ·  2 辆跨省车：上海/合肥各 1 辆专线车，沿高速走廊跨省往返
未加载数据集时回退到北京 5 车演示清单。
正式版本将替换为 JT/T 808 终端协议网关。
"""

import asyncio
import math
import random
from datetime import datetime, timedelta

from app.db import get_conn, insert_gps_points, insert_risk_events, insert_warn_info
from app.services import cep_engine

# 演示事件调度：约每 12~20 秒产生 1 起"终端报警 + 风险事件"（5 分钟约 20 起）
# (累计权重, event_code, rule_id, source, level, 808报警位, 标题, 仅高速跨省车)
# 报警位与 base_warn_type 对齐：1 超速 / 2 疲劳 / 5 设备故障
_DEMO_EVENT_TYPES = [
    (0.30, "SPEED_GENERAL", 1, "national", 2, 1, "一般超速", True),
    (0.50, "SPEED_SEVERE", 2, "national", 3, 1, "严重超速", True),
    (0.72, "FATIGUE_DRIVE", 3, "national", 3, 2, "疲劳驾驶", False),
    (0.88, "DSM_FATIGUE", 4, "DSM", 3, 2, "终端疲劳预警", False),
    (1.00, "DEVICE_FAULT", None, "national", 2, 5, "终端设备故障报警", False),
]

# 城市中心（与 dataset_gen 保持一致）
CITY_CENTER = {
    "济南市": (117.050, 36.651), "青岛市": (120.382, 36.067), "烟台市": (121.448, 37.464),
    "南京市": (118.796, 32.060), "无锡市": (120.312, 31.491), "苏州市": (120.585, 31.299),
    "上海市": (121.400, 31.260), "合肥市": (117.350, 31.840), "芜湖市": (118.433, 31.353),
}
# 跨省走廊
CORRIDORS = {
    "SH_HF": [(121.400, 31.260), (121.005, 31.386), (120.585, 31.299),
              (118.433, 31.353), (117.350, 31.840)],   # 上海→苏州→芜湖→合肥
}

# 数据集未加载时的回退清单（北京）
FALLBACK_VEHICLES = [
    {"identityCode": "SIM_001", "plateNo": "京A12345", "city": None},
    {"identityCode": "SIM_002", "plateNo": "京A23456", "city": None},
    {"identityCode": "SIM_003", "plateNo": "京A34567", "city": None},
    {"identityCode": "SIM_004", "plateNo": "京B45678", "city": None},
    {"identityCode": "SIM_005", "plateNo": "京B56789", "city": None},
]


def _haversine(p1, p2) -> float:
    r = 6371.0
    lon1, lat1, lon2, lat2 = map(math.radians, [p1[0], p1[1], p2[0], p2[1]])
    dlon, dlat = lon2 - lon1, lat2 - lat1
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def _point_at(path, dist):
    if dist <= 0:
        return path[0]
    total = 0.0
    for i in range(1, len(path)):
        seg = _haversine(path[i - 1], path[i])
        if total + seg >= dist:
            t = 0.0 if seg == 0 else (dist - total) / seg
            return (path[i - 1][0] + (path[i][0] - path[i - 1][0]) * t,
                    path[i - 1][1] + (path[i][1] - path[i - 1][1]) * t)
        total += seg
    return path[-1]


def _load_roster() -> list[dict]:
    """从库中选取 10 辆市内车 + 2 辆跨省专线车"""
    sql = """
        SELECT DISTINCT ON (d.id)
               t.identity_code, v.vehicle_no, d.city_code
        FROM traj.traj_dept d
        JOIN traj.traj_vehicle v ON v.dept_id = d.id AND v.valid_mark = 1
        JOIN traj.traj_vehicle_terminal vt ON vt.vehicle_id = v.id
            AND vt.status = 1 AND vt.valid_mark = 1
        JOIN traj.traj_terminal t ON t.id = vt.terminal_id AND t.valid_mark = 1
        WHERE d.dept_type = 2
        ORDER BY d.id, v.id
    """
    cross_sql = """
        SELECT t.identity_code, v.vehicle_no, d.city_code
        FROM traj.traj_vehicle v
        JOIN traj.traj_dept d ON d.id = v.dept_id
        JOIN traj.traj_vehicle_terminal vt ON vt.vehicle_id = v.id
            AND vt.status = 1 AND vt.valid_mark = 1
        JOIN traj.traj_terminal t ON t.id = vt.terminal_id AND t.valid_mark = 1
        WHERE t.identity_code IN ('DS0353', 'DS0452')
    """
    try:
        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(sql)
                local = [{"identityCode": r[0], "plateNo": r[1], "city": r[2],
                          "mode": "local"} for r in cur.fetchall()]
                cur.execute(cross_sql)
                cross = [{"identityCode": r[0], "plateNo": r[1], "city": r[2],
                          "mode": "cross"} for r in cur.fetchall()]
        roster = local + cross
        if not roster:
            return [{**v, "mode": "fallback"} for v in FALLBACK_VEHICLES]
        return roster
    except Exception as exc:
        print(f"[simulator] load roster failed, fallback: {exc}")
        return [{**v, "mode": "fallback"} for v in FALLBACK_VEHICLES]


class SimulatorService:
    def __init__(self):
        self._task: asyncio.Task | None = None
        self._started_at: str | None = None
        self._ticks = 0
        self._vehicles: list[dict] = []
        self._states: dict[str, dict] = {}
        # 演示事件调度状态
        self._last_demo_ts: datetime | None = None
        self._next_gap = random.uniform(12, 20)
        self._demo_seq = 0
        self._demo_events = 0
        self._last_demo_identity: str | None = None

    @property
    def running(self) -> bool:
        return self._task is not None and not self._task.done()

    async def start(self) -> None:
        if self.running:
            return
        self._vehicles = _load_roster()
        self._init_states()
        self._started_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self._last_demo_ts = datetime.now()
        self._next_gap = random.uniform(12, 20)
        self._demo_seq = 0
        self._demo_events = 0
        self._last_demo_identity = None
        self._task = asyncio.create_task(self._loop())

    def _init_states(self):
        self._states = {}
        for v in self._vehicles:
            mode = v["mode"]
            if mode == "cross":
                path = CORRIDORS["SH_HF"]
                total = sum(_haversine(a, b) for a, b in zip(path, path[1:]))
                state = {"mode": "cross", "path": path, "total": total,
                         "dist": random.uniform(0, total),
                         "dir": 1, "speed": random.uniform(70, 90),
                         "mileage": random.uniform(30000, 150000)}
            elif mode == "local":
                c = CITY_CENTER.get(v["city"], (120.0, 32.0))
                state = {"mode": "local", "center": c,
                         "lng": c[0] + random.uniform(-0.08, 0.08),
                         "lat": c[1] + random.uniform(-0.06, 0.06),
                         "heading": random.uniform(0, 2 * math.pi),
                         "speed": random.uniform(25, 60),
                         "mileage": random.uniform(20000, 120000)}
            else:
                state = {"mode": "fallback",
                         "lng": 116.404 + random.uniform(-0.08, 0.08),
                         "lat": 39.912 + random.uniform(-0.06, 0.06),
                         "heading": random.uniform(0, 2 * math.pi),
                         "speed": random.uniform(30, 70),
                         "mileage": random.uniform(10000, 80000)}
            self._states[v["identityCode"]] = state

    async def stop(self) -> None:
        if not self.running:
            return
        self._task.cancel()
        try:
            await self._task
        except asyncio.CancelledError:
            pass
        self._task = None

    def status(self) -> dict:
        return {
            "running": self.running,
            "startedAt": self._started_at,
            "vehicleCount": len(self._vehicles) or 0,
            "ticks": self._ticks,
            "demoEvents": self._demo_events,
            "vehicles": [{"identityCode": v["identityCode"], "plateNo": v["plateNo"],
                          "mode": v["mode"], "city": v.get("city")}
                         for v in self._vehicles],
        }

    async def _loop(self) -> None:
        while True:
            try:
                self._tick()
            except Exception as exc:
                print(f"[simulator] tick error: {exc}")
            await asyncio.sleep(2)

    def _tick_local(self, st) -> tuple[float, float, int, int]:
        # 平滑转向 + 速度小幅波动
        st["heading"] += random.uniform(-0.35, 0.35)
        st["speed"] = max(8.0, min(78.0, st["speed"] + random.uniform(-8, 9)))
        step = st["speed"] / 3600 / 111 * 2
        lng = st["lng"] + step * math.sin(st["heading"]) / max(
            math.cos(math.radians(st["lat"])), 0.5)
        lat = st["lat"] + step * math.cos(st["heading"])
        c = st["center"]
        # 城区围栏边界反弹
        if abs(lng - c[0]) > 0.12 or abs(lat - c[1]) > 0.09:
            st["heading"] += math.pi
            lng, lat = st["lng"], st["lat"]
        else:
            st["lng"], st["lat"] = lng, lat
        return st["lng"], st["lat"], int(st["speed"]), int(math.degrees(st["heading"])) % 360

    def _tick_cross(self, st) -> tuple[float, float, int, int]:
        st["speed"] = max(60.0, min(108.0, st["speed"] + random.uniform(-6, 7)))
        step = st["speed"] / 3600 * 2  # km / 2s
        st["dist"] += step * st["dir"]
        if st["dist"] >= st["total"]:
            st["dist"] = st["total"]
            st["dir"] = -1
        elif st["dist"] <= 0:
            st["dist"] = 0
            st["dir"] = 1
        lng, lat = _point_at(st["path"], st["dist"])
        # 方向取前方 0.2km
        ahead = _point_at(st["path"], min(st["total"], st["dist"] + 0.2 * st["dir"]))
        heading = (math.degrees(math.atan2(
            (ahead[0] - lng) * math.cos(math.radians(lat)), ahead[1] - lat)) + 360) % 360
        return lng, lat, int(st["speed"]), int(heading)

    def _tick_fallback(self, st):
        st["heading"] += random.uniform(-0.5, 0.5)
        speed = random.randint(20, 80)
        step = speed / 3600 / 111 * 2
        st["lng"] += step * math.sin(st["heading"]) / max(
            math.cos(math.radians(st["lat"])), 0.5)
        st["lat"] += step * math.cos(st["heading"])
        return st["lng"], st["lat"], speed, int(math.degrees(st["heading"])) % 360

    def _tick(self) -> None:
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        rows = []
        for v in self._vehicles:
            code = v["identityCode"]
            st = self._states[code]
            if st["mode"] == "cross":
                lng, lat, speed, heading = self._tick_cross(st)
            elif st["mode"] == "local":
                lng, lat, speed, heading = self._tick_local(st)
            else:
                lng, lat, speed, heading = self._tick_fallback(st)
            st["mileage"] += speed * 2 / 3600
            rows.append({
                "identity_code": code,
                "plate_no": v["plateNo"],
                "gps_time": now,
                "lng": round(lng, 6),
                "lat": round(lat, 6),
                "speed": speed,
                "direction": heading,
                "altitude": 50,
                "alarm_flag": 1 if speed >= 95 else 0,
                "mileage": round(st["mileage"], 2),
            })
        insert_gps_points(rows)
        try:
            cep_engine.evaluate_points(rows)
        except Exception as exc:
            print(f"[cep] evaluate error: {exc}")
        try:
            self._maybe_demo_event(rows)
        except Exception as exc:
            print(f"[simulator] demo event error: {exc}")
        self._ticks += 1

    def _maybe_demo_event(self, rows: list[dict]) -> None:
        """按时间间隔均匀地产出演示事件：终端报警 + 风险事件（自动建工单）。

        间隔 12~20 秒抖动，避免事件集中在某一时段；车辆在 12 辆中轮换，
        不连续落在同一辆车。
        """
        now_dt = datetime.now()
        if (now_dt - self._last_demo_ts).total_seconds() < self._next_gap:
            return
        self._last_demo_ts = now_dt
        self._next_gap = random.uniform(12, 20)

        roll = random.random()
        code, rule_id, source, level, warn_type, title, cross_only = (
            _DEMO_EVENT_TYPES[-1][1:])
        for cum, c, rid, src, lv, wt, t, co in _DEMO_EVENT_TYPES:
            if roll <= cum:
                code, rule_id, source, level, warn_type, title, cross_only = (
                    c, rid, src, lv, wt, t, co)
                break

        cross_rows = [r for r in rows
                      if self._states[r["identity_code"]]["mode"] == "cross"]
        if cross_only:
            pool = cross_rows or rows
        else:
            pool = rows
        if len(pool) > 1:  # 车辆轮换，避免连续同一辆
            alt = [r for r in pool if r["identity_code"] != self._last_demo_identity]
            if alt:
                pool = alt
        r = random.choice(pool)
        # 严重超速需要与车辆当前速度相符，不足 95km/h 时降级为一般超速
        if code == "SPEED_SEVERE" and r["speed"] < 95:
            code, rule_id, level, warn_type, title = (
                "SPEED_GENERAL", 1, 2, 1, "一般超速")

        evt_time = now_dt.strftime("%Y-%m-%d %H:%M:%S")
        plate = r["plate_no"]
        insert_risk_events([{
            "event_code": code, "event_source": source, "plate_no": plate,
            "identity_code": r["identity_code"], "event_time": evt_time,
            "lng": r["lng"], "lat": r["lat"], "speed": r["speed"],
            "risk_level": level, "confidence": round(random.uniform(0.8, 0.98), 2),
            "rule_id": rule_id, "title": f"{plate} {title}",
        }])
        end_dt = now_dt + timedelta(seconds=random.randint(20, 300))
        self._demo_seq += 1
        insert_warn_info({
            "source_id": f"SIM{now_dt.strftime('%H%M%S')}{self._demo_seq:03d}",
            "plate_no": plate, "identity_code": r["identity_code"],
            "start_warn_time": evt_time,
            "end_warn_time": end_dt.strftime("%Y-%m-%d %H:%M:%S"),
            "start_gps_time": evt_time,
            "end_gps_time": end_dt.strftime("%Y-%m-%d %H:%M:%S"),
            "start_lng": str(r["lng"]), "start_lat": str(r["lat"]),
            "end_lng": str(r["lng"]), "end_lat": str(r["lat"]),
            "start_speed": r["speed"], "end_speed": max(0, r["speed"] - 10),
            "type_id": warn_type,
        })
        self._demo_events += 1
        self._last_demo_identity = r["identity_code"]


simulator_service = SimulatorService()

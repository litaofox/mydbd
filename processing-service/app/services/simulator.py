"""北斗轨迹模拟器（演示版）：维护模拟车辆状态并周期推送轨迹点。

正式版本将替换为 JT/T 808 终端协议网关；当前为进程内随机游走模拟。
"""

import asyncio
import math
import random
from datetime import datetime

from app.db import insert_gps_points

CENTER_LNG, CENTER_LAT = 116.404, 39.912

VEHICLES = [
    {"identityCode": "SIM_001", "plateNo": "京A12345"},
    {"identityCode": "SIM_002", "plateNo": "京A23456"},
    {"identityCode": "SIM_003", "plateNo": "京A34567"},
    {"identityCode": "SIM_004", "plateNo": "京B45678"},
    {"identityCode": "SIM_005", "plateNo": "京B56789"},
]


class SimulatorService:
    def __init__(self):
        self._task: asyncio.Task | None = None
        self._started_at: str | None = None
        self._ticks = 0
        self._positions: dict[str, tuple[float, float]] = {}
        self._mileage = {v["identityCode"]: 10000.0 for v in VEHICLES}
        for v in VEHICLES:
            angle = random.uniform(0, 2 * math.pi)
            radius = random.uniform(0.02, 0.08)
            self._positions[v["identityCode"]] = (
                CENTER_LNG + radius * math.cos(angle),
                CENTER_LAT + radius * math.sin(angle),
            )

    @property
    def running(self) -> bool:
        return self._task is not None and not self._task.done()

    async def start(self) -> None:
        if self.running:
            return
        self._started_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self._task = asyncio.create_task(self._loop())

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
            "vehicleCount": len(VEHICLES),
            "ticks": self._ticks,
        }

    async def _loop(self) -> None:
        while True:
            try:
                self._tick()
            except Exception as exc:  # 单个 tick 失败不影响后续
                print(f"[simulator] tick error: {exc}")
            await asyncio.sleep(2)

    def _tick(self) -> None:
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        rows = []
        for v in VEHICLES:
            code = v["identityCode"]
            lng, lat = self._positions[code]
            heading = random.uniform(0, 2 * math.pi)
            speed = random.randint(20, 80)
            step_deg = speed / 3600 / 111 * 2  # 2 秒位移（度，近似）
            lng += step_deg * math.sin(heading) / max(math.cos(math.radians(lat)), 0.5)
            lat += step_deg * math.cos(heading)
            self._positions[code] = (lng, lat)
            self._mileage[code] += speed * 2 / 3600
            rows.append({
                "identity_code": code,
                "plate_no": v["plateNo"],
                "gps_time": now,
                "lng": round(lng, 6),
                "lat": round(lat, 6),
                "speed": speed,
                "direction": int(math.degrees(heading)) % 360,
                "altitude": 50,
                "alarm_flag": 0,
                "mileage": round(self._mileage[code], 2),
            })
        insert_gps_points(rows)
        self._ticks += 1


simulator_service = SimulatorService()

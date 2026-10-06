"""终端双锚点解析：手机号两级查找 + truckId 反查 + 未登记隔离。

- 协议/会话层标识 = 终端手机号（报文头 BCD）；业务层 = 网关 truckId；
- 解析顺序：identity_code=手机号 → sim_account=手机号 → gateway_truck_id=truckId；
- 命中且 truckId 为空时回填；查不到进入隔离区（数据不入库不丢弃）；
- 全量预热：消费启动时单次查询装载全部终端双索引，之后按冷却周期整体刷新，
  避免冷启动/缓存过期时对数百台终端逐条 SELECT（逐条查实测 22ms+/台）。
"""

import time

from app import db

_TTL_SEC = 120
# 缓存整体过期后，miss 触发一次全量重载；冷却期内的 miss 才回落单条查询，
# 防止"真正未登记终端"的消息把全量查询打爆
_PREWARM_COOLDOWN_SEC = 120


class TerminalResolver:
    def __init__(self):
        self._by_phone: dict[str, tuple[float, dict | None]] = {}
        self._by_truck: dict[int, tuple[float, dict | None]] = {}
        self._prewarm_at = 0.0

    def _fresh(self, cache, key):
        hit = cache.get(key)
        if hit and time.time() - hit[0] < _TTL_SEC:
            return True, hit[1]
        return False, None

    def prewarm(self) -> int:
        """单次查询全量装载手机号/truckId 双索引（消费启动与周期刷新调用）。"""
        rows = db.list_gateway_terminals()
        now = time.time()
        by_phone: dict[str, tuple[float, dict | None]] = {}
        by_truck: dict[int, tuple[float, dict | None]] = {}
        for term in rows:
            entry = (now, term)
            if term.get("identity_code"):
                by_phone[term["identity_code"]] = entry
            sim = term.get("sim_account")
            if sim and sim != term.get("identity_code"):
                by_phone[sim] = entry
            if term.get("gateway_truck_id") is not None:
                by_truck[term["gateway_truck_id"]] = entry
        self._by_phone = by_phone
        self._by_truck = by_truck
        self._prewarm_at = now
        return len(rows)

    def _maybe_prewarm(self) -> None:
        if time.time() - self._prewarm_at >= _PREWARM_COOLDOWN_SEC:
            self.prewarm()

    def _lookup_phone(self, phone: str) -> dict | None:
        fresh, cached = self._fresh(self._by_phone, phone)
        if fresh:
            return cached
        # 缓存整体过期：先尝试一次全量刷新（冷却内跳过），再回落单条查询
        self._maybe_prewarm()
        hit = self._by_phone.get(phone)
        if hit and time.time() - hit[0] < _TTL_SEC:
            return hit[1]
        term = db.find_terminal_by_phone(phone)
        self._by_phone[phone] = (time.time(), term)
        return term

    def _lookup_truck(self, truck_id) -> dict | None:
        fresh, cached = self._fresh(self._by_truck, truck_id)
        if fresh:
            return cached
        self._maybe_prewarm()
        hit = self._by_truck.get(truck_id)
        if hit and time.time() - hit[0] < _TTL_SEC:
            return hit[1]
        term = db.find_terminal_by_truck(truck_id)
        self._by_truck[truck_id] = (time.time(), term)
        return term

    def resolve(self, phone: str | None, truck_id=None, plate_no: str | None = None) -> dict | None:
        """手机号（其次 truckId）解析平台终端；未命中记入隔离区并返回 None。"""
        term = None
        if phone:
            term = self._lookup_phone(phone)
        if term is None and truck_id is not None:
            term = self._lookup_truck(truck_id)
        if term is not None:
            tid = term.get("id")
            if truck_id is not None and not term.get("gateway_truck_id"):
                try:
                    db.backfill_truck_id(tid, truck_id)
                    term["gateway_truck_id"] = truck_id
                except Exception as exc:
                    print(f"[gateway] backfill truck_id error: {exc}")
            if term.get("gateway_truck_id") is not None:
                self._by_truck[term["gateway_truck_id"]] = (time.time(), term)
            return term
        # 未登记：隔离区计数，数据不丢
        try:
            db.upsert_unknown_terminal(phone, truck_id, plate_no)
        except Exception as exc:
            print(f"[gateway] record unknown terminal error: {exc}")
        return None

    def resolve_by_truck(self, truck_id) -> dict | None:
        """离线事件仅携带 truckId 时使用。"""
        if truck_id is None:
            return None
        return self._lookup_truck(truck_id)


resolver = TerminalResolver()

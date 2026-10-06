"""PostgreSQL/PostGIS 数据访问"""

import contextlib

import psycopg2
import psycopg2.extras
from psycopg2 import pool as pg_pool

from app.config import settings

# 进程内连接池：网关消费为高频短事务（每条消息若干次 DB 调用），
# 逐条新建连接实测 22ms+/次，池化后借还亚毫秒。SimpleConnectionPool 自带锁，
# 可被 FastAPI 线程、simulator、gateway consumer/mock 线程共用。
_pool = pg_pool.SimpleConnectionPool(1, 12, dsn=settings.dsn)


@contextlib.contextmanager
def get_conn():
    conn = _pool.getconn()
    try:
        yield conn
        conn.commit()
    except Exception:
        try:
            conn.rollback()
        except Exception:
            pass
        raise
    finally:
        # closed!=0 表示连接已断/事务中崩溃，交还时关闭并由池补建新连接
        _pool.putconn(conn, close=(conn.closed != 0))


@contextlib.contextmanager
def session(conn=None):
    """批量热路径复用外部连接（事务提交由外层统一管理）；
    不传 conn 时自取池化连接，退出即提交（单条调用方行为不变）。"""
    if conn is not None:
        yield conn
    else:
        with get_conn() as own:
            yield own


def insert_gps_points(rows: list[dict]) -> int:
    """批量写入轨迹点，同时维护 PostGIS location 列。

    rows 元素键：identity_code, plate_no, gps_time, lng, lat,
                 speed, direction, altitude, alarm_flag, mileage
    """
    if not rows:
        return 0
    sql = """
        INSERT INTO traj.traj_gps_point
            (identity_code, plate_no, gps_time, lng, lat, speed,
             direction, altitude, alarm_flag, mileage, location)
        VALUES %s
    """
    values = [
        (
            r["identity_code"], r["plate_no"], r["gps_time"],
            r["lng"], r["lat"], r.get("speed"), r.get("direction"),
            r.get("altitude"), r.get("alarm_flag", 0), r.get("mileage"),
            r["lng"], r["lat"],
        )
        for r in rows
    ]
    with get_conn() as conn:
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(
                cur,
                sql,
                values,
                template="""(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                             ST_SetSRID(ST_MakePoint(%s, %s), 4326))""",
            )
    return len(rows)


def delete_gps_points(identity_code: str, start_time: str, end_time: str) -> None:
    """按设备与时间范围清理（样例重导前的幂等处理）"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                DELETE FROM traj.traj_gps_point
                WHERE identity_code = %s AND gps_time BETWEEN %s AND %s
                """,
                (identity_code, start_time, end_time),
            )


def count_risk_events_on(date_str: str) -> int:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT count(*) FROM mon.risk_event WHERE event_time::date = %s",
                (date_str,),
            )
            return cur.fetchone()[0]


_SLA_CACHE: dict | None = None
_SLA_CACHE_AT = 0.0


def _load_sla(cur) -> dict:
    """F20 风险等级 → (处置时限分钟, 升级宽限分钟)，60 秒缓存；缺省 60/30。"""
    global _SLA_CACHE, _SLA_CACHE_AT
    import time
    if _SLA_CACHE is None or time.time() - _SLA_CACHE_AT > 60:
        cur.execute("SELECT risk_level, limit_min, grace_min FROM mon.risk_order_sla")
        _SLA_CACHE = {lv: (lm, gm) for lv, lm, gm in cur.fetchall()}
        _SLA_CACHE_AT = time.time()
    return _SLA_CACHE


def insert_risk_events(rows: list[dict], conn=None) -> int:
    if not rows:
        return 0
    sql = """
        INSERT INTO mon.risk_event
            (event_code, event_source, plate_no, identity_code, event_time, lng, lat,
             speed, risk_level, confidence, media_url, handle_status,
             rule_id, fence_id, title)
        VALUES %s
        RETURNING id, risk_level, COALESCE(event_time, CURRENT_TIMESTAMP)::timestamp
    """
    values = [
        (
            r.get("event_code"), r.get("event_source"), r.get("plate_no"),
            r.get("identity_code"), r.get("event_time"), r.get("lng"), r.get("lat"),
            r.get("speed"), r.get("risk_level", 2), r.get("confidence"),
            r.get("media_url"), 0,
            r.get("rule_id"), r.get("fence_id"), r.get("title"),
        )
        for r in rows
    ]
    with session(conn) as conn:
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(cur, sql, values)
            inserted = cur.fetchall()  # (id, risk_level, event_time)
            from datetime import timedelta
            sla = _load_sla(cur)
            order_values = []
            src_by_id = {}
            # 以 event id 回填原始行快照
            for (eid, lv, etime), raw in zip(inserted, rows):
                src_by_id[eid] = raw
            for eid, lv, etime in inserted:
                limit_min, grace_min = sla.get(lv, (60, 30))
                raw = src_by_id.get(eid, {})
                deadline = etime + timedelta(minutes=limit_min)
                order_values.append((
                    eid, raw.get("title"), raw.get("event_code"), raw.get("event_source"),
                    raw.get("plate_no"), raw.get("identity_code"), lv or 2, etime,
                    deadline, limit_min, grace_min,
                ))
            psycopg2.extras.execute_values(
                cur,
                """
                INSERT INTO mon.risk_work_order
                    (order_no, event_id, event_title, event_code, event_source, plate_no,
                     identity_code, risk_level, event_time, status, deadline,
                     sla_limit_min, grace_min, creator)
                SELECT mon.fmt_order_no(), v.event_id, v.event_title, v.event_code,
                       v.event_source, v.plate_no, v.identity_code, v.risk_level,
                       v.event_time::timestamp, 'PENDING', v.deadline::timestamp,
                       v.limit_min, v.grace_min, 'cep-engine'
                FROM (VALUES %s) AS v(event_id, event_title, event_code, event_source,
                                      plate_no, identity_code, risk_level, event_time,
                                      deadline, limit_min, grace_min)
                RETURNING id
                """,
                order_values,
            )
            order_ids = [row[0] for row in cur.fetchall()]
            psycopg2.extras.execute_values(
                cur,
                """
                INSERT INTO mon.risk_order_log
                    (order_id, action, to_status, operator_name, remark)
                VALUES %s
                """,
                [(oid, "CREATE", "PENDING", "CEP", "风险事件自动建单") for oid in order_ids],
            )
    return len(rows)


def insert_warn_info(row: dict) -> None:
    """写入一条终端报警（模拟器演示事件与 risk_event 成对产生）。"""
    sql = """
        INSERT INTO traj.traj_warn_info
            (source_id, plate_no, identity_code, start_warn_time, end_warn_time,
             start_gps_time, end_gps_time, start_lng, start_lat, end_lng, end_lat,
             start_speed, end_speed, type_id, warn_continue_mark,
             handle_status, creator)
        VALUES (%(source_id)s, %(plate_no)s, %(identity_code)s, %(start_warn_time)s,
                %(end_warn_time)s, %(start_gps_time)s, %(end_gps_time)s,
                %(start_lng)s, %(start_lat)s, %(end_lng)s, %(end_lat)s,
                %(start_speed)s, %(end_speed)s, %(type_id)s, 1, 0, 'simulator')
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, row)


# =====================================================================
# F18 CEP 引擎数据访问
# =====================================================================

def load_enabled_rules() -> list[dict]:
    """启用中的风控规则（params jsonb 由 psycopg2 自动解析为 dict）"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, rule_code, rule_name, rule_type, event_code,
                       risk_level, params, cooldown_sec
                FROM mon.risk_rule
                WHERE status = 1 AND valid_mark = 1
                ORDER BY id
                """
            )
            cols = [d[0] for d in cur.description]
            return [dict(zip(cols, row)) for row in cur.fetchall()]


def load_enabled_fences() -> list[dict]:
    """启用中的电子围栏（标量字段；几何判定下推 SQL，不取 geom）"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, fence_name, fence_type,
                       center_lng, center_lat, radius_m,
                       trigger_dir, risk_level, cooldown_sec
                FROM mon.risk_geo_fence
                WHERE status = 1 AND valid_mark = 1
                ORDER BY id
                """
            )
            cols = [d[0] for d in cur.description]
            return [dict(zip(cols, row)) for row in cur.fetchall()]


def latest_driving_session(identity_code: str, as_of: str,
                           continuous_min: int, gap_min: int, conn=None):
    """最近一段"连续行驶会话"的 (起点, 终点)。

    speed>0 的点为行驶点；相邻行驶点间隔 > gap_min 则切分会话；
    只回看 continuous_min + gap_min 分钟。返回 (datetime, datetime) 或 None。
    """
    sql = """
        WITH base AS (
            SELECT gps_time,
                   LAG(gps_time) OVER (ORDER BY gps_time) AS prev_t
            FROM traj.traj_gps_point
            WHERE identity_code = %s
              AND speed > 0
              AND gps_time BETWEEN %s::timestamp - ((%s + %s) * interval '1 minute')
                               AND %s::timestamp
        ),
        grp AS (
            SELECT gps_time,
                   COUNT(*) FILTER (
                       WHERE prev_t IS NULL
                          OR gps_time - prev_t > %s * interval '1 minute'
                   ) OVER (ORDER BY gps_time) AS g
            FROM base
        )
        SELECT MIN(gps_time) AS s0, MAX(gps_time) AS s1
        FROM grp
        GROUP BY g
        ORDER BY s1 DESC
        LIMIT 1
    """
    with session(conn) as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (identity_code, as_of, continuous_min, gap_min,
                              as_of, gap_min))
            return cur.fetchone()


def fences_inside_status(lng: float, lat: float, conn=None) -> list[dict]:
    """给定一个点，返回所有启用围栏的 inside 判定（圆形用 geography 米制）"""
    sql = """
        SELECT f.id, f.fence_name, f.trigger_dir, f.risk_level,
               f.cooldown_sec,
               CASE WHEN f.fence_type = 'CIRCLE'
                    THEN ST_DWithin(
                        ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography,
                        ST_SetSRID(ST_MakePoint(f.center_lng, f.center_lat),
                                   4326)::geography,
                        f.radius_m)
                    ELSE ST_Contains(f.polygon_geom,
                        ST_SetSRID(ST_MakePoint(%s, %s), 4326))
               END AS inside
        FROM mon.risk_geo_fence f
        WHERE f.status = 1 AND f.valid_mark = 1
    """
    with session(conn) as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (lng, lat, lng, lat))
            cols = [d[0] for d in cur.description]
            return [dict(zip(cols, row)) for row in cur.fetchall()]


def get_fence_states(identity_code: str, fence_ids: list[int], conn=None) -> dict:
    if not fence_ids:
        return {}
    with session(conn) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT fence_id, inside, last_enter_time, last_exit_time
                FROM mon.risk_fence_state
                WHERE identity_code = %s AND fence_id = ANY(%s)
                """,
                (identity_code, fence_ids),
            )
            return {
                row[0]: {"inside": row[1], "last_enter_time": row[2],
                         "last_exit_time": row[3]}
                for row in cur.fetchall()
            }


def apply_fence_transitions(identity_code: str, plate_no: str,
                            point: dict, transitions: list[dict],
                            conn=None) -> int:
    """在同一事务内：upsert 围栏状态 + 插入越界事件。

    transitions 元素：{fence_id, inside, event: None|'GEO_ENTER'|'GEO_EXIT',
                       risk_level, fence_name, cooldown_sec}
    """
    if not transitions:
        return 0
    upsert_sql = """
        INSERT INTO mon.risk_fence_state
            (identity_code, fence_id, inside, last_point_time,
             last_enter_time, last_exit_time, update_date)
        VALUES (%s, %s, %s, %s::timestamp,
                CASE WHEN %s = 'GEO_ENTER' THEN %s::timestamp ELSE NULL END,
                CASE WHEN %s = 'GEO_EXIT' THEN %s::timestamp ELSE NULL END,
                CURRENT_TIMESTAMP)
        ON CONFLICT (identity_code, fence_id) DO UPDATE SET
            inside = EXCLUDED.inside,
            last_point_time = EXCLUDED.last_point_time,
            last_enter_time = COALESCE(EXCLUDED.last_enter_time,
                                      mon.risk_fence_state.last_enter_time),
            last_exit_time = COALESCE(EXCLUDED.last_exit_time,
                                     mon.risk_fence_state.last_exit_time),
            update_date = CURRENT_TIMESTAMP
    """
    event_sql = """
        INSERT INTO mon.risk_event
            (event_code, event_source, plate_no, identity_code, event_time,
             lng, lat, speed, risk_level, confidence, handle_status,
             fence_id, title)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 1.00, 0, %s, %s)
    """
    event_count = 0
    with session(conn) as conn:
        with conn.cursor() as cur:
            for t in transitions:
                event_time = point.get("gps_time")
                cur.execute(
                    upsert_sql,
                    (identity_code, t["fence_id"], t["inside"], event_time,
                     t.get("event"), event_time,
                     t.get("event"), event_time),
                )
                if t.get("event"):
                    title_prefix = "进入围栏" if t["event"] == "GEO_ENTER" else "离开围栏"
                    cur.execute(
                        event_sql,
                        (t["event"], "北斗", plate_no, identity_code, event_time,
                         point.get("lng"), point.get("lat"), point.get("speed"),
                         t["risk_level"], t["fence_id"],
                         f"{title_prefix}·{t['fence_name']}"),
                    )
                    event_count += 1
    return event_count


def last_rule_event_times(plate_no: str, rule_ids: list[int], conn=None) -> dict:
    """本车各规则最近事件时间（冷却判定）"""
    if not rule_ids:
        return {}
    with session(conn) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT rule_id, MAX(event_time)
                FROM mon.risk_event
                WHERE plate_no = %s AND rule_id = ANY(%s)
                GROUP BY rule_id
                """,
                (plate_no, rule_ids),
            )
            return {row[0]: row[1] for row in cur.fetchall()}


def has_recent_rule_event(plate_no: str, rule_id: int, since, conn=None) -> bool:
    with session(conn) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT 1 FROM mon.risk_event
                WHERE plate_no = %s AND rule_id = %s AND event_time >= %s
                LIMIT 1
                """,
                (plate_no, rule_id, since),
            )
            return cur.fetchone() is not None


def upsert_video_analysis(task: dict) -> None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO mon.video_analysis
                    (task_id, plate_no, channel, clip_url, status,
                     result_summary, event_count, create_date, update_date)
                VALUES (%(task_id)s, %(plate_no)s, %(channel)s, %(clip_url)s,
                        %(status)s, %(result_summary)s, %(event_count)s,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                task,
            )


def update_video_analysis(task_id: str, status: str, summary: str, event_count: int) -> None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE mon.video_analysis
                SET status = %s, result_summary = %s, event_count = %s,
                    update_date = CURRENT_TIMESTAMP
                WHERE task_id = %s
                """,
                (status, summary, event_count, task_id),
            )


# =====================================================================
# vps 网关对接数据访问（GATEWAY-PLAN-001；只增不改既有函数）
# =====================================================================

def load_gateway_config_rows() -> dict:
    """读取 sys_config 中 gateway.* 启动参数（仅启动时调用一次）。

    返回 {config_key: config_value}；数据库不可用时由调用方回退环境变量。
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT config_key, config_value
                FROM traj.sys_config
                WHERE config_key LIKE 'gateway.%%'
                """
            )
            return {r[0]: (r[1] or "").strip() for r in cur.fetchall()}


def set_gateway_mock_delivery(enabled: bool) -> None:
    """持久化 mock 仿真投递开关（运行时启停时同步写入，重启后保持）。"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO traj.sys_config
                    (config_key, config_value, config_name, value_type,
                     is_system, remark, creator)
                VALUES
                    ('gateway.mock-delivery-enabled', %s,
                     '仿真投递开关（on投递 / off停止，仅测试模式）', 'STRING', 1,
                     '运行时启停即时生效；重启 processing 后保持', 'system')
                ON CONFLICT (config_key) DO UPDATE
                    SET config_value = EXCLUDED.config_value,
                        update_date = CURRENT_TIMESTAMP,
                        updater = 'processing'
                """,
                ("on" if enabled else "off",),
            )


def insert_gps_points_ext(rows: list[dict]) -> int:
    """网关轨迹点批量写入（多 ext 原始列；同终端同 trackId 幂等去重）。"""
    if not rows:
        return 0
    sql = """
        INSERT INTO traj.traj_gps_point
            (identity_code, plate_no, gps_time, lng, lat, speed,
             direction, altitude, alarm_flag, mileage, ext, location)
        VALUES %s
        ON CONFLICT (identity_code, (ext ->> 'trackId')) WHERE ext ? 'trackId'
        DO NOTHING
    """
    values = [
        (
            r["identity_code"], r.get("plate_no"), r["gps_time"],
            r["lng"], r["lat"], r.get("speed"), r.get("direction"),
            r.get("altitude"), r.get("alarm_flag", 0), r.get("mileage"),
            psycopg2.extras.Json(r.get("ext") or {}),
            r["lng"], r["lat"],
        )
        for r in rows
    ]
    with get_conn() as conn:
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(
                cur,
                sql,
                values,
                template="""(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                             ST_SetSRID(ST_MakePoint(%s, %s), 4326))""",
            )
    return len(rows)


def find_terminal_by_phone(phone: str) -> dict | None:
    """两级查找：identity_code=手机号 → sim_account=手机号。"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT t.id, t.identity_code, t.gateway_truck_id, v.vehicle_no AS plate_no
                FROM traj.traj_terminal t
                LEFT JOIN traj.traj_vehicle_terminal vt
                       ON vt.terminal_id = t.id AND vt.valid_mark = 1
                LEFT JOIN traj.traj_vehicle v
                       ON v.id = vt.vehicle_id AND v.valid_mark = 1
                WHERE t.valid_mark = 1
                  AND (t.identity_code = %s OR t.sim_account = %s)
                LIMIT 1
                """,
                (phone, phone),
            )
            row = cur.fetchone()
            if not row:
                return None
            return {"id": row[0], "identity_code": row[1],
                    "gateway_truck_id": row[2], "plate_no": row[3]}


def find_terminal_by_truck(truck_id) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT t.id, t.identity_code, t.gateway_truck_id, v.vehicle_no AS plate_no
                FROM traj.traj_terminal t
                LEFT JOIN traj.traj_vehicle_terminal vt
                       ON vt.terminal_id = t.id AND vt.valid_mark = 1
                LEFT JOIN traj.traj_vehicle v
                       ON v.id = vt.vehicle_id AND v.valid_mark = 1
                WHERE t.valid_mark = 1 AND t.gateway_truck_id = %s
                LIMIT 1
                """,
                (truck_id,),
            )
            row = cur.fetchone()
            if not row:
                return None
            return {"id": row[0], "identity_code": row[1],
                    "gateway_truck_id": row[2], "plate_no": row[3]}


def list_gateway_terminals() -> list[dict]:
    """全量有效终端（含 sim_account 双手机号锚点），供 resolver 单次查询预热，
    替代冷启动/缓存过期时对每个终端逐条 SELECT。"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT DISTINCT t.id, t.identity_code, t.sim_account,
                       t.gateway_truck_id,
                       FIRST_VALUE(v.vehicle_no) OVER (
                           PARTITION BY t.id ORDER BY vt.id) AS plate_no
                FROM traj.traj_terminal t
                LEFT JOIN traj.traj_vehicle_terminal vt
                       ON vt.terminal_id = t.id AND vt.valid_mark = 1
                LEFT JOIN traj.traj_vehicle v
                       ON v.id = vt.vehicle_id AND v.valid_mark = 1
                WHERE t.valid_mark = 1
                """
            )
            return [
                {"id": r[0], "identity_code": r[1], "sim_account": r[2],
                 "gateway_truck_id": r[3], "plate_no": r[4]}
                for r in cur.fetchall()
            ]


def backfill_truck_id(terminal_id, truck_id) -> None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE traj.traj_terminal
                SET gateway_truck_id = %s, update_date = CURRENT_TIMESTAMP
                WHERE id = %s AND gateway_truck_id IS NULL
                """,
                (truck_id, terminal_id),
            )


def upsert_unknown_terminal(phone, truck_id, plate_no) -> None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO traj.gateway_unknown_terminal
                    (phone_number, truck_id, plate_no)
                VALUES (%s, %s, %s)
                ON CONFLICT (COALESCE(phone_number, ''), COALESCE(truck_id, -1))
                DO UPDATE SET msg_count = traj.gateway_unknown_terminal.msg_count + 1,
                              plate_no = COALESCE(EXCLUDED.plate_no,
                                                  traj.gateway_unknown_terminal.plate_no),
                              last_seen = CURRENT_TIMESTAMP
                """,
                (phone, truck_id, plate_no),
            )


def upsert_terminal_online(terminal_id, event: str, ts) -> None:
    """event: online(0102) / heartbeat / offline（离线仅 truckId 反查后调用）。"""
    sets = {
        "online": "online_status = 1, last_online_time = %(ts)s, "
                  "last_heartbeat_time = %(ts)s",
        "heartbeat": "online_status = 1, last_heartbeat_time = %(ts)s",
        "offline": "online_status = 0, last_offline_time = %(ts)s",
    }
    if event not in sets:
        return
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                UPDATE traj.traj_terminal
                SET {sets[event]}, update_date = CURRENT_TIMESTAMP
                WHERE id = %(id)s
                """,
                {"id": terminal_id, "ts": ts},
            )


def upsert_warn_lifecycle(w: dict) -> None:
    """报警开闭环：按 source_id=warnId 开始插入、结束回填结束字段。"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id FROM traj.traj_warn_info WHERE source_id = %s",
                (w["warn_id"],),
            )
            found = cur.fetchone()
            if found is None:
                cur.execute(
                    """
                    INSERT INTO traj.traj_warn_info
                        (source_id, plate_no, identity_code, start_warn_time,
                         end_warn_time, start_gps_time, end_gps_time,
                         start_lng, start_lat, end_lng, end_lat,
                         start_speed, end_speed, type_id, warn_continue_mark,
                         handle_status, creator)
                    VALUES (%(warn_id)s, %(plate_no)s, %(identity_code)s,
                            %(start_warn_time)s, %(end_warn_time)s,
                            %(start_gps_time)s, %(end_gps_time)s,
                            %(start_lng)s, %(start_lat)s, %(end_lng)s, %(end_lat)s,
                            %(start_speed)s, %(end_speed)s, %(type_id)s,
                            CASE WHEN %(end_warn_time)s IS NULL THEN 1 ELSE 0 END,
                            0, 'jt808')
                    """,
                    w,
                )
            elif w.get("end_warn_time"):
                cur.execute(
                    """
                    UPDATE traj.traj_warn_info
                    SET end_warn_time = %(end_warn_time)s,
                        end_gps_time = %(end_gps_time)s,
                        end_lng = %(end_lng)s, end_lat = %(end_lat)s,
                        end_speed = %(end_speed)s,
                        warn_continue_mark = 0,
                        updater = 'jt808', update_date = CURRENT_TIMESTAMP
                    WHERE source_id = %(warn_id)s
                    """,
                    w,
                )


def insert_gateway_risk_event(row: dict) -> bool:
    """网关报警 → 风险事件 + 自动工单（source_id=warnId 幂等；重复返回 False）。

    单线程消费者下前置检查即可去重；唯一索引 uk_risk_event_source 兜底。
    """
    sid = row.get("source_id")
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT 1 FROM mon.risk_event WHERE source_id = %s", (sid,))
            if cur.fetchone():
                return False
            cur.execute(
                """
                INSERT INTO mon.risk_event
                    (event_code, event_source, plate_no, identity_code, event_time,
                     lng, lat, speed, risk_level, confidence, handle_status,
                     title, source_id)
                VALUES (%(event_code)s, %(event_source)s, %(plate_no)s,
                        %(identity_code)s, %(event_time)s, %(lng)s, %(lat)s,
                        %(speed)s, %(risk_level)s, %(confidence)s, 0,
                        %(title)s, %(source_id)s)
                RETURNING id, risk_level,
                          COALESCE(event_time, CURRENT_TIMESTAMP)::timestamp
                """,
                row,
            )
            eid, lv, etime = cur.fetchone()
            from datetime import timedelta
            limit_min, grace_min = _load_sla(cur).get(lv, (60, 30))
            deadline = etime + timedelta(minutes=limit_min)
            cur.execute(
                """
                INSERT INTO mon.risk_work_order
                    (order_no, event_id, event_title, event_code, event_source,
                     plate_no, identity_code, risk_level, event_time, status,
                     deadline, sla_limit_min, grace_min, creator)
                VALUES (mon.fmt_order_no(), %s, %s, %s, %s, %s, %s, %s, %s,
                        'PENDING', %s, %s, %s, 'cep-engine')
                RETURNING id
                """,
                (eid, row.get("title"), row.get("event_code"), row.get("event_source"),
                 row.get("plate_no"), row.get("identity_code"), lv or 2, etime,
                 deadline, limit_min, grace_min),
            )
            order_id = cur.fetchone()[0]
            cur.execute(
                """
                INSERT INTO mon.risk_order_log
                    (order_id, action, to_status, operator_name, remark)
                VALUES (%s, 'CREATE', 'PENDING', 'jt808-gateway', '网关报警自动建单')
                """,
                (order_id,),
            )
    return True


def insert_warn_media(row: dict) -> None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO traj.traj_warn_media
                    (warn_id, identity_code, truck_id, file_name, file_type,
                     file_size, url, local_path, file_status, receive_time)
                VALUES (%(warn_id)s, %(identity_code)s, %(truck_id)s,
                        %(file_name)s, %(file_type)s, %(file_size)s, %(url)s,
                        %(local_path)s, %(file_status)s, %(receive_time)s)
                ON CONFLICT (warn_id, file_name) DO UPDATE SET
                    file_status = EXCLUDED.file_status,
                    local_path = COALESCE(EXCLUDED.local_path,
                                          traj.traj_warn_media.local_path),
                    url = EXCLUDED.url,
                    file_size = EXCLUDED.file_size,
                    receive_time = EXCLUDED.receive_time
                """,
                row,
            )


def get_warn_media(media_id: int) -> dict | None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, warn_id, file_name, file_type, local_path, url
                FROM traj.traj_warn_media WHERE id = %s
                """,
                (media_id,),
            )
            row = cur.fetchone()
            if not row:
                return None
            return {"id": row[0], "warn_id": row[1], "file_name": row[2],
                    "file_type": row[3], "local_path": row[4], "url": row[5]}


def insert_dlq(topic: str, partition, offset, payload: str, error: str) -> None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO traj.gateway_dlq
                    (topic, partition_id, offset_val, payload, error)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (topic, partition, offset, payload, error),
            )


def flush_ingest_stats(buf: dict) -> None:
    """批量刷新各 topic 消费统计（消费线程内存聚合后每秒落库一次）。

    buf: {topic: [ok_count, err_count]}；一条 SQL 完成全部 topic 累加，
    替代逐消息 upsert（旧实现每消息一次连接+事务，是消费主热点）。
    """
    rows = [(t, c[0], c[1]) for t, c in buf.items() if c[0] or c[1]]
    if not rows:
        return
    with get_conn() as conn:
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(
                cur,
                """
                INSERT INTO traj.gateway_ingest_stat
                    (topic, msg_count, err_count, last_time)
                VALUES %s
                ON CONFLICT (topic) DO UPDATE SET
                    msg_count = traj.gateway_ingest_stat.msg_count + EXCLUDED.msg_count,
                    err_count = traj.gateway_ingest_stat.err_count + EXCLUDED.err_count,
                    last_time = EXCLUDED.last_time,
                    update_time = CURRENT_TIMESTAMP
                """,
                [(t, ok, err) for t, ok, err in rows],
                template="(%s, %s, %s, CURRENT_TIMESTAMP)",
            )


_EVENT_MAP_CACHE: dict | None = None
_EVENT_MAP_AT = 0.0


def load_gateway_event_map() -> dict:
    """网关报警类型映射（60 秒缓存）：{type_id: {event_code, default_level, name}}"""
    global _EVENT_MAP_CACHE, _EVENT_MAP_AT
    import time
    if _EVENT_MAP_CACHE is None or time.time() - _EVENT_MAP_AT > 60:
        with get_conn() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT type_id, event_code, default_level, name
                    FROM traj.gateway_event_map WHERE valid_mark = 1
                    """
                )
                _EVENT_MAP_CACHE = {
                    r[0]: {"event_code": r[1], "default_level": r[2], "name": r[3]}
                    for r in cur.fetchall()
                }
        _EVENT_MAP_AT = time.time()
    return _EVENT_MAP_CACHE


def gateway_status_data() -> dict:
    """接入状态页数据：各 topic 统计 + 死信/未登记计数。"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT topic, msg_count, err_count, last_time
                FROM traj.gateway_ingest_stat ORDER BY topic
                """
            )
            stats = [{"topic": r[0], "msgCount": r[1], "errCount": r[2],
                      "lastTime": r[3].strftime("%Y-%m-%d %H:%M:%S") if r[3] else None}
                     for r in cur.fetchall()]
            cur.execute("SELECT count(*) FROM traj.gateway_dlq")
            dlq = cur.fetchone()[0]
            cur.execute("SELECT count(*) FROM traj.gateway_unknown_terminal")
            unknown = cur.fetchone()[0]
    return {"stats": stats, "dlqCount": dlq, "unknownCount": unknown}


def list_unknown_terminals(limit: int = 200) -> list[dict]:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, phone_number, truck_id, plate_no, msg_count,
                       first_seen, last_seen
                FROM traj.gateway_unknown_terminal
                ORDER BY last_seen DESC LIMIT %s
                """,
                (limit,),
            )
            return [{
                "id": r[0], "phoneNumber": r[1], "truckId": r[2], "plateNo": r[3],
                "msgCount": r[4],
                "firstSeen": r[5].strftime("%Y-%m-%d %H:%M:%S") if r[5] else None,
                "lastSeen": r[6].strftime("%Y-%m-%d %H:%M:%S") if r[6] else None,
            } for r in cur.fetchall()]

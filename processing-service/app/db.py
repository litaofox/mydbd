"""PostgreSQL/PostGIS 数据访问"""

import contextlib

import psycopg2
import psycopg2.extras

from app.config import settings


@contextlib.contextmanager
def get_conn():
    conn = psycopg2.connect(settings.dsn)
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


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


def insert_risk_events(rows: list[dict]) -> int:
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
    with get_conn() as conn:
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
                           continuous_min: int, gap_min: int):
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
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (identity_code, as_of, continuous_min, gap_min,
                              as_of, gap_min))
            return cur.fetchone()


def fences_inside_status(lng: float, lat: float) -> list[dict]:
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
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (lng, lat, lng, lat))
            cols = [d[0] for d in cur.description]
            return [dict(zip(cols, row)) for row in cur.fetchall()]


def get_fence_states(identity_code: str, fence_ids: list[int]) -> dict:
    if not fence_ids:
        return {}
    with get_conn() as conn:
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
                            point: dict, transitions: list[dict]) -> int:
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
    with get_conn() as conn:
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


def last_rule_event_times(plate_no: str, rule_ids: list[int]) -> dict:
    """本车各规则最近事件时间（冷却判定）"""
    if not rule_ids:
        return {}
    with get_conn() as conn:
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


def has_recent_rule_event(plate_no: str, rule_id: int, since) -> bool:
    with get_conn() as conn:
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

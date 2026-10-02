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
        psycopg2.extras.execute_values(
            conn,
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


def insert_risk_events(rows: list[dict]) -> int:
    if not rows:
        return 0
    sql = """
        INSERT INTO mon.risk_event
            (event_code, event_source, plate_no, event_time, lng, lat,
             speed, risk_level, confidence, media_url, handle_status)
        VALUES %s
    """
    values = [
        (
            r.get("event_code"), r.get("event_source"), r.get("plate_no"),
            r.get("event_time"), r.get("lng"), r.get("lat"), r.get("speed"),
            r.get("risk_level", 2), r.get("confidence"),
            r.get("media_url"), 0,
        )
        for r in rows
    ]
    with get_conn() as conn:
        psycopg2.extras.execute_values(conn, sql, values)
    return len(rows)


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

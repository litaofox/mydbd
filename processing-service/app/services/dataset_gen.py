"""标准测试数据集生成器（可重复生成）。

覆盖：4 家公司 / 10 个车队 / 500 车 / 500 终端 / 500 司机 / 2026-09 全月轨迹
地区：山东（济南/青岛/烟台）、江苏（南京/无锡/苏州）、上海、安徽（合肥/芜湖）
含：省内行程 + 跨省高速走廊行程、终端报警、风险事件+工单+干预、每日驾驶评分。

确定性：固定随机种子，任意环境生成结果一致；数据集主数据 creator='SEED_DS'，
终端号 DS%04d，清空时可整体替换。
"""

import json
import math
import random
import threading
from datetime import datetime, timedelta, date, time

import psycopg2.extras

from app.db import get_conn, insert_gps_points

CREATOR = "SEED_DS"
MONTH_START = date(2026, 9, 1)
MONTH_DAYS = 30

# 固定种子，保证数据集可重复
RNG = random.Random(20260930)

# =====================================================================
# 组织与地理模型
# =====================================================================
FLEETS = [
    # 山东 3 队 150 车
    {"id": 110, "co": 10, "name": "济南干线车队", "prov": "山东省", "city": "济南市",
     "county": "历下区", "plate": "鲁A", "center": (117.120, 36.651), "corr": "JN_NJ"},
    {"id": 111, "co": 10, "name": "青岛物流车队", "prov": "山东省", "city": "青岛市",
     "county": "城阳区", "plate": "鲁B", "center": (120.382, 36.067), "corr": "QD_SH"},
    {"id": 112, "co": 10, "name": "烟台运输车队", "prov": "山东省", "city": "烟台市",
     "county": "芝罘区", "plate": "鲁F", "center": (121.448, 37.464), "corr": "YT_SH"},
    # 江苏 3 队 150 车
    {"id": 113, "co": 20, "name": "南京干线车队", "prov": "江苏省", "city": "南京市",
     "county": "江宁区", "plate": "苏A", "center": (118.796, 32.060), "corr": "JN_NJ"},
    {"id": 114, "co": 20, "name": "无锡货运车队", "prov": "江苏省", "city": "无锡市",
     "county": "新吴区", "plate": "苏B", "center": (120.312, 31.491), "corr": "WX_SH"},
    {"id": 115, "co": 20, "name": "苏州物流车队", "prov": "江苏省", "city": "苏州市",
     "county": "吴中区", "plate": "苏E", "center": (120.585, 31.299), "corr": "SZ_SH"},
    # 上海 2 队 100 车
    {"id": 116, "co": 30, "name": "上海干线车队", "prov": "上海市", "city": "上海市",
     "county": "嘉定区", "plate": "沪A", "center": (121.265, 31.374), "corr": "QD_SH"},
    {"id": 117, "co": 30, "name": "上海城配车队", "prov": "上海市", "city": "上海市",
     "county": "浦东新区", "plate": "沪A", "center": (121.544, 31.221), "corr": "HF_SH"},
    # 安徽 2 队 100 车
    {"id": 118, "co": 40, "name": "合肥干线车队", "prov": "安徽省", "city": "合肥市",
     "county": "肥东县", "plate": "皖A", "center": (117.460, 31.860), "corr": "HF_SH"},
    {"id": 119, "co": 40, "name": "芜湖货运车队", "prov": "安徽省", "city": "芜湖市",
     "county": "鸠江区", "plate": "皖B", "center": (118.433, 31.353), "corr": "HF_SH"},
]

COMPANIES = [
    {"id": 10, "name": "山东鲁通物流有限公司", "prov": "山东省", "person": "张建国",
     "phone": "0531-88001100", "addr": "山东省济南市历下区经十路9699号"},
    {"id": 20, "name": "江苏宁苏快运有限公司", "prov": "江苏省", "person": "李文斌",
     "phone": "025-86602200", "addr": "江苏省南京市江宁区将军大道100号"},
    {"id": 30, "name": "上海浦江运输有限公司", "prov": "上海市", "person": "王海峰",
     "phone": "021-59103300", "addr": "上海市嘉定区博园路7575号"},
    {"id": 40, "name": "安徽皖江物流有限公司", "prov": "安徽省", "person": "陈志强",
     "phone": "0551-65504400", "addr": "安徽省合肥市肥东县裕溪路高架东口"},
]

# 跨省高速走廊（途经主要城市，方向在生成时正反交替）
CORRIDORS = {
    # 济南-泰安-枣庄-徐州-蚌埠-南京（鲁→苏→皖→苏）
    "JN_NJ": [(117.120, 36.651), (117.087, 36.189), (117.324, 34.810),
              (117.284, 34.205), (117.363, 32.916), (118.796, 32.060)],
    # 青岛-日照-连云港-盐城-南通-上海（鲁→苏→沪）
    "QD_SH": [(120.382, 36.067), (119.527, 35.416), (119.221, 34.597),
              (120.139, 33.377), (120.894, 32.016), (121.473, 31.230)],
    # 烟台-青岛-日照-连云港-盐城-南通-上海（鲁→苏→沪）
    "YT_SH": [(121.448, 37.464), (120.382, 36.067), (119.527, 35.416),
              (119.221, 34.597), (120.139, 33.377), (120.894, 32.016),
              (121.473, 31.230)],
    # 苏州-昆山-上海（苏→沪）
    "SZ_SH": [(120.585, 31.299), (121.005, 31.386), (121.473, 31.230)],
    # 无锡-苏州-昆山-上海（苏→沪）
    "WX_SH": [(120.312, 31.491), (120.585, 31.299), (121.005, 31.386),
              (121.473, 31.230)],
    # 合肥-芜湖-宣城-湖州-上海（皖→苏→浙→沪）
    "HF_SH": [(117.227, 31.821), (118.433, 31.353), (118.758, 30.945),
              (120.094, 30.894), (121.473, 31.230)],
}

# 省内多市路线（按起点城市组织），用于模拟同省多市之间的物流流转
INTRA_PROVINCE_ROUTES = {
    "济南市": [
        [(117.120, 36.651), (120.382, 36.067), (121.448, 37.464)],  # 济南-青岛-烟台
        [(117.120, 36.651), (120.382, 36.067)],                      # 济南-青岛
    ],
    "青岛市": [
        [(120.382, 36.067), (117.120, 36.651)],                      # 青岛-济南
        [(120.382, 36.067), (121.448, 37.464)],                      # 青岛-烟台
    ],
    "烟台市": [
        [(121.448, 37.464), (120.382, 36.067)],                      # 烟台-青岛
        [(121.448, 37.464), (120.382, 36.067), (117.120, 36.651)],   # 烟台-青岛-济南
    ],
    "南京市": [
        [(118.796, 32.060), (120.312, 31.491), (120.585, 31.299)],   # 南京-无锡-苏州
        [(118.796, 32.060), (120.312, 31.491)],                      # 南京-无锡
    ],
    "无锡市": [
        [(120.312, 31.491), (118.796, 32.060)],                      # 无锡-南京
        [(120.312, 31.491), (120.585, 31.299)],                      # 无锡-苏州
    ],
    "苏州市": [
        [(120.585, 31.299), (120.312, 31.491)],                      # 苏州-无锡
        [(120.585, 31.299), (120.312, 31.491), (118.796, 32.060)],   # 苏州-无锡-南京
    ],
    "合肥市": [
        [(117.227, 31.821), (118.433, 31.353)],                      # 合肥-芜湖
        [(117.227, 31.821), (118.433, 31.353), (120.094, 30.894)],   # 合肥-芜湖-湖州
    ],
    "芜湖市": [
        [(118.433, 31.353), (117.227, 31.821)],                      # 芜湖-合肥
    ],
    "上海市": [
        [(121.473, 31.230), (120.585, 31.299)],                      # 上海-苏州
        [(121.473, 31.230), (120.094, 30.894)],                      # 上海-湖州
    ],
}

# 每个行程类型的权重：跨省 45% / 省内多市 40% / 市内短途 15%
_TRIP_TYPE_WEIGHTS = [("cross_province", 45), ("intra_province", 40), ("local", 15)]


def _pick_route(fleet: dict) -> tuple[list, str]:
    """按权重选择当日行程路线，返回 (waypoints, trip_type)。"""
    r = RNG.random() * 100
    acc = 0
    chosen = "local"
    for ttype, w in _TRIP_TYPE_WEIGHTS:
        acc += w
        if r <= acc:
            chosen = ttype
            break
    if chosen == "cross_province":
        corr = CORRIDORS[fleet["corr"]]
        waypoints = list(reversed(corr)) if RNG.random() < 0.5 else list(corr)
        return waypoints, "cross_province"
    if chosen == "intra_province":
        routes = INTRA_PROVINCE_ROUTES.get(fleet["city"])
        if routes:
            return list(RNG.choice(routes)), "intra_province"
    return _local_waypoints(fleet["center"]), "local"


def _trip_start_time(day: date, trip_index: int, anomaly: bool, highway: bool) -> datetime:
    """行程发车时间。

    正常作息：5:00-9:00 上午 / 14:00-15:00 下午（长途）或 14:00-17:00 短途；
    22:00 后原则上收车，确保 24:00 前结束；0:00-5:00 休息。
    异常日（anomaly=True）首趟发车在凌晨 2:00-4:30，作为疲劳/夜间违规数据。
    """
    if anomaly and trip_index == 0:
        h = RNG.randint(2, 4)
        m = RNG.randint(0, 30) if h == 4 else RNG.randint(0, 59)
        return datetime.combine(day, time(h, m, RNG.randint(0, 59)))
    if trip_index == 0:
        # 上午：长途 5:00-7:30 发车（12:00 前后结束），短途可到 9:00
        if highway:
            return datetime.combine(day, time(RNG.randint(5, 7), RNG.randint(0, 30), RNG.randint(0, 59)))
        return datetime.combine(day, time(RNG.randint(5, 9), RNG.randint(0, 59), RNG.randint(0, 59)))
    # 第二趟：长途限 14:00-14:30 发车（23:00 前结束，不跨午夜），短途 14:00-16:30
    if highway:
        m = RNG.randint(0, 30)
        return datetime.combine(day, time(14, m, RNG.randint(0, 59)))
    return datetime.combine(day, time(RNG.randint(14, 16), RNG.randint(0, 59), RNG.randint(0, 59)))

_PLATE_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
SURNAMES = list("王李张刘陈杨黄赵周吴徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾")
GIVEN_1 = list("伟芳娜敏静秀丽强磊军洋勇艳杰娟涛明超霞平刚桂英")
GIVEN_2 = list("建华强晓明志强俊杰海燕春雷勇军卫东国庆佳鑫宇航梓涵浩然欣怡雨泽")
BRANDS = ["解放J6P", "东风天龙", "重汽豪沃", "陕汽德龙", "福田欧曼", "江淮格尔发"]
VTYPES = ["重型货车", "中型货车", "轻型货车", "牵引车", "冷链车"]
ASSIGNEES = ["刘洋", "赵磊", "孙敏", "周强"]

MODES = {
    # 标准版：30s 一点，每日 1~2 趟，跨省/省内多市为主，约 700~900 万点
    # 风险事件/工单总量封顶 96（<100），均匀分布在 9 月各天
    "standard": {"interval_s": 30, "trips": (1, 2), "event_budget": 96,
                 "guarantee": {"SPEED_SEVERE": 6, "FATIGUE_DRIVE": 4,
                               "COMBO_FATIGUE_SPEED": 1}},
    # 稠密版：15s 一点，每日 2~3 趟，约 1500~1800 万点；事件/工单封顶 192（<200）
    "dense": {"interval_s": 15, "trips": (2, 3), "event_budget": 192,
              "guarantee": {"SPEED_SEVERE": 12, "FATIGUE_DRIVE": 8,
                            "COMBO_FATIGUE_SPEED": 2}},
}

# =====================================================================
# 任务状态（后台线程 + 进度查询）
# =====================================================================
JOB = {
    "running": False, "mode": None, "stage": "", "percent": 0,
    "counts": {}, "error": None, "started_at": None, "finished_at": None,
}
_LOCK = threading.Lock()


def _set(**kw):
    with _LOCK:
        JOB.update(kw)


def job_status() -> dict:
    with _LOCK:
        return dict(JOB)


# =====================================================================
# 工具函数
# =====================================================================
def _plate_suffix(n: int) -> str:
    """0..28629150 → 5 位 31 进制车牌序号，零填充"""
    chars = []
    for _ in range(5):
        n, r = divmod(n, 31)
        chars.append(_PLATE_ALPHABET[r])
    return "".join(reversed(chars))


def _haversine(p1, p2) -> float:
    r = 6371.0
    lon1, lat1, lon2, lat2 = map(math.radians, [p1[0], p1[1], p2[0], p2[1]])
    dlon, dlat = lon2 - lon1, lat2 - lat1
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def _bearing(p1, p2) -> int:
    lon1, lat1, lon2, lat2 = map(math.radians, [p1[0], p1[1], p2[0], p2[1]])
    y = math.sin(lon2 - lon1) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(lon2 - lon1)
    return int((math.degrees(math.atan2(y, x)) + 360) % 360)


def _build_path(waypoints):
    """折线 → 累积里程数组 [(lng,lat,cum_km)]"""
    path = [(waypoints[0][0], waypoints[0][1], 0.0)]
    for prev, cur in zip(waypoints, waypoints[1:]):
        path.append((cur[0], cur[1], path[-1][2] + _haversine(prev, cur)))
    return path


def _point_at(path, dist):
    if dist <= 0:
        return path[0][0], path[0][1]
    for a, b in zip(path, path[1:]):
        if a[2] <= dist <= b[2]:
            seg = b[2] - a[2]
            t = 0.0 if seg == 0 else (dist - a[2]) / seg
            return a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
    return path[-1][0], path[-1][1]


# =====================================================================
# 1. 清空业务数据（保留 IAM/菜单/字典/配置/审计）
# =====================================================================
def clear_business_data() -> dict:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                TRUNCATE TABLE mon.risk_intervention, mon.risk_order_log,
                    mon.risk_work_order, mon.driver_score, mon.risk_event,
                    mon.video_analysis, mon.risk_fence_state
                RESTART IDENTITY
            """)
            cur.execute("""
                TRUNCATE TABLE traj.traj_warn_info, traj.traj_gps_photo,
                    traj.traj_gps_point
                RESTART IDENTITY
            """)
            cur.execute("DELETE FROM traj.traj_vehicle_terminal")
            cur.execute("DELETE FROM traj.traj_vehicle_driver")
            cur.execute("DELETE FROM traj.traj_terminal")
            cur.execute("DELETE FROM traj.traj_driver")
            cur.execute("DELETE FROM traj.traj_vehicle")
            cur.execute("DELETE FROM traj.traj_dept")
            for seq in ("traj_dept_id_seq", "traj_vehicle_id_seq",
                        "traj_terminal_id_seq", "traj_driver_id_seq",
                        "traj_vehicle_terminal_id_seq",
                        "traj_vehicle_driver_id_seq"):
                cur.execute(f"SELECT setval('traj.{seq}', 1, false)")
    return {"cleared": True}


def clear_runtime_data() -> dict:
    """清除模拟运行数据，保留基础数据（2026-10-06 需求）。

    清除：GPS 轨迹/照片、终端报警+附件、风险事件、工单+日志+干预、
          日评分、视频分析、围栏穿越状态、死信/消费统计/未登记隔离区、
          站内信与通知发送日志。
    保留：公司/车队/车辆/终端/司机及绑定、风控规则、地理围栏配置、
          报警类型映射、账号/角色/菜单/字典/系统参数、工单 SLA 配置。
    注意：不用 RESTART IDENTITY——Java WS 推送按事件 ID 游标增量拉取，
    序列继续增长才不会丢推；同步清空 mock 在途报警避免结束包补建事件。
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                TRUNCATE TABLE mon.risk_intervention, mon.risk_order_log,
                    mon.risk_work_order, mon.driver_score, mon.risk_event,
                    mon.video_analysis, mon.risk_fence_state
            """)
            cur.execute("""
                TRUNCATE TABLE traj.traj_warn_info, traj.traj_warn_media,
                    traj.traj_gps_photo, traj.traj_gps_point
            """)
            cur.execute("""
                TRUNCATE TABLE traj.gateway_dlq, traj.gateway_ingest_stat,
                    traj.gateway_unknown_terminal
            """)
            cur.execute("TRUNCATE TABLE traj.notify_send_log, traj.sys_message")
    from app.gateway import mock_producer
    mock_producer.reset_mock_state()
    return {"cleared": True}


# =====================================================================
# 2. 主数据建档
# =====================================================================
def _seed_mdm(cur):
    psycopg2.extras.execute_values(cur, """
        INSERT INTO traj.traj_dept
            (id, parent_id, dept_name, dept_code, dept_type, contact_person,
             contact_phone, province_code, address, sort_no, creator)
        VALUES %s
    """, [(c["id"], 0, c["name"], f"CO{c['id']}", 1, c["person"], c["phone"],
           c["prov"], c["addr"], c["id"], CREATOR) for c in COMPANIES])

    psycopg2.extras.execute_values(cur, """
        INSERT INTO traj.traj_dept
            (id, parent_id, dept_name, dept_code, dept_type,
             province_code, city_code, county_code, address, sort_no, creator)
        VALUES %s
    """, [(f["id"], f["co"], f["name"], f"FL{f['id']}", 2,
           f["prov"], f["city"], f["county"],
           f"{f['prov']}{f['city']}{f['county']}物流园{f['id']-109}号",
           f["id"], CREATOR) for f in FLEETS])

    vehicles, terminals, drivers, bind_vt, bind_vd = [], [], [], [], []
    for i in range(500):
        f = FLEETS[i // 50]
        local_no = i % 50
        vid, tid, did = 500001 + i, 600001 + i, 700001 + i
        plate = f"{f['plate']}{_plate_suffix((i // 50) * 800 + local_no)}"
        vtype = VTYPES[(i + i // 50) % len(VTYPES)]
        color = "黄色" if vtype in ("重型货车", "牵引车") else "蓝色"
        co_name = next(c["name"] for c in COMPANIES if c["id"] == f["co"])
        vehicles.append((
            vid, f["id"], plate, color, f"LS{202600000000000 + i}",
            vtype, 1, "道路货物运输", f"YX{f['id']}{local_no+1:03d}",
            f["prov"], f["city"], f["county"],
            ("银", "白", "灰", "红", "蓝")[(i + 2) % 5],
            BRANDS[i % len(BRANDS)], co_name,
            f"139{(i*137+10000000) % 100000000:08d}", "标准测试数据集", CREATOR,
        ))
        terminals.append((
            tid, f"DS{i+1:04d}",
            f"02:{(i >> 8) & 0xFF:02X}:{i & 0xFF:02X}:{(i*7) & 0xFF:02X}:1A",
            ("FORYOU", "KEDACOM", "STREAMAX", "HIK")[i % 4],
            ("FY-V5", "KD-DVR8", "SR-M10", "HK-MD")[i % 4],
            f"1440{(i*211+10000000) % 100000000:08d}",
            "JT808", ("DSM", "DVR", "ADAS")[i % 3], 4, 1,
            "标准测试数据集", 1, CREATOR,
        ))
        gname = GIVEN_2[(i * 3) % len(GIVEN_2)] if i % 2 else GIVEN_1[(i * 2) % len(GIVEN_1)]
        idcard = f"3{(i*7919) % 10**17:017d}"
        drivers.append((
            did, SURNAMES[i % len(SURNAMES)] + gname, 1 if i % 2 else 2,
            idcard, f"138{(i*97+20000000) % 100000000:08d}", idcard,
            ("A2", "B2", "B2", "A2", "C1")[i % 5], 1,
            "标准测试数据集", 1, CREATOR,
        ))
        bind_vt.append((vid, tid, 1, "2026-08-01 09:00:00",
                        "2026-08-01 10:00:00", "安装队", CREATOR))
        bind_vd.append((vid, did, 1, "2026-08-01 09:30:00", CREATOR))

    psycopg2.extras.execute_values(cur, """
        INSERT INTO traj.traj_vehicle
            (id, dept_id, vehicle_no, vehicle_plate_color, vin, vehicle_type,
             operation_type, vehicle_industry, road_license_no,
             province_code, city_code, county_code, vehicle_color, vehicle_brand,
             owner_name, owner_phone, remark, creator)
        VALUES %s
    """, vehicles)
    psycopg2.extras.execute_values(cur, """
        INSERT INTO traj.traj_terminal
            (id, identity_code, tl_mac, oem_code, tl_model, sim_account,
             protocol_type, equipment_type, video_channel, status, remark,
             valid_mark, creator)
        VALUES %s
    """, terminals)
    psycopg2.extras.execute_values(cur, """
        INSERT INTO traj.traj_driver
            (id, driver_name, sex, idcard, contact_phone, license_code,
             licence_category, status, remark, valid_mark, creator)
        VALUES %s
    """, drivers)
    psycopg2.extras.execute_values(cur, """
        INSERT INTO traj.traj_vehicle_terminal
            (vehicle_id, terminal_id, bind_type, bind_time, install_time,
             installer, creator)
        VALUES %s
    """, bind_vt)
    psycopg2.extras.execute_values(cur, """
        INSERT INTO traj.traj_vehicle_driver
            (vehicle_id, driver_id, driver_type, bind_time, creator)
        VALUES %s
    """, bind_vd)

    cur.execute("SELECT setval('traj.traj_dept_id_seq', 200, true)")
    cur.execute("SELECT setval('traj.traj_vehicle_id_seq', 600000, true)")
    cur.execute("SELECT setval('traj.traj_terminal_id_seq', 700000, true)")
    cur.execute("SELECT setval('traj.traj_driver_id_seq', 800000, true)")

    return [{
        "idx": i, "did": 700001 + i, "fleet": FLEETS[i // 50],
        "identity": f"DS{i+1:04d}", "plate": vehicles[i][2],
    } for i in range(500)]


# =====================================================================
# 3. 行程与轨迹生成
# =====================================================================
def _local_waypoints(center):
    lng0, lat0 = center
    pts = [(lng0 + RNG.uniform(-0.05, 0.05), lat0 + RNG.uniform(-0.04, 0.04))]
    cur = pts[0]
    for _ in range(RNG.randint(3, 7)):
        dist = RNG.uniform(0.8, 4.5)
        brng = math.radians(RNG.uniform(0, 360))
        dlat = (dist / 111.0) * math.cos(brng)
        dlng = (dist / (111.0 * max(math.cos(math.radians(cur[1])), 0.5))) * math.sin(brng)
        nxt = (max(lng0 - 0.14, min(lng0 + 0.14, cur[0] + dlng)),
               max(lat0 - 0.10, min(lat0 + 0.10, cur[1] + dlat)))
        pts.append(nxt)
        cur = nxt
    return pts


def _emit_trip_points(start_dt, path, speed_fn, interval_s, mileage0):
    """沿路径按速度模型产出轨迹点"""
    total = path[-1][2]
    rows, dist, mileage, t = [], 0.0, mileage0, start_dt
    prev = None
    while dist <= total:
        lng, lat = _point_at(path, dist)
        speed = speed_fn(dist, total)
        if prev is not None:
            mileage += _haversine(prev, (lng, lat))
        rows.append({
            "lng": round(lng, 6), "lat": round(lat, 6), "speed": speed,
            "time": t, "mileage": round(mileage, 2),
            "direction": _bearing(prev, (lng, lat)) if prev else RNG.randint(0, 359),
        })
        prev = (lng, lat)
        dist += max(speed, 8) * interval_s / 3600.0
        t += timedelta(seconds=interval_s)
    return rows, mileage


def _plan_schedule(vehicles, budget: int) -> tuple[dict, dict, dict, dict]:
    """配额制事件排期。

    返回 (active_days, event_days, highway_days, anomaly_days)：
      active_days: {车序号: [活跃日 d,...]}
      event_days: {车序号: set(d)} 被安排 1 起风险事件的"车-日"
      highway_days: {车序号: set(d)} 当天有跨省/省内多市行程（高速类事件候选日）
      anomaly_days: {车序号: set(d)} 当天首趟发车在凌晨 2-4 点（夜间异常数据）
    事件按车辆风险倾向加权抽样，总量=budget，30 天均匀分布；高速日加权。
    """
    active_days: dict[int, list[int]] = {}
    highway_days: dict[int, set] = {}
    anomaly_days: dict[int, set] = {}
    weights: dict[int, float] = {}
    candidates = []
    for vi in range(len(vehicles)):
        weights[vi] = 0.3 + (RNG.random() ** 4) * 6.0
        days, vi_highway, vi_anomaly = [], set(), set()
        for d in range(MONTH_DAYS):
            day = MONTH_START + timedelta(days=d)
            if day.weekday() == 6 and RNG.random() < 0.55:
                continue  # 部分周日停驶
            days.append(d)
            # 85% 的活跃日含跨省/省内多市行程（高速日）
            if RNG.random() < 0.85:
                vi_highway.add(d)
            # 约 2.5% 的车-日为夜间异常日（凌晨 2-4 点发车）
            if RNG.random() < 0.025:
                vi_anomaly.add(d)
        active_days[vi] = days
        highway_days[vi] = vi_highway
        anomaly_days[vi] = vi_anomaly
        for d in days:
            w = weights[vi] * (4 if d in vi_highway else 1)
            candidates.append((-math.log(max(RNG.random(), 1e-9)) / w, vi, d))
    candidates.sort(key=lambda x: x[0])
    event_days: dict[int, set] = {}
    for _, vi, d in candidates[:budget]:
        event_days.setdefault(vi, set()).add(d)
    return active_days, event_days, highway_days, anomaly_days


# 单日事件类型权重（合计 100），按行驶场景区分：
# 高速跨省日：严重超速/疲劳/组合事件占比高；市内日：一般超速与 DSM/ADAS 为主
_EVENT_TYPES_HIGHWAY = [
    ("SPEED_GENERAL", 1, "national", 2, "一般超速", 22, 1),
    ("SPEED_SEVERE", 2, "national", 3, "严重超速", 30, 1),
    ("FATIGUE_DRIVE", 3, "national", 3, "疲劳驾驶", 24, 2),
    ("DSM_FATIGUE", 4, "DSM", 3, "终端疲劳预警", 8, 2),
    ("DSM_DISTRACTION", 5, "DSM", 2, "分心驾驶预警", 5, None),
    ("ADAS_FCW", 6, "ADAS", 3, "前向碰撞预警", 4, None),
    ("ADAS_LDW", 7, "ADAS", 2, "车道偏离预警", 4, None),
    ("COMBO_FATIGUE_SPEED", 8, "national", 3, "疲劳叠加超速", 3, 1),
]
_EVENT_TYPES_URBAN = [
    ("SPEED_GENERAL", 1, "national", 2, "一般超速", 36, 1),
    ("DSM_FATIGUE", 4, "DSM", 3, "终端疲劳预警", 14, 2),
    ("DSM_DISTRACTION", 5, "DSM", 2, "分心驾驶预警", 18, None),
    ("ADAS_FCW", 6, "ADAS", 3, "前向碰撞预警", 12, None),
    ("ADAS_LDW", 7, "ADAS", 2, "车道偏离预警", 20, None),
]


def _make_event_for_day(plate, identity, day_points, highway: bool = False,
                        filled: dict | None = None, targets: dict | None = None
                        ) -> tuple[dict, tuple] | tuple[None, None]:
    """为被选中的"车-日"构造恰好 1 起事件（+可选终端报警）。

    事件类型结合当天真实行程特征：严重超速需要高速点（>=92）、疲劳类需要
    清晨/夜间连续行程（>=40 点），不满足条件的类型被剔除后按权重抽选，
    保证事件与轨迹相互印证。filled/targets 为规则类型保底席位计数，
    确保 8 类规则事件均有覆盖。
    """
    filled = filled if filled is not None else {}
    targets = targets or {}
    type_table = _EVENT_TYPES_HIGHWAY if highway else _EVENT_TYPES_URBAN
    max_speed = max(p["speed"] for p in day_points)
    early_night = [p for p in day_points
                   if datetime.strptime(p["gps_time"][11:19], "%H:%M:%S").hour >= 21
                   or datetime.strptime(p["gps_time"][11:19], "%H:%M:%S").hour <= 5]
    pool = []
    for code, rule_id, source, level, title, w, warn_type in type_table:
        if code in ("SPEED_SEVERE", "COMBO_FATIGUE_SPEED") and max_speed < 92:
            continue
        if code == "SPEED_GENERAL" and max_speed < 62:
            continue  # 城区货车限速 50~60，62 即可判超速；高速 92 判严重超速
        if code in ("FATIGUE_DRIVE", "COMBO_FATIGUE_SPEED") and len(early_night) < 40:
            continue
        pool.append((code, rule_id, source, level, title, w, warn_type))
    if not pool:  # 兜底：终端分心预警不依赖速度/时段
        pool = [("DSM_DISTRACTION", 5, "DSM", 2, "分心驾驶预警", 1, None)]

    def weighted_pick(cands):
        tw = sum(x[5] for x in cands)
        r = RNG.uniform(0, tw)
        s = 0
        for item in cands:
            s += item[5]
            if r <= s:
                return item
        return cands[-1]

    chosen = None
    # 组合事件条件苛刻、席位少：合格且未满额时 50% 概率直接保底
    combo = [x for x in pool if x[0] == "COMBO_FATIGUE_SPEED"
             and filled.get(x[0], 0) < targets.get(x[0], 0)]
    if combo and RNG.random() < 0.5:
        chosen = combo[0]
    if chosen is None:
        guaranteed = [x for x in pool
                      if x[0] in targets and filled.get(x[0], 0) < targets[x[0]]]
        # 60% 概率优先消化保底席位，其余按常规权重抽选，保证分布自然
        if guaranteed and RNG.random() < 0.6:
            chosen = weighted_pick(guaranteed)
        else:
            chosen = weighted_pick(pool)
    code, rule_id, source, level, title, w, warn_type = chosen
    filled[code] = filled.get(code, 0) + 1
    # 选点：超速类取当天速度最高的一档；疲劳类取清晨/夜间段；其余随机
    if code.startswith("SPEED") or code == "COMBO_FATIGUE_SPEED":
        top = sorted(day_points, key=lambda p: -p["speed"])[:max(8, len(day_points) // 20)]
        r = RNG.choice(top)
    elif code in ("FATIGUE_DRIVE", "DSM_FATIGUE") and early_night:
        mid = len(early_night) // 2
        idx = max(0, min(len(early_night) - 1, mid + RNG.randint(-3, 3)))
        r = early_night[idx]
    else:
        r = RNG.choice(day_points)
    event = {
        "event_code": code, "event_source": source, "plate_no": plate,
        "identity_code": identity, "event_time": r["gps_time"],
        "lng": r["lng"], "lat": r["lat"], "speed": r["speed"],
        "risk_level": level, "confidence": round(RNG.uniform(0.75, 0.98), 2),
        "rule_id": rule_id, "title": f"{plate} {title}",
    }
    warn_hit = (r, warn_type) if warn_type else None
    return event, warn_hit


# =====================================================================
# 4. 衍生数据落库（事件 / 工单 / 干预 / 报警）
# =====================================================================
_SLA_MIN = {1: 240, 2: 60, 3: 15}
_CLOSE_RESULTS = ["FALSE_ALARM", "PHONE_REMIND", "EDUCATION", "SUSPEND", "OTHER"]
_RESULT_WEIGHTS = [8, 38, 38, 9, 7]


def _flush_derivatives(cur, plate, identity, events, warn_hits, warn_seq):
    """events: 事件 dict 列表；warn_hits: [(point_row, type_id)]。返回新报警序号。"""
    if not events:
        return warn_seq
    # 先确定每个事件的处置形态，事件 handle_status 与工单状态保持一致
    outcomes = []
    for e in events:
        roll = RNG.random()
        etime = datetime.strptime(e["event_time"], "%Y-%m-%d %H:%M:%S")
        deadline = etime + timedelta(minutes=_SLA_MIN[e["risk_level"]])
        if roll < 0.55:
            result = RNG.choices(_CLOSE_RESULTS, weights=_RESULT_WEIGHTS)[0]
            claim = etime + timedelta(minutes=RNG.randint(2, 30))
            # 70% 的闭环单在 SLA 时限内办结，30% 超时闭环
            if RNG.random() < 0.70:
                slack_min = max(2.0, (deadline - claim).total_seconds() / 60.0 - 1)
                close = claim + timedelta(minutes=RNG.randint(2, int(max(5, min(60, slack_min)))))
            else:
                close = deadline + timedelta(minutes=RNG.randint(5, 120))
            outcomes.append(("CLOSED", result, claim, close, deadline, 2))
        elif roll < 0.78:
            claim = etime + timedelta(minutes=RNG.randint(2, 40))
            outcomes.append(("PROCESSING", None, claim, None, deadline, 1))
        else:
            outcomes.append(("PENDING", None, None, None, deadline, 0))
        e["handle_status"] = outcomes[-1][5]

    psycopg2.extras.execute_values(cur, """
        INSERT INTO mon.risk_event
            (event_code, event_source, plate_no, identity_code, event_time, lng, lat,
             speed, risk_level, confidence, media_url, handle_status,
             rule_id, fence_id, title)
        VALUES %s
        RETURNING id
    """, [(e["event_code"], e["event_source"], e["plate_no"], e["identity_code"],
           e["event_time"], e["lng"], e["lat"], e["speed"], e["risk_level"],
           e["confidence"], None, e["handle_status"], e["rule_id"], None,
           e["title"]) for e in events])
    eids = [r[0] for r in cur.fetchall()]

    order_rows, log_rows, intervention_rows, warn_rows = [], [], [], []
    for eid, e, oc in zip(eids, events, outcomes):
        status, result, claim, close, deadline, _ = oc
        assignee = RNG.choice(ASSIGNEES)
        etime = e["event_time"]
        overdue = 1 if close is None or close > deadline else 0
        order_rows.append((
            eid, e["title"], e["event_code"], e["event_source"], plate, identity,
            e["risk_level"], etime, status, assignee if claim else None,
            claim, close, result, deadline,
            _SLA_MIN[e["risk_level"]],
            max(10, _SLA_MIN[e["risk_level"]] // 2), overdue,
        ))
        log_rows.append((eid, "CREATE", "PENDING", "dataset-gen", etime, "风险事件自动建单"))
        if claim:
            log_rows.append((eid, "CLAIM", "PROCESSING", assignee, claim, "坐席认领"))
        if close:
            log_rows.append((eid, "CLOSE", "CLOSED", assignee, close,
                             f"闭环结论：{result}"))
            if result != "FALSE_ALARM" and RNG.random() < 0.45:
                intervention_rows.append((
                    eid, plate, identity,
                    RNG.choice(["PHONE_REMIND", "EDUCATION", "SUSPEND"]),
                    assignee, claim + timedelta(minutes=RNG.randint(1, 30))))

    for i, (r, wtype) in enumerate(warn_hits):
        wtime = datetime.strptime(r["gps_time"], "%Y-%m-%d %H:%M:%S")
        st = wtime - timedelta(seconds=10)
        et = wtime + timedelta(seconds=RNG.randint(20, 300))
        warn_rows.append((
            f"DSW{warn_seq + i:08d}", plate, identity, st, et, st, et,
            str(r["lng"]), str(r["lat"]), str(r["lng"]), str(r["lat"]),
            r["speed"], max(0, r["speed"] - 10), wtype, 1,
            RNG.choice([0, 1, 1, 2]), CREATOR,
        ))

    if order_rows:
        psycopg2.extras.execute_values(cur, """
            INSERT INTO mon.risk_work_order
                (order_no, event_id, event_title, event_code, event_source,
                 plate_no, identity_code, risk_level, event_time, status,
                 assignee_name, claim_time, close_time, close_result, deadline,
                 sla_limit_min, grace_min, overdue, creator)
            SELECT mon.fmt_order_no(), v.event_id, v.title, v.code::varchar, v.source,
                   v.plate, v.identity, v.level::smallint, v.etime::timestamp, v.status,
                   v.assignee, v.claim::timestamp, v.close::timestamp, v.result,
                   v.deadline::timestamp, v.sla_min::int, v.grace_min::int,
                   v.overdue::smallint, 'dataset-gen'
            FROM (VALUES %s) AS v(event_id, title, code, source, plate, identity,
                                  level, etime, status, assignee, claim, close,
                                  result, deadline, sla_min, grace_min, overdue)
        """, order_rows)
        psycopg2.extras.execute_values(cur, """
            INSERT INTO mon.risk_order_log
                (order_id, action, to_status, operator_name, create_date, remark)
            SELECT w.id, v.action, v.to_status, v.op, v.op_time::timestamp, v.remark
            FROM (VALUES %s) AS v(event_id, action, to_status, op, op_time, remark)
            JOIN mon.risk_work_order w ON w.event_id = v.event_id
        """, log_rows)
    if intervention_rows:
        psycopg2.extras.execute_values(cur, """
            INSERT INTO mon.risk_intervention
                (order_id, event_id, plate_no, identity_code, action_type,
                 action_result, operator_name, source, create_date)
            SELECT w.id, w.event_id, v.plate, v.identity, v.atype, 'SUCCESS',
                   v.op, 'DATASET', v.ct::timestamp
            FROM (VALUES %s) AS v(event_id, plate, identity, atype, op, ct)
            JOIN mon.risk_work_order w ON w.event_id = v.event_id
        """, intervention_rows)
    if warn_rows:
        psycopg2.extras.execute_values(cur, """
            INSERT INTO traj.traj_warn_info
                (source_id, plate_no, identity_code, start_warn_time, end_warn_time,
                 start_gps_time, end_gps_time, start_lng, start_lat, end_lng, end_lat,
                 start_speed, end_speed, type_id, warn_continue_mark,
                 handle_status, creator)
            VALUES %s
        """, warn_rows)
        warn_seq += len(warn_rows)
    return warn_seq


# =====================================================================
# 5. 主流程
# =====================================================================
def _build_vehicle_list() -> list:
    """构造车辆元信息列表（不写库），供 dry_run 与建档共用。"""
    vehicles = []
    for i in range(500):
        f = FLEETS[i // 50]
        local_no = i % 50
        plate = f"{f['plate']}{_plate_suffix((i // 50) * 800 + local_no)}"
        vehicles.append({
            "idx": i, "did": 700001 + i, "fleet": f,
            "identity": f"DS{i+1:04d}", "plate": plate,
        })
    return vehicles


def build_dataset(mode: str, dry_run: bool = False) -> dict:
    """构建标准测试数据集。

    dry_run=True 时不写库、不清空，仅生成轨迹与衍生数据的统计摘要，
    供用户审批后再正式入库。
    """
    interval_s = MODES[mode]["interval_s"]
    trip_range = MODES[mode]["trips"]
    warn_seq = 1
    counts = {"vehicles": 500, "points": 0, "events": 0, "warns": 0, "scores": 0}
    stats = {
        "vehicles": 500,
        "trips": {"cross_province": 0, "intra_province": 0, "local": 0,
                  "night_anomaly": 0, "total": 0},
        "points_by_hour": {str(h): 0 for h in range(24)},
        "night_rest_points": 0,        # 22:00~次日05:00 的点数
        "anomaly_points": 0,           # 02:00~05:00 的点数（异常数据）
        "vehicle_day_anomaly": 0,      # 含夜间异常行程的车-日数
        "vehicle_day_total": 0,        # 总车-日数
        "_pt_count": 0,                # dry_run 累计点数（不入库）
    }

    if not dry_run:
        _set(running=True, mode=mode, stage="清空旧业务数据", percent=2,
             counts=counts, error=None,
             started_at=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
             finished_at=None)
        clear_business_data()

    point_buf, total_points = [], 0

    def flush_points():
        nonlocal point_buf, total_points
        if point_buf and not dry_run:
            insert_gps_points(point_buf)
            total_points += len(point_buf)
            point_buf = []

    vehicles = _build_vehicle_list()
    if not dry_run:
        with get_conn() as conn:
            with conn.cursor() as cur:
                _set(stage="建档：公司/车队/车辆/终端/司机", percent=5)
                _seed_mdm(cur)

    active_days, event_days, highway_days, anomaly_days = _plan_schedule(
        vehicles, MODES[mode]["event_budget"])
    type_filled: dict[str, int] = {}
    type_targets = MODES[mode]["guarantee"]

    for vi, v in enumerate(vehicles):
        f = v["fleet"]
        mileage = RNG.uniform(20000, 120000)
        my_event_days = event_days.get(vi, set())
        my_highway_days = highway_days.get(vi, set())
        my_anomaly_days = anomaly_days.get(vi, set())

        cur = None
        conn = None
        conn_cm = None
        if not dry_run:
            conn_cm = get_conn()
            conn = conn_cm.__enter__()
            cur = conn.cursor().__enter__()

        for d in active_days[vi]:
            day = MONTH_START + timedelta(days=d)
            day_points, day_events, day_warn_hits = [], [], []
            mileage_start = mileage
            ntrips = RNG.randint(*trip_range)
            is_anomaly_day = d in my_anomaly_days
            if is_anomaly_day:
                stats["vehicle_day_anomaly"] += 1
            stats["vehicle_day_total"] += 1

            for ti in range(ntrips):
                waypoints, ttype = _pick_route(f)
                stats["trips"][ttype] += 1
                stats["trips"]["total"] += 1
                is_highway = ttype in ("cross_province", "intra_province")
                start_dt = _trip_start_time(day, ti, is_anomaly_day, is_highway)
                if ti == 0 and is_anomaly_day:
                    stats["trips"]["night_anomaly"] += 1

                path = _build_path(waypoints)
                if is_highway:
                    base = RNG.uniform(70, 96)

                    def speed_fn(dist, total, b=base):
                        ramp = min(dist, max(total - dist, 0)) / 3.0
                        return max(30, int(b - max(0.0, 1 - ramp) * 32
                                          + RNG.uniform(-6, 8)))
                else:
                    urban = RNG.uniform(28, 60)

                    def speed_fn(dist, total, b=urban):
                        return max(0, int(b + RNG.uniform(-14, 16)
                                          - (6 if dist < 0.4 else 0)))

                rows, mileage = _emit_trip_points(
                    start_dt, path, speed_fn, interval_s, mileage)
                for r in rows:
                    hr = r["time"].hour
                    stats["points_by_hour"][str(hr)] += 1
                    if hr >= 22 or hr < 5:
                        stats["night_rest_points"] += 1
                    if 2 <= hr < 5:
                        stats["anomaly_points"] += 1
                    day_points.append({
                        "identity_code": v["identity"],
                        "plate_no": v["plate"],
                        "gps_time": r["time"].strftime("%Y-%m-%d %H:%M:%S"),
                        "lng": r["lng"], "lat": r["lat"], "speed": r["speed"],
                        "direction": r["direction"], "altitude": RNG.randint(5, 80),
                        "alarm_flag": 1 if r["speed"] >= 92 else 0,
                        "mileage": r["mileage"],
                    })
            if not day_points:
                continue

            if d in my_event_days:
                ev, wh = _make_event_for_day(
                    v["plate"], v["identity"], day_points,
                    highway=(d in my_highway_days),
                    filled=type_filled, targets=type_targets)
                day_events.append(ev)
                if wh:
                    day_warn_hits.append(wh)

            if not dry_run:
                warn_seq = _flush_derivatives(
                    cur, v["plate"], v["identity"], day_events,
                    day_warn_hits, warn_seq)
                counts["warns"] = warn_seq - 1
            counts["events"] += len(day_events)

            night_pts = sum(
                1 for p in day_points
                if datetime.strptime(p["gps_time"][11:19], "%H:%M:%S").hour >= 21)
            score = round(max(55.0, min(99.0,
                96 - len(day_events) * RNG.uniform(2.5, 6)
                - (1.5 if night_pts else 0) + RNG.uniform(-2, 2))), 1)
            level = ("A" if score >= 90 else "B" if score >= 80
                     else "C" if score >= 65 else "D")
            features = json.dumps({
                "speed_events": sum(1 for e in day_events if "SPEED" in e["event_code"]),
                "fatigue_events": sum(1 for e in day_events if "FATIGUE" in e["event_code"]),
                "dsm_adas_events": sum(1 for e in day_events
                                       if e["event_source"] in ("DSM", "ADAS")),
                "night_points": night_pts,
                "mileage_km": round(mileage - mileage_start, 1),
            }, ensure_ascii=False)
            if not dry_run:
                psycopg2.extras.execute_values(cur, """
                    INSERT INTO mon.driver_score
                        (score_date, driver_id, identity_code, plate_no, dept_id,
                         score, level, features, sample_points, event_count)
                    VALUES %s
                    ON CONFLICT (score_date, driver_id) DO NOTHING
                """, [(day, v["did"], v["identity"], v["plate"], f["id"],
                       score, level, features, len(day_points),
                       len(day_events))])
            counts["scores"] += 1

            point_buf.extend(day_points)
            if dry_run:
                stats["_pt_count"] += len(day_points)
            if not dry_run and len(point_buf) >= 20000:
                flush_points()
                counts["points"] = total_points

        if not dry_run:
            conn.commit()
            cur.__exit__(None, None, None)
            conn_cm.__exit__(None, None, None)
        else:
            # dry_run 不保留点明细，避免内存爆炸
            point_buf = []

        counts["points"] = total_points if not dry_run else stats["_pt_count"]
        if not dry_run:
            _set(stage=f"生成轨迹与风险数据（{vi+1}/500，{v['plate']}）",
                 percent=5 + int(88 * (vi + 1) / 500), counts=dict(counts))

    if dry_run:
        counts["points"] = stats["_pt_count"]
        counts["event_types"] = dict(sorted(type_filled.items()))
        stats["points"] = stats["_pt_count"]
        stats["events"] = counts["events"]
        stats["warns"] = counts["warns"]
        stats["scores"] = counts["scores"]
        stats["event_types"] = dict(sorted(type_filled.items()))
        stats.pop("_pt_count", None)
        return stats

    flush_points()
    counts["points"] = total_points
    counts["event_types"] = dict(sorted(type_filled.items()))
    _set(stage="完成", percent=100, running=False, counts=dict(counts),
         finished_at=datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    return counts

"""processing 鉴权模块（两类口径）：

1. verify_service_token：内部/数据面接口（ingest、analysis、gateway 数据面），
   X-Service-Token 静态令牌比对，与 Java 侧 mydbd.service-token 同源。
2. require_access：浏览器侧演示/测试工具接口（simulator/dataset），
   三层校验 JWT 登录态 → 运行模式硬拦截 → 权限码。
   - JWT 与 Java 平台共享 HMAC 密钥（JWT_HMAC_SECRET），claims 契约见
     backend-java platform-common JwtUtil（uid/sub/purpose/exp），purpose=access。
     算法口径：Java 侧已显式钉死 HS256（2026-10-06 起）；验签同时接受 HS256/HS384/
     HS512，以兼容历史存量令牌——JJWT signWith(key) 曾按密钥长度自动选算法，50 字节
     密钥签发的是 HS384，仅放行 HS256 会误判 401。
   - 运行模式取启动期固化值（sys_config/env 覆盖后的 settings.gateway_mode），
     不做热生效：改模式必须重启 processing，与网关配置卡片口径一致。
   - 超管判定与 Java DbAuthRealm 同口径：启用角色含 role_code='SUPER_ADMIN' 直通。
"""

import jwt
from fastapi import Header, HTTPException, status

from app.config import settings
from app.db import get_conn

PERM_TOOL_EDIT = "system:config:edit"


async def verify_service_token(
    x_service_token: str = Header(default=""),
) -> None:
    if x_service_token != settings.service_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="服务令牌无效",
        )


def _bearer_uid(authorization: str | None) -> int:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="未登录或缺少访问令牌")
    try:
        claims = jwt.decode(
            authorization[7:].strip(),
            settings.jwt_hmac_secret,
            # 与 Java JwtUtil 的显式 HS256 对齐；兼容 HS384/HS512 存量令牌
            algorithms=["HS256", "HS384", "HS512"],
        )
    except Exception:
        raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
    if claims.get("purpose") != "access":
        raise HTTPException(status_code=401, detail="令牌类型不正确")
    uid = claims.get("uid")
    if not isinstance(uid, int):
        raise HTTPException(status_code=401, detail="令牌缺少用户标识")
    return uid


def _require_active_user(uid: int) -> None:
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT 1 FROM traj.sys_user
                WHERE id = %s AND valid_mark = 1 AND status = 1
                """,
                (uid,),
            )
            if cur.fetchone() is None:
                raise HTTPException(status_code=401, detail="账号不存在或已停用")


def _require_perm(uid: int, perm: str) -> None:
    """启用角色码 + 权限码一次查询；SUPER_ADMIN 直通（与 Java 同口径）。"""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT r.role_code, m.perm_code
                  FROM traj.sys_user_role ur
                  JOIN traj.sys_role r
                    ON r.id = ur.role_id AND r.valid_mark = 1 AND r.status = 1
                  LEFT JOIN traj.sys_role_menu rm ON rm.role_id = r.id
                  LEFT JOIN traj.sys_menu m
                    ON m.id = rm.menu_id AND m.status = 1 AND m.perm_code IS NOT NULL
                 WHERE ur.user_id = %s
                """,
                (uid,),
            )
            roles: set[str] = set()
            perms: set[str] = set()
            for role_code, perm_code in cur.fetchall():
                roles.add(role_code)
                if perm_code:
                    perms.add(perm_code)
    if "SUPER_ADMIN" in roles or perm in perms:
        return
    raise HTTPException(status_code=403, detail=f"无操作权限（需要 {perm}）")


def require_access(modes: tuple[str, ...] | None = None,
                   mode_message: str = "",
                   perm: str | None = PERM_TOOL_EDIT):
    """构造 FastAPI 依赖：登录态 → 模式 → 权限。

    modes=None 表示仅要求登录（状态查询类）；写操作传允许的模式元组与提示语。
    """
    def dep(authorization: str | None = Header(default=None)) -> int:
        uid = _bearer_uid(authorization)
        _require_active_user(uid)
        if modes is not None and settings.gateway_mode not in modes:
            raise HTTPException(status_code=403, detail=mode_message)
        if perm is not None:
            _require_perm(uid, perm)
        return uid

    return dep

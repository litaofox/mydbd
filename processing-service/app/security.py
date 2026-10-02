"""内部接口鉴权"""

from fastapi import Header, HTTPException, status

from app.config import settings


async def verify_service_token(
    x_service_token: str = Header(default=""),
) -> None:
    if x_service_token != settings.service_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="服务令牌无效",
        )

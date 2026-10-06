"""vps 网关接入运维接口（内部：服务令牌鉴权）。

- GET /api/gateway/status          消费运行状态 + 各 topic 统计 + 死信/未登记计数
- GET /api/gateway/unknown-terminals  未登记终端隔离区清单
- GET /api/gateway/media/{id}      本地已转存附件取件（供 Java 平台带用户鉴权后中转）

浏览器侧请经 Java 平台鉴权接口中转，不直接暴露本网关接口。
"""

import mimetypes

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse

from app import db
from app.gateway import media_store
from app.gateway.runner import gateway_runner
from app.security import verify_service_token

router = APIRouter(
    prefix="/api/gateway", tags=["gateway"],
    dependencies=[Depends(verify_service_token)],
)


@router.get("/status")
def status():
    data = db.gateway_status_data()
    return {**gateway_runner.status(), **data}


@router.get("/unknown-terminals")
def unknown_terminals():
    return {"items": db.list_unknown_terminals()}


@router.get("/media/{media_id}")
def get_media(media_id: int):
    media = db.get_warn_media(media_id)
    if not media:
        raise HTTPException(status_code=404, detail="附件不存在")
    if media.get("local_path"):
        path = media_store.absolute(media["local_path"])
        if path:
            ctype, _ = mimetypes.guess_type(media["file_name"] or "")
            return FileResponse(path, media_type=ctype or "application/octet-stream",
                                filename=media["file_name"])
    raise HTTPException(status_code=404, detail="附件未转存到平台存储")

from fastapi import APIRouter

from app.services.simulator import simulator_service

router = APIRouter(prefix="/api/simulator", tags=["simulator"])


@router.post("/start")
async def start():
    # 演示环境开放控制（容器网络边界内）；正式版本需登录态/服务令牌
    await simulator_service.start()
    return simulator_service.status()


@router.post("/stop")
async def stop():
    await simulator_service.stop()
    return simulator_service.status()


@router.get("/status")
async def status():
    return simulator_service.status()

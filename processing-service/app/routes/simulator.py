from fastapi import APIRouter, Depends, HTTPException

from app import db
from app.security import require_access
from app.gateway.runner import gateway_runner
from app.services.simulator import simulator_service

router = APIRouter(prefix="/api/simulator", tags=["simulator"])

# 写操作三重校验：登录态 + 仅演示模式 + 参数编辑权限
_guard = require_access(("simulator",), "实时轨迹模拟器仅演示模式可用")
# mock 仿真投递开关：登录态 + 仅测试模式 + 参数编辑权限
_mock_guard = require_access(("mock",), "仿真投递开关仅测试模式可用")


@router.post("/start")
async def start(_uid: int = Depends(_guard)):
    await simulator_service.start()
    return simulator_service.status()


@router.post("/stop")
async def stop(_uid: int = Depends(_guard)):
    await simulator_service.stop()
    return simulator_service.status()


@router.get("/status")
async def status(_uid: int = Depends(require_access(perm=None))):
    return simulator_service.status()


@router.post("/mock/start")
async def mock_start(_uid: int = Depends(_mock_guard)):
    try:
        gateway_runner.start_mock()
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
    db.set_gateway_mock_delivery(True)
    return gateway_runner.status()


@router.post("/mock/stop")
async def mock_stop(_uid: int = Depends(_mock_guard)):
    gateway_runner.stop_mock()
    db.set_gateway_mock_delivery(False)
    return gateway_runner.status()


@router.get("/mock/status")
async def mock_status(_uid: int = Depends(require_access(perm=None))):
    return gateway_runner.status()

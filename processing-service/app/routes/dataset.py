"""标准测试数据集：一键加载（标准/稠密两档）、清空、进度查询"""

import threading
import traceback

from fastapi import APIRouter, Depends, HTTPException

from app.security import require_access
from app.services import dataset_gen

router = APIRouter(prefix="/api/ingest/dataset", tags=["dataset"])

# 写操作三重校验：登录态 + 演示/测试模式（正式模式禁用）+ 参数编辑权限
_guard = require_access(("simulator", "mock"), "正式模式禁止加载/清空测试数据")


def _run(mode: str):
    try:
        dataset_gen.build_dataset(mode)
    except Exception:  # 线程内兜底，错误回传状态接口
        tb = traceback.format_exc()
        print(tb)
        dataset_gen._set(running=False, error=tb[-800:], stage="失败")


@router.post("/load")
def load(payload: dict | None = None, _uid: int = Depends(_guard)):
    """后台异步生成并加载标准测试数据集。mode=standard|dense"""
    mode = (payload or {}).get("mode", "standard")
    if mode not in dataset_gen.MODES:
        raise HTTPException(status_code=400, detail="mode 仅支持 standard / dense")
    st = dataset_gen.job_status()
    if st["running"]:
        raise HTTPException(status_code=409, detail="已有数据集任务在运行中")
    t = threading.Thread(target=_run, args=(mode,), daemon=True)
    t.start()
    return dataset_gen.job_status()


@router.post("/clear")
def clear(_uid: int = Depends(_guard)):
    """清空全部业务数据（保留 IAM/菜单/字典/配置/审计），用于重新加载"""
    st = dataset_gen.job_status()
    if st["running"]:
        raise HTTPException(status_code=409, detail="数据集任务运行中，禁止清空")
    return dataset_gen.clear_business_data()


@router.post("/clear-runtime")
def clear_runtime(_uid: int = Depends(_guard)):
    """清除模拟运行数据（轨迹/报警/事件/工单/评分/通知等），保留基础数据"""
    st = dataset_gen.job_status()
    if st["running"]:
        raise HTTPException(status_code=409, detail="数据集任务运行中，禁止清除")
    return dataset_gen.clear_runtime_data()


@router.get("/status")
def status(_uid: int = Depends(require_access(perm=None))):
    return dataset_gen.job_status()

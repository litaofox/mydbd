"""标准测试数据集：一键加载（标准/稠密两档）、清空、进度查询"""

import threading
import traceback

from fastapi import APIRouter, HTTPException

from app.services import dataset_gen

router = APIRouter(prefix="/api/ingest/dataset", tags=["dataset"])


def _run(mode: str):
    try:
        dataset_gen.build_dataset(mode)
    except Exception:  # 线程内兜底，错误回传状态接口
        tb = traceback.format_exc()
        print(tb)
        dataset_gen._set(running=False, error=tb[-800:], stage="失败")


@router.post("/load")
def load(payload: dict | None = None):
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
def clear():
    """清空全部业务数据（保留 IAM/菜单/字典/配置/审计），用于重新加载"""
    st = dataset_gen.job_status()
    if st["running"]:
        raise HTTPException(status_code=409, detail="数据集任务运行中，禁止清空")
    return dataset_gen.clear_business_data()


@router.get("/status")
def status():
    return dataset_gen.job_status()

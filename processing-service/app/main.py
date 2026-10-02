"""mydbd 北斗导航数据处理服务"""

from fastapi import FastAPI

from app.routes import analysis, health, ingest, simulator

app = FastAPI(title="mydbd-processing", version="0.1.0")

app.include_router(health.router)
app.include_router(simulator.router)
app.include_router(ingest.router)
app.include_router(analysis.router)

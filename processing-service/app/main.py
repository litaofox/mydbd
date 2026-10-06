"""mydbd 北斗导航数据处理服务"""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.routes import analysis, dataset, gateway, health, ingest, simulator


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # vps 网关参数：sys_config 非空值 > 环境变量 > 默认（仅启动时读一次）
    from app.gateway import startup_config
    startup_config.apply_db_overrides()
    # vps 网关消费（GATEWAY_MODE=mock/gateway 且配置 Kafka 地址时启动）
    from app.gateway.runner import gateway_runner
    gateway_runner.start()
    yield
    gateway_runner.stop()


app = FastAPI(title="mydbd-processing", version="0.1.0", lifespan=lifespan)

app.include_router(health.router)
app.include_router(simulator.router)
app.include_router(ingest.router)
app.include_router(dataset.router)
app.include_router(analysis.router)
app.include_router(gateway.router)

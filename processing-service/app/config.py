"""处理服务配置（环境变量注入）"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # 数据库
    db_host: str = "localhost"
    db_port: int = 5432
    db_name: str = "mydbd"
    db_user: str = "mydbd"
    db_password: str = "mydbd"

    # 安全
    service_token: str = "change-me-service-token"
    jwt_hmac_secret: str = "change-me-dev-secret"

    # 存储与联动
    storage_root: str = "/data"

    # F18 CEP 引擎：规则/围栏配置缓存秒数（配置改动最坏延迟）
    risk_cache_sec: int = 30

    @property
    def samples_dir(self) -> str:
        return f"{self.storage_root}/samples"

    @property
    def dsn(self) -> str:
        return (
            f"postgresql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()

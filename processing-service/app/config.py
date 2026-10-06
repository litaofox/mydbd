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

    # ===== vps 车载网关对接（GATEWAY-PLAN-001） =====
    # 运行模式：simulator=内置模拟器（默认） / mock=仿真数据灌本地Kafka验证 / gateway=对接正式网关
    gateway_mode: str = "simulator"
    kafka_bootstrap_servers: str = ""
    kafka_group_id: str = "mydbd-ingest"
    kafka_poll_timeout_ms: int = 1000
    # 网关 topic（与网关投递配置保持一致）
    gateway_topic_gps: str = "jt808_gps_topic"
    gateway_topic_warn: str = "jt808_warn_topic"
    gateway_topic_warn_media: str = "jt808_warn_media_topic"
    gateway_topic_heartbeat: str = "jt808_heartbeat_topic"
    gateway_topic_offline: str = "jt808_offline_topic"
    gateway_topic_command: str = "jt808_command_topic"
    # 附件转存：网关 18009 静态服务地址；local=下载转存平台卷 / proxy=仅记录原始URL
    gateway_file_base_url: str = ""
    gateway_media_strategy: str = "local"
    # mock 仿真投递开关（on/off）：运行时启停即时生效并持久化 sys_config，重启后保持
    mock_delivery_enabled: str = "on"
    media_store_dir: str = "/data/media"
    gateway_media_max_mb: int = 50

    @property
    def gateway_enabled(self) -> bool:
        """mock/gateway 模式且配置了 Kafka 地址才启动消费（simulator 模式完全不启动）"""
        return self.gateway_mode in ("mock", "gateway") and bool(self.kafka_servers)

    @property
    def kafka_servers(self) -> list[str]:
        return [s.strip() for s in self.kafka_bootstrap_servers.split(",") if s.strip()]

    @property
    def gateway_topics(self) -> list[str]:
        return [
            self.gateway_topic_gps, self.gateway_topic_warn,
            self.gateway_topic_warn_media, self.gateway_topic_heartbeat,
            self.gateway_topic_offline, self.gateway_topic_command,
        ]

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

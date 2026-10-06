"""网关参数启动期加载（不做热生效；修改 sys_config 后需重启 processing）。

优先级：sys_config 非空值 > 环境变量 > 代码默认值。
在 main.py lifespan 中、gateway runner 启动前调用一次。
"""

import os

from app import db
from app.config import settings

# sys_config 键 → (settings 属性, 对应环境变量)
_KEY_MAP = {
    "gateway.mode": ("gateway_mode", "GATEWAY_MODE"),
    "gateway.kafka.bootstrap-servers": ("kafka_bootstrap_servers", "KAFKA_BOOTSTRAP_SERVERS"),
    "gateway.kafka.group-id": ("kafka_group_id", "KAFKA_GROUP_ID"),
    "gateway.file-base-url": ("gateway_file_base_url", "GATEWAY_FILE_BASE_URL"),
    "gateway.media-strategy": ("gateway_media_strategy", "GATEWAY_MEDIA_STRATEGY"),
    "gateway.mock-delivery-enabled": ("mock_delivery_enabled", "GATEWAY_MOCK_DELIVERY"),
}

_VALID_MODES = {"simulator", "mock", "gateway"}
_VALID_MEDIA = {"local", "proxy"}

# 各参数最终来源：db / env / default
_sources: dict[str, str] = {}
_applied = False


def apply_db_overrides() -> dict:
    """启动时用 sys_config 覆盖 settings；返回来源映射。失败回退环境变量。"""
    global _applied
    if _applied:
        return dict(_sources)
    _applied = True
    rows = {}
    try:
        rows = db.load_gateway_config_rows()
    except Exception as exc:
        print(f"[gateway] 读取 sys_config 失败，使用环境变量/默认值：{exc}")

    for key, (attr, env_name) in _KEY_MAP.items():
        db_value = rows.get(key, "").strip()
        env_value = (os.environ.get(env_name) or "").strip()
        if db_value:
            if _validate(attr, db_value):
                setattr(settings, attr, db_value)
                _sources[key] = "db"
            else:
                print(f"[gateway] 参数 {key}={db_value} 非法，忽略并回退")
                _sources[key] = "env" if env_value else "default"
        elif env_value:
            _sources[key] = "env"
        else:
            _sources[key] = "default"
    print(f"[gateway] 启动配置来源：{_sources}；mode={settings.gateway_mode}")
    return dict(_sources)


def _validate(attr: str, value: str) -> bool:
    if attr == "gateway_mode":
        return value in _VALID_MODES
    if attr == "gateway_media_strategy":
        return value in _VALID_MEDIA
    if attr == "mock_delivery_enabled":
        return value in ("on", "off")
    return True


def sources() -> dict:
    return dict(_sources)

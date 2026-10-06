"""报警附件转存：local 流式下载到平台卷 / proxy 仅记录 / mock:// 演示占位。

- 受 settings.gateway_media_strategy / gateway_file_base_url / media_store_dir 控制；
- 下载有界（gateway_media_max_mb），流式写盘，避免 384M 容器内存超限；
- 返回相对 media_store_dir 的路径，供鉴权取件接口定位文件。
"""

import os
import urllib.request

from app.config import settings

_EXTS = {0: ".jpg", 1: ".amr", 2: ".mp4"}


def _safe_name(name: str) -> str:
    base = os.path.basename(str(name or "")).replace("..", "_").strip()
    return base or "unnamed"


def _target_path(warn_id: str, file_name: str, file_type) -> tuple[str, str]:
    """返回 (绝对路径, 相对路径)。"""
    from datetime import datetime
    day = datetime.now().strftime("%Y-%m-%d")
    name = _safe_name(file_name)
    if "." not in name and file_type in _EXTS:
        name += _EXTS[file_type]
    rel = f"{day}/{warn_id or 'unknown'}/{name}"
    return os.path.join(settings.media_store_dir, *rel.split("/")), rel


def _write_mock_placeholder(abs_path: str, warn_id: str) -> None:
    """mock 模式无网关 18009 服务，生成占位 SVG 便于全链路演示。"""
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360">'
        '<rect width="100%" height="100%" fill="#1e2530"/>'
        f'<text x="50%" y="45%" fill="#7aa2f7" font-size="24" text-anchor="middle">mock evidence</text>'
        f'<text x="50%" y="60%" fill="#9aa5b1" font-size="14" text-anchor="middle">warnId: {warn_id}</text>'
        "</svg>"
    )
    with open(abs_path, "w", encoding="utf-8") as f:
        f.write(svg)


def store(warn_id: str, file_name: str, url: str | None, file_type=None) -> str | None:
    """按策略转存附件，返回相对路径；不转存返回 None。异常返回 None 并打印。"""
    if settings.gateway_media_strategy != "local":
        return None
    abs_path, rel = _target_path(warn_id, file_name, file_type)
    try:
        os.makedirs(os.path.dirname(abs_path), exist_ok=True)
        if os.path.exists(abs_path):
            return rel
        if url and url.startswith("mock://"):
            _write_mock_placeholder(abs_path, warn_id)
            return rel
        if not url:
            return None
        full = url if url.startswith("http") else (
            settings.gateway_file_base_url.rstrip("/") + "/" + url.lstrip("/")
        )
        if not full.startswith("http"):
            return None
        limit = settings.gateway_media_max_mb * 1024 * 1024
        with urllib.request.urlopen(full, timeout=15) as resp, open(abs_path, "wb") as out:
            written = 0
            while True:
                chunk = resp.read(64 * 1024)
                if not chunk:
                    break
                written += len(chunk)
                if written > limit:
                    raise ValueError(f"附件超过大小限制 {settings.gateway_media_max_mb}MB")
                out.write(chunk)
        return rel
    except Exception as exc:
        print(f"[gateway] media store error warn={warn_id} file={file_name}: {exc}")
        try:
            if os.path.exists(abs_path):
                os.remove(abs_path)
        except OSError:
            pass
        return None


def absolute(rel_path: str) -> str | None:
    """相对路径 → 绝对路径（防目录穿越）。"""
    root = os.path.abspath(settings.media_store_dir)
    path = os.path.abspath(os.path.join(root, rel_path))
    return path if path.startswith(root) and os.path.isfile(path) else None

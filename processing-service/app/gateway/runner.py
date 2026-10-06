"""网关消费运行器：守护线程启停 + 断线重连 + 运行状态。

由 main.py lifespan 驱动；settings.gateway_enabled 为 False 时完全不启动
（simulator 模式行为与现网一致）。
mock 投递线程支持运行时启停（/api/simulator/mock/start|stop），
启停状态持久化于 sys_config gateway.mock-delivery-enabled，重启后保持。
"""

import threading
import time
from datetime import datetime

from app.config import settings


class GatewayRunner:
    def __init__(self):
        self._thread: threading.Thread | None = None
        self._mock_thread: threading.Thread | None = None
        self._stop = threading.Event()
        self._mock_stop = threading.Event()
        self.started_at: str | None = None
        self.last_error: str | None = None
        self.mock_started_at: str | None = None
        self.mock_stopped_at: str | None = None

    @property
    def running(self) -> bool:
        return bool(self._thread and self._thread.is_alive())

    def start(self) -> None:
        """lifespan 启动：消费线程始终拉起；mock 投递按持久化配置决定。"""
        if not settings.gateway_enabled:
            print(f"[gateway] mode={settings.gateway_mode}，消费未启动")
            return
        self._start_consumer()
        if settings.gateway_mode == "mock":
            if settings.mock_delivery_enabled == "off":
                self.mock_stopped_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                print("[gateway] mock 投递按配置保持停止"
                      "（gateway.mock-delivery-enabled=off），可在运行模式页开启")
            else:
                self.start_mock()

    def _start_consumer(self) -> None:
        if self.running:
            return
        self._stop.clear()
        self._thread = threading.Thread(
            target=self._run_with_reconnect, name="gateway-consumer", daemon=True)
        self._thread.start()
        self.started_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[gateway] 消费已启动 mode={settings.gateway_mode} "
              f"servers={settings.kafka_bootstrap_servers}")

    def start_mock(self) -> None:
        """运行时启动 mock 投递线程（仅 mock 模式；消费线程未跑则一并拉起）。"""
        if settings.gateway_mode != "mock" or not settings.gateway_enabled:
            raise RuntimeError("仅测试模式（mock）且 Kafka 地址就绪时可开启仿真投递")
        if self._mock_thread and self._mock_thread.is_alive():
            return
        self._start_consumer()
        self._mock_stop = threading.Event()
        from app.gateway import mock_producer
        self._mock_thread = threading.Thread(
            target=mock_producer.run_mock, args=(self._mock_stop,),
            name="gateway-mock-producer", daemon=True)
        self._mock_thread.start()
        self.mock_started_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self.mock_stopped_at = None
        print("[gateway] mock 仿真数据投递已启动")

    def stop_mock(self) -> None:
        """运行时停止 mock 投递线程（消费线程保持，清完存量积压后静默）。"""
        t = self._mock_thread
        if t and t.is_alive():
            self._mock_stop.set()
            t.join(timeout=8)
            print("[gateway] mock 仿真数据投递已停止")
        self._mock_thread = None
        self.mock_stopped_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    def stop(self) -> None:
        self._stop.set()
        self.stop_mock()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=5)

    def _run_with_reconnect(self) -> None:
        from app.gateway.consumer import run_loop
        while not self._stop.is_set():
            try:
                run_loop(self._stop, self._record_error)
            except Exception as exc:
                self._record_error(exc)
                if not self._stop.is_set():
                    time.sleep(5)  # 断线退避重连

    def _record_error(self, exc: Exception) -> None:
        self.last_error = f"{datetime.now():%Y-%m-%d %H:%M:%S} {exc}"
        print(f"[gateway] consumer error: {exc}")

    def status(self) -> dict:
        from app.gateway import startup_config
        from app.gateway import mock_producer
        return {
            "mode": settings.gateway_mode,
            "enabled": settings.gateway_enabled,
            "running": self.running,
            "startedAt": self.started_at,
            "lastError": self.last_error,
            "groupId": settings.kafka_group_id,
            "topics": settings.gateway_topics,
            "bootstrapServers": settings.kafka_bootstrap_servers,
            "mediaStrategy": settings.gateway_media_strategy,
            "fileBaseUrl": settings.gateway_file_base_url,
            "configSource": startup_config.sources(),
            "mockDeliveryEnabled": settings.mock_delivery_enabled,
            "mockRunning": bool(self._mock_thread and self._mock_thread.is_alive()),
            "mockStartedAt": self.mock_started_at,
            "mockStoppedAt": self.mock_stopped_at,
            "mockStats": dict(mock_producer.stats),
        }


gateway_runner = GatewayRunner()

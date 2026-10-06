"""vps 车载网关接入子包（GATEWAY-PLAN-001 一期：数据面）

模块划分：
- mappers：网关 Kafka 载荷（fastjson2 JSON）→ 平台入库结构的纯函数映射；
- terminal_resolver：终端双锚点解析（手机号两级 + truckId 反查 + 未登记隔离）；
- media_store：报警附件转存（local 下载 / proxy 仅记录 / mock:// 演示占位）；
- consumer：Kafka 消费循环、按 topic 分发、手动提交、死信隔离；
- runner：生命周期管理（启停 + 运行状态），由 main.py lifespan 驱动；
- mock_producer：GATEWAY_MODE=mock 时的仿真网关数据投递（仅验证用）。
"""

# SmartServe AI 验收报告

| 模块 | 验证范围 |
| --- | --- |
| MySQL 与 Flyway | 本地 MySQL 验证 V1–V6、文档分块持久化和 FULLTEXT 降级。 |
| 本地模型 | 使用 Ollama 的 BGE-M3 和 DeepSeek R1，验证 1024 维向量生成及知识库问答。 |
| RAG | 本地验证 Top-5 召回、Top-3 上下文、LLM 重排、多轮追问、引用诊断和索引重建。小模型的拒答与指代消解仍存在波动。 |
| Web 页面 | 本地浏览器走查注册登录、工单、通知页面、文档处理和知识库问答。 |
| RocketMQ | 历史验收记录包含真实 Broker 的事件投递与消费。本轮本地运行关闭 RocketMQ，工单页面走查与真实消息投递属于不同的验证范围。 |
| Redis Stack | RediSearch 向量索引与重建逻辑已实现，协议测试使用模拟连接。本机真实 Redis Stack 尚未验证。 |
| DashScope | 已接入 Spring AI Alibaba 的 `text-embedding-v3`，尚未进行真实调用。 |

自动化测试使用 H2 和 Mock 覆盖工单并发更新、Outbox 事务回滚与重试、通知幂等、分块处理、向量检索、重排和异常降级。测试命令及本地复现步骤见 [README](README.md)。

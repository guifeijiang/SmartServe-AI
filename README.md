# SmartServe AI

SmartServe AI 是面向企业售后客服的全栈演示项目，将工单协作、处理通知和知识库问答整合到同一套系统。客服人员处理工单，客户查看进度并通过知识库获得带引用的回答。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5.16、Spring Security、JWT、MyBatis、Flyway |
| 前端 | Vue 3、Vite 8 |
| 数据与检索 | MySQL 8、FULLTEXT ngram、Redis Stack / RediSearch |
| 消息 | RocketMQ 4.4.0、Transactional Outbox |
| AI | Spring AI 1.1.2、Spring AI Alibaba 1.1.2.3、Ollama BGE-M3、DeepSeek R1 |

## 系统架构与核心业务

Vue 前端通过 REST API 调用 Spring Boot 后端。Spring Security 校验 JWT，服务层检查角色与资源归属，MyBatis 访问 MySQL。知识库通过独立的模型与向量索引接口连接 Ollama 和检索存储。

- **账号与权限** 支持注册、登录和无状态 JWT。角色为 CUSTOMER、AGENT、ADMIN。工单访问与操作结合角色、创建人和处理人校验。
- **工单状态机** 支持创建、分配、接单、处理、解决和关闭，并记录操作日志。主要状态为 `NEW → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`。SQL 更新以原状态为条件，在适用的操作中同时检查处理人，服务层根据受影响行数处理并发冲突。
- **RocketMQ 与 Outbox** 创建工单与写入 Outbox 使用同一数据库事务。投递任务发送工单创建事件，失败可重试，消费者幂等生成通知。历史验收包含真实 Broker 投递与消费证据。本轮本地演示关闭了 RocketMQ，通知链路的真实投递结果来自历史验收。
- **知识库管理** 支持上传、解析和处理 TXT、PDF、DOCX 文档。文本按 500 字符切块，相邻块重叠 80 字符。BGE-M3 生成 1024 维向量，分块和向量存入 MySQL。重建索引可复用已有向量。

## RAG 检索与问答

1. 用 BGE-M3 向量化问题并召回 Top-5。Redis Stack 实现 RediSearch HNSW 检索，MySQL 方案以 Java 计算余弦相似度。向量不可用、结果为空或相似度过低时，使用 MySQL FULLTEXT。
2. Rerank 对候选块进行 LLM 列表式评分，评分失败时回退至 BM25，选取 Top-3 作为问答上下文。
3. Ollama 上的 DeepSeek R1 根据检索上下文生成回答。回答附带文档、分块及评分引用，并返回检索和降级诊断。会话保存用户及助手消息，用于多轮追问与检索改写。

本地实际运行使用 BGE-M3 和 `deepseek-r1:1.5b`，向量检索使用 MySQL 余弦方案。Redis Stack 代码已实现，尚未在本机真实运行验证。Spring AI Alibaba 提供 `text-embedding-v3` 接入，尚未进行真实调用。

## 数据库设计

| 业务 | 表 |
| --- | --- |
| 用户与角色 | `sys_user` |
| 工单与操作审计 | `service_ticket`、`ticket_operation_log` |
| 消息投递与通知 | `message_outbox`、`ticket_notification` |
| 知识库、文档与分块 | `knowledge_base`、`kb_document`、`document_chunk` |
| 对话、引用与检索诊断 | `chat_session`、`chat_message` |

Flyway 的 V1–V6 在 `local` profile 下执行或校验。分块表使用 FULLTEXT ngram 支持中文检索，消息表保存检索诊断。

## 项目结构

```text
backend/    Spring Boot 后端、Flyway 迁移与测试
frontend/   Vue 前端
scripts/    本地验收与故障降级验证脚本
```

## 环境配置与启动

需要 JDK 17、Maven、支持 Vite 8 的 Node.js（例如 22.12+）、MySQL 8 和 Ollama。以下示例使用 PowerShell，从仓库根目录开始。

1. 在 MySQL 中创建数据库，并给应用账号授予该库所需的建表、迁移和读写权限。

   ```sql
   CREATE DATABASE IF NOT EXISTS knowflow_ai
     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 启动 Ollama 并准备本地模型。

   ```powershell
   ollama pull bge-m3
   ollama pull deepseek-r1:1.5b
   ```

3. 设置当前终端的环境变量并启动后端。将尖括号占位符替换为本机实际值。其他可选变量见 [环境变量模板](.env.example)。

   ```powershell
   $env:KNOWFLOW_DB_USERNAME = '<数据库账号>'
   $env:KNOWFLOW_DB_PASSWORD = '<数据库密码>'
   $env:KNOWFLOW_JWT_SECRET = '<至少 32 字节的随机密钥>'
   $env:SPRING_PROFILES_ACTIVE = 'local'
   $env:SERVER_PORT = '8081'
   $env:KNOWFLOW_AI_EMBEDDING_PROVIDER = 'ollama'
   $env:KNOWFLOW_AI_CHAT_PROVIDER = 'ollama'
   $env:KNOWFLOW_RAG_VECTOR_STORE = 'mysql'
   $env:KNOWFLOW_ROCKETMQ_ENABLED = 'false'
   $env:KNOWFLOW_ROCKETMQ_CONSUMER_ENABLED = 'false'
   $env:KNOWFLOW_ROCKETMQ_OUTBOX_DISPATCH_ENABLED = 'false'
   Set-Location backend
   mvn spring-boot:run
   ```

   健康检查为 `http://127.0.0.1:8081/api/health`。MySQL 默认连接 `127.0.0.1:3306`，Ollama 默认连接 `127.0.0.1:11434`。

4. 在另一终端从仓库根目录启动前端。

   ```powershell
   Set-Location frontend
   npm ci
   npm run dev
   ```

   打开 `http://127.0.0.1:5173/#/login`。Vite 将 `/api` 请求代理到后端。注册测试账号后，可以创建工单或在有权限的知识库中上传并处理样例文档，再打开对话页面测试引用。

5. 验证消息链路时，启动 RocketMQ NameServer 和 Broker，按需设置 `KNOWFLOW_ROCKETMQ_NAME_SERVER`，将上述三个 RocketMQ 开关改为 `true` 后重新启动后端。创建工单并检查 Outbox 状态和通知页面。

## 测试

- 在 `backend/` 执行 `mvn test`。默认测试通过 H2 与 Mock 覆盖权限、工单状态流转、Outbox、通知、检索、重排和降级逻辑。
- 在 `frontend/` 执行 `npm run build` 验证生产构建。
- `scripts/verify-local-acceptance.ps1` 验证登录、文档处理、多轮问答、索引重建与工单。使用独立测试环境，并通过参数提供测试账号和密码。启用 RocketMQ 后可同时验证通知。
- `scripts/verify-degradation.ps1` 用于检查检索降级。真实运行范围见 [验收报告](ACCEPTANCE-REPORT.md)。

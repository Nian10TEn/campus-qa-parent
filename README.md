|------|------|
| 后端框架 | Spring Boot 2.7.18、Spring Cloud 2021.0.5、Spring Cloud Alibaba 2021.0.5.0 |
| 注册中心 / 限流 | Nacos Discovery、Sentinel |
| 通信 | OpenFeign、Spring Cloud Gateway、WebSocket（原生 javax.websocket） |
| 缓存 / 队列 | Redis（问题队列 + 会话历史 + 最近答案） |
| AI 服务 | Python FastAPI、LangChain、FAISS、FastEmbed（bge-small-zh-v1.5）、DeepSeek API |
| 前端 | 原生 HTML / CSS / JavaScript（单页） |
| JDK | 11+ |

## 目录结构

```
campus-qa-parent/
├── ai-service/                  # Python AI 服务（端口 8000）
│   ├── main.py                  # 向量检索 + DeepSeek 大模型生成
│   └── faq.txt                  # FAQ 知识库（按空行分隔，问/答成对）
├── campus-common/               # 公共模块
│   └── Constants.java           # Redis Key 常量
│   └── R.java                   # 统一响应封装
├── campus-gateway/              # 网关服务（端口 8080）
│   └── application.yml          # 路由 /api/** → campus-service，CORS
├── campus-service/              # 核心业务服务（端口 9001）
│   ├── controller/QaController.java     # POST /qa/ask 问题入队
│   ├── consumer/QuestionConsumer.java   # Redis 队列消费线程
│   ├── util/RedisQueueUtil.java         # 队列工具
│   ├── util/SessionHistoryUtil.java     # 会话历史（Redis List，最多 10 轮，30 分钟）
│   ├── websocket/QaWebSocketServer.java # WebSocket 端点 /ws/qa/{sessionId}
│   └── feign/AiServiceClient.java       # 调用 Python AI 服务
└── frontend/                    # 前端聊天页面
    └── index.html
```

## 环境要求

- JDK 11+
- Maven 3.6+
- Python 3.9+
- Redis（默认 127.0.0.1:6379）
- Nacos Server（默认 127.0.0.1:8848）
- （可选）DeepSeek API Key，用于大模型生成回答

## 快速开始

### 1. 启动基础设施

启动 Redis 与 Nacos Server，确保 `127.0.0.1:6379`、`127.0.0.1:8848` 可访问。

### 2. 启动 AI 服务（端口 8000）

```bash
cd ai-service
pip install fastapi uvicorn langchain-community langchain-text-splitters fastembed requests
uvicorn main:app --host 0.0.0.0 --port 8000
```

> 如需大模型生成回答，请修改 `main.py` 中的 `DEEPSEEK_API_KEY` 为你的 Key；
> 不设置 Key 时 AI 服务仍会返回检索到的 FAQ 原文，但大模型接口调用会失败。

### 3. 启动后端服务

```bash
# 在项目根目录安装公共模块
mvn clean install -DskipTests

# 启动业务服务（端口 9001）
cd campus-service
mvn spring-boot:run

# 启动网关（端口 8080）
cd ../campus-gateway
mvn spring-boot:run
```

### 4. 打开前端

直接双击打开 `frontend/index.html`（建议使用 VS Code Live Server，端口 5500，网关已配置对应 CORS）。

## 调用流程

1. 前端生成 `sessionId`，建立 WebSocket 连接 `ws://localhost:9001/ws/qa/{sessionId}`；
2. 用户提问后，前端 `POST http://localhost:8080/api/qa/ask`，携带 `{ sessionId, question }`；
3. 网关将请求路由到 `campus-service`，问题 JSON 写入 Redis 队列 `campus:qa:queue`，接口立即返回"已受理"；
4. 消费线程从队列取出问题，读取最近 5 条会话历史，通过 Feign 调用 Python AI 服务 `/ask`；
5. AI 服务对 `faq.txt` 做向量检索，将命中的知识 + 历史拼进 Prompt 调 DeepSeek 生成回答；
6. 回答写入会话历史与"最近答案"（供 WebSocket 不可用时的兜底轮询），并通过 WebSocket 推送给前端。

## 接口说明

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/qa/ask` | 提问入队，请求体 `{ "sessionId": "web_xxx", "question": "图书馆几点开门？" }` |
| GET | `/qa/health`（AI 服务直连） | AI 服务健康检查 |
| POST | `/ask`（AI 服务直连） | AI 回答，请求体 `{ "question": "...", "history": [] }` |
| WS | `/ws/qa/{sessionId}` | WebSocket 长连接，接收 `{ "type": "answer", "question": "...", "answer": "..." }` |

统一响应格式：`{ "code": 200, "message": "操作成功", "data": { ... } }`

## 配置说明

- `campus-service/src/main/resources/application.yml`
  - `server.port`：业务服务端口（默认 9001）
  - `ai.service.url`：Python AI 服务地址（默认 `http://127.0.0.1:8000`）
  - `spring.redis`：Redis 连接配置
- `campus-gateway/src/main/resources/application.yml`
  - `spring.cloud.gateway.routes`：路由规则（`/api/**` → `lb://campus-service`，StripPrefix=1）
  - `globalcors`：前端跨域白名单（默认 `http://127.0.0.1:5500`）
- `ai-service/main.py`
  - `DEEPSEEK_API_KEY`：DeepSeek API Key（占位符，需替换）
  - `faq.txt`：知识库内容，修改后需重启 AI 服务

## 常见问题

- **AI 回答提示"服务不可用"**：多为 DeepSeek Key 未配置或调用失败，可先配置有效 Key；知识库未加载时请确认 `faq.txt` 与 `main.py` 在同一目录。
- **前端连不上 WebSocket**：确认 `campus-service` 已启动且端口 9001 未被占用；WebSocket 直连业务服务端口。
- **网关 503**：确认 `campus-service` 已成功注册到 Nacos（`127.0.0.1:8848`）。
- **跨域报错**：前端请使用 `http://127.0.0.1:5500` 访问，或修改网关 `globalcors.allowedOrigins`。
- **会话没上下文**：Redis 中 `campus:qa:session:*` 的会话历史默认 30 分钟过期，过期后视为新会话。

## 许可证

本项目仅供学习交流使用，未指定开源许可证。

# 校园智能客服问答系统

基于微服务架构和 RAG 技术的智能客服平台，可自动回答校园常见问题，支持多轮对话和人工辅助。

- **技术栈**：Spring Cloud Alibaba + Redis + Python FastAPI + LangChain + FAISS
- **前端**：原生 HTML/JS，通过 HTTP 请求与 WebSocket 推送交互
- **AI 能力**：本地 ONNX 向量模型 + FAISS 检索 + DeepSeek 大模型生成

---

## 目录结构

```
campus-qa-parent/
├── ai-service/                          # Python AI 服务（端口 8000）
│   ├── main.py                          # FastAPI 应用，向量检索 + DeepSeek 生成
│   └── faq.txt                          # FAQ 知识库（每行一条：问题|答案）
├── campus-common/                       # Java 公共模块
│   └── src/main/java/com/campus/common/
│       ├── Constants.java               # Redis Key 常量
│       └── R.java                       # 统一响应封装
├── campus-gateway/                      # 网关服务（端口 8080）
│   ├── src/main/java/com/campus/gateway/
│   │   └── GatewayApplication.java      # 启动类
│   └── src/main/resources/
│       └── application.yml              # 路由、CORS、Sentinel 控制台
├── campus-service/                      # 核心业务服务（端口 9001）
│   ├── src/main/java/com/campus/service/
│   │   ├── ServiceApplication.java      # 启动类（开启 Feign）
│   │   ├── config/
│   │   │   └── WebSocketConfig.java     # WebSocket 配置
│   │   ├── consumer/
│   │   │   └── QuestionConsumer.java    # Redis 队列消费线程
│   │   ├── controller/
│   │   │   └── QaController.java        # POST /qa/ask 提问接口
│   │   ├── dto/                         # 请求/响应 DTO
│   │   ├── feign/
│   │   │   └── AiServiceClient.java     # 调用 Python AI 服务
│   │   ├── util/
│   │   │   ├── RedisQueueUtil.java      # Redis List 队列工具
│   │   │   └── SessionHistoryUtil.java  # 多轮对话上下文管理
│   │   └── websocket/
│   │       └── QaWebSocketServer.java   # WebSocket 端点
│   └── src/main/resources/
│       └── application.yml              # 业务服务配置
├── frontend/
│   └── index.html                       # 前端聊天页面
├── pom.xml                              # Maven 父工程
└── .gitignore
```

---

## 环境要求

- **JDK** 11+
- **Maven** 3.6+
- **Python** 3.9+
- **Redis**（默认 `127.0.0.1:6379`）
- **Nacos Server** 2.2.x（默认 `127.0.0.1:8848`）
- （可选）**DeepSeek API Key**：用于调用大模型生成回答

---

## 快速开始

### 1. 启动基础设施

- 启动 **Redis**
- 启动 **Nacos**（解压后进入 `bin` 目录，执行 `startup.cmd -m standalone`）
- 确保 `127.0.0.1:6379` 和 `127.0.0.1:8848` 可访问。

### 2. 启动 AI 服务（端口 8000）

```bash
cd ai-service

# 安装依赖（建议在虚拟环境中操作）
pip install fastapi uvicorn langchain langchain-community langchain-text-splitters faiss-cpu fastembed requests

# 修改 main.py 中的 DEEPSEEK_API_KEY 为你的 Key（可选，不配置则大模型生成功能不可用）
# 启动服务
uvicorn main:app --host 0.0.0.0 --port 8000
```

> 首次启动时会自动下载 ONNX 向量模型（约 130MB），请耐心等待。看到 `FAISS 向量库构建完成` 即表示启动成功。

### 3. 启动后端微服务

```bash
# 在项目根目录安装公共模块
mvn clean install -DskipTests

# 启动业务服务（端口 9001）
cd campus-service
mvn spring-boot:run

# 启动网关（端口 8080，另开终端）
cd ../campus-gateway
mvn spring-boot:run
```

> 两个服务启动后，可以在 Nacos 控制台 `http://127.0.0.1:8848/nacos` 看到 `campus-gateway` 和 `campus-service` 的注册实例。

### 4. 打开前端

**推荐方式**：使用 VS Code 的 Live Server 插件，在 `frontend/index.html` 上右键 → Open with Live Server（默认端口 5500）。  
网关已配置 `http://127.0.0.1:5500` 的跨域白名单。

**备选方式**：进入 `frontend` 目录，执行 `python -m http.server 5500`，然后访问 `http://127.0.0.1:5500`。

---

## 调用流程

1. 前端生成 `sessionId`，建立 WebSocket 连接 `ws://localhost:9001/ws/qa/{sessionId}`；
2. 用户提问后，前端 `POST http://localhost:8080/api/qa/ask`，携带 `{ "sessionId": "...", "question": "..." }`；
3. 网关将请求路由到 `campus-service`，Controller 将请求 JSON 压入 Redis 队列 `campus:qa:queue`，并立即返回 `"已受理"`；
4. 后台消费线程从队列中取出问题，读取该会话最近 5 轮历史记录，拼成上下文 Prompt；
5. 消费线程通过 OpenFeign 调用 Python AI 服务的 `/ask` 接口；
6. AI 服务检索 `faq.txt` 向量库，将 Top-3 相关片段与历史对话一起拼接为最终 Prompt，调用 DeepSeek 生成回答；
7. 回答被写回 Redis 会话历史，并通过 WebSocket 推送给对应前端；
8. 前端收到推送后，移除“AI 正在思考中”状态，显示回答。

---

## 接口说明

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/qa/ask` | 提问入队。请求体 `{ "sessionId": "web_xxx", "question": "图书馆几点开门？" }` |
| `GET` | `/qa/health`（AI 服务直连） | AI 服务健康检查 |
| `POST` | `/ask`（AI 服务直连） | AI 回答。请求体 `{ "question": "...", "history": [] }` |
| `WS` | `/ws/qa/{sessionId}` | WebSocket 长连接。服务端会推送格式：`{ "type": "answer", "question": "...", "answer": "..." }` |

**统一响应格式**：`{ "code": 200, "message": "操作成功", "data": { ... } }`

---

## 配置说明

### campus-service / application.yml

```yaml
server.port: 9001
spring.redis.host: 127.0.0.1
spring.redis.port: 6379
ai.service.url: http://127.0.0.1:8000   # Python AI 服务地址
```

### campus-gateway / application.yml

```yaml
server.port: 8080
spring.cloud.gateway.routes:
  - id: campus-service-route
    uri: lb://campus-service
    predicates:
      - Path=/api/**
    filters:
      - StripPrefix=1
spring.cloud.gateway.globalcors.cors-configurations.['/**']:
  allowedOrigins: "http://127.0.0.1:5500"
  allowedMethods: "*"
  allowedHeaders: "*"
  allowCredentials: true
```

### ai-service / main.py

- `DEEPSEEK_API_KEY`：DeepSeek 的 API Key（不配置时，AI 会返回“服务不可用”提示，但向量检索功能不受影响）。
- `faq.txt`：FAQ 知识库，修改后需重启 AI 服务。

---

## 常见问题

**Q：AI 回答提示“AI 服务暂时不可用”**  
A：通常是 DeepSeek API Key 未配置或调用失败。请检查 `main.py` 中的 Key 是否有效，以及网络能否访问 `api.deepseek.com`。

**Q：前端连不上 WebSocket**  
A：确认 `campus-service` 已启动且端口 9001 未被占用。WebSocket 直连业务服务，未经过网关。

**Q：访问网关返回 503**  
A：确认 `campus-service` 已成功注册到 Nacos，并且路由中 `lb://campus-service` 的服务名与 `spring.application.name` 一致。

**Q：浏览器提示跨域错误**  
A：请使用 `http://127.0.0.1:5500` 访问前端，并确保网关 `globalcors.allowedOrigins` 包含该地址。

**Q：多轮对话没有上下文**  
A：Redis 中的会话历史默认 30 分钟过期（`Constants.SESSION_KEY_PREFIX` 相关）。过期后会被视为新会话，之前的对话不会被拼接。

---

## 技术亮点

- **异步解耦**：Redis List 作为轻量级消息队列，避免大模型延迟阻塞用户请求，搭配 WebSocket 实现实时推送。
- **RAG 架构**：本地 ONNX 模型 + FAISS 向量检索 + DeepSeek 生成，有效抑制大模型幻觉。
- **多轮对话**：基于 Redis 的 session 级历史管理，自动拼接 Prompt，实现上下文感知的连续问答。
- **微服务治理**：Spring Cloud Gateway 统一入口，集成 Sentinel 限流（可扩展），Feign 声明式调用，支持 GZIP 压缩。
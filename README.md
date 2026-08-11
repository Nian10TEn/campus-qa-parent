# campus-qa-parent · 校园智能客服

一个基于微服务架构的校园智能客服系统 Demo：前端通过 WebSocket 与后端保持长连接，问题经 Redis 队列异步处理，由 Python AI 服务基于 FAQ 知识库（向量检索）并结合 DeepSeek 大模型生成回答，最终通过 WebSocket 实时推送给用户。

## 功能特性

- 💬 **实时问答**：WebSocket 长连接，答案生成后主动推送，无需轮询
- ⚙️ **异步处理**：Redis 队列解耦请求与处理，支持高并发提问
- 🧠 **RAG 检索增强**：基于 FAQ 知识库构建 FAISS 向量库，检索相关内容再交给大模型回答
- 🔤 **多轮对话**：会话历史保存在 Redis，自动携带最近 5 轮上下文
- 🛡️ **微服务架构**：网关（Gateway）+ 业务服务（Service）+ AI 服务（Python）三层分离，接入 Nacos 注册发现与 Sentinel 限流
- 🖥️ **开箱前端**：单页聊天界面，支持快捷问题、时间戳、连接异常提示

## 系统架构

```mermaid
flowchart LR
    A[前端 index.html] -->|POST /api/qa/ask| B[Gateway :8080]
    A <-->|WebSocket /ws/qa/{sessionId}| C[campus-service :9001]
    B --> C
    C -->|问题入队| D[(Redis 队列)]
    D -->|消费线程| C
    C -->|OpenFeign /ask| E[ai-service :8000]
    E -->|FAISS 检索 FAQ| F[faq.txt 知识库]
    E -->|DeepSeek 生成| G[DeepSeek API]
    C -->|保存会话历史| H[(Redis)]
    C -->|WebSocket 推送答案| A
```

## 技术栈

| 模块 | 技术 |
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

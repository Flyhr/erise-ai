# Python AI Chat 集成说明

## 目标链路

当前正式链路已经切换为：

`Vue UI -> Java Backend (/api/v1/ai) -> Python AiAssistant (/internal/ai/chat/*)`

旧的 `erise-ai-cloud` Java AI 服务仅保留代码作为回滚参考，不再承接运行流量。

## 启动顺序

1. 启动 MySQL / Redis / MinIO
2. 初始化 Python AI 数据库
3. 启动 Python `AiAssistant`
4. 启动 Java `erise-ai-backend`
5. 启动前端 `erise-ai-ui`

如果直接使用 Docker Compose，以上步骤已经编排好。

## Java 到 Python 的关键配置

Java 后端使用以下配置访问 Python AI 服务：

- `CLOUD_BASE_URL=http://cloud:8081`
- `INTERNAL_API_KEY=<same token used by Python>`
- `DEFAULT_MODEL_CODE=<python default model>`

Python 服务使用以下配置回调 Java 内部接口：

- `JAVA_INTERNAL_BASE_URL=http://backend:8080/internal/v1`
- `JAVA_INTERNAL_API_KEY=<same internal token>`

## 数据库初始化

### Docker

首次启动空 MySQL 数据卷时，会自动执行：

- `AiAssistant/sql/mysql/001_ai_chat_schema.sql`

随后 Python 服务启动时会执行默认模型和提示词的 bootstrap。

### 本地非 Docker

在 `AiAssistant/` 下执行：

```bash
python scripts/init_db.py
```

## 联调检查清单

### 1. 检查 Python 服务

```bash
python scripts/smoke_test.py
```

或手动访问：

```bash
curl http://localhost:8081/internal/ai/chat/health
```

### 2. 检查 Java 代理

登录后调用：

- `GET /api/v1/ai/models`
- `GET /api/v1/ai/sessions`
- `POST /api/v1/ai/chat/stream`
- `POST /api/v1/ai/chat/{requestId}/cancel`

### 3. 检查前端页面

- 打开 AI 页面
- 新建会话并发送消息
- 验证流式输出
- 验证停止生成
- 验证刷新后会话仍可回放

## 旧 Java AI 代码现状

以下代码保留但默认停用：

- `erise-ai-cloud/src/main/java/com/erise/ai/cloud/controller/InternalAiController.java`
- `erise-ai-cloud/src/main/java/com/erise/ai/cloud/service/RagChatService.java`
- `erise-ai-cloud/src/main/java/com/erise/ai/cloud/provider/DeepSeekClient.java`
- `erise-ai-cloud/src/main/java/com/erise/ai/cloud/integration/BackendInternalClient.java`

它们的 Spring 注解已经被注释掉，不会被正式运行链路注册。
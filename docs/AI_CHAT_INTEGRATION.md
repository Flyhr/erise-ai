# Erise-AI Chat 联调与部署说明

## 当前正式架构

- `erise-ai-ui`：前端页面与流式会话 UI
- `erise-ai-backend`：统一鉴权、项目权限校验、AI 网关
- `AiAssistant`：Python AI 聊天服务，负责模型调用与会话持久化

## 启动方式

### 方式一：Docker Compose（推荐）

在仓库根目录执行：

```bash
docker compose up --build
```

关键点：

- MySQL 首次初始化时会自动执行 `AiAssistant/sql/mysql/001_ai_chat_schema.sql`
- Python AI 服务带健康检查，Java 后端会在它就绪后再启动
- Java 后端默认把 `/api/v1/ai/*` 代理到 Python AI 服务

### `docker compose up --build` 全链路启动检查清单

1. 启动全部服务

```bash
docker compose up --build
```

2. 另开终端检查容器状态

```bash
docker compose ps
```

期望结果：

- `mysql` 为 `healthy`
- `redis` 为 `healthy`
- `cloud` 为 `healthy`
- `backend` 为 `running`
- `ui` 为 `running`
- `nginx` 为 `running`

3. 检查 Python AI 服务启动日志

```bash
docker compose logs cloud --tail=100
```

重点确认：

- 没有启动期异常
- 没有模型 bootstrap 异常
- 没有数据库建表异常

4. 检查 Java 后端日志

```bash
docker compose logs backend --tail=100
```

重点确认：

- Spring Boot 正常启动
- 没有调用 Python AI 服务的连接错误
- 没有数据源初始化错误

5. 检查基础健康接口

```bash
curl http://localhost:8081/internal/ai/chat/health
curl http://localhost:8080/actuator/health
curl http://localhost:8088
```

期望结果：

- Python AI 返回 `code=0`
- Backend actuator 返回 `UP`
- Nginx 首页可访问

6. 登录前端并验证 AI 页面

- 打开 `http://localhost:8088`
- 登录系统
- 进入 AI 页面
- 确认侧边栏显示的是后端真实返回的模型列表，而不是静态文案
- 切换不同模型，确认页面标题和工具栏会同步变化

7. 验证完整聊天链路

- 新建一个会话
- 发送第一条消息，确认可以流式返回
- 再发送第二条消息，确认上下文记忆生效
- 点击“停止”，确认取消生成可用
- 刷新页面，确认会话和消息可以回放

8. 验证公共 AI API

登录后通过前端或接口工具检查：

- `GET /api/v1/ai/models`
- `GET /api/v1/ai/sessions`
- `POST /api/v1/ai/chat/stream`
- `POST /api/v1/ai/chat/{requestId}/cancel`

### 方式二：本地分服务启动（不推荐）

1. 启动 MySQL / Redis / MinIO
2. 进入 `AiAssistant/`
3. 执行 `python scripts/init_db.py`
4. 执行 `python scripts/smoke_test.py`
5. 执行 `uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload`
6. 启动 `erise-ai-backend`
7. 启动 `erise-ai-ui`

## 联调顺序

### 第一步：确认 Python AI 服务可用

- `GET http://localhost:8081/internal/ai/chat/health`
- `python AiAssistant/scripts/smoke_test.py`

### 第二步：确认 Java 网关已接入 Python

- `GET http://localhost:8080/api/v1/ai/models`
- `GET http://localhost:8080/api/v1/ai/sessions`
- `POST http://localhost:8080/api/v1/ai/chat/stream`

### 第三步：确认前端行为

- 新建聊天会话
- 连续发送两轮消息，确认上下文生效
- 点击“停止”按钮，确认取消生成可用
- 刷新页面，确认会话和消息可以回放
- 侧边栏能显示后端实时返回的模型列表

## 排查建议

### Python 服务启动失败

优先检查：

- `MYSQL_DSN`
- `REDIS_URL`
- `INTERNAL_SERVICE_TOKEN`
- `OPENAI_API_KEY` / `DEEPSEEK_API_KEY`

### Java 调 Python 失败

优先检查：

- `CLOUD_BASE_URL`
- `INTERNAL_API_KEY`
- Python 健康检查是否通过
- Java 与 Python 是否使用同一个内部 token

### Python 调 Java 内部接口失败

优先检查：

- `JAVA_INTERNAL_BASE_URL`
- `JAVA_INTERNAL_API_KEY`
- `erise-ai-backend` 的 `/internal/v1/projects/{id}/context` 是否可访问

# Erise-AI

Erise-AI 当前默认运行架构已经切换为：

- `erise-ai-ui`：Vue 3 + Vite 前端
- `erise-ai-backend`：Spring Boot 业务后端与 AI 网关
- `AiAssistant`：Python AI 聊天服务
- `erise-ai-cloud`：旧 Java AI 服务，保留代码但不参与默认运行

## 仓库结构

```text
AiAssistant/        Python AI chat service
erise-ai-backend/  Spring Boot business backend
erise-ai-cloud/    Legacy Java AI service kept for reference
erise-ai-ui/       Vue 3 frontend
deploy/            Nginx and deployment assets
docs/              Integration and deployment notes
docker-compose.yml Local development stack
```

## AI 聊天运行链路

正式链路：

`UI -> /api/v1/ai -> Java Backend -> Python AiAssistant -> Model Provider`

其中：

- Java 负责鉴权、项目权限、公共 API 与 SSE 转发
- Python 负责会话、消息、模型调用、取消生成、项目上下文拼装
- 旧的 `erise-ai-cloud` 只保留源码，不再承接正式流量

## 快速开始

1. 复制 `.env.example` 为 `.env`
2. 填写 `INTERNAL_API_KEY` 与至少一个模型 provider key
3. 启动整套环境：

```bash
docker compose up --build
```

4. 打开以下地址检查状态：

- Nginx: `http://localhost:8088`
- Backend health: `http://localhost:8080/actuator/health`
- Python AI health: `http://localhost:8081/internal/ai/chat/health`

## `docker compose up --build` 检查清单

1. 运行 `docker compose ps`，确认 `mysql`、`redis`、`cloud` 已健康，`backend`、`ui`、`nginx` 已启动。
2. 运行 `docker compose logs cloud --tail=100`，确认 Python AI 服务没有启动期异常。
3. 运行 `docker compose logs backend --tail=100`，确认 Java 后端没有代理 Python AI 的连接错误。
4. 访问 `http://localhost:8088` 登录系统并进入 AI 页面。
5. 确认 AI 页侧边栏显示的是后端真实返回的模型列表。
6. 发送消息、停止生成、刷新页面，确认完整聊天链路可用。

完整版本见 `docs/AI_CHAT_INTEGRATION.md`。

## Python AI 单独启动

```bash
cd AiAssistant
pip install -r requirements.txt
python scripts/init_db.py
python scripts/smoke_test.py
uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload
```

## 关键文档

- `docs/AI_CHAT_INTEGRATION.md`
- `AiAssistant/README.md`
- `AiAssistant/INTEGRATION_NOTES.md`

## 说明

`erise-ai-cloud` 里的旧 Java AI 代码没有删除，但已通过注释停用 Spring 注册注解，默认不会参与当前部署链路。
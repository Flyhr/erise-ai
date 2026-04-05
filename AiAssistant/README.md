# AiAssistant

`AiAssistant` 是 Erise-AI 当前使用中的 Python AI 聊天服务，负责会话管理、消息持久化、模型适配、流式输出、停止生成，以及结合 Java 后端提供项目上下文能力。

## 当前职责

- 提供内部 AI API：`/internal/ai/chat/*`
- 持久化会话、消息、请求日志、提示词模板与模型配置
- 适配 OpenAI / DeepSeek 等 OpenAI-compatible 模型
- 支持普通回复、SSE 流式回复、取消生成
- 通过 Java 内部接口读取项目上下文

## 配置文件优先级

开发态下配置读取优先级已经调整为：

1. `.env.dev`
2. `.env`

建议从 `.env.dev.example` 复制出 `.env.dev` 并在开发阶段优先维护该文件。

## 独立本地启动

如果需要单独调试 Python AI 服务，可在 `AiAssistant` 目录执行：

1. 安装依赖

```bash
pip install -r requirements.txt
```

2. 初始化数据库

```bash
python scripts/init_db.py
```

3. 运行 smoke test

```bash
python scripts/smoke_test.py
```

4. 启动服务

```bash
uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload
```

## 推荐开发方式

日常联调更推荐在仓库根目录使用纯 Docker 开发态：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml up --build
```

在这种模式下：

- 本地修改 `AiAssistant/src` 会直接同步到容器内
- 容器通过 `uvicorn --reload` 自动重载
- 不需要本机单独安装 Python 运行环境

## 常用环境变量

- `APP_ENV=dev`
- `MYSQL_DSN`
- `REDIS_URL`
- `INTERNAL_SERVICE_TOKEN`
- `JAVA_INTERNAL_BASE_URL`
- `JAVA_INTERNAL_API_KEY`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `DEEPSEEK_API_KEY`
- `DEEPSEEK_BASE_URL`
- `DEFAULT_MODEL_CODE`

## 健康检查

- 容器脚本：`python scripts/healthcheck.py`
- HTTP 接口：`GET /internal/ai/chat/health`

## 目录说明

- `src/app/`：主服务代码
- `scripts/init_db.py`：建表与初始化默认数据
- `scripts/smoke_test.py`：本地快速自检
- `scripts/healthcheck.py`：容器健康检查
- `sql/mysql/001_ai_chat_schema.sql`：MySQL 初始化脚本
- `INTEGRATION_NOTES.md`：与 Java / 前端联调说明

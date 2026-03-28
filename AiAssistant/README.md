# AiAssistant

`AiAssistant` 是当前 Erise-AI 的 Python AI 聊天服务。它负责会话管理、消息持久化、模型适配、流式输出、取消生成，以及向 Java 后端回拉项目上下文。

## 当前职责

- 提供内部 AI API：`/internal/ai/chat/*`
- 持久化会话、消息、请求日志、提示词模板、模型配置
- 适配 OpenAI / DeepSeek 这类 OpenAI-compatible 模型
- 支持普通回复、SSE 流式回复、取消生成
- 通过 Java 内部接口读取项目上下文

## 本地启动

1. 安装依赖

```bash
pip install -r requirements.txt
```

2. 初始化数据库与默认模型/提示词

```bash
python scripts/init_db.py
```

3. 运行本地 smoke 检查

```bash
python scripts/smoke_test.py
```

4. 启动服务

```bash
uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload
```

## 常用环境变量

- `MYSQL_DSN`：数据库连接串。本地默认可用 `sqlite:///./ai_chat.db`。
- `REDIS_URL`：Redis 地址。不可用时健康检查会降级，但取消生成会退回内存标记。
- `INTERNAL_SERVICE_TOKEN`：Java 后端调用 Python 服务的内部鉴权 token。
- `JAVA_INTERNAL_BASE_URL`：Python 服务访问 Java 内部接口的基地址。
- `JAVA_INTERNAL_API_KEY`：Python 服务请求 Java 内部接口使用的鉴权 key。
- `OPENAI_API_KEY` / `OPENAI_BASE_URL`
- `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL`
- `DEFAULT_MODEL_CODE`：默认模型编码。

## 数据库初始化方式

### 方式一：本地开发

直接运行：

```bash
python scripts/init_db.py
```

### 方式二：Docker Compose

根目录 `docker-compose.yml` 已挂载 `AiAssistant/sql/mysql/001_ai_chat_schema.sql` 到 MySQL 初始化目录。首次创建 MySQL 数据卷时会自动建表。

## 健康检查

- 容器内健康检查脚本：`python scripts/healthcheck.py`
- HTTP 接口：`GET /internal/ai/chat/health`

## 目录说明

- `src/app/`：新 AI 聊天服务主代码
- `scripts/init_db.py`：建表并初始化默认数据
- `scripts/smoke_test.py`：本地一键自检
- `scripts/healthcheck.py`：容器健康检查
- `sql/mysql/001_ai_chat_schema.sql`：MySQL 初始化脚本
- `INTEGRATION_NOTES.md`：与 Java / 前端联调说明

## 说明

旧版 `src/routers/*` 与 `src/services/*` 已保留为兼容占位，不再承接正式聊天流量。正式入口统一是 `src.app.main:app`。
# Erise-AIV1.0

Erise-AI 是一个面向个人与轻量团队的项目知识库系统，核心能力覆盖项目管理、文件与文档协作、统一检索，以及 AI / RAG 助理能力。

当前运行链路已经统一为：

`UI -> Nginx -> Java Backend -> Python AiAssistant -> Model Provider`

其中：

- `erise-ai-ui`：Vue 3 + Vite 前端
- `erise-ai-backend`：Spring Boot 业务后端与统一网关入口
- `AiAssistant`：Python AI 聊天服务
- `deploy/nginx`：开发态与部署态 Nginx 配置

## 推荐开发方式

开发态统一使用纯 Docker 方式启动，不要求本机额外安装 Java、Node 或 Python 运行环境。

首次使用：

1. 复制 `.env.dev.example` 为 `.env.dev`
2. 按需填写 `OPENAI_API_KEY`、`DEEPSEEK_API_KEY`、`INTERNAL_API_KEY` 等配置
3. 如果要启用联网搜索，请填写：
   - `WEB_SEARCH_PROVIDER=tavily`
   - `TAVILY_API_KEY=<your tavily key>`
   - 不要把 API Key 填到 `WEB_SEARCH_PROVIDER`
4. 在仓库根目录执行：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml up --build
```

停止环境：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml down
```

## 开发态访问地址

- Chrome 统一入口：`http://localhost:8088`
- 前端直连调试口：`http://localhost:5173`
- Java Backend 健康检查：`http://localhost:8080/actuator/health`
- Python AI 健康检查：`http://localhost:8081/internal/ai/chat/health`

## 热更新说明

开发态下，源码直接挂载到容器内，依赖和构建产物保留在容器卷中：

- 前端：Vite HMR + polling，本地修改 Vue / TS / CSS 后浏览器直接热更新
- Python AI：`uvicorn --reload`，修改 `AiAssistant/src` 后容器内自动 reload
- Java Backend：容器内 watcher + `spring-boot:run` / DevTools 自动重启

说明：

- 前端和 Python 服务可以做到不重新构建镜像即可生效
- Java 代码无法做到“完全不编译就生效”，但编译与重启都发生在容器内部，不需要重新执行 `docker compose up --build`

## 什么时候需要重新 `--build`

仅在以下场景建议重新构建：

- `package.json` / `package-lock.json` 发生变化
- `requirements.txt` 发生变化
- `pom.xml` 或 Maven 依赖发生变化
- `Dockerfile.dev` 或基础镜像发生变化

日常业务代码、样式、配置文件调整通常不需要重新 `--build`

## 生产与其他说明

- 开发态使用：`docker-compose.dev.yml`
- 现有部署态编排仍保留：`docker-compose.yml`
- Nginx 开发态配置文件：`deploy/nginx/default.dev.conf`

## 相关文档

- `docs/DOCKER_DEV_MOUNT.md`
- `docs/AI_CHAT_INTEGRATION.md`
- `docs/LOCAL_OPS_RUNBOOK.md`
- `AiAssistant/README.md`

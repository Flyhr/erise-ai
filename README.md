# Erise-AI（开发中）

- Erise-AI 是基于 V1 版本规划的单仓多模块项目实现：包含 Vue 3 前端、Spring Boot 业务后端、Spring Boot AI 云服务，以及基于 Docker Compose 的本地基础环境。
- Erise-AI is a monorepo implementation of the V1 plan: Vue 3 frontend, Spring Boot business backend, Spring Boot AI cloud service, and Docker Compose based local infrastructure.

## 项目结构/Structure

```text
erise-ai-ui/        Vue 3 + Vite + TypeScript frontend
erise-ai-backend/   Spring Boot business backend
erise-ai-cloud/     Spring Boot AI cloud service
deploy/             Nginx and environment examples
docker-compose.yml  Local development stack
```

## 本地开发/Local development

1. 将 `.env.example `复制为 `.env`，并填写兼容 OpenAI 规范的配置信息。

2. 使用 Docker Compose 启动基础环境和应用服务；也可通过 Vite 本地运行前端，在 IDE 中运行 Java 服务。
3. 后端启动时会通过环境变量自动初始化管理员账号。

4. Copy `.env.example` to `.env` and fill in the OpenAI-compatible configuration.
5. Start infrastructure and applications with Docker Compose, or run the frontend locally with Vite and the Java services from the IDE.
6. The backend seeds the initial admin account from environment variables on startup.

详细本地部署指南/Detailed local deployment guide:

- [docs/Erise-AI 本地部署指南.md](docs/Erise-AI%20本地部署指南.md)

## V1 版本核心功能Main capabilities in this V1

- 基于 JWT 访问令牌 + Redis 刷新令牌的身份认证
- 项目管理
- PDF/Markdown/TXT 文件的上传、下载与解析
- 文档草稿编辑 + 发布版本管理
- 带数据权限过滤的关键词搜索
- 经由业务后端转发、带引用溯源的 AI 对话
- 用户、任务、审计日志基础管理页面

- Authentication with JWT access token and Redis-backed refresh token
- Project management
- File upload/download and parsing for PDF/Markdown/TXT
- Document draft editing and publish versioning
- Keyword search with ownership filtering
- AI chat with citations routed through the business backend
- Basic admin pages for users, tasks, and audit logs

## 服务访问地址/Services

- Nginx 统一入口：默认 http://localhost:8088，可通过 NGINX_HTTP_PORT 配置
- 业务后端健康检查：http://localhost:8080/actuator/health
- AI 云服务健康检查：http://localhost:8081/actuator/health
- 前端容器服务：http://localhost:5173

- Unified Nginx entry: `http://localhost:8088` by default, configurable with `NGINX_HTTP_PORT`
- Business backend health: `http://localhost:8080/actuator/health`
- AI cloud health: `http://localhost:8081/actuator/health`

[//]: # "- Frontend dev server: `http://localhost:5173`"

- Frontend container: `http://localhost:5173`

## 备注/Notes

- 向量检索、MCP（模型上下文协议）、SQL 工具、协同编辑仅作为扩展点预留，暂未实现。
- 仓库架构支持：前端可脱离 Docker 独立运行，MySQL、Redis、MinIO 保留在容器编排中。
- Vector retrieval, MCP, SQL tools, and collaborative editing are intentionally left as extension points only.
- The repository is structured so the frontend can run outside Docker while MySQL, Redis, and MinIO stay in Compose.

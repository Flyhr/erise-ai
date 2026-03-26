# Erise-AI

Erise-AI is a monorepo implementation of the V1 plan: Vue 3 frontend, Spring Boot business backend, Spring Boot AI cloud service, and Docker Compose based local infrastructure.

## Structure

```text
erise-ai-ui/        Vue 3 + Vite + TypeScript frontend
erise-ai-backend/   Spring Boot business backend
erise-ai-cloud/     Spring Boot AI cloud service
deploy/             Nginx and environment examples
docker-compose.yml  Local development stack
```

## Local development

1. Copy `.env.example` to `.env` and fill in the OpenAI-compatible configuration.
2. Start infrastructure and applications with Docker Compose, or run the frontend locally with Vite and the Java services from the IDE.
3. The backend seeds the initial admin account from environment variables on startup.

Detailed local deployment guide:

- [docs/Erise-AI 本地部署指南.md](docs/Erise-AI%20本地部署指南.md)

## Main capabilities in this V1

- Authentication with JWT access token and Redis-backed refresh token
- Project management
- File upload/download and parsing for PDF/Markdown/TXT
- Document draft editing and publish versioning
- Keyword search with ownership filtering
- AI chat with citations routed through the business backend
- Basic admin pages for users, tasks, and audit logs

## Services

- Unified Nginx entry: `http://localhost:8088` by default, configurable with `NGINX_HTTP_PORT`
- Business backend health: `http://localhost:8080/actuator/health`
- AI cloud health: `http://localhost:8081/actuator/health`

[//]: # (- Frontend dev server: `http://localhost:5173`)
- Frontend container: `http://localhost:5173`

## Notes

- Vector retrieval, MCP, SQL tools, and collaborative editing are intentionally left as extension points only.
- The repository is structured so the frontend can run outside Docker while MySQL, Redis, and MinIO stay in Compose.

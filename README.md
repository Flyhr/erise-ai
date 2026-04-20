# Erise-AI

Erise-AI 是一个面向个人或轻量团队的项目知识库系统，核心能力包括：

- 项目管理
- 文件上传、预览、下载与历史跟踪
- 文档阅读、编辑与解析
- 统一搜索与知识检索
- 管理后台
- AI / RAG 能力接入

## 仓库模块

- `erise-ai-ui`：Vue 3 + Vite 前端
- `erise-ai-backend`：Spring Boot 业务后端
- `AiAssistant`：Python AI 服务，负责聊天、RAG、文件解析、Agent 等能力
- `deploy/`：部署、网关与集成说明
- `docs/rebuild/`：重构过程中的设计、基线与运行手册

## 当前官方运行方式

现在只保留一套官方环境配置：

- 主 Compose：`docker-compose.yml`
- 主环境文件：`.env`

历史开发态配置已废弃：

- `docker-compose.dev.yml`
- `docker-compose.override.yml`
- `.env.dev.example`
- `Dockerfile.dev`
- `dev-entrypoint.sh`

## 当前 AI / RAG 技术状态

当前 AI 检索主链已经更新为：

```text
Vue UI -> Nginx -> Java Backend -> AiAssistant -> LiteLLM -> DeepSeek / OpenAI / Ollama
```

检索侧关键更新：

- 查询改写升级为“规则 + LLM”双层方案
- Qdrant 索引升级为 `dense + sparse` 一体化存储
- 召回融合支持动态权重
- 排序升级为“LLM 重排序优先，cross-encoder 回退”
- Java 内部 AI 检索入口 `/internal/v1/knowledge/retrieve` 已退役
- 已完成一轮 Qdrant hybrid 全量重建 / 重索引

相关说明文档：

- [RAG Retrieval Baseline](./docs/rebuild/07_rag_retrieval_baseline.md)
- [RAG Hybrid 索引迁移与重建](./docs/rebuild/17_rag_hybrid_index_migration.md)
- [AiAssistant / RAG 质量升级说明](./AiAssistant/docs/RAG_质量升级说明.md)

## 启动入口

首次准备环境文件：

```powershell
Copy-Item .env.example .env
```

启动系统：

```bash
docker compose --env-file .env -f docker-compose.yml up -d
```

查看状态：

```bash
docker compose --env-file .env -f docker-compose.yml ps
```

停止环境：

```bash
docker compose --env-file .env -f docker-compose.yml down
```

## 进一步说明

更详细的部署、运行和联调说明请优先参考：

- [deploy/DEPLOYMENT_MATRIX.md](./deploy/DEPLOYMENT_MATRIX.md)
- [docs/rebuild/16_local_integration_runbook.md](./docs/rebuild/16_local_integration_runbook.md)
- [deploy/litellm/README.md](./deploy/litellm/README.md)
- [deploy/n8n/README.md](./deploy/n8n/README.md)

# Deployment Matrix

这份文档是 Erise-AI 当前唯一的部署决策入口。

## 固定规则

1. 现在只保留一套官方环境配置：`docker-compose.yml + .env`
2. 历史开发态配置已经废弃，不再作为正式运行面维护
3. 官方 AI 主链以生产环境形态为基准：`cloud -> LiteLLM -> DeepSeek / OpenAI / Ollama`
4. `n8n` 仍然只作为外围自动化，不进入同步聊天主链
5. `vLLM` 只保留为可选生产验证 overlay，不再维护开发态 overlay

## 当前官方运行方式

| 场景 | 官方命令 | 说明 |
| --- | --- | --- |
| 主系统启动 | `docker compose --env-file .env -f docker-compose.yml up -d` | 唯一官方启动命令 |
| 查看状态 | `docker compose --env-file .env -f docker-compose.yml ps` | 查看所有核心服务状态 |
| 停止环境 | `docker compose --env-file .env -f docker-compose.yml down` | 停止但保留卷 |
| 清空环境 | `docker compose --env-file .env -f docker-compose.yml down -v` | 删除卷并重建环境 |
| 叠加 n8n | `docker compose -f deploy/n8n/docker-compose.yml --env-file .env up -d` | 外围自动化 |
| 叠加 vLLM | `docker compose -f docker-compose.yml -f deploy/vllm/docker-compose.prod.yml --env-file .env --profile vllm up -d` | 仅生产级验证场景 |

## 已废弃的历史配置

以下文件保留仅用于历史追溯或兼容工具链，不再作为官方运行方式：

- `docker-compose.dev.yml`
- `docker-compose.override.yml`
- `.env.dev.example`
- `deploy/litellm/docker-compose.dev.yml`
- `deploy/vllm/docker-compose.dev.yml`
- `deploy/n8n/docker-compose.dev.yml`
- 各模块 `Dockerfile.dev`
- 各模块 `docker/dev-entrypoint.sh`

## 当前主链拓扑

```text
erise-ai-ui -> nginx -> erise-ai-backend -> AiAssistant -> LiteLLM
                                                    -> Qdrant
                                                    -> MySQL / Redis / MinIO

LiteLLM -> DeepSeek API
        -> OpenAI API
        -> 本地 Ollama
```

## 环境对齐要求

### 主系统

- 主环境文件：`.env`
- 主 Compose：`docker-compose.yml`
- 后端 profile：`docker`
- AI 服务环境：`APP_ENV=prod`

### LiteLLM 网关

- `MODEL_PROVIDER=LITELLM`
- `MODEL_BASE_URL=http://litellm:4000/v1`
- `DEFAULT_MODEL_CODE` 与 `LITELLM_MODEL` 保持一致

### Ollama

- 当前主 Compose 内已经直接包含 `ollama` 与 `ollama-init`
- 本地模型默认：
  - `OLLAMA_CHAT_MODEL=qwen3:1.7b`
  - `OLLAMA_EMBEDDING_MODEL=nomic-embed-text`

### Qdrant

- AI 检索主链已经使用 Qdrant dense+sparse 一体化索引
- 旧 dense-only collection 在测试数据可丢弃场景下允许直接删除重建

## 推荐阅读顺序

1. 先看本页，确认该用哪条命令
2. 再看根目录 [README.md](../README.md)
3. 如果需要 AI / RAG 细节，再看：
   - [docs/rebuild/07_rag_retrieval_baseline.md](../docs/rebuild/07_rag_retrieval_baseline.md)
   - [docs/rebuild/17_rag_hybrid_index_migration.md](../docs/rebuild/17_rag_hybrid_index_migration.md)
   - [AiAssistant/docs/RAG_质量升级说明.md](../AiAssistant/docs/RAG_质量升级说明.md)
4. 如果需要外围能力，再看：
   - [deploy/litellm/README.md](./litellm/README.md)
   - [deploy/vllm/README.md](./vllm/README.md)
   - [deploy/n8n/README.md](./n8n/README.md)

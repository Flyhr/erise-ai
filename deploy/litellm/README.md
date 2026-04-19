# LiteLLM 网关 Overlay

此目录用于存放 Erise-AI 的 LiteLLM Docker 叠加配置。

## 文件说明

- `docker-compose.dev.yml`：开发环境 overlay，LiteLLM 作为 Ollama 的前置网关
- `docker-compose.vllm.yml`：生产风格 overlay，LiteLLM 作为 vLLM 服务的前置网关
- `litellm.ollama.yaml`：面向 Docker Ollama 的 LiteLLM 模型映射
- `litellm.vllm.yaml`：面向 vLLM 聊天与 embedding 服务的 LiteLLM 模型映射
- `litellm.env.example`：两种 overlay 共用的环境变量示例

## 开发环境用法

```bash
docker compose \
  --env-file .env.dev \
  -f docker-compose.dev.yml \
  -f deploy/litellm/docker-compose.dev.yml \
  up --build
```

在该模式下，`cloud` 连接 LiteLLM，LiteLLM 再把聊天与 embedding 请求转发到同一 Docker 网络内的 Ollama 容器。

## vLLM 网关模式用法

```bash
docker compose \
  -f docker-compose.yml \
  -f deploy/vllm/docker-compose.prod.yml \
  -f deploy/litellm/docker-compose.vllm.yml \
  --env-file .env \
  --env-file deploy/vllm/vllm.env.example \
  --env-file deploy/litellm/litellm.env.example \
  --profile vllm \
  --profile litellm \
  up -d
```

在该模式下，`cloud` 只感知 LiteLLM，而 LiteLLM 会把聊天请求路由到 `vllm`，把 embedding 请求路由到 `vllm-embed`。

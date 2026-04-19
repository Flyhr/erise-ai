# vLLM Overlay

此目录提供 Erise-AI 的 direct vLLM 部署路径。

## 文件说明

- `docker-compose.dev.yml`：叠加在 `docker-compose.dev.yml` 之上的开发环境 overlay
- `docker-compose.yml`：叠加在根目录 `docker-compose.yml` 之上的生产环境 overlay
- `docker-compose.prod.yml`：兼容旧命令的别名文件
- `vllm.env.example`：vLLM 路由、模型命名和 GPU 参数示例

## 模型命名约定

- `VLLM_MODEL`：vLLM 容器实际加载的 Hugging Face 模型
- `VLLM_SERVED_MODEL_NAME`：vLLM 对外暴露的 OpenAI 兼容模型名
- `VLLM_MODEL_CODE`：`AiAssistant` 注册并在运行时发送的模型码
- `DEFAULT_MODEL_CODE`：`AiAssistant` 默认选择的模型码

请保持 `VLLM_MODEL_CODE`、`VLLM_SERVED_MODEL_NAME` 和 `DEFAULT_MODEL_CODE` 一致。这是把现有 provider 抽象真正落成默认可用链路的关键步骤。

## 开发环境用法

1. 将 `.env.dev.example` 复制为 `.env.dev`
2. 当模型是 gated 模型，或你希望加快拉取速度时，补充 `HUGGING_FACE_HUB_TOKEN`
3. 启动基础开发栈，并叠加 vLLM overlay

```bash
docker compose \
  --env-file .env.dev \
  -f docker-compose.dev.yml \
  -f deploy/vllm/docker-compose.dev.yml \
  --profile vllm \
  up --build
```

在该模式下，`cloud` 会把聊天请求路由到 `vllm`，把 embedding 请求路由到 `vllm-embed`。

## 生产环境用法

1. 保留现有基础生产栈 `docker-compose.yml`
2. 将 `vllm.env.example` 复制为安全环境文件，例如 `deploy/vllm/.env.prod`
3. 合并基础 compose 与生产 overlay

```bash
docker compose \
  -f docker-compose.yml \
  -f deploy/vllm/docker-compose.yml \
  --env-file .env \
  --env-file deploy/vllm/.env.prod \
  --profile vllm \
  up -d
```

旧文件 `deploy/vllm/docker-compose.prod.yml` 仍然保留用于兼容，但 `deploy/vllm/docker-compose.yml` 现在是推荐使用的正式生产路径。

## GPU 建议

- `Qwen/Qwen2.5-7B-Instruct` 是一个 7B 模型。按照 vLLM 的显存估算方式，仅 FP16 权重本身大约就需要 14 GB，还需要额外预留 KV cache 和激活空间。
- 当前仓库默认开发配置使用 `VLLM_MAX_MODEL_LEN=32768` 和 `VLLM_MAX_NUM_SEQS=16`，因此聊天侧更实际的 GPU 建议是 20-24 GB 或更高。
- `BAAI/bge-m3` 轻量很多，模型体量大约 2.27 GB，但如果你把聊天和 embedding 拆成独立服务，`vllm-embed` 仍然更适合配一张单独的 8 GB 级别 GPU。
- 如果你只有一张较小显存的 GPU，建议在启用整套 overlay 之前，先下调 `VLLM_MAX_MODEL_LEN`、`VLLM_MAX_NUM_SEQS`，或者改用量化模型。

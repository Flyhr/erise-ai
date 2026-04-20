# LiteLLM 说明

LiteLLM 现在已经直接并入主运行栈，不再需要单独叠加开发态 compose。

## 当前定位

- LiteLLM 是唯一官方 AI 网关
- `cloud` 统一只连 LiteLLM
- LiteLLM 再转发到：
  - DeepSeek API
  - OpenAI API
  - 本地 Ollama

## 当前使用方式

直接使用根目录主配置即可：

```bash
docker compose --env-file .env -f docker-compose.yml up -d
```

## 已废弃内容

以下历史文件不再作为官方入口：

- `deploy/litellm/docker-compose.dev.yml`
- `deploy/litellm/docker-compose.prod.yml` 作为“必须叠加”的入口

说明：

- `docker-compose.prod.yml` 仍可保留作历史参考
- 但当前推荐直接使用根目录 `docker-compose.yml`

## 关键环境变量

- `MODEL_PROVIDER=LITELLM`
- `MODEL_BASE_URL=http://litellm:4000/v1`
- `LITELLM_MODEL=deepseek-chat`
- `DEFAULT_MODEL_CODE=deepseek-chat`

## 相关文件

- `deploy/litellm/litellm.prod.yaml`
- `deploy/litellm/litellm.env.example`

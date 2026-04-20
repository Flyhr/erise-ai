# vLLM 说明

vLLM 现在只保留为可选生产级验证 overlay，不再维护开发态配置。

## 当前定位

- 默认官方主链不启用 vLLM
- 如需验证 vLLM，可在主配置基础上额外叠加生产 overlay

## 使用方式

```bash
docker compose -f docker-compose.yml -f deploy/vllm/docker-compose.prod.yml --env-file .env --profile vllm up -d
```

## 已废弃内容

- `deploy/vllm/docker-compose.dev.yml`
- 与 `.env.dev` 绑定的开发态验证说明

## 相关文件

- `deploy/vllm/docker-compose.prod.yml`
- `deploy/vllm/vllm.env.example`

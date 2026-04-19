# vLLM Production Overlay

This directory contains a production-oriented vLLM overlay for Erise-AI.

## Files

- `docker-compose.prod.yml`: overlay compose for `docker-compose.yml`
- `vllm.env.example`: production parameter template

## Usage

1. Keep the current production base stack in `docker-compose.yml`
2. Copy `vllm.env.example` to a secure env file such as `deploy/vllm/.env.prod`
3. Merge the base compose and the overlay

Example:

```bash
docker compose \
  -f docker-compose.yml \
  -f deploy/vllm/docker-compose.prod.yml \
  --env-file .env \
  --env-file deploy/vllm/.env.prod \
  --profile vllm \
  up -d
```

## Why overlay instead of replacing the base compose

- avoids rewriting the current production topology
- keeps rollback simple
- isolates GPU / model-serving concerns under `deploy/vllm/`
- allows `cloud` to switch providers without changing API routes

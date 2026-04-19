# LiteLLM Gateway Overlays

This directory keeps Docker-only LiteLLM overlays for Erise-AI.

## Files

- `docker-compose.dev.yml`: dev overlay, LiteLLM fronts the Ollama container
- `docker-compose.vllm.yml`: production-style overlay, LiteLLM fronts the vLLM services
- `litellm.ollama.yaml`: LiteLLM model map for Docker Ollama
- `litellm.vllm.yaml`: LiteLLM model map for vLLM chat + embedding services
- `litellm.env.example`: environment hints for both overlays

## Development usage

```bash
docker compose \
  --env-file .env.dev \
  -f docker-compose.dev.yml \
  -f deploy/litellm/docker-compose.dev.yml \
  up --build
```

In this mode, `cloud` talks to LiteLLM and LiteLLM forwards chat + embedding traffic to the Ollama containers in the same Docker network.

## vLLM gateway usage

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

In this mode, `cloud` only sees LiteLLM, while LiteLLM routes chat traffic to `vllm` and embedding traffic to `vllm-embed`.

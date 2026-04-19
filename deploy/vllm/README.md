# vLLM Overlays

This directory contains the direct vLLM deployment path for Erise-AI.

## Files

- `docker-compose.dev.yml`: development overlay on top of `docker-compose.dev.yml`
- `docker-compose.yml`: production overlay on top of the root `docker-compose.yml`
- `docker-compose.prod.yml`: backward-compatible alias for older commands
- `vllm.env.example`: vLLM routing, model naming, and GPU parameter template

## Model naming contract

- `VLLM_MODEL`: the Hugging Face model that the vLLM container loads
- `VLLM_SERVED_MODEL_NAME`: the OpenAI-compatible model name exposed by vLLM
- `VLLM_MODEL_CODE`: the model code that `AiAssistant` registers and sends at runtime
- `DEFAULT_MODEL_CODE`: the default model code selected by `AiAssistant`

Keep `VLLM_MODEL_CODE`, `VLLM_SERVED_MODEL_NAME`, and `DEFAULT_MODEL_CODE` aligned. This is the key step that turns the existing provider adapter into a usable default chain.

## Development usage

1. Copy `.env.dev.example` to `.env.dev`
2. Fill in `HUGGING_FACE_HUB_TOKEN` when the model is gated or needs faster pulls
3. Start the base dev stack plus the vLLM overlay

```bash
docker compose \
  --env-file .env.dev \
  -f docker-compose.dev.yml \
  -f deploy/vllm/docker-compose.dev.yml \
  --profile vllm \
  up --build
```

In this mode, `cloud` routes chat requests to `vllm` and embedding requests to `vllm-embed`.

## Production usage

1. Keep the existing base stack in `docker-compose.yml`
2. Copy `vllm.env.example` to a secure file such as `deploy/vllm/.env.prod`
3. Merge the base compose and the production overlay

```bash
docker compose \
  -f docker-compose.yml \
  -f deploy/vllm/docker-compose.yml \
  --env-file .env \
  --env-file deploy/vllm/.env.prod \
  --profile vllm \
  up -d
```

The legacy file `deploy/vllm/docker-compose.prod.yml` remains available for compatibility, but `deploy/vllm/docker-compose.yml` is now the canonical production path.

## GPU guidance

- `Qwen/Qwen2.5-7B-Instruct` is a 7B model. Based on vLLM's memory formula, FP16 weights alone are about 14 GB, and extra KV cache / activation headroom is still required.
- The default dev profile in this repo uses `VLLM_MAX_MODEL_LEN=32768` and `VLLM_MAX_NUM_SEQS=16`, so a practical chat GPU target is 20-24 GB or more.
- `BAAI/bge-m3` is much lighter at roughly 2.27 GB model size, but the dedicated `vllm-embed` service still benefits from a separate 8 GB class GPU if you keep chat and embedding isolated.
- If you only have one smaller GPU, reduce `VLLM_MAX_MODEL_LEN`, `VLLM_MAX_NUM_SEQS`, or switch to a quantized model variant before enabling the full overlay.

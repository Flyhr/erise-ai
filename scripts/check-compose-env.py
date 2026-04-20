from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VAR_PATTERN = re.compile(r"\$\{([A-Z0-9_]+)(?::-[^}]*)?\}")
ENV_PATTERN = re.compile(r"^([A-Z0-9_]+)\s*=")


SCENARIOS = (
    {
        "name": "dev-base",
        "compose_files": ["docker-compose.dev.yml"],
        "env_files": [".env.dev.example"],
    },
    {
        "name": "dev-vllm",
        "compose_files": ["docker-compose.dev.yml", "deploy/vllm/docker-compose.dev.yml"],
        "env_files": [".env.dev.example"],
    },
    {
        "name": "dev-n8n",
        "compose_files": ["deploy/n8n/docker-compose.dev.yml"],
        "env_files": [".env.dev.example"],
    },
    {
        "name": "prod-base",
        "compose_files": ["docker-compose.yml"],
        "env_files": [".env.example"],
    },
    {
        "name": "prod-vllm",
        "compose_files": ["docker-compose.yml", "deploy/vllm/docker-compose.yml"],
        "env_files": [".env.example", "deploy/vllm/vllm.env.example"],
    },
    {
        "name": "prod-vllm-litellm",
        "compose_files": [
            "docker-compose.yml",
            "deploy/vllm/docker-compose.yml",
            "deploy/litellm/docker-compose.vllm.yml",
        ],
        "env_files": [
            ".env.example",
            "deploy/vllm/vllm.env.example",
            "deploy/litellm/litellm.env.example",
        ],
    },
    {
        "name": "prod-ollama-litellm",
        "compose_files": [
            "docker-compose.yml",
            "deploy/litellm/docker-compose.prod.yml",
        ],
        "env_files": [
            ".env.example",
            "deploy/litellm/litellm.env.example",
        ],
    },
    {
        "name": "prod-n8n",
        "compose_files": ["deploy/n8n/docker-compose.yml"],
        "env_files": [".env.example"],
    },
)


def parse_compose_vars(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8")
    return set(VAR_PATTERN.findall(text))


def parse_env_vars(path: Path) -> set[str]:
    values: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = ENV_PATTERN.match(stripped)
        if match:
            values.add(match.group(1))
    return values


def main() -> int:
    had_error = False
    print("Compose/env validation report\n")
    for scenario in SCENARIOS:
        compose_paths = [ROOT / item for item in scenario["compose_files"]]
        env_paths = [ROOT / item for item in scenario["env_files"]]

        compose_vars: set[str] = set()
        for path in compose_paths:
            compose_vars.update(parse_compose_vars(path))

        env_vars: set[str] = set()
        for path in env_paths:
            env_vars.update(parse_env_vars(path))

        missing = sorted(var for var in compose_vars if var not in env_vars)
        status = "OK" if not missing else "MISSING"
        print(f"[{status}] {scenario['name']}")
        print(f"  compose: {', '.join(scenario['compose_files'])}")
        print(f"  env:     {', '.join(scenario['env_files'])}")
        if missing:
            had_error = True
            print(f"  missing: {', '.join(missing)}")
        else:
            print("  missing: none")
        print()

    if had_error:
        print("Validation failed: some compose variables are missing from the corresponding env examples.")
        return 1

    print("Validation passed: all checked compose variables exist in the corresponding env examples.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

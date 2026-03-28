from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request


HEALTHCHECK_URL = os.getenv('AI_HEALTHCHECK_URL', 'http://127.0.0.1:8081/internal/ai/chat/health')


def main() -> int:
    try:
        with urllib.request.urlopen(HEALTHCHECK_URL, timeout=5) as response:
            if response.status != 200:
                print(f'healthcheck failed with status {response.status}', file=sys.stderr)
                return 1
            payload = json.loads(response.read().decode('utf-8'))
            if payload.get('code') != 0:
                print(f"healthcheck returned non-zero code: {payload}", file=sys.stderr)
                return 1
    except (urllib.error.URLError, TimeoutError, ValueError) as exc:
        print(f'healthcheck request failed: {exc}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
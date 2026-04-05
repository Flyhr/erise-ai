#!/bin/sh
set -eu

cd /workspace

HASH_FILE="node_modules/.package-lock.hash"
CURRENT_HASH="$(cat package.json package-lock.json 2>/dev/null | sha1sum | awk '{print $1}')"
SAVED_HASH=""

if [ -f "$HASH_FILE" ]; then
  SAVED_HASH="$(cat "$HASH_FILE")"
fi

if [ ! -d node_modules ] || [ "$CURRENT_HASH" != "$SAVED_HASH" ]; then
  echo "[ui-dev] installing dependencies..."
  npm install
  mkdir -p node_modules
  printf '%s' "$CURRENT_HASH" > "$HASH_FILE"
fi

exec npm run dev -- --host 0.0.0.0 --port 5173
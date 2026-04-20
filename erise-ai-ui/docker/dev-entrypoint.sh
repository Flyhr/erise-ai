#!/bin/sh
# 废弃说明：
# 历史开发态热更新入口已废弃。
# 现在统一通过正式镜像启动前端，不再使用此脚本作为官方入口。

set -eu

cd /workspace

if [ ! -f package.json ]; then
  echo "[ui-dev] package.json not found"
  exit 1
fi

HASH_FILE="/workspace/node_modules/.package-lock.hash"

if [ -f package-lock.json ]; then
  CURRENT_HASH="$(cat package.json package-lock.json | sha1sum | awk '{print $1}')"
else
  CURRENT_HASH="$(cat package.json | sha1sum | awk '{print $1}')"
fi

SAVED_HASH=""
if [ -f "$HASH_FILE" ]; then
  SAVED_HASH="$(cat "$HASH_FILE")"
fi

if [ ! -d node_modules ] || [ "$CURRENT_HASH" != "$SAVED_HASH" ]; then
  echo "[ui-dev] installing dependencies..."
  npm install
  mkdir -p /workspace/node_modules
  printf '%s' "$CURRENT_HASH" > "$HASH_FILE"
else
  echo "[ui-dev] dependencies unchanged, skip npm install"
fi

exec npm run dev -- --host 0.0.0.0 --port 5173

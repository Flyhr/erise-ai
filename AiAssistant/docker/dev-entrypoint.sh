#!/bin/sh
# 废弃说明：
# 历史开发态热更新入口已废弃。
# 现在统一通过正式镜像启动 AiAssistant，不再使用此脚本作为官方入口。

set -eu

cd /workspace

echo "[chat-service] initializing database..."
python scripts/init_db.py

echo "[chat-service] starting uvicorn..."
exec uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload

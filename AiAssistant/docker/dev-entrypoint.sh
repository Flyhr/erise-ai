#!/bin/sh
set -eu

cd /workspace

echo "[chat-service] initializing database..."
python scripts/init_db.py

echo "[chat-service] starting uvicorn..."
exec uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload

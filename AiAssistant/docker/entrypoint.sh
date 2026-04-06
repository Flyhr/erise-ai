#!/bin/sh
set -eu

cd /app

python scripts/init_db.py

exec uvicorn src.app.main:app --host 0.0.0.0 --port 8081

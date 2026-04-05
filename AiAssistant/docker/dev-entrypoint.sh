#!/bin/sh
set -eu

cd /workspace

pip install -r requirements.txt
python scripts/init_db.py

exec uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload
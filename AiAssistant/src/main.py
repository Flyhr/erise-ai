from __future__ import annotations

from fastapi import FastAPI

from src.routers import chat, analyze

app = FastAPI(title="EriseAi Assistant", version="0.1.0")

app.include_router(chat.router)
app.include_router(analyze.router)


@app.get("/")
def health():
    return {"status": "ok", "service": "AiAssistant"}

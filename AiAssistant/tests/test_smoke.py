import pytest
from fastapi.testclient import TestClient
from src.main import app


def test_health():
    client = TestClient(app)
    resp = client.get("/")
    assert resp.status_code == 200
    assert resp.json().get("status") == "ok"

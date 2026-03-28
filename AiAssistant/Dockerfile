FROM python:3.11-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY src ./src
COPY scripts ./scripts

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --retries=12 CMD ["python", "scripts/healthcheck.py"]

CMD ["uvicorn", "src.app.main:app", "--host", "0.0.0.0", "--port", "8081"]
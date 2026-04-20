FROM python:3.11-slim

WORKDIR /app

ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ARG PIP_EXTRA_INDEX_URL=https://pypi.org/simple
ARG PIP_TRUSTED_HOST="pypi.tuna.tsinghua.edu.cn pypi.org files.pythonhosted.org"

ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    poppler-utils \
    libmagic1 \
    libgomp1 \
    libglib2.0-0 \
    libgl1 \
    libglx-mesa0 \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-chi-sim \
    fonts-liberation \
    fonts-dejavu \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN /bin/sh -c 'set -eu; \
    pip_args="--default-timeout=180 --retries=8 --no-cache-dir"; \
    if [ -n "${PIP_INDEX_URL:-}" ]; then \
      pip_args="$pip_args --index-url ${PIP_INDEX_URL}"; \
    fi; \
    if [ -n "${PIP_EXTRA_INDEX_URL:-}" ]; then \
      pip_args="$pip_args --extra-index-url ${PIP_EXTRA_INDEX_URL}"; \
    fi; \
    for host in ${PIP_TRUSTED_HOST:-}; do \
      pip_args="$pip_args --trusted-host $host"; \
    done; \
    python -m pip install $pip_args -r requirements.txt'

COPY src ./src
COPY scripts ./scripts

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --retries=12 CMD ["python", "scripts/healthcheck.py"]

CMD ["uvicorn", "src.app.main:app", "--host", "0.0.0.0", "--port", "8081"]

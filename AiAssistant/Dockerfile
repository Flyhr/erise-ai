FROM python:3.11-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1

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
RUN pip install --no-cache-dir -r requirements.txt

COPY src ./src
COPY scripts ./scripts

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --retries=12 CMD ["python", "scripts/healthcheck.py"]

CMD ["uvicorn", "src.app.main:app", "--host", "0.0.0.0", "--port", "8081"]

FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8080

WORKDIR /app

COPY requirements.txt ./requirements.txt
RUN pip install --upgrade pip && pip install -r requirements.txt

COPY . /app/agent_service
WORKDIR /app

EXPOSE 8080

CMD ["sh", "-c", "uvicorn agent_service.main:app --host 0.0.0.0 --port ${PORT:-8080}"]

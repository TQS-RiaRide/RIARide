FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src ./src
# Readable by the unprivileged user below, whatever the permissions on the host.
RUN chmod -R a+rX /app

# Run as an unprivileged user.
RUN useradd --system --no-create-home simulator
USER simulator

CMD ["python", "-m", "riaride_simulator"]

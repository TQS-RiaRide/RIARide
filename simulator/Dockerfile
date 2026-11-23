# Placeholder so the "sim" Compose profile builds and runs (RIA-63).
# The simulator story replaces this with the Python service (ADR 0007).
FROM python:3.12-slim
RUN useradd --system --no-create-home app
USER app
CMD ["python", "-c", "print('RiaRide simulator placeholder: telemetry not implemented yet', flush=True)"]

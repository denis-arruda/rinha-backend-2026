# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw package -DskipTests

# Run locally (after build)
java -cp target/rinha-backend-2026-1.0-SNAPSHOT.jar dev.denisarruda.rinha.Application

# Build Docker image
# Use --platform linux/amd64 when building on non-amd64 machines (e.g. Apple Silicon)
docker build --platform linux/amd64 -t rinha-backend-2026:latest .

# Run with docker-compose (starts api1, api2, nginx)
docker compose up
```

## Architecture

Minimal plain Java HTTP server — no frameworks, no external dependencies. Everything lives in `Application.java`.

- `HttpServer` (JDK built-in `com.sun.net.httpserver`) listens on port 8080.
- Two instances (`api1`, `api2`) run behind **nginx** on port 9999, load-balanced via a round-robin upstream.
- The Dockerfile is a three-stage build: Maven compiles the jar → `jlink` produces a custom JRE with only the required modules → `debian:bookworm-slim` runs the minimal image.
- Java 26 (`maven.compiler.release=26`).

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ready` | Health/readiness check — returns 200 |
| POST | `/fraud-score` | Always returns `{"approved":false,"fraud_score":0.8}` |

# rinha-backend-2026

## Building the Docker image

If you are building on a non-amd64 machine (e.g. Apple Silicon), specify the target platform explicitly:

```bash
docker build --platform linux/amd64 -t rinha-backend-2026:latest .
```
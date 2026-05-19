# rinha-backend-2026

Fraud scoring API built for the [Rinha de Backend 2026](https://github.com/zanfranceschi/rinha-de-backend-2026) competition.

Given a transaction payload, the API returns a fraud score and an approval decision using k-nearest-neighbour search over a pre-built HNSW vector index of ~3 million reference transactions.

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 26 |
| HTTP server | JDK built-in `com.sun.net.httpserver` |
| Vector search | [JVector](https://github.com/jbellis/jvector) 4.x — HNSW graph, SIMD-accelerated via Panama Vector API |
| Load balancer | nginx (round-robin, 2 instances) |
| GC | ZGC |
| Runtime | Custom JRE via `jlink` (no full JDK in the image) |
| Container | Docker multi-stage build — index built at image build time, memory-mapped at startup |

## How it works

1. At **build time**, `IndexBuilder` reads `references.json.gz` (~3 M labelled transactions), builds an HNSW graph and writes it to `/app/index.bin` (~380 MB).
2. At **startup**, `FraudScorer` memory-maps the index (~1 s, no heap allocation for the graph).
3. At **request time**, the 14-feature transaction vector is normalised and the 5 nearest neighbours are retrieved from the index. `fraud_score = fraud_neighbours / 5`; the transaction is approved if `score < 0.6`.

## API

`POST /fraud-score` → `{"approved": <bool>, "fraud_score": <float>}`

`GET /ready` → `200 OK` when the index is loaded.

Licensed under the [MIT License](LICENSE).

## Building the Docker image

If you are building on a non-amd64 machine (e.g. Apple Silicon), specify the target platform explicitly:

```bash
docker build --platform linux/amd64 -t rinha-backend-2026:latest .
```
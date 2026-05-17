# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build (produces fat jar via maven-shade-plugin)
./mvnw package -DskipTests

# Run locally (after build) — 2 GB heap required for the reference dataset
java --add-modules jdk.incubator.vector -Xmx2g \
  -cp target/rinha-backend-2026-1.0-SNAPSHOT.jar dev.denisarruda.rinha.Application

# Build Docker image
# Use --platform linux/amd64 when building on non-amd64 machines (e.g. Apple Silicon)
docker build --platform linux/amd64 -t rinha-backend-2026:latest .

# Run with docker-compose (starts api1, api2, nginx)
docker compose up
```

## Architecture

Minimal plain Java HTTP server with JVector-based fraud scoring.

- `HttpServer` (JDK built-in `com.sun.net.httpserver`) listens on port 8080.
- Two instances (`api1`, `api2`) run behind **nginx** on port 9999, load-balanced via a round-robin upstream.
- The Dockerfile is a three-stage build: Maven compiles the fat jar → `jlink` produces a custom JRE → `debian:bookworm-slim` runs the minimal image.
- Java 26 (`maven.compiler.release=26`).
- Dependencies: `io.github.jbellis:jvector:4.0.0-rc.8` (vector search), `slf4j-simple` (logging). Bundled via `maven-shade-plugin` into a single fat jar (~56 MB including the reference dataset).

### Startup

`FraudScorer.init()` is called in `Application.main()` **before** the HTTP server starts. It loads `src/main/resources/references.json` (~3 M labeled transaction vectors) and builds an in-memory JVector HNSW index. This takes ~3 minutes and requires ~2 GB heap. `/ready` only responds after this completes.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ready` | Health/readiness check — returns 200 |
| POST | `/fraud-score` | Returns `{"approved": <bool>, "fraud_score": <float>}` |

### `POST /fraud-score` — Request payload

```json
{
  "id": "tx-3576980410",
  "transaction": {
    "amount": 384.88,
    "installments": 3,
    "requested_at": "2026-03-11T20:23:35Z"
  },
  "customer": {
    "avg_amount": 769.76,
    "tx_count_24h": 3,
    "known_merchants": ["MERC-009", "MERC-001"]
  },
  "merchant": {
    "id": "MERC-001",
    "mcc": "5912",
    "avg_amount": 298.95
  },
  "terminal": {
    "is_online": false,
    "card_present": true,
    "km_from_home": 13.709
  },
  "last_transaction": {
    "timestamp": "2026-03-11T14:58:35Z",
    "km_from_current": 18.862
  }
}
```

### `POST /fraud-score` — Request fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Transaction identifier (e.g. `tx-1329056812`) |
| `transaction.amount` | number | Transaction amount |
| `transaction.installments` | integer | Number of installments |
| `transaction.requested_at` | string (ISO) | UTC timestamp of the request |
| `customer.avg_amount` | number | Cardholder's historical average spend |
| `customer.tx_count_24h` | integer | Cardholder's transaction count in the last 24h |
| `customer.known_merchants` | string[] | Merchants previously used by the cardholder |
| `merchant.id` | string | Merchant identifier |
| `merchant.mcc` | string | Merchant Category Code |
| `merchant.avg_amount` | number | Merchant's average ticket |
| `terminal.is_online` | boolean | `true` = online transaction, `false` = in-person |
| `terminal.card_present` | boolean | Whether the card is physically present at the terminal |
| `terminal.km_from_home` | number | Distance in km from the cardholder's home address |
| `last_transaction` | object \| null | Previous transaction data (`null` if no prior transaction) |
| `last_transaction.timestamp` | string (ISO) | UTC timestamp of the previous transaction |
| `last_transaction.km_from_current` | number | Distance in km between the previous and current transaction |

### Scoring pipeline

1. `FraudRequestParser.java` — parses the JSON body into a typed record.
2. `Normalizer.java` — maps the record to a `float[14]` feature vector using constants from `NormalizationConstants.java`. `clamp(x)` keeps values in [0.0, 1.0].
3. `FraudScorer.java` — searches the JVector index for the 5 nearest neighbours (EUCLIDEAN) and returns `fraud_score = fraud_count / 5`. `approved = score < 0.6`.

### FraudScorer — JVector KNN

- **Reference dataset**: `src/main/resources/references.json` — ~3 M records, format `{"vector": [float×14], "label": "legit"|"fraud"}`.
- **Fallback**: if the resource is missing, 80 synthetic labeled vectors (8 prototypes × 5 variations, seed 42) are used instead.
- **Index**: HNSW graph via `GraphIndexBuilder` (M=16, efConstruction=100, EUCLIDEAN similarity), built once at startup and held in memory.
- **Inference**: `ThreadLocal<GraphSearcher>` — one searcher per virtual thread. Searches top-5 neighbours; `fraud_score = count(label=="fraud") / 5`.
- **Test cases**: `src/test/resources/fraud-score-test-cases.json` — 50 recorded request/response pairs for regression testing.

### Normalization

| # | Feature | Formula |

| # | Feature | Formula |
|---|---------|---------|
| 0 | transaction amount | `clamp(transaction.amount / max_amount)` |
| 1 | installments | `clamp(transaction.installments / max_installments)` |
| 2 | amount vs customer avg | `clamp((transaction.amount / customer.avg_amount) / amount_vs_avg_ratio)` |
| 3 | hour of day | `hour(transaction.requested_at) / 23` — range [0, 1] |
| 4 | day of week | `weekday(transaction.requested_at) / 6` — Mon=0, Sun=6 |
| 5 | minutes since last tx | `clamp(minutes / max_minutes)`, or `-1` if `last_transaction` is null |
| 6 | km from last tx | `clamp(last_transaction.km_from_current / max_km)`, or `-1` if null |
| 7 | km from home | `clamp(terminal.km_from_home / max_km)` |
| 8 | tx count 24h | `clamp(customer.tx_count_24h / max_tx_count_24h)` |
| 9 | is online | `1` if `terminal.is_online`, else `0` |
| 10 | card present | `1` if `terminal.card_present`, else `0` |
| 11 | unknown merchant | `1` if `merchant.id` ∉ `customer.known_merchants`, else `0` |
| 12 | MCC risk | lookup in `MccRisk.java` (default `0.5`) |
| 13 | merchant avg amount | `clamp(merchant.avg_amount / max_merchant_avg_amount)` |

#### Normalization constants

| Constant | Value |
|----------|-------|
| `max_amount` | 10000 |
| `max_installments` | 12 |
| `amount_vs_avg_ratio` | 10 |
| `max_minutes` | 1440 |
| `max_km` | 1000 |
| `max_tx_count_24h` | 20 |
| `max_merchant_avg_amount` | 10000 |

#### MCC risk table (`MccRisk.java`)

| MCC | Risk |
|-----|------|
| 5411 | 0.15 |
| 5812 | 0.30 |
| 5912 | 0.20 |
| 5944 | 0.45 |
| 7801 | 0.80 |
| 7802 | 0.75 |
| 7995 | 0.85 |
| 4511 | 0.35 |
| 5311 | 0.25 |
| 5999 | 0.50 |

MCCs fora da tabela retornam `0.5`.

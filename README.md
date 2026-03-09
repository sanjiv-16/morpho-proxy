# 🦋 Morpho Proxy

> **Enterprise-grade API Gateway for Zero-Downtime Legacy-to-Microservice Migration**

Morpho Proxy implements the **Strangler Fig pattern** — a battle-tested migration strategy where a new system gradually replaces a legacy one by intercepting traffic at the edge, routing a growing percentage to the modern service while keeping the legacy system as the stable fallback. When the migration is complete, the legacy system is simply decommissioned.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Quick Start](#quick-start)
- [Configuration Reference](#configuration-reference)
- [Helm Chart Deployment](#helm-chart-deployment)
- [Migration Runbook](#migration-runbook)
- [Traffic Flow Diagrams](#traffic-flow-diagrams)

---

## Architecture Overview

```
                          ┌─────────────────────────────────────────────────┐
                          │              Morpho Proxy (Gateway)             │
                          │                                                 │
Client ──── JWT ────────▶ │  OAuth2       Rate         Kafka                │
                          │  Validator    Limiter      Telemetry            │
                          │      │           │             │                │
                          │      └───────────┴─────────────┘                │
                          │                  │                              │
                          │         Weight Predicate                        │
                          │         (migration_group)                       │
                          │          /              \                       │
                          │    CANARY_%          LEGACY_%                   │
                          │       │                  │                      │
                          └───────┼──────────────────┼──────────────────────┘
                                  │                  │
                                  ▼                  ▼
                        ┌──────────────┐   ┌──────────────────┐
                        │   Modern     │   │  Legacy Monolith │
                        │ Microservice │   │                  │
                        └──────────────┘   └────────┬─────────┘
                                                     │ async mirror
                                                     ▼
                                           ┌──────────────┐
                                           │   Modern     │
                                           │ Microservice │
                                           │  (Shadow)    │
                                           └──────────────┘
```

---

## Features

### 🔀 Weighted Canary Routing
Uses Spring Cloud Gateway's native `Weight` predicate to split traffic between legacy and modern services. The split is controlled entirely by environment variables — no code changes or redeployments needed to shift traffic.

```
CANARY_PERCENTAGE=10  →  10% of requests go to modern-microservice
LEGACY_PERCENTAGE=90  →  90% of requests go to legacy-monolith
```

Every routed request is tagged with an `X-Routed-By` header (`canary-modern` or `legacy`) so downstream services and observability tools can identify the traffic source.

### 👻 Shadow Traffic (Dark Launching)
When a request is served by the **legacy route**, the gateway asynchronously mirrors an identical copy of that request to the modern service. The client never waits for the shadow response — it receives the legacy response immediately while the modern service is exercised in parallel.

This enables:
- Production load testing of the modern service with zero client impact
- Response comparison between legacy and modern
- Confidence-building before increasing canary percentage

Shadow traffic is **fire-and-forget**: errors from the modern service are logged but never propagate to the client.

### 🔐 Centralized OAuth2 / OIDC Security
All requests are validated at the gateway edge using Spring Security's OAuth2 Resource Server. JWT tokens are validated against the configured Keycloak (or any OIDC-compliant) issuer. Downstream services receive pre-validated requests and do not need to implement their own token verification.

### 🚦 Distributed Rate Limiting
Redis-backed Token Bucket rate limiting is applied globally as a default filter across all routes. This protects the legacy monolith — often the most vulnerable component — from request spikes and DDoS attempts.

| Parameter | Default | Description |
|---|---|---|
| `RATE_LIMIT_REPLENISH` | `50` | Tokens refilled per second per client IP |
| `RATE_LIMIT_BURST` | `100` | Maximum burst capacity |

Rate limit headers are returned on every response:
```
X-RateLimit-Remaining: 99
X-RateLimit-Requested-Tokens: 1
X-RateLimit-Burst-Capacity: 100
X-RateLimit-Replenish-Rate: 50
```

### 📊 Real-time Kafka Telemetry
Every routed request publishes a telemetry event to a Kafka topic via `KafkaTelemetryFilter`. Events include routing decisions, latency, status codes, and the route taken (`canary-modern` vs `legacy`). This provides a real-time observability stream for dashboards, alerting, and post-migration analysis.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 4.x |
| Gateway | Spring Cloud Gateway (Reactive WebFlux) |
| Security | Spring Security OAuth2 Resource Server |
| Rate Limiting | Redis (Token Bucket via Spring Cloud Gateway) |
| Telemetry | Apache Kafka |
| Identity Provider | Keycloak (OIDC) |
| Build | Gradle (multi-stage Docker build) |
| Packaging | Docker, Helm |

---

## Project Structure

```
morpho-proxy/
├── .env                          # Docker build-time variables
├── Dockerfile                    # Multi-stage JDK build → JRE runtime
├── docker-compose.yml            # Image build orchestration
├── build.gradle                  # Gradle dependencies and Spring Boot plugin
└── src/
    └── main/
        ├── java/com/morphoproxy/
        │   ├── MorphyproxyApplication.java
        │   ├── config/
        │   │   └── GatewayConfig.java        # IpKeyResolver bean for rate limiting
        │   └── filter/
        │       ├── GlobalLoggingFilter.java   # Request/response access log
        │       ├── KafkaTelemetryFilter.java  # Publishes routing events to Kafka
        │       └── ShadowTrafficFilter.java   # Async traffic mirroring
        └── resources/
            └── application.yaml              # All gateway configuration
```

### Helm Chart Structure (Testing)

```
morphoproxy/
├── Chart.yaml
├── values.yaml                   # All env vars with defaults
└── templates/
    ├── morpho-proxy.yaml         # Gateway deployment + service
    ├── legacy-molithic.yaml      # Legacy monolith stub
    ├── modern-microservice.yaml  # Modern service stub
    ├── keycloak.yaml             # Identity provider
    ├── kafka.yaml                # Message broker
    ├── kafka-ui.yaml             # Kafka management UI
    └── redis.yaml                # Rate limit state store
```

---

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- For full integration testing: a running Kubernetes cluster with Helm 3

### 1. Configure the Build

Create or verify the `.env` file in the project root:

```env
APP_NAME=morpho-proxy
APP_VERSION=0.0.1-SNAPSHOT
JAVA_VERSION=21
EXPOSE_PORT=8080
```

### 2. Build the Docker Image

```bash
docker compose up --build
```

This triggers a two-stage build:

**Stage 1 — Builder:** Full JDK 21 image pulls Gradle dependencies and compiles the fat JAR.

**Stage 2 — Runtime:** Lightweight JRE 21 image copies only the compiled JAR. The final image is tagged as `morpho-proxy:0.0.1-SNAPSHOT`.

### 3. Deploy to Kubernetes with Helm

The included Helm chart deploys the full integration environment including all dependencies:

```bash
# Add the chart and install
helm install morpho ./morphoproxy

# Or with custom values
helm install morpho ./morphoproxy \
  --set morphoProxy.canaryPercentage=10 \
  --set morphoProxy.legacyPercentage=90
```

### 4. Obtain a Token and Test

```bash
# Get a JWT from Keycloak
TOKEN=$(curl -s -X POST http://<keycloak-host>/realms/morpho/protocol/openid-connect/token \
  -d "client_id=morpho-client&grant_type=password&username=testuser&password=testpass" \
  | jq -r '.access_token')

# Call the gateway
curl -H "Authorization: Bearer $TOKEN" http://<gateway-host>/api
```

You will see either `[LEGACY SYSTEM]` or `[MODERN SYSTEM]` in the response body depending on which route was selected, and the `X-Routed-By` header will confirm the routing decision.

---

## Configuration Reference

All runtime behaviour is controlled via environment variables. Every variable has a default value so the application starts without any configuration.

### Application

| Variable | Default | Description |
|---|---|---|
| `APP_PORT` | `8080` | HTTP port the gateway listens on |
| `APP_NAME` | `morpho-proxy` | Spring application name |

### Redis (Rate Limiting)

| Variable | Default | Description |
|---|---|---|
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `RATE_LIMIT_REPLENISH` | `50` | Token refill rate (requests/second per IP) |
| `RATE_LIMIT_BURST` | `100` | Maximum burst capacity per IP |

### Kafka (Telemetry)

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers (comma-separated) |

### Security (OIDC)

| Variable | Default | Description |
|---|---|---|
| `OIDC_ISSUER_URI` | `http://localhost:8081/realms/morpho` | JWT issuer URI — must match the `iss` claim in tokens |

### Routing

| Variable | Default | Description |
|---|---|---|
| `ROUTE_PATH` | `/api/**` | Path predicate applied to both routes |
| `WEIGHT_GROUP` | `migration_group` | Weight group name (must match across both routes) |
| `CANARY_PERCENTAGE` | `50` | Percentage of traffic routed to modern service |
| `LEGACY_PERCENTAGE` | `50` | Percentage of traffic routed to legacy service |
| `MODERN_URI` | `http://modern-microservice:8080` | Modern service base URL (used for canary route and shadow) |
| `LEGACY_URI` | `http://legacy-monolith:8080` | Legacy service base URL |
| `CANARY_ROUTE_ID` | `modern_canary_route` | Spring Cloud Gateway route ID for canary |
| `LEGACY_ROUTE_ID` | `legacy_route` | Spring Cloud Gateway route ID for legacy |
| `ROUTED_BY_HEADER` | `X-Routed-By` | Header name added to all proxied requests |
| `CANARY_ROUTED_BY_VALUE` | `canary-modern` | Header value when routed to modern |
| `LEGACY_ROUTED_BY_VALUE` | `legacy` | Header value when routed to legacy |

### Logging

| Variable | Default | Description |
|---|---|---|
| `LOG_LEVEL_ROOT` | `INFO` | Root logger level |
| `LOG_LEVEL_GATEWAY` | `DEBUG` | Spring Cloud Gateway log level |
| `LOG_LEVEL_APP` | `DEBUG` | Application filter log level |
| `LOG_LEVEL_NETTY` | `DEBUG` | Reactor Netty log level |
| `LOG_PATTERN` | `%d{...} [%thread] %-5level ...` | Console log pattern |

---

## Helm Chart Deployment

The Helm chart deploys the complete environment for integration testing. All infrastructure components are included.

### Deployed Components

| Component | Purpose |
|---|---|
| `morpho-proxy` | The gateway itself |
| `legacy-monolith` | Stub legacy service |
| `modern-microservice` | Stub modern service |
| `keycloak` | OIDC identity provider |
| `kafka` | Telemetry message broker |
| `kafka-ui` | Kafka management console |
| `redis` | Rate limit state store |

### Key `values.yaml` Parameters

```yaml
morphoProxy:
  canaryPercentage: 10        # % traffic to modern
  legacyPercentage: 90        # % traffic to legacy
  modernUri: http://modern-microservice:8080
  legacyUri: http://legacy-monolith:8080
  rateLimitReplenish: 50
  rateLimitBurst: 100
  oidcIssuerUri: http://keycloak:8080/realms/morpho
  kafkaBrokers: kafka:9092
  redisHost: redis
```

---

## Migration Runbook

Follow this process to safely migrate traffic from legacy to modern over time.

### Phase 1 — Shadow Only (0% Canary)
Deploy with all traffic on legacy. Shadow traffic validates modern behaves correctly under production load with zero risk.

```bash
helm upgrade morpho ./morphoproxy \
  --set morphoProxy.canaryPercentage=0 \
  --set morphoProxy.legacyPercentage=100
```

Monitor `SHADOW SUCCESS` vs `SHADOW ERROR` rates in Kafka telemetry.

### Phase 2 — Early Canary (10%)
Once shadow shows acceptable error rates and latency, shift a small percentage of live traffic.

```bash
helm upgrade morpho ./morphoproxy \
  --set morphoProxy.canaryPercentage=10 \
  --set morphoProxy.legacyPercentage=90
```

### Phase 3 — Incremental Increase
Gradually increase canary traffic, monitoring at each step. Recommended increments: 10 → 25 → 50 → 75.

### Phase 4 — Full Cutover (100% Canary)
When confidence is high, shift all traffic to modern.

```bash
helm upgrade morpho ./morphoproxy \
  --set morphoProxy.canaryPercentage=100 \
  --set morphoProxy.legacyPercentage=0
```

### Phase 5 — Decommission
Remove the legacy route from `application.yaml` entirely and decommission the legacy service. The `ShadowTrafficFilter` is no longer needed and can be removed.

### Rollback
At any phase, instantly roll back to legacy:

```bash
helm upgrade morpho ./morphoproxy \
  --set morphoProxy.canaryPercentage=0 \
  --set morphoProxy.legacyPercentage=100
```

---

## Traffic Flow Diagrams

### Legacy Route (with Shadow)
```
Client Request
      │
      ▼
OAuth2 Validation ──✗──▶ 401 Unauthorized
      │ ✓
      ▼
Rate Limiter ──✗──▶ 429 Too Many Requests
      │ ✓
      ▼
Weight Predicate → LEGACY (e.g. 90%)
      │
      ├──▶ Legacy Monolith ──▶ Client Response (synchronous)
      │
      └──▶ Modern Service  (async shadow, fire-and-forget)
                │
                ├── 2xx → log SHADOW SUCCESS
                └── 4xx/5xx → log SHADOW ERROR (client unaffected)
```

### Canary Route
```
Client Request
      │
      ▼
OAuth2 Validation ──✗──▶ 401 Unauthorized
      │ ✓
      ▼
Rate Limiter ──✗──▶ 429 Too Many Requests
      │ ✓
      ▼
Weight Predicate → MODERN (e.g. 10%)
      │
      └──▶ Modern Microservice ──▶ Client Response
           (X-Routed-By: canary-modern)
           (no shadow — already on modern)
```

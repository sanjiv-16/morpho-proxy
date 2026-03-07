# 🦋 Morpho Proxy: Enterprise Migration API Gateway

## Overview
Large organizations face massive risks when migrating legacy monolithic applications to modern microservices. "Big-bang" cutovers often lead to downtime and data loss. 

**Morpho Proxy** is a distributed, reactive API Gateway designed to execute the **"Strangler Fig" migration pattern**. Sitting securely in front of both legacy and modern systems, it intelligently shapes network traffic, allowing engineering teams to migrate endpoints incrementally, test modern services with shadow traffic, and monitor real-time performance metrics without disrupting the end-user experience.



## Key Features
* **Dynamic Canary Routing:** Programmatically shifts a specific percentage (e.g., 10%) of live traffic to the new microservice while routing the remaining 90% to the stable legacy monolith.
* **Shadow Traffic (Dark Launching):** Duplicates incoming read-only requests and mirrors them asynchronously to the new system. This allows for production load-testing without the client ever waiting for the new system's response.
* **Centralized OAuth2 Security:** Intercepts requests and validates OpenID Connect (OIDC) JWT tokens at the edge, abstracting security overhead away from downstream services.
* **Distributed Rate Limiting:** Utilizes a Redis-backed Token Bucket algorithm to protect vulnerable legacy systems from request spikes and DDoS attempts.
* **Real-time Migration Telemetry:** Asynchronously publishes routing decisions, latency metrics, and status codes to a Kafka event stream for real-time observability.

## Tech Stack
* **Framework:** Java 21, Spring Boot 4.x, Spring Cloud Gateway (Reactive WebFlux)
* **Security:** Spring Security OAuth2 Resource Server (Keycloak / OIDC)
* **Infrastructure:** Docker, Docker Compose
* **Middleware:** Redis (Rate Limiting), Apache Kafka (Telemetry)
* **Build Tool:** Gradle

---

## Project Structure
```text
morpho-proxy/
├── .env                      # Environment variables for Docker build
├── Dockerfile                # Multi-stage Ubuntu-based JDK/JRE build
├── docker-compose.yml        # Orchestrates the image build process
├── build.gradle              # Spring Boot / Gradle configuration
└── src/                      # Gateway routing and custom filter logic
```

---

## Setup & Build Instructions

This project is fully containerized. The build process uses a multi-stage Dockerfile based on standard Ubuntu `eclipse-temurin` images to compile the Java application safely and cleanly.

### Prerequisites
* [Docker](https://www.docker.com/products/docker-desktop) installed and running.
* Docker Compose (included in Docker Desktop).

### 1. Configure the Environment
Ensure the `.env` file is present in the root directory. This file parameterizes the build process.

```env
APP_NAME=morpho-proxy
APP_VERSION=0.0.1-SNAPSHOT
JAVA_VERSION=21
EXPOSE_PORT=8080
```

### 2. Build the Docker Image
To compile the Spring Boot application and build the optimized Docker image, simply run the following command from the root directory:

```bash
docker compose up --build
```

**What happens under the hood:**
1. Docker Compose reads the `.env` variables and passes them to the `Dockerfile`.
2. **Stage 1 (Builder):** Pulls a full Java 21 JDK, downloads dependencies via Gradle, and compiles the `.jar` file.
3. **Stage 2 (Runtime):** Pulls a lightweight Java 21 JRE, copies the compiled `.jar` from the builder stage, and exposes port `8080`.
4. The final image is tagged locally as `morpho-proxy:local` and the container spins up.

### 3. Verify the Build
Once the build completes, you should see the Spring Boot banner and log output indicating the proxy has started on port 8080. 
*(Note: To fully test routing, the supporting Redis, Kafka, and Keycloak infrastructure must be running).*


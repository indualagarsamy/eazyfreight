# Running Eazy Freight

## Prerequisites

- Java 21 (Gradle toolchain)
- Node.js + npm
- Docker (for Postgres + Jaeger)

## 1. Start infrastructure

```shell
docker compose up -d      # postgres on 5432, jaeger UI on 16686
```

## 2. Backend (Spring Boot, port 8080)

```shell
./gradlew bootRun
curl localhost:8080/actuator/health
```

Run tests instead with `./gradlew build` (37 tests, no Docker needed).

## 3. Frontend (React + Vite, port 5173)

```shell
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Vite proxies `/api` to `localhost:8080`, so the backend must already be running.
Override the target with `VITE_API_TARGET` if the service runs elsewhere.

## Stopping

- Frontend/backend: `Ctrl+C` in their terminals
- Infrastructure: `docker compose down`
# Running Eazy Freight (Windows)

Windows-specific companion to [RUNNING.md](RUNNING.md). Commands below are
PowerShell; substitute the `cmd.exe` equivalents if that's your shell.

## Prerequisites

- Java 21 (Gradle toolchain) — e.g. `winget install EclipseAdoptium.Temurin.21.JDK`
- Node.js + npm — e.g. `winget install OpenJS.NodeJS.LTS`
- Docker Desktop (for Postgres + Jaeger), with WSL2 backend enabled

## 1. Start infrastructure

```powershell
docker compose up -d      # postgres on 5432, jaeger UI on 16686
```

## 2. Backend (Spring Boot, port 8080)

```powershell
.\gradlew.bat bootRun
curl.exe localhost:8080/actuator/health
```

To restart when an instance is already bound to 8080, find and kill it first:

```powershell
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | Stop-Process -Force
.\gradlew.bat bootRun
```

(`.\gradlew.bat --stop` also stops any running Gradle daemon, but it won't free a
port held by a process started outside Gradle.)

Run tests instead with `.\gradlew.bat build` (no Docker needed).

## 3. Frontend (React + Vite, port 5173)

```powershell
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Vite proxies `/api` to `localhost:8080`, so the backend must already be running.
Override the target with `$env:VITE_API_TARGET` if the service runs elsewhere.

## Stopping

- Frontend/backend: `Ctrl+C` in their terminals
- Infrastructure: `docker compose down`
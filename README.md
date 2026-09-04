# Agentic SDLC Platform

A governed agentic software-engineering platform demonstrated through a production URL-shortener service.

## Modules

- `agentic-platform`: workflow orchestration, specialized agents, model providers, controlled repository operations, governance, audit, and operational APIs.
- `url-shortener-service`: production application used by greenfield and brownfield engineering workflows.

## Prerequisites

- Java 21
- Docker Desktop for PostgreSQL integration and container scenarios

## Build

Windows:

```powershell
.\mvnw.cmd clean verify
```

macOS or Linux:

```bash
./mvnw clean verify
```


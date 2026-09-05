# Agentic SDLC Platform

A governed agentic software-engineering platform demonstrated through a production URL-shortener service.

## Modules

- `agentic-platform`: workflow orchestration, specialized agents, model providers, controlled repository operations, governance, audit, and operational APIs.
- `url-shortener-service`: production application used by greenfield and brownfield engineering workflows.

## Prerequisites

- Java 21
- Docker Desktop for PostgreSQL integration and container scenarios

Detailed review material: [Architecture](docs/ARCHITECTURE.md), [Reviewer Guide](docs/REVIEWER-GUIDE.md), and [Engineering Outcome](docs/ENGINEERING-OUTCOME.md).

## Build

Windows:

```powershell
.\mvnw.cmd clean verify
```

macOS or Linux:

```bash
./mvnw clean verify
```

## Run the orchestrator

The orchestrator uses the deterministic model provider by default, so no model API key is required. Start the included PostgreSQL container, set the matching application credentials, and run the packaged application:

```powershell
docker compose up -d postgres

$env:AGENTIC_SDLC_DATABASE_URL = "jdbc:postgresql://localhost:5432/agentic_sdlc"
$env:AGENTIC_SDLC_DATABASE_USERNAME = "agentic_sdlc"
$env:AGENTIC_SDLC_DATABASE_PASSWORD = "agentic_sdlc_local"
java -jar agentic-platform/target/agentic-platform-0.1.0-SNAPSHOT.jar
```

If port `5432` is already occupied, copy `.env.example` to `.env`, change `POSTGRES_PORT`, and use the same port in `AGENTIC_SDLC_DATABASE_URL`. Existing PostgreSQL volumes retain the credentials used when they were first created; run `docker compose down -v` only when you intentionally want to delete local database data and recreate it with the current credentials.

To exercise the real OpenAI provider, set these variables before startup:

```powershell
$env:AGENTIC_SDLC_LLM_PROVIDER = "openai"
$env:OPENAI_API_KEY = "your-key"
$env:AGENTIC_SDLC_LLM_MODEL = "gpt-5.4" # optional
java -jar agentic-platform/target/agentic-platform-0.1.0-SNAPSHOT.jar
```

The OpenAI endpoint defaults to `https://api.openai.com/v1/responses` and can be changed with `AGENTIC_SDLC_LLM_ENDPOINT`. OpenAPI JSON is available at `/v3/api-docs`, and Swagger UI is available at `/swagger-ui.html`.

Local HTTP Basic users are `operator`, `approver`, and `release-approver`. Their passwords default to `operator-local`, `approver-local`, and `release-local`; override them with `AGENTIC_SDLC_OPERATOR_PASSWORD`, `AGENTIC_SDLC_APPROVER_PASSWORD`, and `AGENTIC_SDLC_RELEASE_PASSWORD` outside local evaluation.

## Run the URL shortener

The URL shortener runs on port `8081` and uses its own PostgreSQL database so its Flyway history remains isolated from the orchestrator:

```powershell
docker compose up -d url-shortener-postgres

$env:SHORTENER_DATABASE_URL = "jdbc:postgresql://localhost:5433/url_shortener"
$env:SHORTENER_DATABASE_USERNAME = "url_shortener"
$env:SHORTENER_DATABASE_PASSWORD = "url_shortener_local"
.\mvnw.cmd -pl url-shortener-service spring-boot:run
```

Create a link, follow its redirect, and inspect analytics:

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/urls `
  -ContentType application/json -Body '{"targetUrl":"https://example.com/docs"}'
curl.exe --max-redirs 0 --silent --dump-header - --output NUL $created.shortUrl
Invoke-RestMethod "http://localhost:8081/api/urls/$($created.code)/analytics"
```

The service also supports optional expiry, link inspection, deactivation, UTC daily analytics, OpenAPI at `http://localhost:8081/v3/api-docs`, and Swagger UI at `http://localhost:8081/swagger-ui.html`.

## Run deterministic workflow scenarios

Authenticated reviewers can list the greenfield, brownfield, and ambiguous scenarios at `GET /api/scenarios`. Submit one with `scenarioType` set to `GREENFIELD`, `BROWNFIELD`, or `AMBIGUOUS`:

```json
{
  "scenarioType": "BROWNFIELD",
  "requirement": "Run the repair scenario while adding redirect analytics",
  "repositoryPath": "url-shortener-service"
}
```

The asynchronous workflow stops for exact-revision change approval, runs implementation and test branches through validation, and stops again for release approval. An ambiguous scenario first returns `AWAITING_CLARIFICATION`; clarification creates a selectively replanned revision. Workflow artifacts and their bounded content are available below `/api/workflows/{id}/artifacts`.

## Run the complete container stack

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

This starts both applications and their isolated PostgreSQL databases. See the reviewer guide for scenario, approval, governance, and Prometheus checks.

Two orchestrator instances (`8080` and `8082`) share durable workflow state. Configure
`AGENTIC_SDLC_INSTANCE_ID`, `AGENTIC_SDLC_TASK_LEASE`, and `AGENTIC_SDLC_RECOVERY_INTERVAL`
to tune database-backed claiming and failover. Deterministic scenarios persist their execution inputs and
apply an idempotent generated Java source change inside the revision workspace after change approval.

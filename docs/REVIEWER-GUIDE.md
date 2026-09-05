# Reviewer Guide

## Automated acceptance

```powershell
.\mvnw.cmd clean verify
```

The build compiles both Java 21 services, runs unit and integration tests, validates Flyway schemas with H2 in PostgreSQL mode, generates JaCoCo reports, checks dependency convergence, and packages executable jars. Reports are under each module's `target/surefire-reports`; coverage is under `target/site/jacoco`.

## Container startup

```powershell
Copy-Item .env.example .env
docker compose config
docker compose up --build -d
docker compose ps
```

Wait for all four services to become healthy. Verify:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:8081/actuator/health/readiness
```

The orchestrator runs on `8080`; the URL shortener runs on `8081`. The deterministic model is selected unless `.env` explicitly selects OpenAI.

## URL-shortener acceptance

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/urls `
  -ContentType application/json -Body '{"targetUrl":"https://example.com/docs"}'
curl.exe --max-redirs 0 --silent --dump-header - --output NUL $created.shortUrl
Invoke-RestMethod "http://localhost:8081/api/urls/$($created.code)/analytics"
```

Expect HTTP `302`, a `Location: https://example.com/docs` header, and redirect totals of at least one. Swagger UI is at `http://localhost:8081/swagger-ui.html`.

## Workflow credentials

```powershell
$operator = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("operator:operator-local")) }
$approver = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("approver:approver-local")) }
$release = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("release-approver:release-local")) }
```

Override all three passwords in `.env` outside local evaluation.

## Greenfield and brownfield scenarios

List the catalog:

```powershell
Invoke-RestMethod http://localhost:8080/api/scenarios -Headers $operator
```

Submit a greenfield workflow, or change `scenarioType` to `BROWNFIELD` and use a requirement containing `repair scenario` to demonstrate failure-driven adaptation:

```powershell
$workflow = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/workflows `
  -Headers $operator -ContentType application/json -Body (@{
    scenarioType = "GREENFIELD"
    requirement = "Build a production URL shortener with expiry and redirect analytics"
    repositoryPath = "url-shortener-service"
  } | ConvertTo-Json)

do { Start-Sleep 1; $workflow = Invoke-RestMethod "http://localhost:8080/api/workflows/$($workflow.id)" -Headers $operator } `
while ($workflow.status -eq "RUNNING")
$workflow
```

At `AWAITING_APPROVAL`, inspect `/artifacts`, read selected artifact content through `/artifacts/{artifactId}`, and submit its named SHA-256 hashes to `/approvals/change`. After the second pause, repeat the evidence review with `/approvals/release`. The approver and release-approver identities are intentionally separate.

## Ambiguous scenario

Submit `scenarioType: AMBIGUOUS` with `Make URL analytics better`. Expect `AWAITING_CLARIFICATION` and a `clarification-request` artifact. Resume with:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows/$($workflow.id)/clarifications" `
  -Headers $operator -ContentType application/json -Body (@{
    requirement = "Track total redirects per short code and daily UTC counts"
    repositoryPath = "url-shortener-service"
  } | ConvertTo-Json)
```

Expect revision 2, reused unaffected work, replanned downstream tasks, invalidated previous approvals, and a new change-approval pause.

## Governance and metrics

Review audit, policy, revision, and artifact endpoints beneath `/api/workflows/{id}`. Prometheus output is available to an authenticated user at `http://localhost:8080/actuator/prometheus`. Search for `agentic_workflow_` and `agentic_task_` meters.

Stop containers without deleting evidence volumes:

```powershell
docker compose down
```

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

Wait for all five services to become healthy. Verify both orchestrator instances:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:8082/actuator/health/readiness
Invoke-RestMethod http://localhost:8081/actuator/health/readiness
```

The orchestrator instances run on `8080` and `8082`; the URL shortener runs on `8081`. Both orchestrators share PostgreSQL workflow state and the isolated runtime volume, but use distinct instance IDs. The deterministic model is selected unless `.env` explicitly selects OpenAI.

## Restart and failover acceptance

For an observable exercise, configure a `10s` task lease and `2s` recovery interval on both orchestrator services. Submit a workflow, then stop the instance owning an active task:

```powershell
docker compose stop agentic-platform
Start-Sleep 15
Invoke-RestMethod http://localhost:8082/api/workflows/$($workflow.id) -Headers $operator
```

The secondary instance reclaims expired work with a higher fencing token and resumes from persisted execution inputs. Workflows at clarification or approval gates remain paused across restarts. After change approval, inspect the `generated-source-mutation` artifact for the isolated source path, resulting manifest hash, and unified diff.

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
    repositoryPath = "bhavana"
  } | ConvertTo-Json)

do { Start-Sleep 1; $workflow = Invoke-RestMethod "http://localhost:8080/api/workflows/$($workflow.id)" -Headers $operator } `
while ($workflow.status -eq "RUNNING")
$workflow
```

At the first `AWAITING_APPROVAL`, inspect the generated plan and approve its exact
persisted hash:

```powershell
$artifacts = (Invoke-RestMethod "http://localhost:8080/api/workflows/$($workflow.id)/artifacts?size=100" -Headers $operator).content
$plan = $artifacts | Where-Object key -eq "engineering-plan" | Select-Object -First 1
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows/$($workflow.id)/approvals/change" `
  -Headers $approver -ContentType application/json -Body (@{
    revision = $workflow.revision; decision = "APPROVED"
    artifactHashes = @{ "engineering-plan" = $plan.contentHash }
    reason = "Repository-specific design reviewed"
  } | ConvertTo-Json)
```

Poll again until the second approval pause. Read `generated-source-mutation`,
`validation-attempt-*`, `repair-patch` when present, and `engineering-outcome`.
The release approver must submit the current `engineering-outcome` hash; an
invented, stale, or earlier-revision hash is rejected:

```powershell
do { Start-Sleep 1; $workflow = Invoke-RestMethod "http://localhost:8080/api/workflows/$($workflow.id)" -Headers $operator } `
while ($workflow.status -eq "RUNNING")
$artifacts = (Invoke-RestMethod "http://localhost:8080/api/workflows/$($workflow.id)/artifacts?size=100" -Headers $operator).content
$outcome = $artifacts | Where-Object key -eq "engineering-outcome" | Select-Object -First 1
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows/$($workflow.id)/approvals/release" `
  -Headers $release -ContentType application/json -Body (@{
    revision = $workflow.revision; decision = "APPROVED"
    artifactHashes = @{ "engineering-outcome" = $outcome.contentHash }
    reason = "Diff, real Maven evidence, risks, and outcome reviewed"
  } | ConvertTo-Json)
```

For the brownfield run, use `Run the repair scenario while adding redirect
analytics`. The first Maven build receives a deliberately invalid generated Java
change, records the compiler output, invokes the repair agent, reapplies a corrected
source patch, and reruns Maven. The release evidence must show two attempts.

## Ambiguous scenario

Submit `scenarioType: AMBIGUOUS` with `Make URL analytics better`. Expect `AWAITING_CLARIFICATION` and a `clarification-request` artifact. Resume with:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/workflows/$($workflow.id)/clarifications" `
  -Headers $operator -ContentType application/json -Body (@{
    requirement = "Track total redirects per short code and daily UTC counts"
    repositoryPath = "bhavana"
  } | ConvertTo-Json)
```

Expect revision 2, reused unaffected work, replanned downstream tasks, invalidated previous approvals, and a new change-approval pause.

## Governance and metrics

Review audit, policy, revision, and artifact endpoints beneath `/api/workflows/{id}`. Prometheus output is available to an authenticated user at `http://localhost:8080/actuator/prometheus`. Search for `agentic_workflow_` and `agentic_task_` meters.

Stop containers without deleting evidence volumes:

```powershell
docker compose down
```

## Production controls

Use `compose.production.yaml` as an overlay after providing the external Docker
secrets and OIDC/TLS environment variables described in
`docs/PRODUCTION-DEPLOYMENT.md`:

```powershell
docker compose -f compose.yaml -f compose.production.yaml config
docker compose -f compose.yaml -f compose.production.yaml up --build -d
```

Set `SHORTENER_REGION=eu` and create a URL; its code must begin with `eu`.
Submissions targeting localhost, private IP literals, configured blocked hosts,
URLs with user information, or non-HTTP schemes must return a problem response.
Temporarily set `SHORTENER_RATE_LIMIT=2` and confirm that a third request from
one client within the configured window returns HTTP `429` with `Retry-After`.
Retention cleanup removes expired redirect events before inactive or expired
short URLs according to `SHORTENER_EVENT_RETENTION` and
`SHORTENER_URL_RETENTION`.

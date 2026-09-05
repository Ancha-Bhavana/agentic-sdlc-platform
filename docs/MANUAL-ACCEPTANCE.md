# Manual Acceptance Walkthrough

Run these commands from the repository root in Windows PowerShell. Docker Desktop
must be running. The default deterministic provider requires no API key.

## Start and check the system

```powershell
cd C:\Users\prabh\IdeaProjects\bhavana
Copy-Item .env.example .env -ErrorAction SilentlyContinue
docker compose config
docker compose up --build -d
docker compose ps

Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:8082/actuator/health/readiness
Invoke-RestMethod http://localhost:8081/actuator/health/readiness
```

All three health responses must contain `status: UP`; both PostgreSQL containers
must be healthy in `docker compose ps`.

Create reusable credentials and helpers:

```powershell
$orchestrator = "http://localhost:8080"
$operator = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("operator:operator-local")) }
$approver = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("approver:approver-local")) }
$release = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("release-approver:release-local")) }

function Wait-Workflow([string]$Id, [string]$Base = $orchestrator) {
  do {
    Start-Sleep 1
    $current = Invoke-RestMethod "$Base/api/workflows/$Id" -Headers $operator
  } while ($current.status -eq "RUNNING")
  return $current
}

function Get-WorkflowArtifacts([string]$Id, [string]$Base = $orchestrator) {
  return (Invoke-RestMethod "$Base/api/workflows/$Id/artifacts?size=100" -Headers $operator).content
}

function Approve-Change($Workflow, [string]$Base = $orchestrator) {
  $plan = Get-WorkflowArtifacts $Workflow.id $Base |
    Where-Object { $_.revision -eq $Workflow.revision -and $_.key -eq "engineering-plan" } |
    Select-Object -First 1
  Invoke-RestMethod -Method Post -Uri "$Base/api/workflows/$($Workflow.id)/approvals/change" `
    -Headers $approver -ContentType application/json -Body (@{
      revision = $Workflow.revision
      decision = "APPROVED"
      artifactHashes = @{ "engineering-plan" = $plan.contentHash }
      reason = "Current repository-specific plan reviewed"
    } | ConvertTo-Json -Depth 4)
}

function Approve-Release($Workflow, [string]$Base = $orchestrator) {
  $outcome = Get-WorkflowArtifacts $Workflow.id $Base |
    Where-Object { $_.revision -eq $Workflow.revision -and $_.key -eq "engineering-outcome" } |
    Select-Object -First 1
  Invoke-RestMethod -Method Post -Uri "$Base/api/workflows/$($Workflow.id)/approvals/release" `
    -Headers $release -ContentType application/json -Body (@{
      revision = $Workflow.revision
      decision = "APPROVED"
      artifactHashes = @{ "engineering-outcome" = $outcome.contentHash }
      reason = "Generated diff, validation, risks, and outcome reviewed"
    } | ConvertTo-Json -Depth 4)
}
```

## URL creation, redirect, and analytics

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/urls `
  -ContentType application/json -Body '{"targetUrl":"https://example.com/docs"}'
$created
curl.exe --max-redirs 0 --silent --dump-header - --output NUL $created.shortUrl
Invoke-RestMethod "http://localhost:8081/api/urls/$($created.code)/analytics"
```

Confirm that creation returns a code and short URL, redirect returns HTTP `302`
with `Location: https://example.com/docs`, and `totalRedirects` is at least one.

## Greenfield workflow

```powershell
$greenfield = Invoke-RestMethod -Method Post -Uri "$orchestrator/api/workflows" `
  -Headers $operator -ContentType application/json -Body (@{
    scenarioType = "GREENFIELD"
    requirement = "Build a production URL shortener with expiry and redirect analytics"
    repositoryPath = "bhavana"
  } | ConvertTo-Json -Depth 4)
$greenfield = Wait-Workflow $greenfield.id
$greenfield.status
Approve-Change $greenfield
$greenfield = Wait-Workflow $greenfield.id
$greenfield.status
Approve-Release $greenfield
$greenfield = Invoke-RestMethod "$orchestrator/api/workflows/$($greenfield.id)" -Headers $operator
$greenfield.status
```

The states must be `AWAITING_APPROVAL`, `AWAITING_APPROVAL`, then `COMPLETED`.

## Brownfield validation and repair

```powershell
$brownfield = Invoke-RestMethod -Method Post -Uri "$orchestrator/api/workflows" `
  -Headers $operator -ContentType application/json -Body (@{
    scenarioType = "BROWNFIELD"
    requirement = "Run the repair scenario while adding redirect analytics"
    repositoryPath = "bhavana"
  } | ConvertTo-Json -Depth 4)
$brownfield = Wait-Workflow $brownfield.id
Approve-Change $brownfield
$brownfield = Wait-Workflow $brownfield.id

$brownfieldArtifacts = Get-WorkflowArtifacts $brownfield.id
$brownfieldArtifacts | Where-Object key -Like "validation-attempt-*" |
  Sort-Object key | Format-Table key, revision, contentHash
$summaryMeta = $brownfieldArtifacts | Where-Object key -eq "validation-summary" | Select-Object -First 1
(Invoke-RestMethod "$orchestrator/api/workflows/$($brownfield.id)/artifacts/$($summaryMeta.id)" -Headers $operator).content

Approve-Release $brownfield
(Invoke-RestMethod "$orchestrator/api/workflows/$($brownfield.id)" -Headers $operator).status
```

Confirm two validation attempt artifacts. The summary must contain
`"successful":true`, `"attempts":2`, and `"repaired":true`; final status must be
`COMPLETED`.

## Ambiguity, clarification, and revision 2

```powershell
$ambiguous = Invoke-RestMethod -Method Post -Uri "$orchestrator/api/workflows" `
  -Headers $operator -ContentType application/json -Body (@{
    scenarioType = "AMBIGUOUS"
    requirement = "Make URL analytics better"
    repositoryPath = "bhavana"
  } | ConvertTo-Json -Depth 4)
$ambiguous = Wait-Workflow $ambiguous.id
$ambiguous.status

$ambiguous = Invoke-RestMethod -Method Post -Uri "$orchestrator/api/workflows/$($ambiguous.id)/clarifications" `
  -Headers $operator -ContentType application/json -Body (@{
    requirement = "Track total redirects per short code and daily UTC counts"
    repositoryPath = "bhavana"
  } | ConvertTo-Json -Depth 4)
$ambiguous = Wait-Workflow $ambiguous.id
$ambiguous.revision
Approve-Change $ambiguous
$ambiguous = Wait-Workflow $ambiguous.id
Approve-Release $ambiguous
(Invoke-RestMethod "$orchestrator/api/workflows/$($ambiguous.id)" -Headers $operator).status
```

Confirm the first pause is `AWAITING_CLARIFICATION`, the resumed workflow is revision
`2`, the second pause is change approval, and the final state is `COMPLETED`.

## Artifact, revision, policy, and audit evidence

Use any workflow ID from the preceding sections:

```powershell
$id = $brownfield.id
$artifacts = Get-WorkflowArtifacts $id
$artifacts | Sort-Object createdAt | Format-Table revision, key, version, producer, contentHash
$mutationMeta = $artifacts | Where-Object key -eq "generated-source-mutation" | Select-Object -First 1
$outcomeMeta = $artifacts | Where-Object key -eq "engineering-outcome" | Select-Object -First 1
(Invoke-RestMethod "$orchestrator/api/workflows/$id/artifacts/$($mutationMeta.id)" -Headers $operator).content
(Invoke-RestMethod "$orchestrator/api/workflows/$id/artifacts/$($outcomeMeta.id)" -Headers $operator).content

(Invoke-RestMethod "$orchestrator/api/workflows/$id/audit-events?size=100" -Headers $operator).content |
  Sort-Object createdAt | Format-Table revision, eventType, actor, role, payloadHash
Invoke-RestMethod "$orchestrator/api/workflows/$id/revisions?size=100" -Headers $operator
Invoke-RestMethod "$orchestrator/api/workflows/$id/policy-results?size=100" -Headers $operator
```

The mutation must show isolated paths, a manifest hash, and diff. The outcome must
link artifact hashes, validation, repair state, assumptions, limitations, and the
release decision. Audit rows must show agent, gate, approval, and completion events.

## Secondary-instance lease recovery

Restart the orchestrators with short test intervals:

```powershell
$env:AGENTIC_SDLC_TASK_LEASE = "10s"
$env:AGENTIC_SDLC_RECOVERY_INTERVAL = "2s"
docker compose up -d --force-recreate agentic-platform agentic-platform-secondary

$failover = Invoke-RestMethod -Method Post -Uri "$orchestrator/api/workflows" `
  -Headers $operator -ContentType application/json -Body (@{
    scenarioType = "BROWNFIELD"
    requirement = "Run the repair scenario while adding redirect analytics"
    repositoryPath = "bhavana"
  } | ConvertTo-Json -Depth 4)
$failover = Wait-Workflow $failover.id
Approve-Change $failover
Start-Sleep 2
docker compose stop --timeout 0 agentic-platform
Start-Sleep 15
$failover = Wait-Workflow $failover.id "http://localhost:8082"
$failover.status
```

The secondary must return the same workflow and eventually reach release
`AWAITING_APPROVAL`. Inspect its audit and artifacts through port `8082`, then approve
release with `Approve-Release $failover "http://localhost:8082"`. Restart the primary
after the exercise with `docker compose start agentic-platform`.

## Optional OpenAI provider

Use a temporary process environment. Do not put the key in `.env` or commit it:

```powershell
$env:AGENTIC_SDLC_LLM_PROVIDER = "openai"
$env:AGENTIC_SDLC_LLM_MODEL = "gpt-5.4"
$env:AGENTIC_SDLC_LLM_ENDPOINT = "https://api.openai.com/v1/responses"
$env:OPENAI_API_KEY = "<temporary-reviewer-key>"
docker compose up --build -d --force-recreate agentic-platform agentic-platform-secondary
docker compose logs --tail 100 agentic-platform
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

Submit a greenfield workflow and repeat its approval flow. Successful `agent-*`
artifacts confirm that the shared agent catalog used the real provider. Provider
errors appear as a terminal failed workflow and redacted audit evidence.

Remove the temporary key when finished:

```powershell
docker compose down
Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
$env:AGENTIC_SDLC_LLM_PROVIDER = "deterministic"
Remove-Item Env:AGENTIC_SDLC_TASK_LEASE -ErrorAction SilentlyContinue
Remove-Item Env:AGENTIC_SDLC_RECOVERY_INTERVAL -ErrorAction SilentlyContinue
```

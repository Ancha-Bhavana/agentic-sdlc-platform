# Engineering Outcome

## Result

The repository contains a governed agentic SDLC platform and a working URL-shortener service. The platform accepts requirements, decomposes work through an explicit dependency graph, persists revision and decision lineage, pauses for ambiguity and human approvals, constrains repository mutation, validates outputs, supports bounded repair and rollback, and exposes evidence through authenticated APIs.

## Delivered artifacts

- Spring Boot 4.1 / Java 21 multi-module build
- PostgreSQL and Flyway persistence for workflows, governance, URLs, and analytics
- Dependency-aware orchestration with parallel branches, gates, retries, fallback, cancellation, recovery, and selective replanning
- Database-backed task ownership with heartbeats, expiry, fencing tokens, startup recovery, and multi-instance failover
- Specialized schema-driven agents with deterministic and OpenAI providers
- Isolated repository snapshots, manifests, bounded context, atomic patching, diff evidence, controlled Maven execution, and verified rollback
- Authentication, role separation, exact-revision approvals, policies, audit persistence, correlation IDs, pagination, RFC problem details, and OpenAPI
- Runnable URL create, redirect, expiry, deactivate, inspection, and analytics APIs
- Greenfield, brownfield repair, and ambiguous clarification assessment scenarios
- Prometheus domain metrics, health probes, non-root containers, Docker Compose, CI, and automated evidence upload
- OIDC/JWT production identity, TLS and config-tree secrets, URL abuse controls, regional allocation, and retention cleanup

## Key decisions

PostgreSQL is the durable source of truth because workflow and approval evidence must survive application restarts. The URL service uses a separate database to avoid migration-history coupling. Human checkpoints are workflow states rather than blocking threads. Artifact hashes bind approvals to reviewed evidence. Model output crosses a schema-validated boundary and cannot directly execute commands or write arbitrary files. Deterministic mode keeps evaluation repeatable and free of paid credentials.

## Validation and risk controls

The Maven reactor exercises graph correctness, concurrent scheduling, gate behavior, retries, clarification revisions, persistence, model schemas, HTTP provider behavior, path confinement, snapshots, patch policy, atomic rollback, real controlled process execution, validation classification, governance, exact artifact verification, security roles, RFC errors, audit redaction, metrics, all three scenarios, and URL-shortener HTTP behavior. CI repeats the build, stores test and coverage evidence, builds both containers, and validates Compose.

Principal risks are untrusted generated changes, credential leakage, stale approval, unsafe paths, partial writes, hanging builds, and failed repair. Controls include schema validation, environment-only credentials, audit redaction, current-revision enforcement, SHA-256 optimistic locking, approved roots, atomic file replacement, fixed capabilities, time/output limits, retry bounds, cancellation, and baseline restoration.

## Assumptions and trade-offs

- Reviewers have Java 21 and Docker Desktop, or can run the Maven tests with embedded H2 without Docker.
- Local Basic users demonstrate authorization boundaries; an enterprise deployment supplies federated identity and managed secrets.
- Daily analytics use UTC to remove timezone ambiguity.
- Redirect events favor traceability and simple correctness over write-minimized high-volume aggregation.
- Deterministic scenario evidence makes demonstrations reproducible; real model quality depends on the selected OpenAI model and prompt context.

## Known limitations

Application rate limiting is per instance, so production ingress should enforce the global client budget. DNS and network egress policy must complement target checks to prevent DNS rebinding. Identity-provider provisioning, certificate issuance, and external secret creation are deployment responsibilities. Recovery waits for the task lease to expire; deliberately paused clarification and approval workflows continue waiting for their required human decision. Container builds require network access for base images and uncached Maven artifacts. A real OpenAI evaluation requires a reviewer-provided API key and may incur cost.

## Acceptance decision

The prototype satisfies the assignment's working-system, architecture, three-scenario, setup, testing, controlled-autonomy, governance, and final-summary deliverables. The documented limitations bound claims that would require a distributed production environment.

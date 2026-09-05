# Production Deployment

## Identity

Set `AGENTIC_SDLC_SECURITY_MODE=oidc`, the HTTPS issuer URI, audience, and optionally an explicit JWK Set URI. Spring Security validates JWT signature, issuer, expiry, not-before, and audience. The identity provider must emit a `roles` array containing `OPERATOR`, `APPROVER`, or `RELEASE_APPROVER`. Local Basic authentication remains the default for deterministic reviewer use.

## TLS and secrets

Both services support PKCS#12 TLS through their `*_TLS_*` variables. They import a configurable Spring config tree, defaulting to `/run/secrets`. The production Compose overlay expects externally managed Docker secrets and contains no credential values:

```powershell
docker compose -f compose.yaml -f compose.production.yaml config
docker compose -f compose.yaml -f compose.production.yaml up --build -d
```

Create the external database-password, TLS-keystore, and keystore-password secrets before deployment. Use certificates whose SANs match the public service names. A load balancer may terminate TLS when it sends trusted forwarded headers and enforces HTTPS at the edge.

## URL controls

`SHORTENER_REGION` is a two-to-four character namespace embedded in each generated code. Give each region a unique value; the shared PostgreSQL uniqueness constraint remains the collision guard. Configure the blocked-host list, request window, redirect-event retention, retired-URL retention, and cleanup interval for the deployment.

The target policy accepts public HTTP(S) URLs and rejects credentials, fragments, local hostnames, configured deny-list entries, and private or reserved literal addresses. DNS and outbound network policy should also block private destinations to protect against DNS rebinding.

Rate limiting uses bounded per-instance client windows. Production ingress should add a distributed edge limit when traffic is balanced across replicas. The application control returns an RFC problem response with `Retry-After`.

Retention deletes expired redirect events first, then removes sufficiently old inactive or expired URLs with no remaining event rows. Active URLs are retained regardless of age.

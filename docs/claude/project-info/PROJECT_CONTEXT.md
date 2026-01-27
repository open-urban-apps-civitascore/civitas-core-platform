# Project Context

Organizational and situational context for the CIVITAS CORE Platform AuthZ work.

## Situation

It's crunch time for the v2.0 release. The v2.0 release is already three months late and eagerly awaited by users. Features are being cut to make the release, we're focusing on the essentials - the MVP. We're optimizing for efficiency — 80/20 decisions, pragmatic TDD, no gold-plating. Java, Rego and Javascript code must be production-ready. Dev infrastructure config (docker-compose, APISIX config) is a handoff to Team 3 who will adapt it for k8s.

## Team Structure

| Team | Owns | Relevance to Us |
|------|------|-----------------|
| Team 1 | Configuration Adapters | Own the Kafka-based Config Adapter for APISIX. May own FROST server integration later. |
| Team 2 | Portal Frontend & Backend | We hand them our Next.js proxy changes. They owe us HTTP-to-permission operation mappings. |
| Team 3 | Deployment / DevOps / Kubernetes | We hand them our docker-compose and config. They produce Helm charts and manage prod deployment. Not operations — we build a product. |
| Us (infosec) | AuthZ implementation | Not a standing platform team. Helping out with engineering during crunch. Building APISIX + OPA + AuthZ Adapter + Rego + frontend proxy changes. |

## What We Own vs. Hand Off

| Artifact | We Build | We Hand To |
|----------|----------|------------|
| AuthZ Adapter (Java) | Production-ready code + tests + Dockerfile | Team 3 (deployment) |
| Rego policies + tests | Production-ready | Team 3 (deployment) |
| APISIX config | Dev docker-compose | Team 3 (adapts for k8s) |
| OPA deployment config | Dev docker-compose | Team 3 (adapts for k8s) |
| Next.js proxy changes | Implementation | Team 2 (maintains going forward) |
| Keycloak requirements | Document settings needed | Team 3 / whoever manages Keycloak |
| k8s requirements | Document only | Team 3 (implements) |

## Working Constraints

- **VM is ours exclusively** — we can create Keycloak users, seed DB data, restart containers freely.
- **External dependencies** — Team 2 owes us permission mappings, backend URL conventions. We can work with assumptions and adjust later.
- **PoC exists** — same architecture was PoC'd previously. We follow the architecture but don't blindly copy — simplifications were made in the PoC.

## Decision-Making Principles

1. **User's instructions trump all** — over CIVITAS docs, over PoC, over general best practices.
2. **CIVITAS/CORE docs are second** — the official architecture is the baseline.
3. **PoC is reference only** — useful for patterns, not authoritative.
4. **Optimize for shipping** — pick the simplest approach that's production-quality. Don't over-engineer.
5. **Leave upgrade paths open** — e.g., Caffeine cache now, Redis later. Don't paint ourselves into corners.
6. **Playwright is the truth** — if it doesn't work in the browser, it doesn't work. Backend test passes don't count if the user can't use the product.
7. **Touch other people's code only if we need to** - This repo contains code for frontend, backend and configuration adapter. We only work with code pertaining to the AuthZ related task at hand. We touch other people's code only to the extent we need to get our functionality running. Don't refactor or fix other people's code beyond that.

## Current State of the Platform

- **PostgreSQL**: Running, healthy.
- **Keycloak**: Running. `civitas-core` realm exists. No test users yet (M0 task).
- **Portal Backend**: Spring Boot 3.5.7. Builds and runs. 8 REST controllers with standard CRUD (assignments, datasets, dataspaces, groups, permissions, roles, users, catalogs). API completeness unverified.
- **Portal Frontend**: Next.js 15 + React 19. Builds. CSP fix applied (unsafe-eval for dev). BFF proxy exists at `src/app/api/[...path]/route.ts` — this is the single point we change for APISIX routing.
- **Frontend ↔ Backend integration**: Unverified, assumed broken.
- **APISIX / OPA / AuthZ Adapter**: Not yet deployed. This is what we're building.

## Security Posture Notes

- We're from the infosec team — security is not an afterthought but we also don't over-engineer it for dev.
- CSP is enforced (strict in production, permissive in dev for HMR/eval).
- BFF pattern keeps OAuth tokens server-side (HTTP-only cookies, never exposed to client JS).
- AuthN is JWKS-only at the gateway — no session management in APISIX.
- Stale cache (60s TTL) is an accepted tradeoff for v2.0. Active invalidation is post-2.0.
- Any security posture changes get flagged to the user before implementation.

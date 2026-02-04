# CLAUDE.md

Quick reference for Claude Code when working with CIVITAS CORE Platform v2.0.

## Repository Overview

Monorepo with three main components:
- **portal-frontend**: Next.js 15 + React 19 + Keycloak auth
- **portal-backend**: Spring Boot 3.5.7 + PostgreSQL + OAuth2
- **config-adapter**: Event-driven Kafka/CloudEvents framework

All components use Keycloak for authentication.

## Quick Start

```bash
# Infrastructure
cd dev-environment/postgres && docker compose up -d
cd dev-environment/keycloak && docker compose up -d   # admin/admin @ http://localhost:8080

# Frontend: http://localhost:3000
cd portal-frontend && pnpm install && cp .env.local.template .env.local && pnpm dev

# Backend: http://localhost:8089/v2 (Swagger: /v2/swagger-ui.html)
cd portal-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Security — CRITICAL Rules

- **NEVER** implement custom cryptography (AR-12)
- **NEVER** use production data in dev/test environments (ORG-7)
- **NEVER** expose tokens to client-side (use HTTP-only cookies)
- Use prepared statements/ORMs (never string concatenation for queries)
- Never modify existing Flyway migrations, always create new
- See [Security Guide](/docs/claude/project-info/SECURITY.md) for full TR-03187 compliance details

## Important Reminders

- **Keycloak required**: Both frontend and backend need Keycloak running
- **Code formatting**: Always run `mvn spotless:apply` / `pnpm format` before committing
- **E2E tests**: Require Keycloak + backend + credentials in `.env.local`
- **Testcontainers**: Backend/adapter integration tests require Docker
- **Production builds**: Build against `pnpm-lock.yaml`, not `package.json`

## Technology Versions

- **Node**: 22 | **Java**: 21 | **pnpm**: via corepack | **Maven**: 3.9+
- **Next.js**: 15.5.0 | **React**: 19.1.0 | **Spring Boot**: 3.5.7
- **PostgreSQL**: Latest (Docker) | **Keycloak**: Latest (Docker)

## Task Lifecycle Protocol

Follow these phases automatically for every non-trivial task. Do not wait for the user to ask.

### Pre-conditions (before writing code):
- Read `docs/claude/memory/` for relevant patterns and gotchas
- Check git status — warn if working tree is dirty
- Check backlog for related items (TD/F/B that touch the same area)
- For tasks needing infrastructure: verify services are up before starting
- **Match existing patterns**: Before implementing in any component, read existing code in the same layer/area first. Follow the conventions already in use — don't invent new patterns. If a framework decision is needed (e.g., "backend uses X, config-adapter uses Y"), present the options and let the user decide.

### During implementation:
- For tasks taking >3 minutes, give progress updates. No silent 10+ minute stretches.

### Post-conditions (after modifying global Claude config):
- If `~/.claude/CLAUDE.md` or `~/.claude/commands/` were changed, run: `bash /mnt/shared/claude-tests/claude/setup-claude-config.sh --push`

### Post-conditions (after writing code, before saying "done"):
- Run formatters: `mvn spotless:apply` (backend) and/or `pnpm format` (frontend)
- Run targeted tests for the affected area (not the full suite — just what's relevant)
- Check if any backlog items were resolved or created by this change
- If you learned something new or hit a non-obvious problem, update `docs/claude/memory/`
- **If Rego files changed**: Generate review prep materials (see below)

### Gate (when user asks for full confidence, e.g. /preflight):
- Full test suite: frontend unit, backend unit, Rego tests
- E2E tests (chromium-only locally)
- Doc staleness check: test counts, milestone status, stale backlog items

### Continuous improvement:
- Log process friction as FR-xxx entries in BACKLOG.md as you encounter it during normal work
- After each milestone completion, suggest running `/retro` for a full retrospective
- The `/retro` includes a 1:1 where you interview the user and give them honest feedback

### Error handling:
- Before retrying an error: check `docs/claude/memory/gotchas.md` for known patterns
- After fixing a non-obvious error: record the pattern in memory
- Max 2 attempts on the same approach. If stuck, change strategy or ask user.

### Test execution defaults:
- Playwright: chromium-only locally (3-browser matrix is CI-only)
- Backend: `mvn test` for unit tests, `mvn verify` only when integration tests are affected
- Rego: `opa test . -v` from authz/rego/
- Never silently skip failing tests

## Rego Review Prep

When Rego policy files are changed, generate review materials **in addition to** the code diff (line-by-line review is still required). Include:

1. **Decision table (before/after)**: Show behavioral delta as a table of scenarios with old vs. new outcomes
2. **Security impact summary**: Plain-English description of what changed and how it affects the attack surface
3. **Edge cases to scrutinize**: Flag specific scenarios the reviewer should pay attention to

## Detailed Documentation

**Architecture & Code:**
- [Backend Architecture](/docs/claude/project-info/BACKEND_ARCHITECTURE.md) - Six-layer pattern, packages, implementation flow
- [Frontend Architecture](/docs/claude/project-info/FRONTEND_ARCHITECTURE.md) - Stack, directories, authentication, patterns
- [Security Guide](/docs/claude/project-info/SECURITY.md) - TR-03187 compliance, authentication, authorization
- [Code Style Guide](/docs/claude/project-info/CODE_STYLE.md) - Naming conventions, formatting, best practices
- [Development Patterns](/docs/claude/project-info/DEVELOPMENT_PATTERNS.md) - API design, data access, event-driven, config adapter

**Operations:**
- [Testing Guide](/docs/claude/project-info/TESTING.md) - Test organization, running tests, coverage requirements
- [CI/CD Guide](/docs/claude/project-info/CICD.md) - Pipeline stages, security, local testing

**Project Knowledge Base** (syncs across machines):
- [Patterns & Solutions](/docs/claude/memory/patterns.md) - Reusable implementation patterns
- [Gotchas & Debugging](/docs/claude/memory/gotchas.md) - Known pitfalls and fixes

**External:**
- [Official v2.0 Docs](https://docs.core.civitasconnect.digital/review-arch-v2-doc/docs_v2/intro)
- [GitLab](https://gitlab.com/civitas-connect/civitas-core/)

## Changelog Convention

- **Latest entry only**: `/docs/claude/CHANGELOG-LASTCHANGE.md` (for context efficiency)
- **Full history**: `/docs/claude/project-info/CHANGELOG.md`

When updating the changelog, replace the content in `CHANGELOG-LASTCHANGE.md` with the new entry, then append the previous entry to `project-info/CHANGELOG.md`.

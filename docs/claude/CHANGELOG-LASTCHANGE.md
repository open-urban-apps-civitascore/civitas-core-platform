# Latest Change

This file contains only the most recent changelog entry for context efficiency.
Full history is in `project-info/CHANGELOG.md`.

## 2026-02-07 (session — idle maintenance + watchtower)

- **chore**: E2E test ownership separation
  - Moved `oauth-flow.spec.ts` to `authz/e2e/tests/` (separate from frontend E2E suite)
- **feat**: `/upstream` skill for GitLab API upstream monitoring (two-tier MR flagging, conflict detection, ADR/pipeline tracking)
- **feat**: cc-watchtower private repo for upstream intelligence
  - Workstream-based digests (authz, security, dx)
  - Symlink integration into feature repo
  - Watchtower is the control plane; reads from feature repos, writes locally
- **chore**: Preflight: automated test count verification, pnpm audit, authz E2E command
- **docs**: Handoff note for Next.js vulnerability (H-004), backlog updates (TD-020–022, F-007–010), fixed stale test counts, new gotchas

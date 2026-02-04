Autonomous work while the user is away. Argument: time budget and optional focus hint.

Examples: `/idle 1h`, `/idle 4h rego-review-prep`, `/idle overnight frontend-tests`

## Rules
- NO commits. Stage changes and leave a summary for the user to review.
- NO architectural decisions. If you hit one, note it and move on.
- NO security posture changes. Flag them for the user.
- NO destructive actions (deleting files, dropping data, force-anything).
- Give progress updates as you go (the user may check in).
- Token budget: ask the user to run `/usage` before they leave. Note the limits and reset timing, and pace work accordingly. Stop well before exhausting limits so the user has capacity for interactive work when they return.
- When uncertain about intent, scope, or approach: **ask** (the user may check in). Store answers in `docs/claude/memory/` so you don't need to ask again.
- When the budget is up, stop cleanly and write a summary of what was done and what's left.

## Focus hints
If the user provides a focus hint after the time budget, prioritize that area:
- `rego-review-prep` → Rego review materials (decision tables, traceability, security summaries)
- `frontend-tests` → Frontend test coverage gaps
- `backend-tests` → Backend test coverage gaps
- `docs` → Documentation drafts and updates
- `backlog` → Backlog audit and low-risk TD items
- Any other hint → interpret as a focus area and prioritize related tasks

Still do Tier 1 maintenance first, then focus on the hint area instead of following tier order.

## Task priorities (pick from top, work down as time allows)

### Tier 1 — Always do first (~30 min)
- Run formatters (`mvn spotless:apply`, `pnpm format`)
- Run full test suite (frontend unit, backend unit, Rego)
- Fix any test failures
- Reconcile docs: cross-check test counts, milestone status, and backlog references against actual state. Fix stale info.

### Tier 2 — If 1h+ budget
- Audit BACKLOG.md: mark resolved items, flag stale ones, prune entries that no longer apply
- Check `docs/claude/memory/` for gaps — add patterns or gotchas learned recently, prune outdated ones
- Doc consistency pass: check for contradictions between docs (e.g. CLAUDE.md vs TESTING.md vs MILESTONES.md)
- Dependency vulnerability scan (`pnpm audit`, check for known CVEs)
- Run E2E tests if infrastructure is up

### Tier 3 — If 4h+ budget
- Draft documentation for the current/next milestone
- Write tests for untested code paths (check coverage gaps)
- Generate Rego review prep materials (decision tables, security summaries)
- Address low-risk TD items from backlog (formatting, dead code, minor refactors)
- Update Rego requirements traceability if policies changed
- Prune dead documentation: remove or archive docs that reference completed/abandoned work

### Tier 4 — Overnight
- Full M6-level documentation drafts (handoff docs, deployment guides, config guides)
- Deep code quality audit (unused imports, dead code, naming consistency)
- Explore open F-xxx items and write feasibility notes
- Prepare review materials for upcoming reviews
- Run full /preflight and document results
- Full doc reconciliation: every doc checked for accuracy, currency, and relevance

## When done
Write a summary to the user:
1. **What was completed** — with file paths for each change
2. **What needs review** — flag anything that needs human judgment
3. **Rollback guide** — for each change, the `git checkout` or `git restore` command to undo it individually. Also include a single command to roll back everything wholesale.
4. **What was skipped** and why (hit a decision, blocked on something, ran out of time)
5. **Questions** — anything you weren't sure about and need answered for next time
6. **Shower thoughts** — observations, suggestions, things that caught your eye

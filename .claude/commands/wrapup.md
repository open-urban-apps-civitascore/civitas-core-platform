End-of-session wrapup. Do everything from /springclean, plus self-improvement steps:

## Springclean (same as before):
- Look for any temporary files and delete them
- If running docker containers, check logs for errors and write to errorlogs.txt
- Update CHANGELOG-LASTCHANGE.md (move previous entry to project-info/CHANGELOG.md)
- Update all other documentation based on changes since last commit
- Ensure good test coverage
- Run all tests, fix any errors

## Self-improvement — structured reflection:

Answer these questions about the session. For each "yes", take the indicated action:

1. **What took more than one attempt?** → Add to `docs/claude/memory/gotchas.md` if not already there
2. **What manual step did I repeat more than twice?** → Candidate for a script or skill. Log as FR-xxx in BACKLOG.md.
3. **What assumption was wrong?** → Add to `docs/claude/memory/patterns.md` or `gotchas.md`
4. **Did any skill feel wrong or incomplete when used?** → Update the skill in `.claude/commands/`
5. **Are there new friction entries (FR-xxx) to log?** → Add to BACKLOG.md Process Friction section
6. **Are any existing FR entries now resolved?** → Strike them through

## Docs maintenance:
- Check test counts in TESTING.md — update if stale
- Check MILESTONES.md — update status if milestones were completed or progressed
- Check BACKLOG.md — mark resolved items, add new ones discovered during session
- Update `docs/claude/memory/` files with any new patterns or gotchas

## Report:
Summarize what was updated and list any open suggestions for workflow improvements.

<!-- VENDORED from agent-context@b924326c — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@6847d4f0 · version next · Development/development-guidelines/ai-usage-policy/quality-and-review.md
     Raw:    https://gitlab.com/civitas-connect/civitas-core/documentation/-/raw/6847d4f0ad9bda2133fcff49b3048e4f571966f6/docs_v2/Development/development-guidelines/ai-usage-policy/quality-and-review.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/next/Development/development-guidelines/ai-usage-policy/quality-and-review -->

---
sidebar_position: 3
---

# Quality Assurance & Review

## Quality Assurance

### Quality Gates Apply Regardless of Origin

The quality gates of the CI pipeline apply to all code, regardless of how it was created. The [Secure Development Guide](./ssdlc-distilled.md) describes what developers need to fulfill.

These gates are the primary scaling mechanism: they automatically check what can no longer be checked manually as code volume increases.

### AI-Specific Additions

In addition to the existing gates, the following requirements apply:

**Check dependency changes with increased attention.**
AI tools can suggest non-existent packages (so-called "slopsquatting"). For every change to lockfiles (`pnpm-lock.yaml`, `pom.xml`), check:
- Does the package exist in the official registry?
- Is the package name plausible (no typo variants of known packages)?
- Was the rationale for new dependencies documented in the MR (see [Secure Development Guide – Dependencies](./ssdlc-distilled.md#dependencies))?

**Infrastructure files require extra review diligence.**
Changes to CI/CD configurations, Dockerfiles, Helm charts, and build scripts require review by a person familiar with the deployment infrastructure. AI-generated changes to these files may look functionally correct while still introducing security gaps (e.g., additional pipeline steps, changed network rules, loosened permissions).

**Test coverage for new code.**
AI-generated code should meet at least the same testing standard as manually written code. In particular:
- Test security-relevant error paths (see [Secure Development Guide – Testing](./ssdlc-distilled.md#testing))
- Do not accept tests that only cover the "happy path"
- Do not accept tests that merely mirror the implementation or verify only mock behavior without checking a functional expectation

**Check license and provenance risk for extensive AI suggestions.**
For non-trivial, extensive AI suggestions (algorithms, complex business logic), a brief plausibility check is advisable to see whether it is a known, licensed implementation that must not simply be adopted. This complements the existing dependency license checks, which only cover imported packages, not code copied directly into our own source code — relevant especially given our "Public Money, Public Code" / EUPL-1.2 position.

---

## Review Expectations

### Principle

> Code generation is no longer the bottleneck — **verification is.**

AI tools increase the speed at which code is created. Review capacity must respond to that. Our approach: **automate every check that can be automated, and reserve human review for what cannot.**

### Division of Responsibilities

| Type of check | Responsible | Examples |
|-------------|---------------|-----------|
| **Automatable** | CI pipeline and AI-assisted review tools | Formatting, linting, known vulnerability patterns, unused imports, naming and convention checks, duplicate detection, contextual review hints |
| **Non-automatable** | Humans with the relevant expertise | Architecture conformance, business logic, proportionality of the solution, institutional knowledge, auth flows, data flows, trust boundaries, secrets handling, attack surface |

Automatability is a spectrum: AI-assisted checks count as automated checks, and a check that can be automated only in part should be automated as far as possible. Human review capacity is then spent on the remainder. For security-relevant changes, involve the security team as described in the [Secure Development Guide](./ssdlc-distilled.md#when-to-involve-security).

### What Reviewers Should Pay Particular Attention To

For MRs where AI use has been disclosed, the following aspects deserve particular attention, from human reviewers and AI-assisted review alike. Delegate to automated checks whatever they can cover. For a detailed overview of typical patterns, see the [appendix](./ai-usage-policy-security-and-exceptions.md#appendix-typical-patterns-in-ai-generated-code).

1. **Proportionality**: Is the solution appropriate for the problem? AI-generated code tends toward over-abstraction and unnecessary complexity.

2. **Duplication**: Was existing code reused, or was a new implementation generated for something that already exists?

3. **Understanding**: Can the author explain the design decisions? (see [Understanding Expectations](./ai-usage-policy-purpose-and-responsibility.md#understanding-expectations))

4. **Conventions**: Does the code follow project conventions? Automated tools (linters, AI-assisted review) catch part of this; reviewers check what goes beyond pure formatting (architecture layers, patterns, idioms).

5. **Existence check**: Are the APIs, methods, or parameters used actually real? For unfamiliar or exotic calls, it is worth checking the documentation.

### MR Contract

Every merge request should include:

1. **Intent**: What was changed and why (1–2 sentences)
2. **Evidence**: Test results, screenshots, or execution logs
3. **AI maturity**: Declared maturity level (see [Maturity Levels](./ai-usage-policy-purpose-and-responsibility.md#maturity-levels-ai-maturity-protocol))
4. **Review focus**: Targeted hints to reviewers about what to pay particular attention to. For larger MRs with multiple reviewers, the focus should be differentiated per person (e.g., "@reviewer-a: please check database access; @reviewer-b: look at CSS modularization").

This supplements the [MR checklists](./ssdlc-distilled.md#working-with-the-mr-checklists) of the Secure Development Guide.

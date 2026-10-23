<!-- VENDORED from agent-context@b924326c — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@6847d4f0 · version next · Development/development-guidelines/ai-usage-policy/security-and-exceptions.md
     Raw:    https://gitlab.com/civitas-connect/civitas-core/documentation/-/raw/6847d4f0ad9bda2133fcff49b3048e4f571966f6/docs_v2/Development/development-guidelines/ai-usage-policy/security-and-exceptions.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/next/Development/development-guidelines/ai-usage-policy/security-and-exceptions -->

---
sidebar_position: 4
---

# Security Guardrails

## Security Guardrails

### Principle: Least Agency

AI agents should operate with the **minimum necessary permissions** (OWASP ASI02/ASI03). This means:

- Restrict filesystem access to the necessary repository
- Restrict network access where the tool allows it
- No automatic execution of generated shell commands without confirmation

### Data Protection and Confidentiality

- **No secrets** (API keys, tokens, passwords, certificates) in prompts or as context.
- As a general rule: the more sensitive the context, the more critical the choice of tool. The following table governs which data may go into which tool class:

| Data category | Any LLM | Cloud LLM with training opt-out | Cloud LLM with DPA (no training) | Local |
|---|---|---|---|---|
| Public code (e.g., public GitHub repos) | ✅ | ✅ | ✅ | ✅ |
| Zulip chats, private GitLab content (wiki, issues, internal code) | ❌ | ✅ | ✅ | ✅ |
| Secrets (API keys, tokens, passwords, certificates) | ❌ | ❌ | ❌ | ❌ |

**Tool classes**:
- **Any LLM**: Any tool without special configuration (e.g., free tiers without opt-out).
- **Cloud LLM with training opt-out**: Consumer subscriptions (e.g., Claude Pro/Max, ChatGPT Plus) with opt-out enabled in the settings. No contractual DPA — the assurance is based on the terms of service and the opt-out setting. Acceptable for internal project data, but not for data with elevated protection needs.
- **Cloud LLM with DPA**: API or enterprise/business tiers (e.g., Anthropic API, OpenAI API, ChatGPT Enterprise) with a signed data processing agreement and a contractual exclusion of training on customer data.
- **Local**: Self-hosted models with no external data transfer.

**Explanation**: Public code is crawled anyway — there is no additional risk from AI tools here. For internal project data (Zulip, GitLab), at least an enabled training opt-out is required. Secrets must never be used in any AI tool under any circumstances.

### Agent Configuration Files and Shared Context

Configuration files for AI agents (e.g., `AGENTS.md`, `.cursor/rules`, `.github/copilot-instructions.md`) are **code** and are subject to the same review requirements:

- Changes require review in the MR process.
- Check for invisible Unicode characters (a potential attack vector for "rules file poisoning").
- Content should be minimal, current, and well-maintained — outdated instructions lead to faulty AI behavior.

**Shared Context (AGENTS.md)**: The shared agent context is maintained in the `agent-context` repository. From there it is distributed by merge request as a marked block into the root `AGENTS.md` of the consuming repositories (`civitas-core-platform`, `civitas-core-deployment`, `conformance-workbench`). Do not edit this block in a consuming repository; change it in `agent-context`. `AGENTS.md` is a [standard initiated by OpenAI and donated to the Linux Foundation (AAIF)](https://agents.md/) that is read by all common AI coding tools. The shared context contains:

- Project purpose, priorities, and governance rules for agents
- Guidelines from this documentation, held as local copies so agents do not need to fetch them
- The applicable BSI TR-03187 requirements

Context that belongs to a single repository (build, test, and deployment commands, architecture boundaries, security rules such as the Flyway migration policy, project conventions) lives in that repository and is owned by it. Team-level and developer-level context can be docked locally and is not committed.

Maintenance:
- Responsibility for the shared context lies with the maintainers of `agent-context`. Responsibility for repository-specific context lies with the respective team lead.
- Recommended size: 100–300 lines in the root file, max. 8 KB. Studies on AGENTS.md files show that unnecessarily extensive context does not improve task completion but increases cost. Content should be limited to what is necessary ([Gloaguen et al., ETH Zurich 2026](https://www.sri.inf.ethz.ch/publications/gloaguen2026agentsmd)).
- Regular currency check: ideally every 2 sprints.

---

## Appendix: Typical Patterns in AI-Generated Code

These patterns are not inherently wrong, but they deserve review attention:

| Pattern | Description | Review question |
|--------|-------------|--------------|
| **Over-abstraction** | Unnecessarily complex patterns (Observer, Command, State Machine) for simple problems | Is this abstraction justified by the requirement? |
| **Duplication** | Functionally identical implementations instead of reusing existing code | Does this already exist in the project? |
| **Unnecessary code** | Custom implementation where a library call would have sufficed; the code you don't write is the best code | Is there an existing library or utility for this? |
| **Excessive error handling** | Error handling for scenarios that cannot occur; excessive edge-case coverage | Can this error case actually occur? Does the risk justify the code? |
| **Custom conventions** | Deviation from project naming, logging patterns, layer separation | Does this fit our existing conventions? |
| **Hallucinated APIs** | Use of non-existent methods, parameters, or libraries | Does this API/library actually exist? |

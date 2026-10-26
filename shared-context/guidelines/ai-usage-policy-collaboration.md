<!-- VENDORED from agent-context@b924326c — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@6847d4f0 · version next · Development/development-guidelines/ai-usage-policy/collaboration.md
     Raw:    https://gitlab.com/civitas-connect/civitas-core/documentation/-/raw/6847d4f0ad9bda2133fcff49b3048e4f571966f6/docs_v2/Development/development-guidelines/ai-usage-policy/collaboration.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/next/Development/development-guidelines/ai-usage-policy/collaboration -->

---
sidebar_position: 2
---

# Collaboration

## Collaboration and Readability

### Starting Point

The amount of AI-generated content feels demotivating to the team — especially sprawling, unclear descriptions and hard-to-understand issues ("slop"). This section aims to bring back more humanity to collaboration, beyond the existing [Maturity Levels](./ai-usage-policy-purpose-and-responsibility.md#maturity-levels-ai-maturity-protocol).

### Limiting and Simplifying AI Output

- **Agent Skills**: Agents are constrained more tightly via skills (collected in a project-wide skill repository) and trimmed for readability.
- **Controlled Language (STE)**: Agents communicate in ASD-STE100 (already mandated in `AGENTS.md`) — this policy makes that binding project-wide for all AI text output, not just for coding agents.

### Base Rule: "Don't be a meat proxy"

> No one should expect others to read AI-generated content that they themselves have not read.

Issues, descriptions, documentation, and chat messages should therefore be at least **AI:AMBER** before being passed on. AI:RED content may be passed on, but the work needed to bring it to AI:AMBER is not generally the recipient's task.

**Concrete rules for issues:**

- Use AI labels consistently.
- AI:RED issues may be passed on. The recipient is not generally expected to do the work needed to bring them to AI:AMBER.
- AI:RED issues may always be rejected.
- AI:RED issues do not go into sprint planning.
- Whoever owns an issue must be able to explain it (cf. [Understanding Expectations](./ai-usage-policy-purpose-and-responsibility.md#understanding-expectations)).
- Principle: AI is a tool that serves the work. The labels make the level of human control visible; they do not restrict the use of AI.

### Code Comments

AI-generated code comments are particularly disruptive because they often add volume instead of information. The following rules apply:

- **Why, not what.** A comment explains the intent or reason behind a decision, not what the next line already shows (e.g., `// increment counter` above `counter++`). Such comments are removed in review, not tolerated.
- **No prose repetition of names.** Comments that merely paraphrase the method or variable name (e.g., `// Gets the user by ID` above `getUserById(id)`) are redundant and will be deleted.
- **No meta-comments about the origin.** Notes like "// Added this per your request" or "// Fixed as suggested" belong in the commit message or MR description, not in the code.
- **Javadoc/JSDoc stays informative, not decorative.** The documentation required by the [Java Style Guide](./backend-style-guide.md) or [Frontend Style Guide](./frontend-style-guide.md) is not a license to repeat parameters and return values in prose. It should document what is *not* evident from the signature (side effects, invariants, edge cases).
- **Comments are code.** They are subject to the same review expectations as the code itself (see [Review Expectations](./ai-usage-policy-quality-and-review.md#review-expectations)). The author is responsible for them; reviewers may flag superfluous AI comments as a finding and require their removal.

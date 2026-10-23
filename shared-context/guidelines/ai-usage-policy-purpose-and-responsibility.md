<!-- VENDORED from agent-context@b924326c — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@6847d4f0 · version next · Development/development-guidelines/ai-usage-policy/purpose-and-responsibility.md
     Raw:    https://gitlab.com/civitas-connect/civitas-core/documentation/-/raw/6847d4f0ad9bda2133fcff49b3048e4f571966f6/docs_v2/Development/development-guidelines/ai-usage-policy/purpose-and-responsibility.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/next/Development/development-guidelines/ai-usage-policy/purpose-and-responsibility -->

---
sidebar_position: 1
---

# Purpose & Responsibility

## Purpose and Scope

This document supplements the [Secure Development Guide](./ssdlc-distilled.md) with rules for the use of AI tools in software development. It specifies existing quality and security requirements for AI-assisted code generation.

### Scope

- Applies to all contributors to the CIVITAS/CORE project, regardless of the AI tool used.
- Covers all forms of AI-assisted code generation: autocomplete, chat-based generation, autonomous coding agents.
- Applies to code, configuration, documentation, tests, infrastructure files, and GitLab work items (issues and requirements).

### Guiding Principle

> AI tools change *how fast* code is created, but not *which standards* apply to it.

This document follows the principle: good engineering practices apply regardless of whether code is written by humans or with AI assistance. Where AI introduces specific new risks, these are addressed explicitly.

### References

This document builds on the following existing project standards and refers to them:

| Document | Relevance |
|----------|-------|
| [Secure Development Guide](./ssdlc-distilled.md) | Day-to-day development practice, MR checklists, vulnerability management |
| [Java Style Guide](./backend-style-guide.md) | Backend coding standards |
| [Frontend Style Guide](./frontend-style-guide.md) | Frontend coding standards |
| [Security Architecture Principles](./security-architecture-principles.md) | Architecture security principles |

---

## Responsibility Model

### Principle

**Whoever submits an artifact within the scope defined above is responsible for it**. This applies regardless of how the artifact was created. The use of AI tools does not shift responsibility.

### Roles

| Role | Responsibility |
|-------|---------------|
| **Author** | Functionality, correctness, test coverage, compliance with the MR checklist. Must be able to defend the submitted change and diagnose problems (see [Understanding Expectations](#understanding-expectations)). |
| **Reviewer** | Architecture conformance, security implications, business logic, compliance with project conventions. Focus on what automated checks cannot cover (see [Review Expectations](./ai-usage-policy-quality-and-review.md#review-expectations)). |
| **Project Leadership / Security Team** | Maintenance of the automated quality gates and this standard. Exceptions follow the [vulnerability management process](./ssdlc-distilled.md#vulnerability-management). |

### Understanding Expectations

What "being responsible for the code" means in concrete terms depends on the level of abstraction:

| Level | Expectation |
|-------|-----------|
| **Design decisions** | Must be able to explain why this solution was chosen and which alternatives were considered. Non-trivial decisions should be recorded as an [ADR](https://docs.core.civitasconnect.digital/docs_v2/next/Architecture/architecture-decisions/valid-adr/). This practice is meant to help avoid Comprehension Debt. |
| **Control flow** | Must be able to describe the flow of the change and defend it in review |
| **Details / boilerplate** | Must know *that* it is there and *why*. It is not necessary to be able to explain every single line on the spot. |
| **Debugging** | Must be able to diagnose problems in the submitted code using the tools available (including AI) |

### Comprehension Debt

AI-assisted development can lead to code existing in the project that no one on the team fully understands. This is so-called *Comprehension Debt*. This is not an exclusively AI-specific phenomenon (scaffolding tools, copied Stack Overflow solutions, and team member rotation produce similar effects), but AI can accelerate it.

Comprehension Debt, like technical debt or security debt, is not a failure but a risk that must be managed deliberately:

- **Visibility**: Reviewers should raise comprehension gaps as a finding, not as an accusation. The [maturity level](#maturity-levels-ai-maturity-protocol) declared in an MR makes the debt visible.
- **Reduction**: Reducing Comprehension Debt is a planning task for the team (e.g., quality sprints, code walkthroughs, pair reviews), not a duty of the individual author.
- **Overlap with knowledge management**: Knowledge transfer during team changes, documentation of architecture decisions, and context-building for new team members are related tasks. AI context (AGENTS.md, ADRs) and human context (onboarding, walkthroughs) benefit from each other.

### Maturity Levels (AI Maturity Protocol)

Inspired by the Traffic Light Protocol (TLP), maturity levels indicate how much human control went into an AI-assisted work result. The levels apply to code, documentation, analyses, and other artifacts, with a focus on work in GitLab.

| Level | Label | Meaning |
|---|---|---|
| 🔴 | **AI:RED** | AI-generated, checked for plausibility only. Only as a basis for discussion or a draft — not for production. |
| 🟡 | **AI:AMBER** | AI-generated to a large extent. Architecture and design actively worked out and understood, hot spots reviewed — but not every line checked. Conscious risk of Comprehension Debt. |
| 🟢 | **AI:GREEN** | AI-generated, fully reviewed and understood line by line. The author can explain every part. |
| ⚪ | **AI:WHITE** | Written independently, no significant AI use. |

**Rules:**
- **Minimum level for merge requests**: AI:AMBER. AI:RED artifacts must not be submitted as an MR.
- **AI:AMBER requires** a description in the MR of which parts were reviewed in detail (hot spots) and where Comprehension Debt exists. Reviewers can use this information to check specifically.
- **AI:GREEN and AI:WHITE** are equivalent in review. The distinction serves transparency only.

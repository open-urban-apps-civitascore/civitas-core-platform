<!-- VENDORED from agent-context@37494c39 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@e2f8a6fc · version 2.0-rc2 · Development/Development Process/SSDLC_Distilled.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/Development/Development Process/SSDLC_Distilled -->

# Secure Development Guide

**Version 0.9**

> This is our practical SSDLC guide for everyday development.
> The goal is to make secure-by-default changes without having to read the full SSDLC for each ticket.

This is a distilled view of our SSDLC and the underlying TR-03187 requirements. It does **not** replace the full document; it focuses on what we need to do in our daily dev work. The full document lives [here](https://docs.core.civitasconnect.digital/docs_v2/Development/Development%20Process/SSDLC).

This document itself includes only information and prescriptions for the entire CIVITAS/CORE team.

This document is meant to be used in conjunction with the following team specific guidelines:
* [Security Architecture Principles](./security-architecture-principles.md): This document needs to be followed by everyone creating a ticket. It includes architecture-wide/overarching design principles that need to be incorporated into requirements and designs; they affect the coding phase only via the specifications from the ticket.
* [Backend-Style Guide](./backend-style-guide.md): This document is intended for Team 1 and the backend developers of Team 2
* [Frontend-Style Guide](./frontend-style-guide.md): This document is intended for the Frontend developers of Team 2
* [DevOps Deployment Standards](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-deployment/-/blob/main/docs/deplyoment-standards.md): This document contains prescriptions for Team 3.
* [Open Source Standards](https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_General/Open_Source_Standards): This policy is to followed whenever new open source components are selected

## 1. Scope & Roles (developer view)

**Developer responsibilities**

You are responsible for:

- writing secure code and avoiding unnecessary increases of attack surface,
- keeping defaults safe,
- shipping only the necessary artifacts (no debugging tools, no unused dependencies)
- ensuring the CI pipeline is green and security findings are addressed (we enforce this anyway), and
- escalating high sensitivity changes for dedicated security review.

**What you do *not* need to handle manually**

CI and release tooling **already**:

- run SAST and linters on each change,
- run dependency and license scans and enforce policies,
- perform secret scanning on commits and images,
- generate SBOMs and attach them to releases, and
- build and sign release artifacts.

You do **not** need to:

- Run these tools locally (unless it speeds you up, e.g. by shortening the feedback cycle when you’re trying to fix it),
- manually construct SBOMs or signatures, or
- maintain separate spreadsheets / lists of dependencies.

You **do** need to:

- keep the pipeline green, and
- investigate and fix or explicitly document exceptions for any security findings.

Operators are responsible for hosting, monitoring, backups, incident response, and their local ISMS. We are responsible for enabling them through documentation and guidance.

## 2. Working with the MR checklists

When merging from your feature branch to the dev branch, you SHOULD fill in the non functional requirement checklist. As this may not be feasible in early stages of work on a feature, it is not mandatory. Before closing a ticket, you MUST refer to the MR (e.g. using the `Closes` functionality in Gitlab) and the checklist MUST be filled out.

## 3. Requirements you need to fulfill

### Keep defaults secure

- New feature flags default to the safest mode (least privilege, minimal exposure).
- New configuration options have a documented “secure default” and reasoning.
- If the feature employs Authentication or Authorization, default to a "block all" setting

### Interfaces

For each **externally reachable interface**:
- Enforce input validation and authentication at the first entry point.

### Dependencies
- Before adding a dependency, consider if it actually adds value considering the risk of adding a component. As a rule of thumb, anything that's less than 50 lines of code should not be added through a dependency, but instead added in code.
- When adding a new dependency:
  - Ensure it is pinned in the lockfile
  - Add a short rationale to the MR (why did you choose this particular library over others? Why do you think this component is secure enough?)
  - For high risk/widely used components, note the risk as calculated from the scheme in the [Open Source Standards](https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_General/Open_Source_Standards). For these components, the MR reviewer must also comment on the reasoning.
  - Make reasonable effort to minimize dependencies/shipped binaries

Dependency analysis and SBOM generation are handled in CI, but you are responsible for choosing appropriate dependencies. If you need help choosing, ping your team mates or the security team.

### Miscellaneous
- Encode/sanitize any content that is rendered into HTML or passed to shell/system calls.
- Do **not** commit secrets, keys, or tokens to the repository (secret scanning should find out if you do :) ).
- Pull all secrets from environment variables or from mounted files (which are then to be pulled from Kubernetes Secrets at runtime)
- Do not display information about versions or components to unauthenticated users. Do not display information about internal components to non-admin users.
- Do not pass sensitive information in URLs. Always use the HTTP Body for that.

### Cryptography
- If any use of cryptography is introduced (e.g. hashes, TLS, signing), ensure that the algorithms and key lengths comply to BSI TR-02102-*. BSI TR-02102-1 lists permissible algorithms for symmetric encryption, asymmetric encryption and hash functions, BSI TR-02102-2 lists permissible TLS settings. TL;DR: The following algorithms are permissible for use in CIVITAS/CORE: AES-128 (or longer), RSA with 3096 bit keys and OAEP padding (or longer), ECC keys with 250 bit (or longer) and SHA-128 or longer (no SHA-1!). For internal TLS, use only TLS 1.3 with TLS_AES_256_GCM_SHA384. When in doubt, involve infosec team.

### Error Handling & Logging
- Default to not logging any personal data. Where logging of personal data is needed for debugging purposes, mask data using '*'. Do not log passwords or secrets under any circumstances.
- Should it be deemed beneficial to display debugging output in development environments (e.g. stack traces), add a mechanism to turn these on or off, with a default of "off".
- Include correlation/trace IDs for debugging
- In production, users must only receive generic error messages. Details can be logged on the server side.

```java
❌ Exposing internal errors
catch (SQLException e) {
  return ResponseEntity.status(500).body(e.getMessage());
}

✅ Generic error, detailed logging
catch (SQLException e) {
  logger.error("Database error for user {}", userId, e);
  return ResponseEntity.status(500).body("Internal server error");
}
```
### Testing
- Unit/integration tests for authorization decisions (allowed vs blocked)
- Tests for error paths
- Include security-relevant test scenarios for non-trivial changes
- Test input validation

### Vulnerability Management
- Before Dev can be merged to Main, all Vulnerabilities by the CI chain must be removed or triaged.
- Exceptions up to level medium can be granted by any developer, but a comprehensive rationale has to be given why the risk is acceptable ("e.g. the affected functionality of the component is not enabled/not exposed because...")
- Exceptions up to level high need approval from an additional developer
- Exceptions for level Critical need approval from product owner

## 4. When to involve security

Always involve the security team when:
- You modify authentication, authorization, cryptography, or secret handling.
- You introduce a new external integration, plugin mechanism, or cross-instance feature.
- You are uncertain about data classification or tenant isolation impacts.
- You introduce temporary security debt that should be consciously accepted.

A short summary and a tag in the PR description is sufficient; the aim is early, focused feedback.

## 5. Further references

- [**SSDLC document** – full process, TR-03187 mapping, and artefact descriptions](https://docs.core.civitasconnect.digital/docs_v2/Development/Development%20Process/SSDLC).
- [**Threat landscape** – documentation of who might be potential attackers](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform/-/wikis/home/Information-Security/Threat-Landscape).
- TODO: **Hardening and operations documentation** – what operators are expected to configure.

Following this guide ensures that individual changes remain aligned with the overall SSDLC and TR-03187 expectations without requiring you to work from the full text every time.

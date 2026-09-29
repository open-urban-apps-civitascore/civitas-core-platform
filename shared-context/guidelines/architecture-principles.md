<!-- VENDORED from agent-context@37494c39 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@e2f8a6fc · version 2.0-rc2 · Architecture/Architecture_General/Architecture_Principles.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_General/Architecture_Principles -->

---
sidebar_position: 3
---

# Architecture Principles

1. **Model-Driven Data Flow**
   The CIVITAS/CORE platform follows a model-driven data flow. Models define the structure, behavior, and configuration of platform components. They are authoritative for data persistence, orchestration/control, and automated processing.

2. **Distributed Architecture with a Unified User Experience**
   The platform consists of distributed, decoupled components, each with a specific responsibility. User interaction generally occurs through a central UI. Direct access to individual systems is provided only where functionally required.

3. **Modular Design**
   The platform architecture is modular. Core components are reduced to the functional minimum. Domain-specific components must be switchable or replaceable without impacting the platform core.

4. **Integrability via Well-Defined Interfaces**
   All platform capabilities are exposed through clear, documented (standard) interfaces (e.g., OGC standards, REST, MQTT, …). External components can be integrated into existing data and control flows through these interfaces.

5. **Open Source by Default**
   The platform is developed and operated entirely according to open-source principles. All artifacts required for operation are published. New components must adhere to this principle.

6. **Cloud-Native Architecture**
   CIVITAS/CORE is developed and operated cloud-natively. Components must be containerized, horizontally scalable, and runnable on Kubernetes or equivalent platforms.

7. **Standard Solutions Before Custom Development**
   Before building custom components, it must be assessed whether existing, economically viable standard components meet the requirements. Evaluation must fully consider adaptation, operations, and maintenance effort for both standard and custom solutions. Open-source standard components with a small community should be treated as custom development, with responsibility for ongoing maintenance and evolution assumed.

8. **Self-Contained Deployment**
   The platform must be fully operable within a single Kubernetes cluster. Infrastructure services such as monitoring, CI/CD, or source control must be optional and replaceable by external solutions.

9. **Technological Consistency to Ensure Maintainability**
   Custom developments must use consistent, long-lived technologies. Technological diversity is to be limited to what is strictly necessary to ensure maintainability, onboarding, and continued evolution.

10. **Multi-Tenancy**
    The platform should support secure and efficient multi-tenant operation. Required scalability and appropriate guardrails must be ensured (e.g., protection against the noisy-neighbor problem).

11. **Security & Privacy by Design**
    To preserve confidentiality and integrity, common security and data-protection best practices must be considered and implemented from the outset. For details, please see [Security Architecture Principles](./security-architecture-principles.md)

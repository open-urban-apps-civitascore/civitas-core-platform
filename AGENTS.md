# AGENTS.md

<!-- BEGIN shared-context — generated, do not edit inside this block -->
# CIVITAS/CORE v2 — Agent Context

## What and why
Open-source urban data platform. Municipalities and municipal utilities
consolidate heterogeneous data, control access at a fine grain, and publish
selectively — to their own systems, to other municipalities, to the public.

The goal is digital sovereignty. Today service providers carry the innovation,
which produces vendor lock-in through per-customer adaptation; the project's
answer is commonly maintained code. Product consists of well established FOSS
components plus custom management layer plus glue components.

**Product development, not operations.** We ship Helm charts and container
images. Hosting, monitoring, backup and incident response belong to the
operator. We never see production data; telemetry is opt-in.

## Sprint Priorities (21.8.-1.10.2026)
- Working on v2 RC2 - Release imminent.
- Bug fixing
- SSDLC and TR 03187 conformance
- **Model Forge** integration

**Everything else can generally wait.** Don't start new features, don't
refactor beyond the task, don't tidy up on the side. When in doubt, propose
rather than start — this close to a release, scatter costs more than it gains.

## Governance
- Steward: **Civitas Connect e.V.**, consists of several dozens municipalities and municipal utilities who give the requirements.
- Community-driven, partly federally funded.
- Built by several service providers under contract. Service Providers can change.
- **Public Money, Public Code** — licensed **EUPL-1.2**.
- One development team. (Formerly LeSS with several teams)
- Security regime: **BSI TR-03187, level 1.** Applicable requirements are in [tr-03187-level1.md](./shared-context/tr-03187-level1.md).

## Stakeholders and their incentives
| Who | Incentive |
|---|---|
| **Municipalities and cities**, small town to metropolis | Share cost instead of paying for bespoke builds; escape vendor lock-in. Small ones need something that runs with few resources and limited skillset without an IT department; large ones need adaptability. User Acceptance through broad feature set and consistent UX.|
| **Municipal utilities** | Make grid and sensor data usable; often also the operator. |
| **Development team** (service providers) | Deliver against contract. They want clear requirements and acceptable results, not open scope. |
| **Operators** | Operability: hardening guidance, secure defaults, predictable updates. They carry the runtime risk alone. Are averse to deep kubernetes cluster integration to the extent it impedes multi-application/multi-tenant setups. |
| **Project leadership** | Schedule, funding compliance, ability to release, defined quality |

## Decision rights:
| What | Who |
|---|---|
| Architecture decisions, architecture concept, technical review | **Architecture board** — product architect, PO, lead devs, security architect |
| Functional elaboration and review, backlog, roadmap | **Product Owner** based on community requirements|
| Issues, refinement, planning | **Development team** |
| External acceptance | **Community sponsors** (Community-Paten) |
| Impediments | **Scrum Master** |

## Always read
[Secure Development Guide](./shared-context/guidelines/ssdlc-distilled.md)
[Supplemental, per-repository agent context](./per-project-agentcontext)
[Supplemental, per-team agent context](./per-team-agentcontext)
[Supplemental, per-developer agent context](./per-dev-agentcontext)

## Always read if your task involves the area listed
| Task Area | Document |
|---|---|
| Backend | [Backend Style Guide](./shared-context/guidelines/backend-style-guide.md) |
| Frontend | [Frontend Style Guide](./shared-context/guidelines/frontend-style-guide.md) |
| Container images | [Container Image Guidelines](./shared-context/guidelines/container-image-guidelines.md) |
| Deployment, Helm, cluster | [Deployment Requirements](./shared-context/guidelines/deployment-requirements.md) |
| Authorization, permissions, roles | [Authorization Data Model](./shared-context/guidelines/authorization-data-model.md) |
| Architecture | [Architecture Principles](./shared-context/guidelines/architecture-principles.md) · [Security Architecture Principles](./shared-context/guidelines/security-architecture-principles.md) · [tr-03187-level1.md](./shared-context/tr-03187-level1.md) · [accepted ADRs](https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_Decisions/accepted_adrs/) |

Follow 'Accepted' ADRs in design and implementation. Use 'In Review' and 'Reviewed' ADRs as context for architecture suggestions, where asked for such.

# Repositories
https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform - Custom management layer, glue components

https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-deployment - Deployment of all components of the platform (both 1st and 3rs party) in kubernetes via helmfile and helm charts

https://gitlab.com/civitas-connect/civitas-core/documentation - a Docusaurus repo with the public documentation. When you use, be sure to target docs applicable to current version

# Language
For all natural language communications, use ASD-STE100 Simplified Technical English (STE).

# AI maturity labels
Work results carry a label that says how much human control went into AI-assisted work
(inspired by the Traffic Light Protocol). It applies to code, documentation, analyses and
other artefacts. In GitLab the labels are scoped: `AI::RED`, `AI::AMBER`, `AI::GREEN`, `AI::WHITE`.

| Label | Meaning |
|---|---|
| **AI:RED** | AI-generated, checked for plausibility only. A basis for discussion or a draft — not for production. |
| **AI:AMBER** | AI-generated in large parts. Architecture and design worked out and understood together, hot spots reviewed — but not every line. A known risk of comprehension debt. |
| **AI:GREEN** | AI-generated, reviewed and understood line by line. The author can explain every part. |
| **AI:WHITE** | Written without substantial AI use. |

# Glossary
| Term | Meaning |
|---|---|
| **Tenant** | The platform's top-level separation. Data and permissions stop at the tenant boundary. |
| **Data pool** (FKA Dataspace) | Groups related Datasets and carries permissions. |
| **Dataset** | A single body of data within a Data pool. Also includes metadata, dataflow definitions and access configurations. |
| **Data source** | A definition of how to integrate data from an external system into the platform. Uses various connectors. |
| **Data structure** | A versioned schema or data model for data moving through the platform. Used for Data sources, Data sinks and Mappings. |
| **Pipeline** | A graph-based ETL definition of a data flow within a Dataset to extract data from a Data source, optionally map it and write it to a Data sink. |
| **Data sink** | A definition of where a Pipeline writes its data. |
| **Data storage** | A kind of Data sink that persists data inside the platform, e.g. in a database. FrostSink and PostGisSink are both Data storages. |
| **Mapping** | A defition of how to transform data from a defined Data structure to another. |
| **Connector** | A building block of a Data source providing access through various protocols or systems, e.g. SQL or MQTT. |
| **Scope** | The reach of a permission: Tenant, Data pool, Data structure, Data source or Dataset. |
| **Permission** | The right to perform an operation on a resource type. |
| **Role** | A bundle of permissions. |
| **Assignment** | Granting a Role to a Group of User, within a scope. |
| **Operation** | Abstract action (read, create, update, delete) that HTTP methods map onto. |
<!-- END shared-context -->

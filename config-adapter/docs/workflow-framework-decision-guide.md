# Workflow Framework Decision: Flowable vs. Temporal

> **Issue:** [#1273](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform/-/work_items/1273)
> **Date:** April 2026
> **Scope:** Saga orchestration for multi-adapter provisioning (FROST, APISIX, NiFi, Keycloak)
> **Detailed analysis:** [saga-temporal-comparison.md](saga-temporal-comparison.md), [workflow-framework-evaluation-matrix.md](workflow-framework-evaluation-matrix.md)

---

## Context

The config-adapter needs a saga orchestration framework to coordinate provisioning workflows across multiple backend services. Workflows connect DataSources with DataSinks — different adapter combinations depending on the use case. After evaluating 27 frameworks, two candidates remain.

---

## Flowable vs. Temporal at a Glance

| | **Flowable** | **Temporal** |
|---|---|---|
| **In one sentence** | Embeddable BPMN engine — visual workflows, runs in our JVM, no extra server | Durable execution platform — code-based workflows, best observability, separate Go server |
| **License** | Apache 2.0 | MIT |
| **Architecture** | Embedded in existing JVM | +1 Go server, reuses existing PostgreSQL |
| **Workflow definition** | BPMN (visual/XML) | Java code |
| **Compensation** | Automatic reverse-order (BPMN standard) | Automatic reverse-order (`Saga.java`) |
| **Kafka integration** | Event Registry (needs Spring Boot) | Bridge worker needed |
| **UI integration** | bpmn-js Modeler + Viewer embeddable in our own UI (MIT) | Separate Web UI, not embeddable |
| **Audit / history** | HistoryService in PostgreSQL, queryable via API | Native OpenTelemetry + full event history |
| **OpenTelemetry** | Not built-in, addable (~1-2 PT via EventListener + TracedDelegate) | Native, out-of-the-box |
| **Versioning** | Automatic on re-deploy, live migration of running instances | Patching API in code |
| **Additional infra** | None (embedded) — reuses existing PostgreSQL (~36 tables) | +1 Go server container |
| **Spring Boot** | Required for full Kafka + metrics integration (migration acceptable) | Not required |
| **Company** | Flowable Ltd. (Switzerland), ~30-80 employees, no VC | Temporal Technologies (USA), ~200-400 employees, $120M+ VC |
| **Maturity** | 9.2k stars, 392 contributors, 16 year lineage (Activiti) | 19.8k stars, 278 contributors, 7 years (10 with Cadence) |
| **BSI code review** | High risk — core committers merge without formal review | Low risk — multi-reviewer standard |
| **Rug-pull risk** | Low (no VC pressure) | Moderate (VC-funded, but MIT is irrevocable) |

---

## Choose Flowable if

- **BPMN visual modeling** is valued — workflows are self-documenting diagrams, not code
- **UI integration** matters — bpmn-js Modeler and live status Viewer can be embedded directly in the product UI
- **Minimal infrastructure** is preferred — no extra server, embedded in existing JVM
- **Conditional adapter routing** is needed — BPMN Gateways handle different DataSource/DataSink combinations without code changes
- **HistoryService** is sufficient for audit (which steps ran, duration, inputs/outputs — all in PostgreSQL)
- **Workflow versioning with live migration** is important

## Choose Temporal if

- **Full distributed tracing** (OpenTelemetry) is a hard requirement from day one
- **Code-based workflows** are preferred over BPMN/XML
- **Open-source project maturity** (governance, code review process, CI) is weighted higher than functional fit
- The team does **not plan to embed workflow visualization** in the product UI

---

## BSI TR-03187 Risk Summary

| Risk Weight | Criterion | Flowable | Temporal |
|---|---|---|---|
| Very High | License | Low (Apache 2.0) | Low (MIT) |
| Very High | Code review | **High** ⚠️ | Low |
| Very High | User documentation | Very Low | Very Low |
| High | Governance transparency | **High** ⚠️ | Low |
| High | Last commit | Very Low | Very Low |
| High | CI pipeline | Low | Very Low |
| High | Developer docs | Medium | Very Low |

Temporal has better open-source hygiene. Flowable's main weaknesses are code review practices and governance transparency. Neither has had anti-community incidents. Both licenses are OSI-approved and EUPL-1.2 compatible.

---

## OTel Integration Effort (if needed later with Flowable)

Flowable does not have native OpenTelemetry, but integration is straightforward:

| Phase | Effort | Result |
|---|---|---|
| OTel Java Agent | 0.5 PT | JDBC + HTTP + Kafka auto-instrumented |
| TracedJavaDelegate base class | 1 PT | Every adapter call as a named span |
| FlowableEventListener | 1-2 PT | Full BPMN structure visible in traces |
| **Total** | **~3 PT** | Comparable to Temporal's native OTel |

---

## Evaluated and Excluded Frameworks (25)

| # | Framework | License | Exclusion reason |
|---|---|---|---|
| 1 | Restate.dev | BUSL-1.1 | Not OSI-approved Open Source — violates Open Source requirement |
| 2 | Camunda 8 (Zeebe) | Camunda License v1 | Proprietary license, production use requires commercial contract |
| 3 | LittleHorse | AGPL-3 (server) | AGPL-3 needs legal review, 380 stars, 1.0 just released, bus-factor risk — too risky for public-sector project |
| 4 | Netflix Conductor | Apache 2.0 | No native saga compensation, heaviest infrastructure (+Server +PG +Elasticsearch) |
| 5 | Eventuate Tram Sagas | Apache 2.0 | No dashboard, no OTel, bus-factor 1 (single maintainer: Chris Richardson) |
| 6 | Axon Framework | Apache 2.0 | Requires CQRS/Event-Sourcing paradigm buy-in, over-engineering risk for saga-only use |
| 7 | Kogito / Apache KIE | Apache 2.0 | Still in Apache Incubation since Jan 2023, no graduation timeline, unstable APIs |
| 8 | Cadence (Uber) | MIT | Predecessor of Temporal, community shrinking since 2020 fork |
| 9 | MicroProfile LRA | EPL 2.0 | HTTP-only (no Kafka), only 1 implementation (Narayana) |
| 10 | Apache Camel Saga | Apache 2.0 | Thin saga layer, in-memory coordinator not production-ready |
| 11 | Spring State Machine | Apache 2.0 | No saga semantics, less capable than existing custom solution |
| 12 | Apache Airflow | Apache 2.0 | Python-only batch scheduler, no Java SDK, wrong tool for microservice sagas |
| 13 | Flowable *(selected)* | Apache 2.0 | — |
| 14 | Temporal *(selected)* | MIT | — |
| 15 | Apache Seata | Apache 2.0 | Database-transaction focused, limited Kafka integration |
| 16 | CIB seven | Apache 2.0 | Camunda 7 fork with 119 stars, long-term viability uncertain |
| 17 | nFlow | EUPL v1.1 | No Kafka integration, DB-polling only |
| 18 | Infinitic | MIT | Requires Apache Pulsar instead of Kafka |
| 19 | Activiti | Apache 2.0 | Superseded by Flowable (same origin, Flowable more active) |
| 20 | Kestra | Apache 2.0 | Data pipeline orchestrator, no saga/compensation support |
| 21 | Bonita BPM | GPLv2 | Restrictive license, heavyweight BPM suite, no Kafka |
| 22 | Netflix Maestro | Apache 2.0 | Data/ML pipeline orchestrator, no saga support |
| 23 | Quarkus Flow | Apache 2.0 | Pre-1.0, no saga support yet |
| 24 | Imixs-Workflow | EPL 2.0 | Human-centric workflow, not microservice saga orchestration |
| 25 | Narayana LRA | LGPL 2.1 | Same as MicroProfile LRA, preview status |
| 26 | Unify-Flowret | Apache 2.0 | Extremely lightweight, no Kafka, no saga primitives |
| 27 | javactrl-kafka | Unknown | Experimental research project, not production-ready |

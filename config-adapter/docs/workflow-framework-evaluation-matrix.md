# Workflow Framework Evaluation Matrix

> **Based on:** PO/Architect answers (April 2026)
> **Issue:** [#1273](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform/-/work_items/1273)
> **Total frameworks surveyed:** 27 (12 original + 15 additional market scan)
> **Frameworks after K.O. filter:** 7 + Custom (Option 0)

---

## 1. Market Overview

### 1.1 All Surveyed Frameworks (27)

| # | Framework | License | Architecture | Saga Support | Outcome |
|---|-----------|---------|-------------|--------------|---------|
| 1 | Temporal.io | MIT | Server (Go) + Worker | Erstklassig | **Tier 1** |
| 2 | Netflix Conductor | Apache 2.0 | Server (Java) + Worker | Via Branching | **Tier 1** |
| 3 | Restate.dev | BUSL-1.1 / MIT SDKs | Server (Rust) + Service | Durable Execution | ~~**Eliminated**~~ |
| 4 | Eventuate Tram Sagas | Apache 2.0 | Embedded Library | Erstklassig (DSL) | **Tier 1** |
| 5 | Axon Framework | Apache 2.0 / commercial | Embedded / opt. Server | Erstklassig (@Saga) | **Tier 1** |
| 6 | Camunda 8 (Zeebe) | Camunda License v1 | Server (Java/Raft) | BPMN Compensation | ~~**Eliminated**~~ |
| 7 | Kogito / Apache KIE | Apache 2.0 | Embedded / Server | BPMN Compensation | ~~**Eliminated**~~ |
| 8 | Cadence (Uber) | MIT | Server (Go) | Like Temporal | ~~**Excluded**~~ |
| 9 | MicroProfile LRA | EPL 2.0 | Coordinator + Participants | HTTP-Callbacks | ~~**Excluded**~~ |
| 10 | Apache Camel Saga | Apache 2.0 | Embedded (Camel Route) | Thin layer | ~~**Excluded**~~ |
| 11 | Spring State Machine | Apache 2.0 | Embedded Library | None | ~~**Excluded**~~ |
| 12 | Apache Airflow | Apache 2.0 | Server (Python) | None | ~~**Excluded**~~ |
| 13 | **Flowable** | **Apache 2.0** | **Embedded / Server** | **BPMN Compensation** | **Tier 1 (NEW)** |
| 14 | **LittleHorse** | **AGPL-3 / Apache 2.0 SDKs** | **Server (Java/Kafka Streams)** | **Code-defined** | **Tier 1 (NEW)** |
| 15 | Apache Seata | Apache 2.0 | Server + Agent | State Machine Saga | ~~**Excluded**~~ |
| 16 | CIB seven (Camunda 7 fork) | Apache 2.0 | Embedded / Server | BPMN Compensation | ~~**Excluded**~~ |
| 17 | nFlow | EUPL v1.1 | Embedded | State Machine | ~~**Excluded**~~ |
| 18 | Infinitic | MIT | Server (Pulsar) | Code-defined | ~~**Excluded**~~ |
| 19 | Activiti | Apache 2.0 | Embedded / Cloud | BPMN Compensation | ~~**Excluded**~~ |
| 20 | Kestra | Apache 2.0 | Server (Java) | None | ~~**Excluded**~~ |
| 21 | Bonita BPM | GPLv2 | Server | None | ~~**Excluded**~~ |
| 22 | Netflix Maestro | Apache 2.0 | Server (Java) | None | ~~**Excluded**~~ |
| 23 | Quarkus Flow | Apache 2.0 | Embedded (Quarkus) | Not yet | ~~**Excluded**~~ |
| 24 | Imixs-Workflow | EPL 2.0 | Server (Jakarta EE) | BPMN (human-centric) | ~~**Excluded**~~ |
| 25 | Narayana LRA | LGPL 2.1 | Coordinator | HTTP-Callbacks | ~~**Excluded**~~ |
| 26 | Unify-Flowret (AmEx) | Apache 2.0 | Embedded | None | ~~**Excluded**~~ |
| 27 | javactrl-kafka | Unknown | Embedded (Kafka Streams) | Experimental | ~~**Excluded**~~ |

### 1.2 Elimination Reasons

| Framework | Reason |
|-----------|--------|
| **Restate** | BUSL-1.1 is NOT OSI-approved Open Source → violates Open Source requirement |
| **Camunda 8** | Camunda License v1 is proprietary → violates Open Source requirement |
| **Kogito / Apache KIE** | Still in Apache Incubation since Jan 2023, no graduation timeline, unstable APIs |
| **Cadence** | Predecessor of Temporal, shrinking community since 2020 fork |
| **MicroProfile LRA** | HTTP-only, no Kafka support, only 1 implementation |
| **Apache Camel Saga** | Thin layer, in-memory coordinator not production-ready |
| **Spring State Machine** | No saga semantics, less capable than the existing custom solution |
| **Apache Airflow** | Python-only, batch scheduler, no Java SDK |
| **Apache Seata** | Database-transaction focused, limited Kafka integration |
| **CIB seven** | Camunda 7 fork with tiny community (119 stars), long-term viability uncertain |
| **nFlow** | No Kafka integration, DB-polling only |
| **Infinitic** | Requires Apache Pulsar instead of Kafka |
| **Activiti** | Superseded by Flowable (same origin, Flowable is more active) |
| **Kestra** | Data pipeline orchestrator, no saga/compensation support |
| **Bonita** | GPLv2 restrictive, heavyweight BPM suite, no Kafka |
| **Maestro** | Data/ML pipeline orchestrator, no saga support |
| **Quarkus Flow** | Pre-1.0, no saga support documented yet |
| **Imixs** | Human-centric workflow, not microservice saga orchestration |
| **Narayana LRA** | Same as MicroProfile LRA (#9), preview status |
| **Unify-Flowret** | Extremely lightweight, no Kafka, no saga primitives |
| **javactrl-kafka** | Research/experimental, not production-ready |

---

## 2. Requirements from PO/Architect Answers

### 2.1 Requirements Classified by Priority

| Priority | # | Requirement | Source (Answer) |
|----------|---|-------------|-----------------|
| **MUST** | R1 | Open Source license | A8: "Must be Open Source" |
| **MUST** | R2 | Self-hosted, no cloud | A8: "No cloud" |
| **MUST** | R3 | Java SDK / Java-native | A8: "Java" |
| **HIGH** | R4 | Saga compensation / rollback | A3: "Complete rollback for now" |
| **HIGH** | R5 | Scales to multiple workflow variants | A1: Workflows connect DataSources with DataSinks (different adapter combinations). DataPool is a type of DataSink, not a separate workflow. |
| **HIGH** | R6 | Idempotent behavior | A6: "System must behave idempotently" |
| **HIGH** | R7 | Kafka integration | Existing architecture, A6: "Ordering via Kafka" |
| **HIGH** | R8 | Minimal additional infrastructure | A8: "Use current infrastructure if possible" |
| **HIGH** | R9 | New adapters can be added easily | A2: "Yes, new ones will be added" |
| **MEDIUM** | R10 | Workflow status visibility in own UI | A4: "Desired, but hard to realize." Clarification: team builds own UI, does not use external dashboards. bpmn-js Viewer embedded in product UI is the preferred approach. |
| **MEDIUM** | R11 | Audit logging of saga steps | A5: "OpenTelemetry." Clarification: scope unclear — full distributed tracing (OTel stack) vs. DB-based history (which steps ran, duration, inputs/outputs). Flowable HistoryService may suffice. OTel can be added later if needed. |
| **MEDIUM** | R12 | Saga status feedback to caller | A7: "Saga status. Audit logging is best case." |
| **MEDIUM** | R13 | Low ramp-up / familiar to Java team | A8: "No ramp-up time planned" |
| **MEDIUM** | R14 | Crash recovery | A5: "Yes" |
| **HIGH** | R15 | Declarative workflow definitions (BPMN preferred) | A9: "Would be great." Clarification: BPMN is preferred. Visual modeling + embeddable editor (bpmn-js) in own UI is a strong plus. |
| **LOW** | R16 | Workflow versioning | A10: "Would be good" |
| **LOW** | R17 | Parallel steps | A2: "Not yet" |
| **LOW** | R18 | Branching / conditions | A2: "Maybe, no priority" |

### 2.2 Infrastructure Context

| Resource | Available | Notes |
|----------|-----------|-------|
| Kafka | Yes | Core infrastructure, already in use |
| PostgreSQL | Yes | Already in Docker Compose (port 5433) |
| Docker | Yes | Standard deployment model |
| Additional container | Acceptable | One extra container for framework server is OK |
| Additional database | Reluctant | Acceptable only if clear value justification |

---

## 3. Evaluation Matrix

### 3.1 MUST Requirements (K.O. Filter)

All remaining Tier 1 candidates pass the MUST filter:

| Requirement | Custom | Temporal | Conductor | Eventuate | Axon | Flowable | LittleHorse |
|-------------|--------|----------|-----------|-----------|------|----------|-------------|
| R1: Open Source | ✅ Own | ✅ MIT | ✅ Apache 2.0 | ✅ Apache 2.0 | ✅ Apache 2.0 | ✅ Apache 2.0 | ⚠️ AGPL-3 Server |
| R2: Self-hosted | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| R3: Java | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

> **Note on LittleHorse:** AGPL-3 is OSI-approved Open Source and listed as EUPL-1.2 compatible. However, AGPL-3 requires sharing source code of the server if it is accessed over a network. This needs legal review for the Civitas project context.

### 3.2 HIGH Priority Requirements

Rating: ✅ = fully met, ⚠️ = partially met, ❌ = not met

| Requirement | Custom | Temporal | Conductor | Eventuate | Axon | Flowable | LittleHorse |
|-------------|--------|----------|-----------|-----------|------|----------|-------------|
| R4: Saga compensation | ✅ | ✅ `Saga.java` | ⚠️ `failureWorkflow` workaround | ✅ DSL | ✅ `@Saga` | ✅ BPMN Compensation (auto-reverse) | ⚠️ Manual handlers (no auto-reverse) |
| R5: 3+ workflows | ⚠️ Becomes internal framework | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| R6: Idempotency | ⚠️ Manual | ✅ Durable Execution | ⚠️ Manual | ⚠️ Manual | ⚠️ Manual | ⚠️ Manual | ✅ Durable Execution |
| R7: Kafka integration | ✅ Native | ⚠️ Bridge needed | ✅ Events | ✅ Via CDC | ⚠️ Extension | ⚠️ Event Registry (needs Spring Boot) | ✅ Built on Kafka |
| R8: Minimal infra | ✅ None | ⚠️ +1 Go server (reuses PG) | ❌ +Server +PG +ES | ⚠️ +CDC service (reuses PG) | ✅ Embedded possible | ✅ Embedded possible | ✅ +1 server (no DB, uses Kafka) |
| R9: Easy to add adapters | ❌ ~500 LOC per workflow | ✅ ~150 LOC per workflow | ✅ JSON definition | ✅ DSL definition | ✅ Annotation-based | ✅ BPMN model | ✅ Code-defined |

### 3.3 MEDIUM Priority Requirements

| Requirement | Custom | Temporal | Conductor | Eventuate | Axon | Flowable | LittleHorse |
|-------------|--------|----------|-----------|-----------|------|----------|-------------|
| R10: Dashboard / UI | ❌ | ✅ Web UI (best) | ✅ UI | ❌ | ⚠️ Axon Server only | ✅ Flowable UI | ✅ Dashboard |
| R11: OpenTelemetry | ❌ | ✅ Native OTel | ⚠️ Metrics | ❌ | ⚠️ Micrometer | ❌ No native OTel (Actuator with Spring Boot) | ⚠️ Prometheus only, OTel undocumented |
| R12: Audit logging | ❌ | ✅ Full event history | ✅ Execution history | ⚠️ DB log | ⚠️ Event Store | ✅ Process history | ✅ Event history |
| R13: Low ramp-up | ✅ Team knows it | ⚠️ New programming model | ⚠️ JSON workflows | ✅ Java DSL, lightweight | ❌ CQRS/ES paradigm | ⚠️ BPMN modeling | ⚠️ New API |
| R14: Crash recovery | ⚠️ Kafka replay | ✅ Automatic | ✅ Automatic | ✅ Outbox pattern | ✅ Event replay | ✅ Automatic | ✅ Automatic (Kafka) |

### 3.4 LOW Priority Requirements

| Requirement | Custom | Temporal | Conductor | Eventuate | Axon | Flowable | LittleHorse |
|-------------|--------|----------|-----------|-----------|------|----------|-------------|
| R15: Declarative | ❌ | ❌ Code only | ✅ JSON | ❌ Java DSL | ❌ Annotations | ✅ BPMN visual | ❌ Code only |
| R16: Versioning | ❌ | ✅ Workflow versioning | ✅ | ❌ | ❌ | ✅ NiFi-style registry | ⚠️ |
| R17: Parallel steps | ❌ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| R18: Branching | ❌ | ✅ | ✅ | ❌ | ✅ | ✅ BPMN gateways | ✅ |

---

## 4. Scoring

### 4.1 Weighted Score (final — updated after deep-dive + clarified priorities)

Weights updated based on clarifications:
- **BPMN/Declarative** raised to HIGH (BPMN is preferred, bpmn-js embeddable in own UI)
- **Dashboard** lowered (team builds own UI, no external dashboards used)
- **Community** lowered (important but not decisive)
- **UI-embeddable visualization** added to Flowable's Dashboard score (bpmn-js Viewer)
- **Observability** re-evaluated: HistoryService-based audit (without OTel stack) may suffice
- **LittleHorse excluded** after risk assessment (AGPL-3, 380 stars, bus-factor, 1.0 just released)

> **Context:** Config-adapter is plain Java, but Spring Boot migration is acceptable (backend is already Spring Boot).
> BPMN is the preferred workflow notation. The team embeds tooling in their own UI (bpmn-js).
> Workflows connect DataSources with DataSinks — different adapter combinations via BPMN Gateways.

| Criterion (Weight) | Custom | Temporal | Flowable |
|---------------------|--------|----------|----------|
| **Saga compensation** (15%) | 10 | 10 | 10 |
| **Workflow scalability** (15%) | 4 | 10 | 10 |
| **Kafka integration** (12%) | 10 | 3 | 7 |
| **Infrastructure fit** (10%) | 10 | 7 | 8 |
| **BPMN / declarative + UI-embeddable** (12%) | 1 | 2 | 10 |
| **Audit / history** (10%) | 1 | 10 | 8 |
| **Ramp-up / team familiarity** (8%) | 10 | 5 | 6 |
| **Versioning + migration** (6%) | 1 | 7 | 10 |
| **Community / long-term support** (4%) | 3 | 10 | 8 |
| **License compatibility** (4%) | 10 | 10 | 10 |
| **Workflow status in own UI** (4%) | 1 | 3 | 10 |
| **Weighted Score** | **5.42** | **6.73** | **8.46** |

**Key scoring notes:**

| Criterion | Temporal score | Flowable score | Why different |
|-----------|---------------|----------------|---------------|
| BPMN / UI-embeddable | 2 (code-only, no visual modeling) | **10** (BPMN standard + bpmn-js Modeler/Viewer MIT) | Flowable's core strength |
| Kafka integration | 3 (bridge needed) | 7 (Event Registry with Spring Boot) | Both need adaptation, Flowable closer to native |
| Audit / history | 10 (native OTel + full event history) | 8 (HistoryService in DB, OTel addable later) | Temporal stronger if full OTel stack is needed |
| Workflow status in own UI | 3 (Temporal Web UI is separate, not embeddable) | **10** (bpmn-js Viewer shows live status in product UI) | Fundamental architectural difference |
| Ramp-up | 5 (new programming model) | 6 (BPMN learning curve, but well-documented + visual) | Similar, slight Flowable edge due to visual tooling |

### 4.2 Ranking

| Rank | Framework | Score | Profile |
|------|-----------|-------|---------|
| **1** | **Flowable** | **8.46** | BPMN visual modeling embeddable in own UI (bpmn-js Modeler + Viewer). Auto-reverse compensation. Embedded mode, reuses PostgreSQL. HistoryService for audit. Workflow versioning with migration. Apache 2.0. Needs Spring Boot for Kafka Event Registry (migration acceptable). |
| **2** | **Temporal** | **6.73** | Best if full OTel distributed tracing is required. Largest community, most mature. But: code-only (no BPMN), separate dashboard (not embeddable in product UI), Go server + Kafka bridge needed. |
| 3 | Custom (Option 0) | 5.42 | No ramp-up, no infra. But: no dashboard, no audit, no versioning, ~500 LOC per new workflow, no BPMN. |
| — | LittleHorse | *excluded* | AGPL-3 server license, 380 stars, bus-factor risk, 1.0 just released. Too risky for public-sector Open Source project. |
| — | Conductor | *excluded* | No native compensation, heaviest infrastructure. |
| — | Eventuate | *excluded* | No dashboard, no OTel, bus-factor 1. |
| — | Axon | *excluded* | Requires CQRS/ES buy-in, over-engineering risk. |

---

## 5. Infrastructure Comparison (Top 2 + Custom)

| Dimension | Custom | Temporal | Flowable |
|-----------|--------|----------|----------|
| **Extra containers** | 0 | +1 (Go server) | 0 (embedded) |
| **Uses existing PG** | N/A | ✅ Yes | ✅ Yes (own schema, ~36 tables) |
| **Uses existing Kafka** | ✅ Native | ⚠️ Bridge needed | ✅ Event Registry (with Spring Boot) |
| **New dependency** | None | Go binary | None (embedded in JVM) |
| **UI integration** | ❌ | ⚠️ Separate Web UI | ✅ bpmn-js embeddable in own UI |
| **Audit data** | ❌ Kafka logs only | ✅ Full event history + OTel | ✅ HistoryService in PG (OTel addable) |
| **Spring Boot required** | No | No | Yes (for Kafka + metrics) |

---

## 6. Key Tension

The PO answers reveal a tension that should be discussed:

> **"Should work out-of-the-box"** + **"No ramp-up time planned"**
> vs.
> **3+ workflows planned** + **Dashboard desired** + **Audit logging desired** + **New adapters will come**

The first set of constraints favors the custom solution (team knows it). The second set favors a framework (custom solution lacks dashboard, audit, and becomes an internal framework at 3+ workflows). This tension needs explicit resolution.

**Recommended question for PO/Architect:**
> Is the team willing to invest ramp-up time into a framework, given that it would provide dashboard, audit logging, and significantly lower per-workflow implementation cost — features that the custom solution cannot deliver without substantial additional effort?

---

## 7. BSI TR-03187 Open Source Risk Assessment

Based on the project's [Open Source Standards](https://docs.core.civitasconnect.digital/docs_v2/next/Architecture/Architecture_General/Open_Source_Standards/).

### 7.1 Risk Matrix (Flowable vs. Temporal)

| Criterion | Risk Weight | Flowable | Risk Level | Temporal | Risk Level |
|---|---|---|---|---|---|
| **License** | Very High | Apache 2.0 (OSI) | **Low** | MIT (OSI) | **Low** |
| **Stars** | Medium | 9,217 | **Low** (2k–10k) | 19,779 | **Very Low** (≥10k) |
| **Contributors** | Medium | 392 | **Very Low** (≥50) | 278 | **Very Low** (≥50) |
| **Org diversity** | Medium | 3-4 orgs, Flowable-dominated | **Low/Medium** | 3-4 orgs, Temporal-dominated | **Low/Medium** |
| **Governance transparency** | High | No CONTRIBUTING.md in repo, stale roadmap (2021) | **High** ⚠️ | CONTRIBUTING.md, proposals repo, CLA | **Low** |
| **Project age** | Medium | ~10 years (16 with Activiti) | **Very Low** (>5y) | ~7 years (10 with Cadence) | **Very Low** (>5y) |
| **Last commit** | High | 2026-04-20 | **Very Low** (<1 week) | 2026-04-23 | **Very Low** (<1 week) |
| **Issue closure time** | Medium | ~33 days | **Very High** ⚠️ | ~24 days | **High** |
| **Open issues %** | Low | 25.7% | **Medium** (21-35%) | 33.4% | **Medium** (21-35%) |
| **Code review** | Very High | Core committers merge without review | **High** ⚠️ | Multi-reviewer standard | **Low** |
| **CI pipeline** | High | Build + tests (multi-DB, multi-JDK), no linting | **Low** | Build + linting + tests + coverage + flaky detection | **Very Low** |
| **User documentation** | Very High | Comprehensive with examples (BPMN/CMMN/DMN) | **Very Low** | Comprehensive, multi-language SDKs | **Very Low** |
| **Developer docs** | High | Sparse, wiki-based | **Medium** | Architecture docs, testing guide, CONTRIBUTING.md | **Very Low** |

### 7.2 Code Review Deep-Dive

A sample of 16 merged Flowable PRs (spanning 2024-02 to 2026-04) was compared against 8 merged Temporal PRs:

| Metric | Flowable (n=16) | Temporal (n=8) |
|---|---|---|
| PRs with 0 formal reviews | **87.5%** (14/16) | 12.5% (1/8) |
| PRs with 1+ APPROVED review | 6.25% (1/16) | **87.5%** (7/8) |
| PRs with multiple reviewers | 0% | **50%** (4/8) |
| Avg. review comments per PR | 0.25 | **~22** |
| Self-merge without any review | 2 cases | 0 cases (self-merge only after approval) |
| External contributor PRs reviewed | 0 of 5 | N/A |

Notable examples:
- Flowable PR #4202 (tijsrademakers): 3,101 additions, 155 files — self-merged, 0 reviews
- Flowable PR #4191 (vzickner, external): 2,451 additions — 0 reviews, merged after 8 days
- Only 1 Flowable PR received a formal APPROVED review (Spring Boot upgrade, "LGTM and thank you")
- filiphr is the central gatekeeper (merged 11/16 PRs) but without formal review approval

**Caveat:** Flowable's core team (3-4 people) has worked together for 15+ years. Reviews may happen informally (offline, pair programming, Slack) without being visible on GitHub. The sample size is limited.

**Assessment:** This is a risk factor, not an exclusion criterion. We use Flowable as a dependency — our own code (delegates, BPMN models) follows our own review process. However, it means bugs in Flowable's engine may be less likely to be caught during development.

### 7.3 Company Background

#### Flowable Ltd. (Switzerland, Zurich)

| Aspect | Details |
|---|---|
| **Founded** | 2016 (fork from Activiti) |
| **Founders** | Joram Barrez & Tijs Rademakers — the original Activiti developers |
| **Employees** | ~30-80 |
| **Funding** | No known VC funding (bootstrapped / organic growth) |
| **Business model** | Open-core: OSS engine (Apache 2.0) + commercial platform (Flowable Work/Design/Control) |
| **Why they forked** | Alfresco steered Activiti too much toward their own ECM platform. The people who forked had written most of the Activiti code. |
| **Commits from employees** | ~85-95% — clearly company-driven |
| **Community** | Forum, moderate activity. Smaller, BPM-focused community. |
| **Rug-pull risk** | **Low** — no VC pressure, Apache 2.0 irrevocable, no precedent of anti-community moves |
| **Open-source reputation** | Consistent Apache 2.0. Considered more trustworthy than Camunda after Camunda's relicensing. The open-core boundary is the main tension. |

#### Temporal Technologies Inc. (USA, Seattle)

| Aspect | Details |
|---|---|
| **Founded** | 2019 (fork from Uber Cadence) |
| **Founders** | Maxim Fateev & Samar Abbas — Cadence creators at Uber, previously AWS Simple Workflow Service |
| **Employees** | ~200-400 |
| **Funding** | **$120M+ VC** (Sequoia Capital, Greenoaks). Valuation ~$1.5B. |
| **Business model** | Open-core: OSS server (MIT) + Temporal Cloud (SaaS) + Enterprise Support |
| **Why they forked** | Uber's priorities for Cadence were limited to internal needs. Wanted to build a general-purpose platform with proper community investment. |
| **Commits from employees** | ~90%+ — company-driven, but larger team |
| **Community** | Slack with 20,000+ members, dedicated "Replay" conference, very active |
| **Rug-pull risk** | **Moderate** — VC pressure is real (Redis, Elastic, HashiCorp all relicensed after VC funding). MIT is irrevocable and trivially forkable, which acts as a deterrent. Minor concern: Web UI licensing changes observed. |
| **Open-source reputation** | Active community engagement, transparent about strategy. VC dynamics bear watching. |

#### Key Observation

Both projects have an identical origin story: core engineers left a larger organization (Alfresco / Uber) because the open-source project was not community-friendly enough, and forked under a better license. Neither is a true community project — both are **company-driven open source with permissive licenses**. This is standard in the workflow engine market. No foundation-governed workflow framework exists (Apache KIE is the closest, but still in incubation).

### 7.4 BSI Assessment Summary

**Temporal scores better on BSI criteria**, particularly on the high-weight items:
- Code review (Very High weight): Temporal Low risk vs. Flowable High risk
- Governance (High weight): Temporal Low risk vs. Flowable High risk
- CI pipeline (High weight): Temporal Very Low risk vs. Flowable Low risk
- Developer docs (High weight): Temporal Very Low risk vs. Flowable Medium risk

**Flowable scores better on:**
- Contributors count (392 vs. 278)
- Project age (16 years with Activiti lineage)
- Open/closed issue ratio (25.7% vs. 33.4%)
- Rug-pull risk (no VC pressure)

**Impact on decision:** The BSI criteria highlight a real gap in Flowable's development practices (code review, governance transparency). This should be weighed against Flowable's functional advantages (BPMN, embedded mode, UI integration). Temporal is the more mature open-source project; Flowable is the better functional fit.

---

## 8. Recommendation

### 8.1 Primary Recommendation: Flowable

Based on the clarified priorities (BPMN preferred, own UI integration, no external dashboards, audit via HistoryService acceptable), **Flowable is the recommended framework**.

| Strength | Details |
|----------|---------|
| **BPMN in own UI** | bpmn-js Modeler for visual workflow editing, bpmn-js Viewer for live saga status — both embedded in the product UI |
| **Auto-reverse compensation** | BPMN standard, engine manages reverse order automatically |
| **Embedded, no extra server** | Runs in existing JVM, reuses PostgreSQL, zero extra containers |
| **Conditional adapter routing** | BPMN Gateways for different DataSource/DataSink combinations without code changes |
| **Versioning + migration** | Automatic versioning on re-deploy, live migration of running instances |
| **Apache 2.0** | Fully EUPL-1.2 compatible, no license cost, no legal risk |
| **15+ year maturity** | Activiti lineage, 9.2k stars, production-proven |

### 8.2 When to choose Temporal instead

Temporal remains the stronger choice **only if**:
- Full distributed tracing (OTel Collector + Jaeger/Tempo) is a hard requirement
- The team prefers code-only workflows over BPMN
- Visual workflow modeling and UI-embedded status are not valued

### 8.3 Open questions before final decision

1. **OTel scope** — Does Flowable's HistoryService suffice for audit, or is full distributed tracing required?
2. **Where does the orchestrator live?** — Config-adapter (needs Spring Boot migration) vs. Backend (already Spring Boot) vs. standalone Flowable?

### 8.4 Suggested next steps

1. Clarify the two open questions above
2. **PoC with Flowable** — implement the Dataset-Create saga as BPMN with JavaDelegates, test bpmn-js Viewer integration
3. If OTel turns out to be a hard requirement: parallel PoC with Temporal for comparison

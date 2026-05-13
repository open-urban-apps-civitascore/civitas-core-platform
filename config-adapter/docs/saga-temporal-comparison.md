# Vergleich: Custom Saga Orchestrator vs. Workflow-Frameworks

> **Zweck:** Input-Dokument fuer ADR-031 Update  
> **Stand:** 2026-04-08  
> **Scope:** config-adapter Modul, civitas-core-platform  
> **Issue:** [#1273](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform/-/work_items/1273)  
> **ADR:** [ADR-031 Orchestrated Saga for Multi-Adapter Provisioning](https://gitlab.com/civitas-connect/civitas-core/documentation/-/blob/main/docs_v2/Architecture/Architecture_Decisions/adrs_reviewed/adr031.md)

---

## Inhaltsverzeichnis

1. [Bestandsaufnahme: Custom Saga Orchestrator (Ist-Zustand)](#1-bestandsaufnahme-custom-saga-orchestrator-ist-zustand)
2. [Kandidaten-Uebersicht und Vorauswahl](#2-kandidaten-uebersicht-und-vorauswahl)
3. [Detailanalyse Tier-1-Kandidaten](#3-detailanalyse-tier-1-kandidaten)
4. [Detailanalyse Tier-2-Kandidaten](#4-detailanalyse-tier-2-kandidaten)
5. [Grosse Vergleichstabelle (alle Dimensionen)](#5-grosse-vergleichstabelle-alle-dimensionen)
6. [Bewertung und Empfehlung](#6-bewertung-und-empfehlung)
7. [Zusammenfassung der Zahlen](#7-zusammenfassung-der-zahlen)

---

## 1. Bestandsaufnahme: Custom Saga Orchestrator (Ist-Zustand)

### 1.1 Architektur-Ueberblick

Der Orchestrator folgt einem **Pure-Function State Machine + Kafka Event Sourcing** Pattern. Er wurde gemaess ADR-031 als dediziertes `config-adapter-orchestrator` Modul implementiert, um Adapter als einfache Command-Handler zu belassen.

```
Portal-Backend ──trigger──> Kafka ──> SagaTriggerConsumer
                                          |
                                    SagaEngine (facade)
                                          |
                              SagaStateMachine (pure logic)
                                    |            |
                              PersistState    SendCommand
                                    |            |
                          KafkaSagaStateStore   KafkaSagaActionDispatcher
                              (compacted topic)       |
                                              +-------+--------+
                                        ApisixHandler  FrostHandler  RedpandaHandler
                                              |
                                        SagaResultConsumer ──> SagaEngine
```

**ADR-031 Kontext:** Die Loesung wurde gewaehlt, weil Choreographie bei >=3 sequentiellen Steps zu impliziter Workflow-Kopplung, kaskadierender Compensation und verteilter Routing-Logik fuehrt. Ein Process Engine (z.B. Camunda) wurde als "heavyweight infrastructure dependency, overkill for the current number of workflows" zurueckgestellt.

### 1.2 Klassen-Inventar

#### Infrastruktur (Framework/Plumbing) — ~3.800 LOC

| Klasse | LOC | Modul | Verantwortung |
|--------|-----|-------|---------------|
| `SagaStateMachine` | 455 | orchestrator | Pure State-Transitions, kein I/O |
| `DatasetCommandBuilder` | 303 | orchestrator | Command-Payload-Konstruktion pro Step |
| `SagaEngine` | 216 | orchestrator | Facade: StateMachine + Persistence + Dispatch |
| `AbstractSagaCommandHandler` | 215 | api | Base-Class fuer Adapter-Handler |
| `RetryConsumerLoop` | 212 | api | Non-blocking Retry mit Virtual Threads |
| `KafkaSagaActionDispatcher` | 202 | orchestrator | Command-Dispatch via Kafka |
| `DatasetSagaOrchestrator` | 193 | orchestrator | Lifecycle-Management (init/start/stop) |
| `KafkaSagaCommandConsumer` | 187 | event-handler-kafka | Saga-Command Consumer |
| `SagaResultConsumer` | 157 | orchestrator | Step-Result Consumer |
| `KafkaSagaStateStore` | 154 | orchestrator | Kafka-backed State Persistence |
| `SagaStep` (record) | 153 | api | Immutable Step mit Status-Transitions |
| `SagaPayloadBuilder` | 143 | orchestrator | Payload-Generierung |
| `SagaDefinitions` | 139 | orchestrator | Registry der Saga-Definitionen |
| `KafkaSagaStateRecovery` | 137 | orchestrator | Crash-Recovery via Topic-Replay |
| `SagaTriggerConsumer` | 131 | orchestrator | Trigger-Event Consumer |
| `SagaContext` (record) | 125 | api | Immutable Saga-State |
| `KafkaPollingConsumer` | 99 | orchestrator | Basis-Consumer-Abstraktion |
| `SagaContextHelper` | 92 | api | Helper-Methoden |
| `SagaCommandMessage` (record) | 65 | api | Command-DTO |
| `SagaDefinition` (record) | 65 | orchestrator | Step-Liste + Lookup |
| `SagaCommandResult` (record) | 63 | api | Result-DTO |
| `InMemorySagaStateStore` | 63 | orchestrator | Test-Implementierung |
| `SagaStepDefinition` (record) | 63 | orchestrator | Step-Definition mit Predicate |
| `SagaAction` (sealed) | 61 | orchestrator | 7 Action-Typen (Command Pattern) |
| `SagaStatus` (enum) | 58 | api | 7 Saga-Zustaende |
| `SagaCommandHandler` (interface) | 55 | api | Handler-Contract |
| `BackoffCalculator` | 48 | api | Exponential Backoff |
| `SagaStepStatus` (enum) | 47 | api | 8 Step-Zustaende |
| `SagaStateStore` (interface) | 42 | orchestrator | Persistence-Contract |
| `SagaType` (enum) | 39 | api | CREATE/UPDATE/DELETE |
| `SagaTransitionResult` (record) | 28 | orchestrator | Context + Actions Tuple |
| `SagaActionDispatcher` (interface) | 28 | orchestrator | Dispatch-Contract |
| `SagaFailure` (record) | 23 | api | Failure-Details |

**Summe Infrastruktur: ~3.800 LOC** (34 Klassen, 2 Module)

#### Business-Logik (Adapter-Handler) — ~1.015 LOC

| Klasse | LOC | Modul |
|--------|-----|-------|
| `RedpandaSagaHandler` | 430 | redpanda |
| `ApisixSagaHandler` | 300 | apisix |
| `FrostSagaHandler` | 285 | frost |

#### Tests — ~9.900 LOC

| Bereich | LOC | Dateien |
|---------|-----|---------|
| StateMachine/Engine/Definition | ~2.800 | 4 Tests |
| Kafka-Integration (Consumer, Store, Dispatcher) | ~1.700 | 5 Tests |
| Integration Tests (Testcontainers) | ~800 | 2 ITs |
| Handler-Tests (Apisix, Frost, Redpanda) | ~3.900 | 8 Tests |
| API-Model-Tests | ~700 | 3 Tests |

### 1.3 State Persistence

| Aspekt | Implementierung |
|--------|----------------|
| **Mechanismus** | Kafka Compacted Topic (`saga-state`) |
| **Format** | JSON-serialisierter `SagaContext` Record |
| **Crash Recovery** | `KafkaSagaStateRecovery` replayed den gesamten State-Topic beim Start |
| **Tombstones** | Terminale Sagas werden per `null`-Value entfernt |
| **Caching** | In-Memory Map + Kafka Write-Through |
| **DB-Abhaengigkeit** | Keine — rein Kafka-basiert |

### 1.4 Compensation/Rollback

| Modus | Verhalten |
|-------|-----------|
| **Create/Update** | Reverse-Order Compensation bei Failure (letzter erfolgreicher Step zuerst) |
| **Delete** | Best-Effort Forward (kein Rollback, Fehler werden gesammelt) |
| **Compensation-Failure** | Status `COMPENSATION_FAILED` + `PublishManualIntervention` Action |
| **Data-Passing** | `compensationData` wird bei Success gespeichert und bei Compensation uebergeben |
| **Granularitaet** | Step-Level: jeder Handler implementiert eigene Compensation-Logik |

### 1.5 Fehlerbehandlung & Retries

| Aspekt | Implementierung |
|--------|----------------|
| **Exception-Hierarchie** | `RetryableAdapterException` (2xxx) vs. `FatalAdapterException` (1xxx) |
| **Retry-Mechanismus** | `RetryConsumerLoop` mit exponential Backoff, non-blocking via `consumer.poll()` |
| **Backoff** | `BackoffCalculator`: `initialMs * 2^(attempt-1)`, Cap bei `maxBackoffMs` |
| **Timeout** | HTTP-Level: connectTimeout=10s, readTimeout=30s. Saga-Level: `handleStepTimeout()` |
| **DLQ** | Fatal Exceptions -> sofort DLQ; Retryable nach Max-Retries -> DLQ |
| **Error Codes** | `AdapterErrorCode` Enum mit retryable-Flag, Log-Template, External-Message |

### 1.6 Observability

| Aspekt | Status |
|--------|--------|
| **Logging** | Strukturierte Logs mit OWASP-encoded Strings (`Encode.forJava()`) |
| **State Tracking** | `SagaContext` mit `status`, `currentStepId`, Step-Level Status, Timestamps |
| **Metrics-Export** | **Nicht vorhanden** — keine Micrometer/Prometheus Integration |
| **Dashboard** | **Nicht vorhanden** — kein UI fuer Saga-Zustand |
| **Audit** | Kafka-Topic enthaelt State-History (bis Compaction) |

### 1.7 Dependencies

Keine externen Saga/Workflow-Libraries. Rein custom-built mit:
- `org.apache.kafka:kafka-clients` (bereits vorhanden fuer Event-Handling)
- `io.cloudevents:cloudevents-*` (bereits vorhanden)
- Standard Java 21 (Records, Sealed Interfaces, Virtual Threads)

### 1.8 Bekannte Limitierungen

| # | Limitierung | Impact |
|---|-------------|--------|
| L1 | Kein Observability-Dashboard | Saga-Zustand nur via Kafka-Consumer-Tools oder Logs einsehbar |
| L2 | Kein Retry auf Orchestrator-Ebene | Crash waehrend Transition erfordert Full-Topic-Replay |
| L3 | Keine Workflow-Versionierung | Schema-Aenderungen erfordern manuelle Migration |
| L4 | Kein Query-API | Laufende Sagas nicht von aussen abfragbar |
| L5 | Keine Signals/Events | Externe Events (z.B. Admin-Genehmigung) koennen nicht injiziert werden |
| L6 | Timeout nur HTTP-Level | Kein Schedule-to-Start oder Workflow-Level Timeout |
| L7 | Kein Metrics-Export | Keine Prometheus/Micrometer Integration |
| L8 | Hoher Test-Overhead | ~4.500 LOC Infrastruktur-Tests die bei Framework-Einsatz entfallen |
| L9 | Skaliert nicht fuer neue Workflows | Jeder neue Workflow braucht SagaDefinition, CommandBuilder, Consumer |

---

## 2. Kandidaten-Uebersicht und Vorauswahl

Insgesamt wurden 12 Alternativen evaluiert. Die folgende Tabelle zeigt die Vorauswahl mit Begruendung.

### 2.1 Evaluierte Frameworks

| # | Framework | Architektur | Lizenz | Java SDK | Kafka-nativ | Saga-Support | Tier |
|---|-----------|-------------|--------|----------|-------------|--------------|------|
| 1 | **Temporal.io** | Server (Go) + Worker | MIT | Mature (v1.34) | Nein (Bridge) | Erstklassig (`Saga.java`) | **Tier 1** |
| 2 | **Netflix Conductor** | Server (Java) + Worker | Apache 2.0 | Mature (OSS) | Ja (Events) | Via Workflow-Branching | **Tier 1** |
| 3 | **Restate.dev** | Server (Rust) + Service | BUSL-1.1 / MIT SDKs | Aktiv (v2.4) | Ja (nativ) | Durable Execution | **Tier 1** |
| 4 | **Eventuate Tram Sagas** | Embedded Library | Apache 2.0 | Mature (v0.25) | Ja (via CDC) | Erstklassig (DSL) | **Tier 1** |
| 5 | **Axon Framework** | Embedded / opt. Server | Apache 2.0 | Mature (v4.11/5.1-RC) | Extension | Erstklassig (`@Saga`) | **Tier 1** |
| 6 | **Camunda 8 (Zeebe)** | Server (Java/Raft) | Camunda License v1 | Mature (v8.8) | Connectors | BPMN Compensation | **Tier 2** |
| 7 | **Kogito / Apache KIE** | Embedded / Server | Apache 2.0 | v10.1 (Incubating) | CloudEvents | BPMN Compensation | **Tier 2** |
| 8 | **Cadence (Uber)** | Server (Go) | MIT | v3.7 | Nein | Wie Temporal | **Ausgeschlossen** |
| 9 | **MicroProfile LRA** | Coordinator + Participants | EPL 2.0 | Spec-Level | Nein | HTTP-Callbacks | **Ausgeschlossen** |
| 10 | **Apache Camel Saga** | Embedded (Camel Route) | Apache 2.0 | Camel 4.18 | Ja | Duenne Schicht | **Ausgeschlossen** |
| 11 | **Spring State Machine** | Embedded Library | Apache 2.0 | v4.0 | Nein | Keine Saga-Semantik | **Ausgeschlossen** |
| 12 | **Apache Airflow** | Server (Python) | Apache 2.0 | Kein Java SDK | Nein | Keine Saga-Semantik | **Ausgeschlossen** |

### 2.2 Ausschlussbegruendungen

| Framework | Ausschlussgrund |
|-----------|-----------------|
| **Cadence** | Vorgaenger von Temporal; Community schrumpft seit 2020 Fork. Kein technischer Grund, Cadence statt Temporal zu waehlen. |
| **MicroProfile LRA** | Rein HTTP/REST-basiert (JAX-RS Annotations, HTTP-Callbacks). Kein Kafka-Support. Choreographie-Ansatz (Participants self-compensate) statt Orchestrierung. Nur 1 Implementierung (Narayana). |
| **Apache Camel Saga** | Saga EIP ist duenne Schicht. In-Memory Coordinator nicht produktionsreif, LRA-Coordinator bringt MicroProfile-LRA-Probleme zurueck. Erfordert Adoption des gesamten Camel-Routing-DSL. |
| **Spring State Machine** | Generische State Machine ohne Saga-Semantik. Bietet weniger als die bestehende Custom-Implementierung. `SagaStateMachine` ist bereits ein sauberer, zweckgebauter Automat. |
| **Apache Airflow** | Python-only Batch-Scheduler. Kein Java SDK. Kein Real-Time Event Processing. Grundlegend falsches Tool fuer Microservice-Sagas. |

---

## 3. Detailanalyse Tier-1-Kandidaten

### 3.1 Temporal.io

**Ueberblick:** Durable Execution Platform. Workflows werden als normaler Code geschrieben, der Framework garantiert exactly-once Ausfuehrung durch Event-Sourced Replay.

| Aspekt | Details |
|--------|---------|
| **Lizenz** | MIT (Server + SDKs) |
| **Version** | Server v1.30.3 (April 2026), Java SDK v1.34.0 |
| **Community** | ~19.500 GitHub Stars, 200+ Contributors, $300M+ Funding |
| **Architektur** | Temporal Server (Go Binary) + PostgreSQL/MySQL/Cassandra + Worker (Java) |
| **Min. Infrastruktur** | 1x Temporal Server + 1x PostgreSQL + 1x Worker Process |

**Saga-Compensation:**
```java
// Temporal Saga Pattern - nativer Support via Saga-Utility
@WorkflowMethod
public void provisionDataset(DatasetRequest request) {
    Saga saga = new Saga(new Saga.Options.Builder().build());
    try {
        String projectId = activities.createFrostProject(request);
        saga.addCompensation(activities::deleteFrostProject, projectId);

        String routeId = activities.createApisixRoute(request, projectId);
        saga.addCompensation(activities::deleteApisixRoute, routeId);

        activities.deployRedpandaPipelines(request, projectId, routeId);
        saga.addCompensation(activities::undeployRedpandaPipelines, request);
    } catch (ActivityFailure e) {
        saga.compensate(); // Reverse-Order Compensation automatisch
        throw e;
    }
}
```

**Retry-Konfiguration:**
```java
ActivityOptions options = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(30))
    .setScheduleToStartTimeout(Duration.ofMinutes(5))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(3)
        .setInitialInterval(Duration.ofSeconds(1))
        .setBackoffCoefficient(2.0)
        .setMaximumInterval(Duration.ofSeconds(30))
        .setDoNotRetry(FatalAdapterException.class.getName())
        .build())
    .build();
```

**Staerken:**
- Gold-Standard fuer Durable Execution; exactly-once Semantik
- `TestWorkflowEnvironment` mit Time-Skipping fuer deterministische Tests
- Web UI mit Workflow-Suche, Event History, Stack Traces, Pending Activities
- Prometheus Metrics + OpenTelemetry Tracing out-of-the-box
- Query API (synchron laufende Workflows abfragen) + Signal API (externe Events injizieren)
- Workflow Versioning via `Workflow.getVersion()` / Patching API
- Workflow-ID-Uniqueness ersetzt custom `existsForDataset()` Concurrency-Check

**Schwaechen:**
- **Kein nativer Kafka-Support**: Temporal nutzt eigene interne Task Queues (gRPC). Kafka-Trigger erfordern einen Bridge-Worker der Kafka konsumiert und Temporal Workflows startet. Die bestehende Kafka-zentrische Architektur muss umgebaut werden.
- **Operationaler Overhead**: Temporal Server Cluster + eigene DB. Fuer HA: 3+ Server-Instanzen. Alternative: Temporal Cloud (SaaS, ~$200/Mo fuer kleine Workloads).
- **Impedance Mismatch**: Temporal ersetzt Kafka als Kommunikationsmedium zwischen Orchestrator und Adaptern. Adapter muessten von Kafka-Consumer auf Temporal Worker umgestellt werden, oder ein Kafka-Bridge-Pattern wird verwendet.

**Kafka-Integration:**
```
Kafka Topic (Trigger) --> Bridge Worker --> Temporal Workflow starten
                                                |
                                          Activity: createFrostProject()
                                          Activity: createApisixRoute()
                                          Activity: deployRedpanda()
                                                |
                                          Kafka Topic (Result) <-- Result Publisher
```

**Loest Limitierungen:** L1 (Web UI), L2 (Auto-Replay), L3 (Versioning), L4 (Query API), L5 (Signals), L6 (4 Timeout-Typen), L7 (Prometheus), L8 (TestWorkflowEnv), L9 (Workflow = Code)

---

### 3.2 Netflix Conductor (conductor-oss/conductor)

**Ueberblick:** Allgemeiner Workflow-Orchestrator, urspruenglich von Netflix entwickelt. Seit Dez 2023 von Netflix archiviert, Community-Fork als `conductor-oss` aktiv. Orkes bietet kommerzielle SaaS-Version.

| Aspekt | Details |
|--------|---------|
| **Lizenz** | Apache 2.0 (conductor-oss) |
| **Version** | conductor-oss aktiv gepflegt; Orkes Client v4 |
| **Community** | ~17.300 GitHub Stars (conductor-oss/conductor), Produktion bei Netflix, Tesla, LinkedIn |
| **Architektur** | Conductor Server (Java) + Persistence (PostgreSQL/MySQL/Redis) + Elasticsearch + Worker |
| **Min. Infrastruktur** | 1x Conductor Server + 1x PostgreSQL + 1x Elasticsearch + Worker Processes |

**Saga-Compensation:**
```json
// Conductor Workflow Definition (JSON)
{
  "name": "dataset_provisioning_saga",
  "tasks": [
    {
      "name": "create_frost_project",
      "type": "SIMPLE",
      "taskReferenceName": "frost_step"
    },
    {
      "name": "create_apisix_route",
      "type": "SIMPLE",
      "taskReferenceName": "apisix_step",
      "inputParameters": {
        "projectId": "${frost_step.output.projectId}"
      }
    }
  ],
  "failureWorkflow": "dataset_provisioning_compensation"
}
// Compensation als separater Workflow definiert
```
Conductor hat **keine native Saga-Compensation**. Compensation wird als `failureWorkflow` modelliert — ein separater Workflow der manuell die Reverse-Order-Logik implementiert. Dies ist weniger elegant als Temporal's `Saga.java` oder die bestehende Custom-Loesung.

**Retry-Konfiguration:**
```json
{
  "name": "create_frost_project",
  "retryCount": 3,
  "retryLogic": "EXPONENTIAL_BACKOFF",
  "retryDelaySeconds": 1,
  "timeoutSeconds": 30,
  "responseTimeoutSeconds": 15
}
```

**Staerken:**
- Reifes Produkt, Produktion bei grossen Unternehmen
- Web UI fuer Workflow-Visualisierung, Execution History, Task Search, Replay
- Workflow-Definitionen als JSON (deklarativ, versionierbar in Git)
- **Kafka-Integration besser als Temporal**: Conductor kann Kafka Events als Trigger konsumieren und Events publishen. `Event`-Task-Typ fuer Kafka Publish/Subscribe.
- Task-Worker-Modell: Worker pollen den Server nach Aufgaben — aehnlich dem bestehenden Kafka-Consumer-Pattern
- HTTP/gRPC API fuer Workflow-Management, Start, Pause, Resume, Retry
- Sub-Workflows, Dynamic Forks, Conditional Branching

**Schwaechen:**
- **Keine native Saga-Compensation**: `failureWorkflow` ist ein Workaround, kein erstklassiges Saga-Pattern. Reverse-Order-Compensation muss manuell im Compensation-Workflow implementiert werden. Die bestehende Custom-Loesung hat hier bessere Semantik.
- **Hoher operationaler Overhead**: Conductor Server + PostgreSQL + Elasticsearch (fuer Indexing/Search). Mehr Infrastruktur als Temporal.
- **JSON-basierte Workflow-Definitionen**: Kein Workflow-as-Code. Logik in JSON ist schwerer zu testen und zu debuggen als Java-Code.
- **Community-Situation unsicher**: Netflix hat das Original-Repo Dez 2023 archiviert. conductor-oss ist der Community-Fork, aber die Governance ist weniger klar als bei Temporal (VC-funded company).
- **Worker sind zustandslos**: Kein Equivalent zu Temporal's Workflow-State im Worker. Zustand liegt vollstaendig im Server.

**Kafka-Integration:**
```
Kafka Topic (Trigger) --> Conductor Event Handler --> Workflow starten
                                                          |
                                                    Task: createFrostProject
                                                    Task: createApisixRoute
                                                    Task: deployRedpanda
                                                          |
                                                    Event Task --> Kafka Topic (Result)
```

**Loest Limitierungen:** L1 (Web UI), L2 (Server-managed State), L4 (REST API), L6 (Task Timeouts), L7 (Metrics), L9 (JSON Definitionen wiederverwendbar). **Loest NICHT:** L3 (kein Workflow-Versioning wie Temporal), L5 (kein Signal-Equivalent).

---

### 3.3 Restate.dev

**Ueberblick:** Moderne Durable Execution Platform. Leichtgewichtiger Server (Single Rust Binary), kein externes DB noetig. Fokus auf Developer Experience und operationale Einfachheit. Gegruendet von Stephan Ewen (Mitgruender Apache Flink).

| Aspekt | Details |
|--------|---------|
| **Lizenz** | BUSL-1.1 (Server) / MIT (SDKs). BUSL erlaubt interne Produktionsnutzung, verbietet nur Restate-as-a-Service Angebot. Konvertiert nach 4 Jahren zu Open Source. |
| **Version** | Server v1.3+, Java SDK v2.4.1 (Feb 2026) |
| **Community** | ~3.700 GitHub Stars, wachsend. SDKs fuer Java, Kotlin, TS, Python, Go, Rust. |
| **Architektur** | Restate Server (Single Rust Binary, ~50MB) + Service Handler (Java HTTP) |
| **Min. Infrastruktur** | 1x Restate Binary (kein externes DB, nutzt RocksDB intern) |

**Saga-Compensation:**
```java
// Restate Durable Execution - Saga via journaled execution
@Handler
public void provisionDataset(ObjectContext ctx, DatasetRequest request) {
    List<Runnable> compensations = new ArrayList<>();
    try {
        String projectId = ctx.run("createFrost",
            () -> frostClient.createProject(request));
        compensations.add(() -> ctx.run("deleteFrost",
            () -> frostClient.deleteProject(projectId)));

        String routeId = ctx.run("createApisix",
            () -> apisixClient.createRoute(request, projectId));
        compensations.add(() -> ctx.run("deleteApisix",
            () -> apisixClient.deleteRoute(routeId)));

        ctx.run("deployRedpanda",
            () -> redpandaClient.deployPipelines(request, projectId));
        compensations.add(() -> ctx.run("undeployRedpanda",
            () -> redpandaClient.undeployPipelines(request)));
    } catch (TerminalException e) {
        Collections.reverse(compensations);
        compensations.forEach(Runnable::run);
        throw e;
    }
}
```

**Staerken:**
- **Geringster operationaler Overhead aller Server-basierten Loesungen**: Single Binary, kein externes DB, kein Elasticsearch. Deployment so einfach wie ein weiterer Container.
- **Native Kafka-Integration**: Restate kann direkt Kafka Topics subscriben und fuer jede Message einen Handler aufrufen. Dies ist die beste Kafka-Story aller evaluierten Frameworks.
  ```
  // Restate subscribed direkt auf Kafka Topics
  restate deployments register http://my-service:9080
  restate subscriptions add --source kafka://my-cluster/saga-triggers \
                            --sink service/DatasetSaga/provisionDataset
  ```
- **Durable Execution**: Jeder `ctx.run()` Aufruf wird journaled. Bei Crash: automatisches Replay ab letztem Checkpoint.
- **Virtual Objects**: Zustandsbehaftete Entities mit Single-Writer-Semantik — ersetzt `existsForDataset()` Check.
- **Latenz**: Sub-Millisekunde Overhead fuer journaled Calls (im Vergleich zu Temporal's gRPC-Roundtrip pro Activity).

**Schwaechen:**
- **Junges Projekt**: Seit 2023, weniger Battle-Tested als Temporal (2020) oder Conductor (2016). Weniger Referenzkunden.
- **BUSL-1.1 Lizenz**: Nicht vollstaendig Open Source. Zwar fuer interne Nutzung uneingeschraenkt, aber das EUPL-1.2-lizenzierte Civitas-Projekt koennte philosophische Vorbehalte haben. SDKs sind MIT.
- **Kleinere Community**: ~3.700 Stars vs. ~19.500 (Temporal). Weniger Stack Overflow Antworten, weniger Blog Posts, weniger Consultants.
- **Kein dediziertes Web UI**: Restate CLI + basic introspection. Kein Equivalent zu Temporal Web UI oder Conductor UI fuer Workflow-Visualisierung.
- **Testing**: `TestRestateRuntime` vorhanden, aber weniger ausgereift als Temporal's `TestWorkflowEnvironment`.

**Kafka-Integration:**
```
Kafka Topic (Trigger) --> Restate (native Subscription) --> Handler
                                                              |
                                                        ctx.run(): createFrostProject
                                                        ctx.run(): createApisixRoute
                                                        ctx.run(): deployRedpanda
                                                              |
                                                        ctx.run(): Kafka Publish (Result)
```

**Loest Limitierungen:** L1 (teilweise, CLI), L2 (Auto-Replay), L4 (Virtual Object State), L5 (Signals via Handlers), L6 (Timeouts), L8 (TestRestateRuntime), L9 (Handler = Code)

---

### 3.4 Eventuate Tram Sagas

**Ueberblick:** Leichtgewichtiges Saga-Framework von Chris Richardson (Autor "Microservices Patterns"). Embedded Library, kein separater Server. Nutzt Transactional Outbox Pattern mit CDC zu Kafka.

| Aspekt | Details |
|--------|---------|
| **Lizenz** | Apache 2.0 |
| **Version** | v0.25.0.RELEASE (aktuell) |
| **Community** | ~870 GitHub Stars. Kleiner aber fokussierter Maintainer (Chris Richardson). |
| **Architektur** | Embedded in Spring Boot App + JDBC DB + CDC Service (Eventuate/Debezium) |
| **Min. Infrastruktur** | 1x PostgreSQL (fuer Outbox) + 1x CDC Service (Debezium) + bestehendes Kafka |

**Saga-Compensation:**
```java
// Eventuate Tram Saga DSL - sehr nah am bestehenden SagaDefinition Pattern
public class DatasetProvisioningSaga implements SimpleSaga<DatasetSagaData> {
    private SagaDefinition<DatasetSagaData> sagaDefinition =
        step()
            .invokeParticipant(this::createFrostProject)
            .withCompensation(this::deleteFrostProject)
        .step()
            .invokeParticipant(this::createApisixRoute)
            .withCompensation(this::deleteApisixRoute)
        .step()
            .invokeParticipant(this::deployRedpandaPipelines)
            .withCompensation(this::undeployRedpandaPipelines)
        .build();
}
```

**Staerken:**
- **Hoechste architektonische Naehe zum Ist-Zustand**: Die `SagaDefinition` DSL mit `step().invokeParticipant().withCompensation()` entspricht fast 1:1 dem bestehenden `SagaDefinition`/`SagaStepDefinition` Modell. Geringster konzeptioneller Migrationsaufwand.
- **Embedded Library**: Kein separater Server, laeuft in der bestehenden JVM.
- **Kafka-nativ**: Commands gehen ueber Kafka Topics an Participant-Services. Replies kommen ueber Kafka zurueck. Sehr aehnlich dem bestehenden Pattern.
- **Saga-Test-Support**: `SagaUnitTestSupport` fuer In-Memory-Tests ohne Kafka. Verifiziert gesendete Commands und simuliert Replies.
- **Apache 2.0**: Vollstaendig kompatibel mit EUPL-1.2.

**Schwaechen:**
- **Erfordert JDBC-Datenbank**: Transactional Outbox Pattern braucht eine relationale DB fuer Saga-State und Message-Outbox. Die aktuelle Loesung kommt ohne DB aus (nur Kafka). Dies ist ein **architektonischer Rueckschritt** gegenueber dem Ist-Zustand.
- **Erfordert CDC-Service**: Debezium oder Eventuate CDC muss DB-Changes nach Kafka publishen. Zusaetzliche Infrastruktur-Komponente.
- **Kleine Community**: ~870 Stars. Im Wesentlichen ein Ein-Personen-Projekt (Chris Richardson). Bus-Faktor = 1.
- **Kein Dashboard/UI**: Keine Visualisierung, kein Query-API.
- **Kein Metrics-Export**: Keine eingebaute Observability.
- **Begrenzte Features**: Kein Workflow-Versioning, keine Signals, keine Timeouts auf Saga-Ebene.

**Kafka-Integration:**
```
Kafka Topic (Trigger) --> SagaManager --> Saga Instance
                                              |
                                        Command Channel (Kafka) --> Frost Participant
                                        Reply Channel (Kafka)   <-- Frost Participant
                                              |
                                        Command Channel (Kafka) --> Apisix Participant
                                        ...
                                              |
                                        Kafka Topic (Result)
```

**Loest Limitierungen:** L8 (SagaUnitTestSupport), L9 (DSL fuer neue Sagas). **Loest NICHT:** L1, L3, L4, L5, L6, L7. **Verschlechtert:** Fuegt DB-Dependency hinzu die aktuell nicht existiert.

---

### 3.5 Axon Framework

**Ueberblick:** CQRS/Event-Sourcing Framework mit erstklassigem Saga-Support. Java-nativ, seit 2010 aktiv. Kann embedded (nur Spring Boot + DB) oder mit Axon Server (eigenem Event Store + Message Router) betrieben werden.

| Aspekt | Details |
|--------|---------|
| **Lizenz** | Apache 2.0 (Framework). Axon Server Standard: frei. Axon Server Enterprise: kommerziell. |
| **Version** | Framework v4.11.x (stable), v5.1.0-RC2 (pre-release) |
| **Community** | ~3.600 GitHub Stars, aktive Entwicklung durch AxonIQ seit 2010 |
| **Architektur** | Embedded Library + optional Axon Server fuer Event Routing |
| **Min. Infrastruktur** | Embedded: nur PostgreSQL. Mit Axon Server: +1 Service. |

**Saga-Compensation:**
```java
// Axon Saga - Event-getrieben mit @SagaEventHandler
@Saga
public class DatasetProvisioningSaga {
    private String datasetId;
    private String projectId;

    @StartSaga
    @SagaEventHandler(associationProperty = "datasetId")
    public void on(DatasetProvisioningStarted event) {
        this.datasetId = event.getDatasetId();
        commandGateway.send(new CreateFrostProjectCommand(datasetId));
    }

    @SagaEventHandler(associationProperty = "datasetId")
    public void on(FrostProjectCreated event) {
        this.projectId = event.getProjectId();
        commandGateway.send(new CreateApisixRouteCommand(datasetId, projectId));
    }

    @SagaEventHandler(associationProperty = "datasetId")
    public void on(ApisixRouteCreationFailed event) {
        // Compensation: bereits erstelltes FROST Projekt loeschen
        commandGateway.send(new DeleteFrostProjectCommand(projectId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "datasetId")
    public void on(DatasetProvisioningCompleted event) {
        // Saga completed
    }
}
```

**Staerken:**
- **Beste Saga-Testbarkeit**: `SagaTestFixture` mit Given-When-Then DSL. Verifiziert dispatched Commands, published Events, Deadlines. Keine Infrastruktur noetig.
  ```java
  fixture.givenAggregate(datasetId).published(new DatasetProvisioningStarted(...))
         .whenPublishingA(new FrostProjectCreated(...))
         .expectDispatchedCommands(new CreateApisixRouteCommand(...));
  ```
- **Embedded**: Kein separater Server fuer Basis-Nutzung. Framework laeuft in der bestehenden JVM.
- **Deadlines**: `DeadlineManager` fuer Step-Level Timeouts — nativer als HTTP-Timeouts.
- **Saga Lifecycle Management**: Framework managed Saga-Instanzen (Create, Associate, End). Kein manuelles State-Management.
- **Kafka Extension**: Events koennen ueber Kafka verteilt werden.

**Schwaechen:**
- **Erfordert CQRS/Event-Sourcing Buy-In**: Axon ist opinionated. Adoption bedeutet Command Bus, Event Bus, Aggregate Pattern. Dies wuerde die gesamte Config-Adapter-Architektur umstrukturieren — weit ueber die Saga hinaus.
- **Kafka ist Sekundaerbuerger**: Axon's primaeres Event-Modell ist der eigene Event Store (JPA oder Axon Server). Kafka ist eine Extension fuer Event-Distribution, nicht das primaere Medium.
- **Axon Server Enterprise ist kommerziell**: Clustering, Multi-Context, Dead-Letter-Queue erfordern die Enterprise-Lizenz. Die Standard-Edition (frei) hat Einschraenkungen.
- **Saga-Pattern ist Event-getrieben (Choreographie-aehnlich)**: Obwohl Axon einen zentralen Saga-Manager hat, reagiert die Saga auf Events — nicht auf Command-Responses. Dies ist ein anderes Modell als der bestehende Request-Reply-basierte Orchestrator.
- **Grosser Footprint**: Axon bringt eigenes Command/Event/Query Bus mit. Fuer nur Saga-Funktionalitaet ist das signifikanter Overhead.

**Loest Limitierungen:** L5 (Deadline-Events), L6 (Deadline-Timeouts), L8 (SagaTestFixture), L9 (deklarative Saga). **Loest NICHT:** L1 (kein Dashboard ohne Axon Server), L3, L4, L7.

---

### 3.6 Flowable Engine

**Ueberblick:** Mature BPMN 2.0 Engine, Fork von Activiti (2016). Java-nativ, einbettbar oder als Server. Gilt als die Open-Source-Alternative zu Camunda nach dessen Lizenzwechsel. Native Compensation Events fuer Saga-Pattern.

| Aspekt | Details |
|--------|---------|
| **Lizenz** | Apache 2.0 (Engine, BPMN, CMMN, DMN, Event Registry). Kommerzielle Add-ons (Design, Control, Work) separat. |
| **Version** | 8.0.0 (Feb 2025), Spring Boot 4 / Spring Framework 7 |
| **Community** | ~9.200 GitHub Stars, 2.800 Forks, 15+ Jahre Lineage (Activiti → Flowable) |
| **Architektur** | Embedded Library (in-process) ODER Standalone Server. Benoetigt relationale DB. |
| **Min. Infrastruktur** | PostgreSQL (shared moeglich) + embedded Engine in bestehender JVM. Kein extra Container noetig. |

**Saga-Compensation:**
```xml
<!-- BPMN 2.0 Compensation: automatische Reverse-Order -->
<serviceTask id="createFrost" flowable:class="de.civitascore.flowable.CreateFrostDelegate"/>
<boundaryEvent id="compensateFrost" attachedToRef="createFrost">
  <compensateEventDefinition/>
</boundaryEvent>
<serviceTask id="undoFrost" isForCompensation="true"
             flowable:class="de.civitascore.flowable.DeleteFrostDelegate"/>
<association sourceRef="compensateFrost" targetRef="undoFrost"/>

<!-- Bei Fehler: Compensation aller abgeschlossenen Steps in Reverse-Order -->
<intermediateThrowEvent id="compensateAll">
  <compensateEventDefinition/>  <!-- Engine kompensiert automatisch in LIFO-Reihenfolge -->
</intermediateThrowEvent>
```

**Java Service Task (JavaDelegate):**
```java
public class CreateFrostDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        String projectName = (String) execution.getVariable("projectName");
        FrostProject project = frostClient.createProject(projectName);
        execution.setVariable("frostProjectId", project.getId());
    }
}
```

**Retry-Konfiguration:**
```xml
<!-- ISO 8601 Retry: 3x alle 30 Sekunden (kein exponential backoff nativ) -->
<serviceTask id="createFrost" flowable:async="true"
             flowable:class="de.civitascore.flowable.CreateFrostDelegate">
  <extensionElements>
    <flowable:failedJobRetryTimeCycle>R3/PT30S</flowable:failedJobRetryTimeCycle>
  </extensionElements>
</serviceTask>
```

**Staerken:**
- **Apache 2.0** — vollstaendig kompatibel mit EUPL-1.2, keine Lizenzkosten
- **Embedded Mode** — laeuft in der bestehenden JVM, kein extra Container noetig
- **Automatische Reverse-Order Compensation** — BPMN-Standard, Engine managed die Reihenfolge
- **Visuelles BPMN-Modelling** — deklarative Workflow-Definition, self-documenting
- **bpmn-js Integration** (MIT, ~9.2k Stars) — BPMN-Editor (bpmn-js/Modeler) und Live-Status-Viewer (bpmn-js/Viewer) direkt in die eigene UI einbettbar. Viewer zeigt laufende Saga-Schritte visuell an (aktiv/abgeschlossen/fehlgeschlagen) mit Daten aus Flowables RuntimeService/HistoryService. Kein separates Dashboard noetig — Workflow-Visualisierung wird Teil der Produkt-UI.
- **Workflow-Versionierung** — automatische Versionierung bei Re-Deployment, multiple Versionen gleichzeitig ausfuehrbar
- **Process Instance Migration** — laufende Instanzen auf neue Version migrierbar
- **JUnit 5 Test-Framework** — `@FlowableTest` mit In-Memory H2, `@Deployment` Annotation
- **Reuses existing PostgreSQL** — eigenes Schema im bestehenden PG-Server, ~36 Tabellen mit `ACT_`-Prefix
- **HistoryService als Audit** — `ACT_HI_*` Tabellen speichern Start/Ende, Duration, Variablen pro Activity. Abfragbar via Java API. Deckt Audit-Anforderungen ohne zusaetzliche OTel-Infrastruktur ab. OTel kann bei Bedarf nachgeruestet werden (Java Agent oder manuell in Delegates).

**Schwaechen:**
- **Kafka Event Registry benoetigt Spring Boot.** Das config-adapter Modul ist aktuell plain Java. Fuer Flowables native Kafka-Integration (Event Registry mit Inbound/Outbound Channels) ist `spring-kafka` Auto-Configuration Voraussetzung. **Migration zu Spring Boot ist akzeptabel** (Backend ist bereits Spring Boot). Alternativ Kafka manuell via Java API bridgen:
  ```java
  // Ohne Spring Boot: Bestehender KafkaEventHandler startet Flowable Prozesse
  runtimeService.startProcessInstanceByKey("datasetSaga",
      Map.of("datasetId", event.getDatasetId()));
  ```
- **Kein natives CloudEvents-Format** — Event Registry nutzt eigenes JSON-Format. CloudEvents-Deserialisierung muss custom implementiert werden.
- **Kein natives OpenTelemetry** — kein `flowable-opentelemetry` Modul. OTel nur via Java Agent Auto-Instrumentation oder manuell in JavaDelegates.
- **Kein exponential Backoff** — `failedJobRetryTimeCycle` nur fixe Intervalle (R3/PT30S). Exponential Backoff erfordert Custom `FailedJobCommandFactory`.
- **Actuator/Prometheus nur mit Spring Boot** — ohne Spring Boot kein Metrics-Endpoint. Bei Spring-Boot-Migration verfuegbar.
- **BPMN-Overhead** — jeder Workflow braucht BPMN-XML oder programmatisches `BpmnModel`. Verbosity hoeher als Java-Code-basierte Frameworks.
- **~36 Datenbanktabellen** — `ACT_RE_*`, `ACT_RU_*`, `ACT_HI_*`, `ACT_GE_*`, `ACT_ID_*`. History-Tabellen wachsen kontinuierlich und brauchen Cleanup.
- **Flowables eigene Open-Source UI als "poor tooling"** beschrieben — Community-Feedback: "good API, poor modeler tooling". Jedoch ist Flowables eigene UI fuer diesen Use Case irrelevant: bpmn-js (MIT) als eingebetteter Modeler/Viewer in der eigenen Produkt-UI ist der empfohlene Ansatz.

**Kafka-Integration (ohne Spring Boot):**
```
Kafka Topic (Trigger) --> Bestehender KafkaEventHandler
                              |
                        runtimeService.startProcessInstanceByKey()
                              |
                        BPMN Process: ServiceTask → ServiceTask → ServiceTask
                              |
                        JavaDelegate publiziert Result via KafkaProducer
```

**Loest Limitierungen:** L1 (bpmn-js Viewer in eigener UI als Live-Status-Dashboard), L3 (Workflow-Versionierung + Migration), L8 (JUnit 5 Test-Framework), L9 (BPMN-Definitionen wiederverwendbar). **Teilweise:** L4 (HistoryService als Query-API), L7 (nur mit Spring Boot Actuator). **Loest NICHT:** L5 (keine nativen Signals, aber BPMN Message Events), L2 (DB-basiert statt Kafka-Replay, also anders geloest).

---

### 3.7 LittleHorse

**Ueberblick:** Kafka-native Workflow Engine. Der Server ist eine Kafka-Streams-Applikation — Kafka IST der Persistence Layer. Kein externes DB noetig. Java-nativ, seit Juli 2023 in Entwicklung, 1.0.0 GA seit Maerz 2026.

| Aspekt | Details |
|--------|---------|
| **Lizenz** | **AGPL-3.0** (Server + Dashboard), **Apache 2.0** (SDKs, CLI, Test-Utils) |
| **Version** | 1.0.0 GA (17. Maerz 2026) |
| **Community** | ~380 GitHub Stars, 30 Contributors, 1-10 Mitarbeiter (LittleHorse Enterprises LLC) |
| **Architektur** | LittleHorse Server (Java, Kafka Streams) + Task Workers (Java SDK) |
| **Min. Infrastruktur** | 1x LittleHorse Server + bestehender Kafka Cluster. Keine DB noetig. |

**Saga-Compensation:**
```java
// LittleHorse Saga — manuelle Compensation-Handler pro Step
public void provisionDataset(WorkflowThread wf) {
    var datasetId = wf.addVariable("dataset-id", VariableType.STR).required();
    var config = wf.addVariable("config", VariableType.JSON_OBJ).required();
    var frostProjectId = wf.addVariable("frost-project-id", VariableType.STR);
    var apisixRouteId = wf.addVariable("apisix-route-id", VariableType.STR);

    // Step 1: Create FROST Project
    NodeOutput frostResult = wf.execute("create-frost-project", datasetId, config);
    wf.mutate(frostProjectId, VariableMutationType.ASSIGN, frostResult);

    // Step 2: Create APISIX Route — bei Fehler: Step 1 kompensieren
    NodeOutput apisixResult = wf.execute("create-apisix-route", datasetId, config);
    wf.handleException(apisixResult, "route-creation-failed", handler -> {
        handler.execute("delete-frost-project", frostProjectId);
        handler.fail("provisioning-failed", "APISIX route creation failed");
    });
    wf.mutate(apisixRouteId, VariableMutationType.ASSIGN, apisixResult);

    // Step 3: Deploy NiFi Pipelines — bei Fehler: Steps 2+1 kompensieren
    NodeOutput nifiResult = wf.execute("deploy-nifi-pipelines", datasetId, config);
    wf.handleException(nifiResult, "nifi-deploy-failed", handler -> {
        handler.execute("delete-apisix-route", apisixRouteId);
        handler.execute("delete-frost-project", frostProjectId);
        handler.fail("provisioning-failed", "NiFi deployment failed");
    });
}
```

**Task Worker:**
```java
public class FrostTaskWorker {
    @LHTaskMethod("create-frost-project")
    public String createFrostProject(String datasetId, String configJson) {
        FrostProject project = frostClient.createProject(datasetId, configJson);
        return project.getId();
    }

    @LHTaskMethod("delete-frost-project")
    public void deleteFrostProject(String projectId) {
        frostClient.deleteProject(projectId);
    }
}
```

**Retry-Konfiguration:**
```java
// Exponential Backoff nativ unterstuetzt
NodeOutput result = wf.execute("create-frost-project", datasetId, config)
    .withRetries(5)
    .withExponentialBackoff(ExponentialBackoffRetryPolicy.newBuilder()
        .setBaseIntervalMs(2000)
        .setMultiplier(2.0F)
        .build());
```

**Staerken:**
- **Kafka-nativ** — gebaut auf Kafka Streams. Nutzt den bestehenden Kafka-Cluster, keine externe DB noetig. Bester Kafka-Fit aller evaluierten Frameworks.
- **Kein externes DB** — State in RocksDB (via Kafka Streams State Stores), WAL in Kafka Topics. Keine PostgreSQL-Tabellen, kein Schema-Management.
- **Nativer exponential Backoff** — deklarativ konfigurierbar pro Task oder Workflow-weit
- **Dashboard** — Next.js Web UI mit Workflow-Graph-Visualisierung, Run-Inspektion, Task-Details inkl. Inputs/Outputs/Stacktraces, durchsuchbar
- **Test-Framework** — JUnit 5 Extension mit Testcontainers, fluent `WorkflowVerifier` API
- **Kafka Connect** — `WfRunSinkConnector` startet Workflows aus Kafka Topics, `ExternalEventSinkConnector` fuer Events
- **External Events** — `wf.waitForEvent()` mit Timeout und Correlation (entspricht Temporal Signals)
- **Workflow-Versionierung** — Major Version + Revision Scheme mit Version-Pinning
- **Latenz** — End-to-End unter 40ms dank Kafka-Streams-Architektur

**Schwaechen:**
- **AGPL-3 Server-Lizenz** — erfordert rechtliche Pruefung. Viele Unternehmen haben pauschale AGPL-Verbote. Analyse: Unmodifizierter Server + Apache-2.0-SDK ueber gRPC = eigener Code ist KEIN Derivative Work. Aber: **Legal Review zwingend erforderlich.**
- **Sehr junges Projekt** — 380 Stars, 1.0.0 erst seit Maerz 2026 (3 Wochen vor dieser Analyse). 60+ Bug-Fixes in den Release Notes deuten auf aktive Stabilisierung hin.
- **Bus-Factor-Risiko** — Colt McNealy hat 40% aller Commits. 1-10 Mitarbeiter. Kein Foundation-Backing (nicht CNCF, nicht ASF).
- **Keine automatische Reverse-Order Compensation** — Compensation-Handlers muessen manuell die korrekte Reihenfolge definieren. Die bestehende Custom-Loesung hat hier bessere Semantik.
- **OpenTelemetry unklar** — keine Dokumentation fuer native OTel-Tracing-Integration im Server. Prometheus-Metrics ja (Port 1822), OTel-Tracing nein.
- **Kein Standard-Notation** — Workflows als LittleHorse-spezifischer Java-Code, nicht BPMN oder CNCF Serverless Workflow. Migration zu anderem Framework = Rewrite.
- **Kafka-Overhead** — LittleHorse erstellt ~20+ interne Kafka Topics (Commands, Timer, Changelog, Repartition). Bei shared Kafka Cluster potentiell Ressourcen-Kontention.
- **Kein deklaratives Format** — Workflows nur als Java-Code definierbar, keine YAML/JSON/BPMN Option.

**Kafka-Integration:**
```
Bestehende Kafka Topics  -->  WfRunSinkConnector  -->  LittleHorse Workflow
(CloudEvents)                 (Kafka Connect)              |
                                                     TaskRun: createFrostProject()
                                                     TaskRun: createApisixRoute()
                                                     TaskRun: deployNifiPipelines()
                                                           |
                                                     Task Worker publiziert Result
                                                     via KafkaProducer --> Result Topic
```

**AGPL-3 Lizenz-Analyse:**
- AGPL-3 ist OSI-approved Open Source und in der EUPL-1.2-Kompatibilitaetsliste
- Anwendungscode nutzt Apache-2.0-SDK ueber gRPC — separate Prozesse, kein Linking
- Unmodifizierter Server = keine AGPL-Verpflichtungen fuer eigenen Code
- **ABER:** Wenn Server-Patches noetig werden (Bug-Fixes vor Upstream-Release), muessen diese unter AGPL-3 veroeffentlicht werden
- Kommerzielle Alternativen: LHK (Kubernetes Operator) und LittleHorse Cloud umgehen AGPL

**Loest Limitierungen:** L1 (Dashboard), L2 (Kafka-basierte Recovery automatisch), L4 (gRPC Query API + searchable Variables), L5 (External Events mit Correlation), L6 (Task + Workflow Timeouts), L7 (Prometheus Metrics), L8 (JUnit 5 + Testcontainers + WorkflowVerifier), L9 (Code-basierte Workflows einfach erweiterbar). **Loest NICHT:** L3 (Versioning vorhanden, aber Migration in Entwicklung).

---

## 4. Detailanalyse Tier-2-Kandidaten

### 4.1 Camunda 8 (Zeebe)

| Aspekt | Details |
|--------|---------|
| **Lizenz** | **Camunda License v1** (source-available, NICHT Open Source). Produktionsnutzung erfordert kommerzielle Lizenz. |
| **Version** | Camunda 8.8 (Jan 2026) |
| **Community** | Grosse Enterprise-Nutzerbasis, ~3-4k Stars |
| **Architektur** | Zeebe Cluster (Raft-basiert, RocksDB) + Operate UI + Elasticsearch |
| **Saga-Support** | Native BPMN Compensation Events seit Camunda 8.5. Model-basiert. |

**Bewertung:** Technisch exzellent mit dem besten visuellen Tooling (Operate UI, BPMN Modeler). Jedoch steht die **Camunda License v1 im Widerspruch zum EUPL-1.2-lizenzierten Civitas-Projekt**. Produktionsnutzung erfordert einen kommerziellen Vertrag mit Camunda. Daher nur relevant, wenn die Organisation bereit ist, Lizenzkosten zu tragen.

**Loest Limitierungen:** L1-L7, L9 (BPMN-basiert). **Neues Problem:** Proprietaere Lizenz, BPMN-Modellierung als zusaetzliche Abstraktionsschicht.

### 4.2 Kogito / Apache KIE

| Aspekt | Details |
|--------|---------|
| **Lizenz** | Apache 2.0 (Apache Incubating) |
| **Version** | Apache KIE 10.1.0 (Incubating) |
| **Community** | ~600 Stars, Transition von Red Hat zu Apache Foundation laeuft |
| **Architektur** | Embedded Runtime (Quarkus/Spring Boot) + PostgreSQL + Data Index Service |
| **Saga-Support** | BPMN Compensation Events. CloudEvents + Kafka Support. |

**Bewertung:** Guter Kafka/CloudEvents-Fit. Jedoch befindet sich das Projekt **mitten im Uebergang von Red Hat zu Apache Foundation** (Incubating). API-Stabilitaet ist nicht garantiert. Nicht empfohlen bis Apache KIE aus der Incubation graduiert.

**Loest Limitierungen:** Aehnlich wie Camunda. **Neues Problem:** Instabile APIs waehrend Incubation-Phase.

---

## 5. Grosse Vergleichstabelle (alle Dimensionen)

### 5.1 Architektur & Betrieb

| Dimension | Custom (Ist) | Temporal | Conductor | Restate | Eventuate Tram | Axon |
|-----------|-------------|----------|-----------|---------|----------------|------|
| **Architektur** | Embedded (Kafka) | Server + Worker | Server + Worker | Server + Service | Embedded (Library) | Embedded / opt. Server |
| **Lizenz** | — (eigen) | MIT | Apache 2.0 | BUSL-1.1 / MIT | Apache 2.0 | Apache 2.0 / kommerz. |
| **Min. Infrastruktur** | Kafka (vorhanden) | Server + PostgreSQL | Server + PostgreSQL + ES | Single Binary | PostgreSQL + CDC | PostgreSQL |
| **Deployment** | In-Process | +3 Container | +4 Container | +1 Container | +2 Container | +0-1 Container |
| **HA-Setup** | Kafka Cluster | 3+ Server | 2+ Server | 3+ Server | CDC HA | Axon Server Cluster |
| **Java Version** | 21 | 8+ | 17+ | 11+ | 8+ | 8+ |

### 5.2 Saga-Funktionalitaet

| Dimension | Custom (Ist) | Temporal | Conductor | Restate | Eventuate Tram | Axon |
|-----------|-------------|----------|-----------|---------|----------------|------|
| **Saga Compensation** | Erstklassig (reverse-order, best-effort delete) | Erstklassig (`Saga.java`, reverse-order) | Workaround (`failureWorkflow`) | Manuell (try/catch + journaling) | Erstklassig (DSL) | Erstklassig (`@Saga`) |
| **Compensation-Qualitaet** | ★★★★★ | ★★★★★ | ★★☆☆☆ | ★★★★☆ | ★★★★★ | ★★★★☆ |
| **Conditional Steps** | Ja (Predicate) | Ja (Code) | Ja (Switch/Decision) | Ja (Code) | Nein | Ja (Event-basiert) |
| **Best-Effort Delete** | Ja (eingebaut) | Manuell codierbar | Manuell codierbar | Manuell codierbar | Nein | Manuell codierbar |
| **Data-Passing** | `compensationData` Record | Activity Return Values | Task Output | `ctx.run()` Return | Saga Data Object | Saga Fields |
| **Concurrency Control** | `existsForDataset()` | Workflow ID Uniqueness | Idempotency Key | Virtual Object Key | Saga Lock | Saga Association |

### 5.3 Retry, Timeout, Fehlerbehandlung

| Dimension | Custom (Ist) | Temporal | Conductor | Restate | Eventuate Tram | Axon |
|-----------|-------------|----------|-----------|---------|----------------|------|
| **Retry-Konfiguration** | Custom (BackoffCalculator) | Deklarativ (RetryOptions) | Deklarativ (JSON) | Deklarativ (Config) | Message Redelivery | Event Replay + Deadline |
| **Retry-Granularitaet** | Kafka-Consumer-Level | Per-Activity | Per-Task | Per-Handler | Per-Participant | Per-Command |
| **Backoff-Strategie** | Exponential + Cap | Exponential + Jitter + Cap | Exponential | Exponential | Redelivery-basiert | Manuell |
| **Timeout-Typen** | HTTP (connect/read) + Step | 4 Typen (Workflow/Run/Schedule/Start) | 2 Typen (Task/Response) | Handler + Call | Kein nativer | Deadline-basiert |
| **Fatal vs. Retryable** | Exception-Hierarchie | `doNotRetry` Liste | Kein Equivalent | Terminal Exception | Kein Equivalent | Exception Handler |
| **DLQ** | Ja (Kafka-basiert) | Nein (kein DLQ-Konzept) | Nein | Nein | Nein | Ja (mit Enterprise) |

### 5.4 Observability & Testing

| Dimension | Custom (Ist) | Temporal | Conductor | Restate | Eventuate Tram | Axon |
|-----------|-------------|----------|-----------|---------|----------------|------|
| **Dashboard/UI** | Keins | Web UI (exzellent) | Web UI (gut) | CLI + basic UI | Keins | Axon Server Dashboard |
| **Workflow-Suche** | Nein | Ja (Search Attributes) | Ja (Elasticsearch) | Nein | Nein | Nein |
| **Event History** | Kafka Topic (bis Compaction) | Persistent (unbegrenzt) | Persistent | Persistent (Journal) | DB-basiert | Event Store |
| **Prometheus Metrics** | Nein | Ja (out-of-the-box) | Ja | Ja | Nein | Via Micrometer |
| **OpenTelemetry** | Nein | Ja | Teilweise | Teilweise | Nein | Nein |
| **Saga-spezifische Tests** | JUnit + Mockito (manuell) | TestWorkflowEnvironment | Worker Unit Tests | TestRestateRuntime | SagaUnitTestSupport | SagaTestFixture (exzellent) |
| **Time-Skipping Tests** | Nein | Ja | Nein | Ja | Nein | Nein |
| **Test-LOC-Reduktion** | Baseline (~9.900) | ~60% weniger | ~40% weniger | ~50% weniger | ~30% weniger | ~50% weniger |

### 5.5 Kafka-Integration

| Dimension | Custom (Ist) | Temporal | Conductor | Restate | Eventuate Tram | Axon |
|-----------|-------------|----------|-----------|---------|----------------|------|
| **Kafka als Transport** | Primaer | Nein (eigene Queue) | Teilweise (Event Tasks) | Ja (native Subscription) | Ja (via CDC) | Extension |
| **Kafka-Trigger** | Direkt (Consumer) | Bridge Worker noetig | Event Handler | Native Subscription | Saga Manager | Event Processor |
| **Kafka-Result-Publish** | Direkt (Producer) | Bridge noetig | Event Task | `ctx.run()` | Outbox + CDC | Kafka Extension |
| **CloudEvents** | Ja | Nein | Nein | Nein | Nein | Nein |
| **Architektur-Impact** | Kein Umbau | Grosser Umbau | Mittlerer Umbau | Geringer Umbau | Mittlerer Umbau | Grosser Umbau |

### 5.6 Community & Nachhaltigkeit

| Dimension | Custom (Ist) | Temporal | Conductor | Restate | Eventuate Tram | Axon |
|-----------|-------------|----------|-----------|---------|----------------|------|
| **GitHub Stars** | — | ~19.500 | ~17.300 | ~3.700 | ~870 | ~3.600 |
| **Contributors** | Team-intern | 200+ | 100+ | 30+ | ~10 | 50+ |
| **Backing** | Civitas Team | VC-funded ($300M+) | Community (ex-Netflix) | VC-funded | Chris Richardson | AxonIQ GmbH |
| **Bus-Faktor** | Team-intern | Hoch (Company) | Mittel (Community) | Mittel (Company) | Niedrig (1 Person) | Mittel (Company) |
| **Lernkurve** | Hoch (Custom verstehen) | Mittel (Workflow/Activity) | Mittel (JSON Workflows) | Niedrig-Mittel | Niedrig (DSL) | Hoch (CQRS/ES) |
| **Vendor Lock-in** | Keiner | Gering (MIT) | Gering (Apache 2.0) | Mittel (BUSL) | Gering (Apache 2.0) | Mittel (Axon Server) |

---

## 6. Bewertung und Empfehlung

### 6.1 Gewichtete Bewertungsmatrix

Gewichtung basiert auf den Civitas-Projektprioritaeten gemaess ADR-031: Adapter-Einfachheit, Kafka-Zentrierung, keine heavyweight Infrastructure, EUPL-Kompatibilitaet.

| Kriterium (Gewicht) | Custom | Temporal | Conductor | Restate | Eventuate | Axon |
|---------------------|--------|----------|-----------|---------|-----------|------|
| **Saga-Semantik** (20%) | 10 | 10 | 5 | 8 | 10 | 8 |
| **Kafka-Integration** (20%) | 10 | 3 | 6 | 9 | 7 | 4 |
| **Operationaler Overhead** (15%) | 10 | 4 | 3 | 8 | 6 | 8 |
| **Observability** (15%) | 2 | 10 | 9 | 6 | 2 | 5 |
| **Testbarkeit** (10%) | 6 | 10 | 6 | 7 | 8 | 10 |
| **Community/Support** (10%) | 3 | 10 | 7 | 5 | 3 | 6 |
| **Lizenz-Kompatibilitaet** (5%) | 10 | 10 | 10 | 7 | 10 | 8 |
| **Migrations-Aufwand** (5%) | 10 | 4 | 5 | 6 | 7 | 3 |
| **Gewichteter Score** | **7.55** | **7.15** | **5.70** | **7.20** | **6.35** | **6.15** |

### 6.2 Interpretation der Scores

| Rang | Framework | Score | Profil |
|------|-----------|-------|--------|
| **1** | **Custom (Ist)** | 7.55 | Hoechste Kafka-Integration + niedrigster Overhead, aber schwache Observability |
| **2** | **Restate** | 7.20 | Bester Kompromiss: native Kafka + durable execution + minimaler Overhead |
| **3** | **Temporal** | 7.15 | Beste Observability + Community, aber Kafka-Impedance-Mismatch |
| **4** | **Eventuate Tram** | 6.35 | Naechste Saga-DSL, aber fuegt DB-Dependency hinzu + keine Observability |
| **5** | **Axon** | 6.15 | Beste Testbarkeit, aber erfordert CQRS/ES-Architektur-Buy-In |
| **6** | **Conductor** | 5.70 | Keine native Saga-Compensation + hoechster Infrastruktur-Overhead |

### 6.3 Marginal-Cost-Analyse: Was kostet ein neuer Workflow mit der Custom-Loesung?

Bevor die Frage "Lohnt sich eine Migration?" beantwortet werden kann, muss klar sein, was
der **naechste Workflow im Ist-Zustand tatsaechlich kostet**. Eine Analyse der bestehenden
Klassen zeigt, dass die Infrastruktur gut, aber nicht vollstaendig wiederverwendbar ist:

#### Wiederverwendbarkeit der bestehenden Komponenten

| Komponente | LOC | Wiederverwendbar | Begruendung |
|------------|-----|------------------|-------------|
| `SagaStateMachine` | 455 | **100%** | Pure State Machine, Workflow-agnostisch |
| `SagaEngine` | 216 | **100%** | Facade, delegiert an StateMachine |
| `KafkaSagaStateStore` | 154 | **100%** | Generischer Key-Value Store |
| `KafkaSagaStateRecovery` | 137 | **100%** | Generische Recovery-Logik |
| `KafkaSagaActionDispatcher` | 202 | **100%** | Generischer Command-Dispatch |
| `SagaResultConsumer` | 157 | **~95%** | 3 hardcoded Result-Topic-Namen (4 LOC) |
| `SagaTriggerConsumer` | 131 | **~85%** | 1 hardcoded Trigger-Topic (1 LOC) |
| `DatasetSagaOrchestrator` | 193 | **~90%** | Nur HAS_PIPELINES-Predicate ist Dataset-spezifisch |
| `SagaPayloadBuilder` | 143 | **~95%** | Nur `applyAdapterMappings` Switch (15 LOC) |
| `AbstractSagaCommandHandler` | 215 | **100%** | Base-Class, vollstaendig generisch |
| `SagaDefinitions` | 139 | **~80%** | Registry; neue Workflows = neue Eintraege (~25 LOC) |
| **`DatasetCommandBuilder`** | **303** | **~15%** | **Hauptproblem: 85% ist Dataset-spezifischer Payload-Aufbau** |

#### Der Engpass: DatasetCommandBuilder

`DatasetCommandBuilder` ist die Klasse, die fuer jeden Saga-Step den konkreten Command-Payload
zusammenbaut. Pro Adapter enthaelt sie:
- Forward-Command-Builder (45-52 LOC pro Adapter)
- Compensation-Command-Builder (20-25 LOC pro Adapter)
- Adapter-spezifische Feldmappings

Bei einem 2. Workflow, der die gleichen 3 Adapter nutzt (z.B. DataPool-Provisioning), muesste
ein `DataPoolCommandBuilder` mit **~250 LOC** entstehen — zu ~95% Copy-Paste aus dem
`DatasetCommandBuilder` mit geaenderten Feldnamen und Payload-Strukturen.

Bei einem Workflow mit **neuen Adaptern** (z.B. Keycloak, MinIO) kaeme jeweils ein neuer
`SagaHandler` dazu (~80-150 LOC pro Adapter).

#### Kostenmodell: Custom vs. Framework pro Workflow

```
                Custom-Loesung                    Framework (Temporal/Restate)
                ==============                    ============================

Workflow #1     ~4.800 LOC (Infra + Business)     Migration: 11-22 PT einmalig
(Dataset)       + ~9.900 LOC Tests                + ~1.000 LOC (Workflow + Activities)
                = Ist-Zustand, funktioniert       + ~3.000 LOC Tests

Workflow #2     + ~580 LOC (CommandBuilder,        + ~400 LOC (neuer Workflow + Activities)
(z.B. DataPool)   Orchestrator, Definitions)      + ~800 LOC Tests
                + ~1.500 LOC Tests                 Kein neues Infra-Code
                + Debugging von Copy-Paste-Fehlern

Workflow #3     + ~580 LOC (wieder Copy-Paste)     + ~400 LOC
                + ~1.500 LOC Tests                 + ~800 LOC Tests
                + Refactoring-Druck steigt

Workflow #4     Refactoring unvermeidlich:          + ~400 LOC
                CommandBuilder-Abstraktion           + ~800 LOC Tests
                noetig (~3-5 PT)
                + ~400 LOC + ~1.200 LOC Tests

Workflow #5     + ~400 LOC + ~1.200 LOC Tests      + ~400 LOC + ~800 LOC Tests
```

#### Kumulierte Kosten (LOC, ohne Infrastruktur-Tests)

| Workflows | Custom (kumuliert) | Framework (kumuliert nach Migration) |
|-----------|-------------------|--------------------------------------|
| 1 | 4.800 + 9.900 Tests = **14.700** | Migration + 1.000 + 3.000 = **4.000** + Migrationskosten |
| 2 | +2.080 = **16.780** | +1.200 = **5.200** |
| 3 | +2.080 = **18.860** | +1.200 = **6.400** |
| 4 | +1.600 + Refactoring = **~21.500** | +1.200 = **7.600** |
| 5 | +1.600 = **~23.100** | +1.200 = **8.800** |

**Die Schere oeffnet sich mit jedem Workflow weiter.** Bei der Custom-Loesung waechst nicht
nur der Code, sondern auch die **kognitive Last**: Entwickler muessen verstehen, wie
`SagaStateMachine`, `SagaEngine`, `SagaAction` (7 Varianten), `SagaStateStore`,
`KafkaSagaActionDispatcher` und `KafkaSagaStateRecovery` zusammenspielen — bevor sie
an der eigentlichen Business-Logik arbeiten koennen.

### 6.4 Qualitative Entscheidungsfaktoren (jenseits der LOC-Rechnung)

Die reine LOC-Betrachtung erzaehlt nicht die ganze Geschichte. Folgende Faktoren
koennen den Kipppunkt frueher oder spaeter eintreten lassen:

#### Faktoren die FUER ein Framework sprechen

| Faktor | Auswirkung | Relevanz fuer Civitas |
|--------|------------|----------------------|
| **Team-Wachstum / Onboarding** | Neue Entwickler brauchen 1-2 Wochen um den Custom-Orchestrator zu verstehen. Temporal/Restate Konzepte sind in 2-3 Tagen erlernbar und uebertragbar auf andere Projekte. | Mittel — abhaengig von Team-Groesse und Fluktuation |
| **Incident Response** | Ohne Dashboard ist das Debugging einer haengenden Saga aufwendig: Kafka-Consumer-Tools, Log-Analyse, manuelles Topic-Lesen. Mit Temporal Web UI: 3 Klicks zum Workflow-State. | Hoch — sobald Sagas in Produktion SLAs haben |
| **Schema-Evolution** | Wenn sich `SagaContext` oder `SagaStep` Records aendern, muessen laufende Sagas manuell migriert werden. Temporal's Patching API erlaubt inkrementelle Versionierung ohne Downtime. | Niedrig heute, steigt mit Lebensdauer |
| **Manuelle Intervention** | Die Custom-Loesung publiziert `PublishManualIntervention` — aber es gibt keinen Mechanismus, um die Intervention dann auch durchzufuehren (Signal an laufende Saga). | Mittel — betrifft Compensation-Failure-Faelle |
| **Compliance / Audit** | Kafka Compacted Topics verlieren History nach Compaction. Frameworks speichern vollstaendige Event History persistent. | Niedrig — Sagas sind kurzlebig |

#### Faktoren die GEGEN ein Framework sprechen

| Faktor | Auswirkung | Relevanz fuer Civitas |
|--------|------------|----------------------|
| **Kafka-Zentrierung** | Die gesamte Config-Adapter-Architektur ist Kafka-zentrisch (CloudEvents, Consumer Groups, Compacted Topics). Temporal/Conductor fuehren ein paralleles Kommunikationsmedium ein. | Hoch — architektonische Konsistenz |
| **Operationale Komplexitaet** | Jeder neue Container ist ein neuer Failure Point: Monitoring, Alerting, Backup, Upgrades. Temporal Server Cluster (3 Nodes + DB) ist signifikanter Betriebsaufwand. | Hoch — kleines Ops-Team |
| **Infrastruktur-Homogenitaet** | Docker Compose hat heute: Kafka, PostgreSQL, Keycloak. Ein Framework-Server (Go Binary bei Temporal, Rust Binary bei Restate) fuehrt ein neues Technology-Stack-Element ein. | Mittel |
| **EUPL-Kompatibilitaet** | Restate (BUSL-1.1) und Axon Server Enterprise (kommerziell) sind nicht uneingeschraenkt EUPL-kompatibel. Temporal (MIT) und Eventuate (Apache 2.0) sind es. | Hoch fuer Civitas |
| **Over-Engineering-Risiko** | Ein Framework bringt Features mit, die fuer 1-3 Workflows nicht benoetigt werden (Child Workflows, Cron, Multi-Tenancy). Diese Features kosten nichts direkt, aber erhoehen die kognitive Komplexitaet des Tech-Stacks. | Mittel |

### 6.5 Option 0: Custom-Loesung verbessern statt migrieren

Bevor eine Framework-Migration angestossen wird, gibt es eine dritte Option: die Custom-Loesung
so refactoren, dass der Marginal-Cost pro neuem Workflow sinkt. Dies adressiert den
Haupt-Engpass (`DatasetCommandBuilder` Duplikation) ohne Framework-Adoption.

#### Moegliche Verbesserungen

| Massnahme | Aufwand | Effekt |
|-----------|---------|--------|
| **CommandBuilder abstrahieren**: Generischen `SagaCommandBuilder<T>` mit Template Method einfuehren, der Adapter-spezifische Payload-Konstruktion an Subklassen delegiert. | 2-3 PT | Reduziert Duplikation von ~250 LOC auf ~50 LOC pro Workflow |
| **Prometheus/Micrometer Metrics**: Zaehler fuer Saga-Start, Saga-Complete, Saga-Failed, Step-Duration, Compensation-Count. | 1 PT | Loest L7 (kein Metrics-Export) |
| **REST Status-Endpoint**: `/saga/{id}/status` der aus dem `KafkaSagaStateStore` liest. | 0.5 PT | Loest L4 teilweise (kein Query-API) |
| **Orchestrator-Factory**: `SagaOrchestratorFactory` die anhand von `SagaType` den richtigen Orchestrator instantiiert, statt pro Workflow eine neue Klasse. | 1-2 PT | Reduziert Boilerplate von ~175 LOC auf ~20 LOC pro Workflow |
| **Gesamt** | **4.5-6.5 PT** | Marginal-Cost pro Workflow sinkt von ~580 LOC auf ~100 LOC |

#### Bewertung von Option 0

**Vorteile:**
- Kein neuer Container, keine neue Datenbank, keine neue Technologie
- Kafka-Zentrierung bleibt erhalten
- Team muss kein neues Framework lernen
- Investition in bestehenden, getesteten Code

**Nachteile:**
- Loest nur L4 (teilweise) und L7 von 9 Limitierungen
- Dashboard (L1), Versionierung (L3), Signals (L5) bleiben ungeloest
- Der Orchestrator bleibt ein internes Framework das dokumentiert und geschult werden muss
- Bei 5+ Workflows wird auch die refactored Loesung zum Wartungsproblem

**Empfehlung:** Option 0 ist die richtige Wahl wenn maximal 2-3 Workflows absehbar sind
und kein starkes Observability-Beduerfnis besteht. Sie verschiebt den Kipppunkt um
~2 Workflows nach oben.

### 6.6 Entscheidungsframework: Wann genau lohnt sich der Wechsel?

Die Entscheidung "Custom behalten vs. Framework adoptieren" haengt nicht nur an der
Anzahl der Workflows, sondern an einer Kombination von Triggern. Folgendes Framework
formalisiert die Entscheidung:

#### Primaer-Trigger (jeder einzelne reicht fuer PoC-Start)

| # | Trigger | Begruendung | Framework-Empfehlung |
|---|---------|-------------|---------------------|
| T1 | **3. Saga-Workflow wird geplant** | Break-Even der LOC-Kosten ist erreicht. Die Custom-Loesung beginnt, sich als internes Framework zu verhalten — mit allen Wartungspflichten die das mit sich bringt. | Temporal oder Restate |
| T2 | **Saga-Monitoring wird fuer Produktion verlangt** (SLAs, Incident-Dashboards) | Die Custom-Loesung hat kein Dashboard und keine Metrics. Ein REST-Endpoint und Micrometer (Option 0) reichen fuer Basic-Monitoring, aber nicht fuer ein Incident-Dashboard mit Workflow-Suche, Event-History und Retry-Steuerung. | Temporal (bestes UI) |
| T3 | **Manuelle Intervention in laufende Sagas wird benoetigt** | `COMPENSATION_FAILED` erfordert heute manuellen Eingriff via Kafka-Tools. Temporal Signals oder Restate Handler Calls ermoeglichen programmatische Intervention. | Temporal (Signals) oder Restate (Handler) |
| T4 | **Neue Adapter-Typen (Stellio, MinIO, NGSI-LD) werden eingefuehrt** | Jeder neue Adapter erfordert einen neuen `SagaHandler` (~80-150 LOC) + CommandBuilder-Erweiterung. Bei 5+ Adaptern wird die `SagaDefinitions`-Registry komplex. | Temporal oder Restate (Workflow = Code) |

#### Sekundaer-Trigger (in Kombination relevant)

| # | Trigger | Begruendung |
|---|---------|-------------|
| S1 | **Team waechst um 2+ Entwickler** die den Orchestrator verstehen muessen | Onboarding-Kosten steigen linear mit Custom-Code-Komplexitaet |
| S2 | **Schema-Aenderung an SagaContext/SagaStep** wird noetig | Ohne Versionierung muss der State-Topic manuell migriert werden |
| S3 | **Long-Running-Workflows** (Minuten bis Stunden statt Sekunden) werden benoetigt | Kafka Consumer Timeouts und Rebalancing werden bei langen Sagas zum Problem |
| S4 | **Cross-Service-Saga** (Config-Adapter + Portal-Backend als gemeinsamer Workflow) | Die Custom-Loesung ist auf ein JVM-Deployment beschraenkt |

#### Entscheidungsbaum

```
Nur 1 Workflow?
  └─ Ja → Option 0 (Quick Wins: Metrics + REST-Endpoint)
          Fertig. Re-evaluate bei naechstem Trigger.

2. Workflow geplant?
  └─ Ja → Ist starkes Observability/Monitoring noetig? (T2)
           └─ Ja → PoC Temporal (3 PT)
           └─ Nein → Ist Kafka-Kompatibilitaet die hoechste Prioritaet?
                      └─ Ja → Option 0 (Refactoring) + 2. Workflow custom bauen
                      └─ Nein → PoC Restate (3 PT)

3+ Workflows geplant?
  └─ Ja → Framework-Migration empfohlen.
          Entscheidung Temporal vs. Restate:
            └─ Priorisierung Observability + Community → Temporal
            └─ Priorisierung Kafka-Integration + geringer Overhead → Restate

Irgendein Primaer-Trigger (T1-T4) ausgeloest?
  └─ Ja → PoC starten, unabhaengig von Workflow-Anzahl

Nur Sekundaer-Trigger (S1-S4)?
  └─ 2+ Sekundaer-Trigger gleichzeitig → PoC evaluieren
  └─ 1 Sekundaer-Trigger → Option 0 ausreichend
```

### 6.7 Framework-Empfehlung nach Szenario

Basierend auf der Gesamtanalyse — gewichtete Scores, Kafka-Fit, Operational Overhead,
Lizenz-Kompatibilitaet, Community-Staerke — ergeben sich folgende Empfehlungen:

#### Szenario A: "Wir brauchen vor allem Observability" → Temporal

Temporal ist die richtige Wahl wenn:
- Saga-Monitoring mit Dashboard, Workflow-Suche und Event-History benoetigt wird
- Das Team den operationalen Overhead (Temporal Server + PostgreSQL) tragen kann
- Die Kafka-Architektur angepasst werden kann (Bridge-Worker-Pattern)
- Langfristig 3+ Workflows geplant sind

**Architektur-Impact:** Mittel-Hoch. Adapter behalten ihre Kafka-Consumer fuer Nicht-Saga-Events.
Saga-Orchestrierung wandert von Kafka zu Temporal's internem Task-Queue-System. Ein
Bridge-Worker konsumiert Trigger-Events aus Kafka und startet Temporal Workflows.

```
Kafka (Trigger) → Bridge Worker → Temporal Workflow
                                      ├── Activity: FROST (HTTP)
                                      ├── Activity: APISIX (HTTP)
                                      └── Activity: Redpanda (HTTP)
                                           → Kafka (Result)
```

**Migrations-Reihenfolge:**
1. Temporal Server + PostgreSQL im Docker Compose aufsetzen (2 PT)
2. `DatasetProvisioningWorkflow` + Activities implementieren (3-5 PT)
3. Bridge-Worker fuer Kafka-Trigger bauen (2 PT)
4. Parallel-Betrieb: alte + neue Saga parallel testen (1-2 PT)
5. Cutover: alte Saga abschalten, Code entfernen (2 PT)
6. **Gesamt: 10-13 PT** (optimistisch) bis **14-22 PT** (konservativ)

#### Szenario B: "Kafka-Architektur darf sich nicht aendern" → Restate

Restate ist die richtige Wahl wenn:
- Die Kafka-zentrische Architektur beibehalten werden soll
- Minimaler operationaler Overhead gewuenscht ist (+1 Container, keine DB)
- Das Team offen fuer ein juengeres Projekt mit kleinerer Community ist
- Die BUSL-1.1 Lizenz (intern nutzbar, nicht als Service anbietbar) akzeptabel ist

**Architektur-Impact:** Gering. Restate subscribed direkt auf Kafka Topics. Die bestehende
CloudEvents/Kafka-Architektur bleibt weitgehend erhalten. Adapter-Handler werden zu
Restate Service Handlers refactored.

```
Kafka (Trigger) → Restate (native Subscription) → Handler
                                                      ├── ctx.run(): FROST (HTTP)
                                                      ├── ctx.run(): APISIX (HTTP)
                                                      └── ctx.run(): Redpanda (HTTP)
                                                           → ctx.run(): Kafka Publish (Result)
```

**Migrations-Reihenfolge:**
1. Restate Server im Docker Compose aufsetzen (0.5 PT)
2. `DatasetSagaHandler` als Restate Virtual Object implementieren (2-3 PT)
3. Kafka Subscription konfigurieren (0.5 PT)
4. Bestehende Handler-Logik in `ctx.run()` Blocks wrappen (2-3 PT)
5. Parallel-Betrieb + Cutover (2-3 PT)
6. **Gesamt: 7-10 PT** (optimistisch) bis **11-16 PT** (konservativ)

#### Szenario C: "Minimalinvasiv, kein neues Framework" → Option 0

Option 0 ist die richtige Wahl wenn:
- Maximal 2-3 Workflows absehbar sind
- Kein starkes Observability-Beduerfnis besteht
- Das Team die Custom-Loesung gut kennt und pflegen kann
- Keine neuen Infrastruktur-Komponenten gewuenscht sind

**Massnahmen:**
1. `CommandBuilder` abstrahieren: generischer `SagaCommandBuilder<T>` (2-3 PT)
2. Prometheus Metrics hinzufuegen (1 PT)
3. REST Status-Endpoint (0.5 PT)
4. **Gesamt: 3.5-4.5 PT**

**Erwartung:** Marginal-Cost pro neuem Workflow sinkt von ~580 LOC + ~1.500 LOC Tests
auf ~100 LOC + ~400 LOC Tests. Die Custom-Loesung bleibt tragfaehig bis ~3-4 Workflows.

#### Szenario D: Explizit NICHT empfohlen

| Framework | Warum nicht |
|-----------|-------------|
| **Conductor** | Keine native Saga-Compensation (`failureWorkflow` ist ein Workaround). Hoechster Infrastruktur-Overhead (Server + PostgreSQL + Elasticsearch). Netflix hat das Projekt archiviert. Fuer den Civitas Use Case bringt Conductor weniger Saga-Qualitaet als die bestehende Custom-Loesung. |
| **Axon** | Erfordert CQRS/Event-Sourcing Buy-In der die gesamte Config-Adapter-Architektur umstrukturieren wuerde. Kafka ist in Axon ein Sekundaerbuerger. Der Aufwand (16-26 PT) steht in keinem Verhaeltnis zum Saga-Nutzen. |
| **Eventuate Tram** | Fuegt eine JDBC-Datenbank-Abhaengigkeit hinzu die heute nicht existiert (architektonischer Rueckschritt). Keine Observability. Bus-Faktor 1. Loest nur 2 von 9 Limitierungen. |
| **Camunda 8** | Camunda License v1 ist nicht Open Source — Produktionsnutzung erfordert kommerziellen Vertrag. Nicht kompatibel mit EUPL-Ethos des Civitas-Projekts. |

### 6.8 Lohnt sich eine Migration? — Zusammenfassung

| Szenario | Empfehlung | Aufwand | Begruendung |
|----------|------------|---------|-------------|
| **1 Workflow, kein Monitoring-Bedarf** | **Custom beibehalten** | 0 PT | Funktioniert. Stabil. Keine zusaetzliche Komplexitaet. |
| **1 Workflow, Monitoring gewuenscht** | **Option 0** | 3.5-4.5 PT | Metrics + REST-Endpoint + CommandBuilder-Refactoring decken die groessten Luecken ab. |
| **2 Workflows geplant** | **Option 0 + PoC** | 4.5 PT + 3 PT PoC | Zuerst Custom verbessern. Parallel PoC mit Temporal oder Restate um Daten fuer die Entscheidung zu sammeln. |
| **3+ Workflows geplant** | **Framework-Migration** | 11-22 PT | Break-Even erreicht. Custom wird zum internen Framework. Migration rechnet sich ab Workflow 3. |
| **Primaer-Trigger T2/T3 (Observability / Signals)** | **Temporal PoC** | 3 PT PoC | Temporal loest 9/9 Limitierungen und hat das beste Tooling fuer Incident Response. |
| **Primaer-Trigger T4 (neue Adapter)** | **Restate PoC** | 3 PT PoC | Restate hat den geringsten Architektur-Impact bei nativer Kafka-Integration. |

### 6.9 Konkrete Bugs/Limitierungen die Frameworks loesen wuerden

| # | Problem | Schwere | Custom-Workaround | Framework-Loesung |
|---|---------|---------|--------------------|--------------------|
| 1 | **Haengende Saga unsichtbar**: Wenn ein Adapter nicht antwortet und kein Timeout greift, bleibt die Saga ewig im Status `EXECUTING`. Ohne Dashboard ist das nur via Kafka-Consumer-Tools oder Log-Monitoring erkennbar. | Hoch | Alerting auf Saga-Alter via Prometheus (Option 0) | Temporal: Workflow Execution Timeout + Web UI zeigt "timed out" Workflows |
| 2 | **Compensation-Failure ist eine Sackgasse**: `COMPENSATION_FAILED` publiziert `ManualIntervention`, aber es gibt keinen Rueckkanal. Ein Operator muss manuell Kafka-Messages produzieren. | Mittel | Dokumentierte Runbook-Prozedur | Temporal: Admin kann via UI/Signal den Workflow manuell abschliessen oder neu starten |
| 3 | **Kein Replay nach Orchestrator-Crash waehrend Transition**: Wenn der Prozess zwischen State-Machine-Transition und Kafka-Publish crasht, wird die Transition wiederholt (at-least-once). Die Idempotenz der Adapter muss das auffangen. | Niedrig | Adapter sind bereits idempotent | Temporal/Restate: Exakt-Once Semantik durch Event-Sourced Replay |
| 4 | **DatasetCommandBuilder Duplikation**: Bei neuem Workflow entsteht eine fast identische Klasse mit hohem Copy-Paste-Risiko. | Mittel (bei 2+ Workflows) | Refactoring zu generischem Builder (Option 0) | Framework: Kein CommandBuilder noetig, Activities sind self-contained |
| 5 | **Kafka Compacted Topic verliert History**: Nach Compaction ist nur der letzte State pro Saga-ID verfuegbar. Fuer Post-Mortem-Analyse eines fehlgeschlagenen Saga-Runs fehlt die Step-by-Step-History. | Niedrig | Separater Audit-Topic ohne Compaction | Temporal: Vollstaendige Event History persistent verfuegbar |

### 6.10 Referenztabelle: Limitierungen x Frameworks

| Limitierung | Temporal | Conductor | Restate | Eventuate | Axon |
|-------------|----------|-----------|---------|-----------|------|
| L1: Kein Dashboard | ✅ Web UI | ✅ Web UI | ⚠️ CLI | ❌ | ⚠️ Axon Server |
| L2: Kein Orchestrator-Retry | ✅ Auto-Replay | ✅ Server-managed | ✅ Journal-Replay | ❌ | ❌ |
| L3: Keine Versionierung | ✅ Patching API | ❌ | ❌ | ❌ | ❌ |
| L4: Kein Query-API | ✅ Query API | ✅ REST API | ⚠️ Virtual Object | ❌ | ❌ |
| L5: Keine Signals | ✅ Signal API | ❌ | ✅ Handler Calls | ❌ | ⚠️ Deadline |
| L6: Timeout limitiert | ✅ 4 Typen | ✅ 2 Typen | ✅ Config | ❌ | ✅ Deadline |
| L7: Kein Metrics | ✅ Prometheus | ✅ Metrics | ✅ Prometheus | ❌ | ⚠️ Micrometer |
| L8: Test-Overhead | ✅ TestWorkflowEnv | ⚠️ Worker Tests | ✅ TestRuntime | ✅ SagaTestSupport | ✅ SagaTestFixture |
| L9: Skaliert nicht | ✅ Workflow=Code | ✅ JSON Defs | ✅ Handler=Code | ✅ DSL | ✅ @Saga |
| **Geloest** | **9/9** | **5/9** | **6-7/9** | **2/9** | **4-5/9** |

### 6.11 Migrationsaufwand pro Framework

| Phase | Temporal | Conductor | Restate | Eventuate | Axon |
|-------|----------|-----------|---------|-----------|------|
| **Server/Infra Setup** | 2-3 PT | 3-4 PT | 1 PT | 1-2 PT | 0-1 PT |
| **Workflow/Saga Interfaces** | 2-3 PT | 2-3 PT | 2-3 PT | 1-2 PT | 3-5 PT |
| **Activity/Handler Migration** | 3-5 PT | 3-5 PT | 3-5 PT | 2-3 PT | 5-8 PT |
| **Kafka Bridge/Integration** | 2-3 PT | 1-2 PT | 0.5 PT | 1-2 PT | 2-3 PT |
| **Retry/Timeout Config** | 1 PT | 1 PT | 1 PT | 0.5 PT | 1-2 PT |
| **Tests Migration** | 2-3 PT | 2-3 PT | 2-3 PT | 1-2 PT | 2-3 PT |
| **Observability Setup** | 1 PT | 1-2 PT | 0.5 PT | 0 PT | 1 PT |
| **Alten Code entfernen** | 1-2 PT | 1-2 PT | 1-2 PT | 1-2 PT | 2-3 PT |
| **Gesamt** | **14-22 PT** | **14-22 PT** | **11-16 PT** | **8-14 PT** | **16-26 PT** |
| **Wochen (1 Entwickler)** | ~4-5 Wo | ~4-5 Wo | ~3-4 Wo | ~2-3 Wo | ~4-6 Wo |

### 6.12 Irrelevante Komplexitaet pro Framework

| Framework | Irrelevante Features fuer Civitas |
|-----------|-----------------------------------|
| **Temporal** | Child Workflows, Continue-as-New, Cron Workflows, Multi-SDK, Nexus, Advanced Visibility (ES), Multi-Tenancy |
| **Conductor** | Dynamic Forks, Sub-Workflows, Human Tasks, Wait Tasks, Event-basiertes Routing, Lambda-Integration |
| **Restate** | Multi-Language SDKs, Keyed Service Deployment, Virtual Object Clustering |
| **Eventuate Tram** | Transactional Outbox fuer alle Messages (nicht nur Sagas), CQRS View-Updates |
| **Axon** | Aggregate Pattern, Command Bus, Event Sourcing, Subscription Queries, Deadline-based Scheduling |

### 6.13 Framework-spezifische Risiken

| Framework | Risiko | Mitigation |
|-----------|--------|------------|
| **Temporal** | Kafka-Architektur-Umbau erforderlich; Adapter muessen von Kafka-Consumer auf Temporal-Worker oder Kafka-Bridge umgestellt werden | Bridge-Pattern: Kafka bleibt fuer Trigger/Result, Temporal nur fuer Orchestrierung |
| **Conductor** | Netflix hat das Projekt archiviert; conductor-oss Governance unklar; Orkes pusht kommerzielle Version | Conductor-OSS hat aktive Community; bei Zweifeln auf Temporal ausweichen |
| **Restate** | BUSL-1.1 ist nicht Open Source; Projekt ist jung (<3 Jahre); Community noch klein | BUSL erlaubt interne Nutzung; SDKs sind MIT; Restate Cloud als Fallback |
| **Eventuate** | Bus-Faktor 1 (Chris Richardson); fuegt DB-Dependency hinzu die heute nicht existiert | Apache 2.0 erlaubt Fork; DB ist bereits fuer Keycloak vorhanden |
| **Axon** | CQRS/ES Buy-In geht weit ueber Saga hinaus; Axon Server Enterprise ist kommerziell | Axon Framework ohne Axon Server betreibbar; nur Saga-Features nutzen |

---

## 7. Zusammenfassung der Zahlen

### 7.1 Code-Metriken (geschaetzt)

| Metrik | Custom | Temporal | Conductor | Restate | Eventuate | Axon |
|--------|--------|----------|-----------|---------|-----------|------|
| Infrastruktur LOC | 3.800 | ~200 | ~300 | ~200 | ~150 | ~200 |
| Infrastruktur-Test LOC | ~4.500 | ~500 | ~600 | ~500 | ~400 | ~300 |
| Business-Logik LOC | 1.015 | ~1.000 | ~1.200 | ~1.000 | ~900 | ~1.100 |
| Business-Test LOC | ~5.400 | ~3.000 | ~3.500 | ~3.000 | ~3.500 | ~2.500 |
| **Total LOC** | **~14.700** | **~4.700** | **~5.600** | **~4.700** | **~4.950** | **~4.100** |
| **LOC-Reduktion** | Baseline | ~68% | ~62% | ~68% | ~66% | ~72% |

### 7.2 Infrastruktur-Vergleich

| Metrik | Custom | Temporal | Conductor | Restate | Eventuate | Axon |
|--------|--------|----------|-----------|---------|-----------|------|
| Externe Dependencies | 0 | +1 Lib + 1 Server | +1 Lib + 1 Server + ES | +1 Lib + 1 Server | +1 Lib + 1 CDC | +1 Lib |
| Neue Container | 0 | +3 | +4 | +1 | +2 | +0-1 |
| Neue Datenbank | Nein | PostgreSQL | PostgreSQL + ES | Nein (intern) | PostgreSQL | PostgreSQL |
| Dashboard | Nein | Ja | Ja | CLI | Nein | Optional |
| Prometheus | Nein | Ja | Ja | Ja | Nein | Via Micrometer |

### 7.3 Entscheidungsmatrix fuer ADR-031 Update

| Frage | Antwort |
|-------|---------|
| Ist die Custom-Loesung fuer 1 Workflow ausreichend? | **Ja.** Stabil, gut getestet, Kafka-nativ. |
| Ab wann lohnt sich ein Framework? | **Ab 2 Workflows** evaluieren, **ab 3 Workflows** migrieren. Frueher bei Primaer-Trigger T2 (Monitoring) oder T3 (manuelle Intervention). |
| Gibt es eine "Option 0"? | **Ja.** Custom-Refactoring (4.5 PT) verschiebt den Kipppunkt um ~2 Workflows. Empfohlen als Sofortmassnahme. |
| Welches Framework bei Kafka-Fokus? | **Restate** (native Subscription, +1 Container, keine DB) |
| Welches Framework bei Observability-Fokus? | **Temporal** (Web UI + Prometheus + OpenTelemetry, 9/9 Limitierungen) |
| Welches Framework bei minimalem Overhead? | **Restate** (Single Binary, kein ext. DB) |
| Welches Framework bei maximalem Feature-Set? | **Temporal** (Signals, Queries, Versioning, Visibility) |
| Welches Framework vermeiden? | **Conductor** (keine native Compensation, hoher Overhead), **Axon** (erfordert CQRS-Umbau), **Eventuate** (fuegt DB hinzu, Bus-Faktor 1) |

### 7.4 Empfohlene Massnahmen — Stufenplan

#### Phase 0: Sofort (0 PT)

- ADR-031 um Framework-Evaluation als "Deferred Decision" ergaenzen mit Verweis auf dieses Dokument
- Trigger-Kriterien (T1-T4, S1-S4) dokumentieren und dem Team kommunizieren
- Issue #1273 bleibt offen als Tracking-Issue fuer die Evaluation

#### Phase 1: Quick Wins fuer Custom-Loesung (3.5-4.5 PT)

| # | Massnahme | Aufwand | Loest |
|---|-----------|---------|-------|
| 1 | Prometheus/Micrometer Metrics zum Saga-Layer | 1 PT | L7 |
| 2 | REST Status-Endpoint `/saga/{id}/status` | 0.5 PT | L4 (teilweise) |
| 3 | `SagaCommandBuilder<T>` Abstraktion (Template Method) | 2-3 PT | Marginal-Cost-Reduktion |

Ergebnis: Marginal-Cost pro Workflow sinkt von ~580 LOC auf ~100 LOC. Monitoring-Basics vorhanden. Custom-Loesung tragfaehig fuer 2-3 Workflows.

#### Phase 2: PoC bei Trigger (3 PT pro PoC)

Wird ausgeloest durch einen Primaer-Trigger (T1-T4) oder 2+ Sekundaer-Trigger.

**PoC-Scope (identisch fuer beide Kandidaten):**
1. Dataset-Create-Saga als Workflow/Handler implementieren (3 Steps: FROST → APISIX → Redpanda)
2. Compensation testen (Redpanda-Deploy schlaegt fehl → APISIX + FROST Rollback)
3. Kafka-Trigger → Workflow starten → Kafka-Result publizieren
4. Metriken: LOC, Testbarkeit, Startup-Zeit, Debugging-Erfahrung, Operational Overhead

**PoC A — Temporal (wenn Observability-Fokus):**
- Temporal Server + PostgreSQL in Docker Compose
- `DatasetProvisioningWorkflow` + `DatasetActivities`
- Bridge-Worker fuer Kafka-Trigger
- Web UI evaluieren

**PoC B — Restate (wenn Kafka-Fokus):**
- Restate Server in Docker Compose
- `DatasetSagaHandler` als Virtual Object
- Native Kafka Subscription
- CLI/Introspection evaluieren

**Entscheidungskriterien nach PoC:**

| Kriterium | Temporal gewinnt wenn... | Restate gewinnt wenn... |
|-----------|-------------------------|------------------------|
| Kafka-Impact | Team akzeptiert Bridge-Pattern | Kafka-Architektur soll unangetastet bleiben |
| Ops-Overhead | Team hat Kapazitaet fuer DB + Server | Minimale Infrastruktur bevorzugt |
| UI/Dashboard | Incident-Response mit UI gewuenscht | CLI-Monitoring ausreichend |
| Community | Langzeitstabilitaet ist Prioritaet | Team ist offen fuer juengeres Projekt |
| Lizenz | — (MIT, kein Problem) | BUSL-1.1 wird vom Projekt akzeptiert |

#### Phase 3: Migration (11-22 PT)

Erst nach positivem PoC-Ergebnis und Team-Entscheidung.

1. Gewaehltes Framework produktionsreif aufsetzen (inkl. HA, Monitoring, Backup)
2. Dataset-Saga migrieren (Parallel-Betrieb mit Feature-Flag)
3. Alte Saga abschalten, Code entfernen (~3.800 LOC Infra + ~4.500 LOC Infra-Tests)
4. 2. Workflow direkt im Framework implementieren (Validierung des Nutzens)
5. Dokumentation und Team-Schulung

### 7.5 TL;DR

> **Die Custom-Loesung ist gut gebaut und funktioniert fuer den aktuellen Use Case.
> Sie hat Schwaechen bei Observability, Skalierung auf neue Workflows und Operational
> Tooling. Die richtige Strategie ist stufenweise:**
>
> 1. **Jetzt:** Quick Wins (Metrics, REST-Endpoint, CommandBuilder-Refactoring) — 3.5-4.5 PT
> 2. **Bei Bedarf fuer 2. Workflow:** PoC mit Temporal (Observability) oder Restate (Kafka-Fit) — 3 PT
> 3. **Bei Bedarf fuer 3+ Workflows:** Vollstaendige Migration zum PoC-Gewinner — 11-22 PT
>
> **Conductor, Axon und Eventuate Tram sind fuer den Civitas Use Case nicht empfohlen.**
> Conductor hat keine native Saga-Compensation, Axon erfordert einen CQRS-Umbau, und
> Eventuate fuegt eine unnoetige DB-Abhaengigkeit hinzu.

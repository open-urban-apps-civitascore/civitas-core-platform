# Saga Orchestrator — Implementation Plan

> **Status**: ABGESCHLOSSEN (14/14 Tasks erledigt, 76 Tests grün)
> **Module**: `config-adapter-orchestrator` (neu)
> **Quellen**: [ADR 030](./adr30-saga.md), [Dataset Use Cases](./SAGA-DATASET-USE-CASES.md), [Saga Design Proposal](./saga.md)
> **Letztes Update**: 2026-02-16

---

## Design-Prinzip

Die Kern-Architektur trennt **pure Logik** von **I/O**:

```
┌─────────────────────────────────────────────────────────┐
│  Schicht 1–5: Kafka-frei, JUnit-testbar                │
│                                                         │
│   SagaStateMachine (pure Funktion)                      │
│     Input:  SagaContext + Event                         │
│     Output: SagaContext + List<SagaAction>              │
│                                                         │
│   SagaEngine (Fassade)                                  │
│     Verbindet StateMachine + StateStore + Dispatcher    │
│     Testbar mit InMemorySagaStateStore + TestDispatcher │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  Schicht 6–7: Kafka-Integration                         │
│                                                         │
│   KafkaSagaStateStore (compacted topic)                 │
│   KafkaSagaStateRecovery (startup replay)               │
│   KafkaSagaActionDispatcher (JSON auf Topics)           │
│   SagaResultConsumer (Adapter Results → Engine)         │
│   SagaTriggerConsumer (Portal Trigger → Engine)         │
│   DatasetSagaOrchestrator (Entry-Point + Wiring)        │
└─────────────────────────────────────────────────────────┘
```

Die `SagaStateMachine` hat **keine Dependencies** auf Kafka, Jackson, etc. — nur auf die Model-Klassen. Alle Szenarien aus dem Use-Cases-Dokument sind als pure Unit Tests abbildbar.

---

## Package-Struktur

```
config-adapter-orchestrator/
  src/main/java/com/civitas/configadapter/orchestrator/
    ├── engine/                        # Kafka-freier Kern
    │   ├── SagaStateMachine.java      # Pure (State + Event) → (State + Actions)
    │   ├── SagaTransitionResult.java  # Rückgabetyp: Context + Actions
    │   ├── SagaAction.java            # Sealed interface: ExecuteStep, CompensateStep, ...
    │   ├── SagaEngine.java            # Fassade: StateMachine + Store + Dispatcher
    │   ├── SagaStepDefinition.java    # Blueprint für einen Step
    │   ├── SagaDefinition.java        # Blueprint für eine Saga
    │   ├── SagaDefinitions.java       # Registry: DATASET_CREATE/UPDATE/DELETE
    │   ├── SagaActionDispatcher.java  # Interface (Kafka-Impl in .kafka)
    │   ├── SagaStateStore.java        # Interface (InMemory + Kafka Impls)
    │   ├── InMemorySagaStateStore.java
    │   └── DatasetCommandBuilder.java # Baut adapter-spezifische Payloads
    ├── kafka/                         # Kafka-Integration
    │   ├── KafkaSagaStateStore.java   # State-Persistenz auf compacted topic
    │   ├── KafkaSagaStateRecovery.java # Startup-Replay des State-Topics
    │   ├── KafkaSagaActionDispatcher.java # Actions → Kafka Messages
    │   ├── SagaResultConsumer.java    # Adapter Results → SagaEngine
    │   └── SagaTriggerConsumer.java   # Portal Trigger → SagaEngine
    └── DatasetSagaOrchestrator.java   # Entry-Point + Wiring
  src/test/java/com/civitas/configadapter/orchestrator/
    └── engine/
        ├── SagaStateMachineTest.java  # 57 Tests (alle Kombinationen)
        └── SagaEngineTest.java        # 14 Tests (Integration)
```

---

## Tasks

### Schicht 1 — Fundament

- [x] **#2** Maven-Modul `config-adapter-orchestrator` anlegen ✅
  - pom.xml mit Parent config-adapter-parent, Dependencies auf config-adapter-api, kafka-clients, jackson, OWASP encoder
  - Root POM: Modul hinzugefügt

### Schicht 2 — Model (in config-adapter-api)

- [x] **#3** Saga Model-Klassen (Records/Enums) implementieren ✅
  - Enums: `SagaType`, `SagaStatus` (mit isTerminal()), `SagaStepStatus`
  - Records: `SagaStep` (mit Transition-Methoden), `SagaFailure`, `SagaContext` (mit with-Methoden)
  - Helper: `SagaContextHelper`

- [x] **#14** ConfigEvent + EventPublisher API-Erweiterungen — **ÜBERSPRUNGEN** ✅
  - Der Orchestrator verwendet eigenes JSON-Nachrichtenformat auf dedizierten Saga-Topics
  - Kein Eingriff in bestehende ConfigEvent-Struktur nötig → kein Backwards-Compatibility-Risiko

### Schicht 3 — Engine-Bausteine (Kafka-frei)

- [x] **#4** SagaStepDefinition + SagaDefinition ✅
  - Factory-Methoden: `mandatory()`, `conditional()`
  - `SagaDefinitions` Registry: 3 Workflows mit Topic-Konstanten

- [x] **#7** SagaStateStore Interface + InMemory-Impl ✅
  - ConcurrentHashMap-basiert, filtert Terminal-Status

### Schicht 4 — Kern-Logik (Pure State Machine)

- [x] **#5** SagaStateMachine ✅
  - Sealed interface `SagaAction` mit 7 Varianten
  - Create/Update: Compensation in reverse order, best-effort
  - Delete: Forward best-effort, keine Compensation
  - `skipRemainingPendingSteps()` bei Compensation-Eintritt (nur Create/Update)
  - `hasPriorFailure()` Check für Delete-Sagas

- [x] **#6** DatasetCommandBuilder ✅
  - Adapter-spezifische Payloads: FROST, APISIX, Redpanda
  - Forward + Compensation Payloads
  - Result-Aggregation mit properties[] Array

- [x] **#9** JUnit Tests für SagaStateMachine — **57 Tests**, alle grün ✅
  - Dataset Create: 16 Tests (Success, Failure at each step, Timeout at each step, Compensation failures, Timeout during compensation, Without-pipelines)
  - Dataset Update: 12 Tests (Success, Failure, Timeout, RESTORE operations)
  - Dataset Delete: 16 Tests (Best-effort forward, Multiple failures, Timeouts, Mixed timeout+failure, Without-pipelines)
  - Structural Guarantees: 13 Tests (PersistState ordering, ID preservation, Unknown stepId, etc.)

### Schicht 5 — Engine-Fassade

- [x] **#8** SagaEngine ✅
  - Fassade: StateMachine + StateStore + Dispatcher
  - Duplicate-Check, Terminal-State Cleanup
  - `processActions()`: PersistState intern, Rest an Dispatcher

- [x] **#10** JUnit Tests für SagaEngine — **14 Tests**, alle grün ✅
  - Happy Path (Create 3 Steps, Without Pipelines, Delete)
  - Failure + Compensation, Compensation Failure
  - Delete Best-Effort (partial, all fail)
  - Timeout (Create, Delete)
  - Duplicate Rejection, Unknown Saga
  - Startup Recovery
  - Test-Double: `CollectingDispatcher` (kein Mockito)

### Schicht 6 — Kafka-Integration

- [x] **#11** KafkaSagaStateStore + Recovery ✅
  - `KafkaSagaStateStore`: ConcurrentHashMap + Kafka compacted topic, sync writes, tombstone removes
  - `KafkaSagaStateRecovery`: Startup replay, terminal state filtering, partition-based consumption

- [x] **#12** KafkaSagaActionDispatcher + Event-Routing ✅
  - `KafkaSagaActionDispatcher`: Pattern-matched dispatch per Action-Typ, JSON messages
  - `SagaResultConsumer`: Adapter result topics → SagaEngine callbacks
  - `SagaTriggerConsumer`: Portal trigger topic → orchestrator.startSaga()
  - Topics: `core.civitas.saga.result`, `core.civitas.saga.manual-intervention`, `core.civitas.dataset.saga.trigger`

### Schicht 7 — Wiring

- [x] **#13** DatasetSagaOrchestrator (Entry-Point) ✅
  - Wiring: Kafka clients, Recovery, Engine, Consumers
  - Lifecycle: initialize() → start() → startSaga() → stop()
  - Shared Kafka producer (idempotent, acks=all)
  - Virtual threads für Consumer-Loops

- [x] **#15** Kafka Integration Tests (Testcontainers) ✅
  - 5 Tests: State Store Round-Trip, Dispatcher Execute, Dispatcher Complete, Result Consumer Routing, Tombstone Recovery
  - Testcontainers `apache/kafka:3.8.0`, Awaitility
  - Echte Kafka-Broker, Topic-Erstellung, Producer/Consumer

---

## Fortschritt

```
Erledigt:  14 von 14 Tasks (100%) ✅
Tests:     76 Tests (57 StateMachine + 14 Engine + 5 Kafka IT), alle grün
```

### Erstellte Dateien

**config-adapter-api** (Model-Klassen):
- `src/main/java/.../model/saga/SagaType.java`
- `src/main/java/.../model/saga/SagaStatus.java`
- `src/main/java/.../model/saga/SagaStepStatus.java`
- `src/main/java/.../model/saga/SagaStep.java`
- `src/main/java/.../model/saga/SagaFailure.java`
- `src/main/java/.../model/saga/SagaContext.java`
- `src/main/java/.../model/saga/SagaContextHelper.java`

**config-adapter-orchestrator** (Engine — Kafka-frei):
- `pom.xml`
- `src/main/java/.../orchestrator/engine/SagaStepDefinition.java`
- `src/main/java/.../orchestrator/engine/SagaDefinition.java`
- `src/main/java/.../orchestrator/engine/SagaDefinitions.java`
- `src/main/java/.../orchestrator/engine/SagaAction.java`
- `src/main/java/.../orchestrator/engine/SagaTransitionResult.java`
- `src/main/java/.../orchestrator/engine/SagaStateMachine.java`
- `src/main/java/.../orchestrator/engine/SagaStateStore.java`
- `src/main/java/.../orchestrator/engine/InMemorySagaStateStore.java`
- `src/main/java/.../orchestrator/engine/SagaActionDispatcher.java`
- `src/main/java/.../orchestrator/engine/SagaEngine.java`
- `src/main/java/.../orchestrator/engine/DatasetCommandBuilder.java`

**config-adapter-orchestrator** (Kafka-Integration):
- `src/main/java/.../orchestrator/kafka/KafkaSagaStateStore.java`
- `src/main/java/.../orchestrator/kafka/KafkaSagaStateRecovery.java`
- `src/main/java/.../orchestrator/kafka/KafkaSagaActionDispatcher.java`
- `src/main/java/.../orchestrator/kafka/SagaResultConsumer.java`
- `src/main/java/.../orchestrator/kafka/SagaTriggerConsumer.java`
- `src/main/java/.../orchestrator/DatasetSagaOrchestrator.java`

**config-adapter-orchestrator** (Tests):
- `src/test/java/.../orchestrator/engine/SagaStateMachineTest.java` (57 Tests)
- `src/test/java/.../orchestrator/engine/SagaEngineTest.java` (14 Tests)
- `src/test/java/.../orchestrator/kafka/SagaKafkaIT.java` (5 Kafka Integration Tests)

---

## Dependency-Graph

```
#2 Maven-Modul ✅
 ├── #3 Model-Klassen ✅
 │    ├── #4 StepDefinition + SagaDefinition ✅
 │    │    ├── #5 SagaStateMachine ✅ ────── #9 StateMachine Tests (57) ✅
 │    │    └── #6 DatasetCommandBuilder ✅
 │    ├── #7 SagaStateStore + InMemory ✅ ── #11 KafkaSagaStateStore ✅
 │    └── #14 ConfigEvent API (übersprungen) ✅
 │
 #5 ✅ + #6 ✅ + #7 ✅
 └── #8 SagaEngine ✅ ──────────────────── #10 Engine Tests (14) ✅
      #8 ✅ + #11 ✅
      └── #12 KafkaDispatcher + Routing ✅
           └── #13 DatasetSagaOrchestrator ✅
                └── #15 Kafka Integration Tests (5) ✅
```

---

## Hinweise

- **Spotless/Google Java Format** funktioniert aktuell nicht (Java 25 Inkompatibilität mit dem Plugin). Code kompiliert sauber.
- **Mockito-Tests in config-adapter-api** haben 6 vorbestehende Fehler (Java 25 Inkompatibilität). Nicht durch unsere Änderungen verursacht.
- **Build-Tipp**: `mvn compile -pl config-adapter-api,config-adapter-orchestrator` für schnelle Validierung
- **Alle Tests**: `mvn test -pl config-adapter-orchestrator -am -Dtest="SagaStateMachineTest,SagaEngineTest,SagaKafkaIT" -Dsurefire.failIfNoSpecifiedTests=false`
- **Architektur-Entscheidung**: Orchestrator verwendet eigenes JSON-Nachrichtenformat statt ConfigEvent. Adapters erhalten Saga-Commands auf dedizierten Topics und antworten mit sagaId/stepId.

---

## Referenzen

- [ADR 030: Orchestrated Saga](./adr30-saga.md) — Architektur-Entscheidung
- [Saga Dataset Use Cases](./SAGA-DATASET-USE-CASES.md) — Alle Szenarien (Create/Update/Delete, Failure, Compensation, Timeout)
- [Saga Design Proposal](./saga.md) — Technisches Design (Interfaces, Model-Klassen, Topic-Struktur)

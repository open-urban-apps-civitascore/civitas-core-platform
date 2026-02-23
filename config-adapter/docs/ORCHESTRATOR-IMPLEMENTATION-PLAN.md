# Saga Orchestrator — Implementation Plan

> **Status**: Phase 1 + Phase 2 abgeschlossen — Phase 3 (Backend-Integration) in Planung
> **Module**: `config-adapter-orchestrator`, `config-adapter-api`
> **Quellen**: [ADR 030](./adr30-saga.md), [Dataset Use Cases](./SAGA-DATASET-USE-CASES.md), [Saga Design Proposal](./saga.md)
> **Letztes Update**: 2026-02-16

---

## Phase 1 — Orchestrator Engine (abgeschlossen)

> 14/14 Tasks, 76 Tests grün. Vollständige Umsetzung der Orchestrator-Engine mit Kafka-Integration.

### Design-Prinzip

Trennung **pure Logik** (Kafka-frei, JUnit-testbar) von **I/O** (Kafka-Integration):

```
Engine-Schicht (Kafka-frei):
  SagaStateMachine → pure (State + Event) → (State + Actions)
  SagaEngine       → Fassade: StateMachine + StateStore + Dispatcher
  DatasetCommandBuilder → Adapter-spezifische Payloads

Kafka-Schicht:
  KafkaSagaStateStore + Recovery → compacted topic
  KafkaSagaActionDispatcher → Actions → Kafka Messages
  SagaResultConsumer / SagaTriggerConsumer → Event-Routing
  DatasetSagaOrchestrator → Entry-Point + Wiring
```

### Erledigte Tasks (Kurzform)

| # | Task | Ergebnis |
|---|------|----------|
| 2 | Maven-Modul anlegen | `config-adapter-orchestrator` mit pom.xml, Parent-Integration |
| 3 | Saga Model-Klassen | `SagaContext`, `SagaStep`, `SagaStatus`, `SagaStepStatus`, `SagaFailure`, `SagaType`, `SagaContextHelper` in `config-adapter-api` |
| 4 | StepDefinition + SagaDefinition | Factory-Methoden `mandatory()`, `conditional()`, Registry `SagaDefinitions` |
| 5 | SagaStateMachine | Sealed `SagaAction` (7 Varianten), Create/Update Compensation, Delete best-effort |
| 6 | DatasetCommandBuilder | FROST/APISIX/Redpanda Payloads, Compensation, Result-Aggregation |
| 7 | SagaStateStore + InMemory | ConcurrentHashMap-basiert, Terminal-Status-Filtering |
| 8 | SagaEngine | Fassade, Duplicate-Check, Terminal-Cleanup, `processActions()` |
| 9 | StateMachine Tests | **57 Tests**: Create (16), Update (12), Delete (16), Structural (13) |
| 10 | Engine Tests | **14 Tests**: Happy Path, Failure, Compensation, Timeout, Recovery |
| 11 | KafkaSagaStateStore + Recovery | Compacted topic, sync writes, startup replay |
| 12 | KafkaDispatcher + Routing | Action dispatch, Result/Trigger consumers |
| 13 | DatasetSagaOrchestrator | Entry-Point, Lifecycle, Virtual Threads |
| 14 | ConfigEvent API-Erweiterung | **Übersprungen** — Orchestrator nutzt eigenes JSON-Format |
| 15 | Kafka IT Tests | **5 Tests**: State round-trip, Dispatcher, Recovery (Testcontainers) |

---

## Zwischenarbeiten — Java 25, Code-Style, Dataset POJOs

### Java 25 Anpassungen

- Spotless/Google Java Format Plugin auf aktuelle Version aktualisiert (Java 25 Kompatibilität)
- Mockito auf Version 4 (Java 25 kompatibel) aktualisiert
- Formatter-Settings angepasst

### Code-Style Refinements

- Code-Formatierung mit `mvn spotless:apply` durchgesetzt
- Spotless-Check in Build-Pipeline integriert

### Test-Ergänzungen

- `DatasetCommandBuilderTest` hinzugefügt (Unit Tests für FROST/APISIX/Redpanda Payload-Building)

### Dataset Jackson POJOs (`config-adapter-api`)

Typisierte POJOs für die Dataset-CloudEvent-Payload:

| Klasse | Typ | Beschreibung |
|--------|-----|--------------|
| `Dataset` | Record | id, name, openDataAccess, datasources, datapipelines |
| `Datasource` | Klasse | Gemeinsame Felder (id, type, name, host, port) + `@JsonAnySetter`/`@JsonAnyGetter` für type-spezifische Properties (ssl_mode, pool, topics, tls, etc.) |
| `DataPipeline` | Record | id, version, action, data (`Map<String, Object>`) |

**Package**: `de.civitascore.configadapter.model.dataset`

**Tests**: `DatasetSerializationTest` (8 Tests) — Deserialisierung aus `dataset-event.json`, Round-Trip, alle Datasource-Typen (PostgreSQL, MQTT), beide Pipeline-Aktionen (ADD, DELETE).

**Design-Entscheidungen**:
- `Datasource` als Klasse statt Record — wegen `@JsonAnySetter` für variable type-spezifische Felder (analog `AbstractApiModel`)
- `DataPipeline.data` als `Map<String, Object>` — Redpanda Connect Pipeline Definition wird durchgereicht
- Kein `ConfigValue`-Interface — Dataset-POJOs sind Payload-Modelle, keine Adapter-Konfigurationen

---

## Phase 2 — Adapter-Integration (abgeschlossen)

> 6 Commits, 33 neue Tests (784 Tests gesamt), alle grün.

### Architektur

Adapter erhalten Saga-Commands als **raw JSON** (nicht CloudEvents) über dedizierte Topics. Ein separater `KafkaSagaCommandConsumer` (parallel zum bestehenden `KafkaEventHandler`) routet Commands an registrierte `SagaCommandHandler`-Implementierungen.

```
Orchestrator                          Adapter-Seite
KafkaSagaActionDispatcher             KafkaSagaCommandConsumer
  │                                      │
  │  raw JSON                            │  ServiceLoader
  ├──► *.frost.execute    ──────────►    ├──► FrostSagaHandler
  ├──► *.frost.compensate ──────────►    │
  ├──► *.apisix.execute   ──────────►    ├──► ApisixSagaHandler
  ├──► *.apisix.compensate ─────────►    │
  │                                      │
  │◄── *.frost.result     ◄─────────    ├──► publishResult()
  │◄── *.apisix.result    ◄─────────    │
```

### Erledigte Commits

| # | Commit | Modul | Neue Tests |
|---|--------|-------|------------|
| 0 | DatasetCommandBuilder POJO-Integration | `config-adapter-orchestrator` | — (bestehende Tests grün) |
| 1 | SagaCommandHandler Interface + SagaCommandMessage/Result | `config-adapter-api` | 12 |
| 2 | FrostSagaHandler | `config-adapter-frost` | 12 |
| 3 | ApisixSagaHandler | `config-adapter-apisix` | 12 |
| 4 | KafkaSagaCommandConsumer | `event-handler-kafka` | 9 |
| 5 | Application Wiring (ServiceLoader + META-INF/services) | `config-adapter-application` | — |

### Neue Klassen

| Klasse | Package | Beschreibung |
|--------|---------|--------------|
| `SagaCommandHandler` | `adapter` | Interface: `adapter()`, `initialize(AdapterConfig)`, `handle(SagaCommandMessage)`, extends `AutoCloseable` |
| `SagaCommandMessage` | `adapter` | Record: Eingehende Saga-Commands mit `fromMap()` (trennt Envelope von Payload) |
| `SagaCommandResult` | `adapter` | Record: Ergebnis mit Factory-Methoden `success()`, `failure()`, `compensationSuccess()`, `compensationFailure()` |
| `FrostSagaHandler` | `frost` | CREATE/UPDATE/DELETE_PROJECT via FROST REST API (JAX-RS) |
| `ApisixSagaHandler` | `apisix` | CREATE/UPDATE/DELETE_ROUTE via APISIX Admin API, deterministische IDs (datasetId), `plugin_config_id` für Auth |
| `KafkaSagaCommandConsumer` | `event.handler.kafka` | Consumer für raw JSON Saga-Messages, Virtual Thread, `ByteArrayDeserializer` |

### Design-Entscheidungen

- **Eigener Consumer statt Erweiterung von `KafkaEventHandler`**: `KafkaEventHandler` nutzt `CloudEventDeserializer` — inkompatibel mit dem raw JSON Format des Orchestrators
- **Eigene Consumer-Group** (`config-adapter-group-saga`): Unabhängig von der CloudEvents-Group, unabhängiges Offset-Management
- **Deterministische IDs bei APISIX**: `PUT /routes/{datasetId}` statt `POST` — idempotent bei Kafka at-least-once
- **`SagaCommandHandler` extends `AutoCloseable`**: Adapter verwalten HTTP-Clients (Jersey `Client`), die bei Shutdown geschlossen werden müssen
- **`SagaCommandMessage.fromMap()`**: Der Orchestrator flacht Payload-Felder in die Top-Level-JSON-Struktur ein. `fromMap()` trennt Envelope-Keys von Payload-Keys

---

## Phase 3 — Backend-Integration & offene Punkte

### TODO: Ressourcen-IDs im Update-Trigger-Payload

**Problem**: Bei `DATASET_UPDATE` und `DATASET_DELETE` brauchen die Adapter die Ressourcen-IDs aus der initialen Provisionierung (CREATE). Diese IDs existieren aktuell nur im Saga-Result des CREATE-Durchlaufs.

**Betroffene IDs:**

| Adapter | CREATE liefert | UPDATE/DELETE braucht |
|---------|---------------|----------------------|
| FROST | `projectId`, `baseUrl` | `projectId` (für PATCH/DELETE) |
| APISIX | `routeId`, `serviceId`, `publicUrl` | `routeId`, `serviceId` (für PUT/DELETE) |

**Lösung**: Das Portal-Backend muss die bei CREATE erhaltenen Ressourcen-IDs (aus dem `ConfigResultEvent.properties`) persistent speichern (z.B. im `DataSet`-Entity) und bei UPDATE/DELETE im Trigger-Payload mitschicken:

```json
{
  "type": "DATASET_UPDATE",
  "datasetId": "ds-001",
  "name": "Updated Name",
  "openDataAccess": true,
  "provisioning": {
    "frost": { "projectId": "42", "baseUrl": "http://frost:8080/v1.1/Projects(42)" },
    "apisix": { "routeId": "ds-001", "serviceId": "ds-001", "publicUrl": "http://..." }
  }
}
```

Der `DatasetCommandBuilder` liest diese IDs aus dem `provisioning`-Objekt und fügt sie in die Adapter-Commands ein. Ohne diese Daten kann der Orchestrator die Update/Delete-Steps nicht korrekt befüllen.

**Offene Punkte Backend-Seite:**
- `DataSet`-Entity im `portal-model` um `provisioningData` (JSON) erweitern
- `DataSetService` speichert CREATE-Results in `provisioningData`
- `EventPublishingService` liest `provisioningData` und fügt es dem Trigger-Payload hinzu

### Weitere offene Punkte

| Bereich | Status | Beschreibung |
|---------|--------|--------------|
| **Provisioning-Daten im Trigger** | **TODO** | Backend muss Ressourcen-IDs bei UPDATE/DELETE mitschicken (s.o.) |
| Recovery Re-dispatch | Offen | IN_PROGRESS Steps nach Crash erneut dispatchen |
| Timeout Scheduler | Offen | `ScheduledExecutorService` für Step-Timeouts |
| Redpanda Adapter | Wartet | Kollege implementiert nach Contract-Spec |

---

## Referenzen

- [ADR 030: Orchestrated Saga](./adr30-saga.md) — Architektur-Entscheidung
- [Saga Dataset Use Cases](./SAGA-DATASET-USE-CASES.md) — Alle Szenarien (Create/Update/Delete, Failure, Compensation, Timeout)
- [Saga Design Proposal](./saga.md) — Technisches Design
- [Orchestrator Architecture](./SAGA-ORCHESTRATOR-ARCHITECTURE.md) — Detailed Architecture & Developer Guide

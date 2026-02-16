# Saga Orchestrator — Implementation Plan

> **Status**: Phase 1 abgeschlossen — Phase 2 (Adapter-Integration) in Planung
> **Module**: `config-adapter-orchestrator`, `config-adapter-api`
> **Quellen**: [ADR 030](./adr30-saga.md), [Dataset Use Cases](./SAGA-DATASET-USE-CASES.md), [Saga Design Proposal](./saga.md)
> **Letztes Update**: 2026-02-17

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

**Package**: `com.civitas.configadapter.model.dataset`

**Tests**: `DatasetSerializationTest` (8 Tests) — Deserialisierung aus `dataset-event.json`, Round-Trip, alle Datasource-Typen (PostgreSQL, MQTT), beide Pipeline-Aktionen (ADD, DELETE).

**Design-Entscheidungen**:
- `Datasource` als Klasse statt Record — wegen `@JsonAnySetter` für variable type-spezifische Felder (analog `AbstractApiModel`)
- `DataPipeline.data` als `Map<String, Object>` — Redpanda Connect Pipeline Definition wird durchgereicht
- Kein `ConfigValue`-Interface — Dataset-POJOs sind Payload-Modelle, keine Adapter-Konfigurationen

---

## Phase 2 — Adapter-Integration (nächste Schritte)

### Überblick: Command Building Flow

Der Orchestrator empfängt ein `Dataset`-JSON vom Portal Backend und muss daraus adapter-spezifische Commands bauen. Jeder Adapter bekommt nur die Informationen, die er braucht.

```
Portal Backend
    │
    ▼  CloudEvent (data: Dataset JSON)
┌─────────────────────────────────────────────┐
│  Orchestrator                                │
│                                              │
│  Dataset JSON → DatasetCommandBuilder        │
│    ├── FROST:   datasetId, name, description │
│    ├── APISIX:  datasetId, baseUrl (von      │
│    │            FROST), openDataAccess        │
│    └── Redpanda: dataPipelines, datasources, │
│                  targetUrl (von FROST)        │
└──┬──────────────┬───────────────┬────────────┘
   │              │               │
   ▼              ▼               ▼
 FROST          APISIX         Redpanda
 Result:        Result:        Result:
 projectId      routeId        pipelineIds
 baseUrl        serviceId
                publicUrl
   │              │               │
   └──────────────┴───────────────┘
                  │
                  ▼
   Orchestrator aggregiert alle Results
   → ConfigResultEvent ans Portal Backend
   → properties[] für Update/Delete-Roundtrip
```

### Task: DatasetCommandBuilder mit typisierten POJOs

**Aktueller Stand**: `DatasetCommandBuilder` arbeitet auf `Map<String, Object>` (untypisiert). Der `triggerPayload` im `SagaContext` ist `Map<String, Object>`.

**Ziel**: Der `DatasetCommandBuilder` soll die neuen `Dataset`/`Datasource`/`DataPipeline`-POJOs nutzen, um typsicher auf die Payload-Felder zuzugreifen. Aktuell passiert z.B.:

```java
// Vorher (untypisiert):
payload.put("datasetName", trigger.get("name"));
payload.put("openDataAccess", trigger.getOrDefault("openDataAccess", false));

// Nachher (typisiert — zu evaluieren):
Dataset dataset = objectMapper.convertValue(trigger, Dataset.class);
payload.put("datasetName", dataset.name());
payload.put("openDataAccess", dataset.openDataAccess());
```

**Offene Frage**: `SagaContext.triggerPayload()` ist `Map<String, Object>`. Soll das geändert werden, oder deserialisiert der `DatasetCommandBuilder` intern?

### Task: FROST Adapter — Saga-Handler

**Was FROST für `CREATE_PROJECT` braucht:**
- `datasetId` → wird als Projekt-Name verwendet
- `name` → Projekt-Beschreibung (Display Name)

**Was FROST zurückliefert:**
- `projectId` — die FROST-interne Projekt-ID
- `baseUrl` — vollständiger Pfad zum Projekt-Endpunkt (z.B. `http://frost:8080/FROST-Server/v1.1/projects/proj-123`)

**Compensation**: `DELETE_PROJECT` mit `projectId`

**Implementierung**:
1. `FrostSagaHandler` im `config-adapter-frost` Modul
2. Routing in `FrostAdapter.doProcessConfigEvent()` über Topic-Check
3. FROST REST API Call: `POST /projects` → projectId + baseUrl

### Task: APISIX Adapter — Saga-Handler

**Entscheidung**: Ein Step im Orchestrator (Option A). Der APISIX-Adapter handelt Service + Route + Plugin-Config-Referenz intern in einem Aufruf ab. Bestätigt durch API-Analyse: Die APISIX Admin API unterstützt `plugin_config_id` als Feld im Route-Objekt — kein separater Request nötig.

**Was APISIX für `CREATE_ROUTE` braucht:**
- `datasetId` → wird zum URI-Segment (`/api/dataspace/{datasetId}/*`)
- `upstreamUrl` — die FROST `baseUrl` aus Step 1
- `openDataAccess` — steuert die Plugin-Konfiguration

**APISIX erstellt pro Dataset:**
1. **Service** (Upstream-Ziel auf FROST) — muss vor Route existieren
2. **Route** (URL-Mapping auf Service) — eine pro Dataset, mit/ohne `plugin_config_id`

**openDataAccess-Logik:**

```
openDataAccess: false (protected)     openDataAccess: true (public)
┌──────────────────────────────┐     ┌──────────────────────────────┐
│ Route: /api/dataspace/ds-1/* │     │ Route: /api/dataspace/ds-1/* │
│   service_id: svc-frost      │     │   service_id: svc-frost      │
│   plugin_config_id: 1 ← auth │     │   (kein plugin_config_id)    │
└──────────────────────────────┘     │   priority: 1 ← bei URI-     │
         │                            │   Überlappung mit protected  │
         ▼                            └──────────────────────────────┘
┌────────────────────────────┐
│ Plugin Config (id: 1)      │
│   openid-connect (JWT)     │
│   opa (with_service: true) │
│   request-id               │
└────────────────────────────┘
```

Plugin Config 1 ist **vorprovisioniert** (nicht vom Adapter verwaltet).

**Update-Szenarien (`UPDATE_ROUTE`):**

| Übergang | Adapter-Aktion |
|----------|----------------|
| `openDataAccess` unverändert | Route-Felder aktualisieren (name, upstream, etc.) |
| `false → true` (protected → public) | `plugin_config_id` entfernen, ggf. `priority: 1` setzen |
| `true → false` (public → protected) | `plugin_config_id: 1` setzen, `priority` entfernen |

Der Adapter speichert den vorherigen `openDataAccess`-Wert als **Compensation-Data**, damit bei fehlgeschlagenem Update der ursprüngliche Zustand wiederhergestellt werden kann (`RESTORE_ROUTE`).

**Was APISIX zurückliefert:**
- `routeId`, `serviceId`, `publicUrl`

**Compensation:**

| Saga-Typ | Compensation |
|----------|-------------|
| Create fehlgeschlagen | `DELETE_ROUTE` — Route löschen (+ Service wenn ungenutzt) |
| Update fehlgeschlagen | `RESTORE_ROUTE` — vorherigen `plugin_config_id`-Zustand wiederherstellen |

**API-Model-Änderung (erledigt):**
`RouteConfigValue` um `plugin_config_id`-Feld erweitert (`Object`-Typ, da APISIX Integer und String akzeptiert). Tests: 32 grün, inkl. protected/public Deserialisierung und Update-Szenarien.

**Implementierung:**
1. `ApisixSagaHandler` im `config-adapter-apisix` Modul
2. Routing in `ApisixAdapter.doProcessConfigEvent()` über Topic-Check
3. `CREATE_ROUTE`: Service anlegen → Route mit/ohne `plugin_config_id` anlegen
4. `UPDATE_ROUTE`: Route aktualisieren, `plugin_config_id` je nach `openDataAccess` setzen/entfernen
5. `DELETE_ROUTE`: Route löschen, Service löschen wenn keine weiteren Routen

### Task: DatasetCommandBuilder — Dataset-POJOs integrieren

Den `DatasetCommandBuilder` erweitern, um:
1. `triggerPayload` als `Dataset`-POJO zu deserialisieren
2. Für FROST: `dataset.name()` + `dataset.id()` extrahieren
3. Für APISIX: `dataset.openDataAccess()` + `baseUrl` aus FROST-Result
4. Für Redpanda: `dataset.datapipelines()` + `dataset.datasources()` durchreichen

**Hinweis APISIX-Update**: Der `DatasetCommandBuilder` liefert `openDataAccess` bereits im APISIX-Payload. Der APISIX-Adapter vergleicht intern den aktuellen Routenzustand mit dem neuen `openDataAccess`-Wert und entscheidet, ob `plugin_config_id` gesetzt oder entfernt werden muss. Der Orchestrator muss dafür keinen Unterschied zwischen Create und Update kennen — die Logik liegt im Adapter.

### Task: Adapter-Subscription und Topic-Konfiguration

- FROST Adapter: `dataset.frost.execute` + `dataset.frost.compensate` zu Topics hinzufügen
- APISIX Adapter: `dataset.apisix.execute` + `dataset.apisix.compensate` zu Topics hinzufügen
- `application.properties` aktualisieren

---

## Phase 3 — Weitere offene Punkte

| Bereich | Status | Beschreibung |
|---------|--------|--------------|
| Recovery Re-dispatch | Offen | IN_PROGRESS Steps nach Crash erneut dispatchen |
| Timeout Scheduler | Offen | `ScheduledExecutorService` für Step-Timeouts |
| Application.java Integration | Offen | ServiceLoader-Discovery des Orchestrators |
| Redpanda Adapter | Wartet | Kollege implementiert nach Contract-Spec |

---

## Referenzen

- [ADR 030: Orchestrated Saga](./adr30-saga.md) — Architektur-Entscheidung
- [Saga Dataset Use Cases](./SAGA-DATASET-USE-CASES.md) — Alle Szenarien (Create/Update/Delete, Failure, Compensation, Timeout)
- [Saga Design Proposal](./saga.md) — Technisches Design
- [Orchestrator Architecture](./SAGA-ORCHESTRATOR-ARCHITECTURE.md) — Detailed Architecture & Developer Guide

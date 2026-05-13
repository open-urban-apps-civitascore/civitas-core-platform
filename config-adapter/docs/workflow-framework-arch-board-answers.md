# Architecture Board Questions

Answers to the questions raised during the architecture board review.

For the detailed footprint and message flow comparison, see [workflow-framework-footprint-and-messageflow.md](workflow-framework-footprint-and-messageflow.md).
For the BSI TR-03187 Open Source Standards assessment, see [workflow-framework-evaluation-matrix.md](workflow-framework-evaluation-matrix.md), Section 7.

---

## 1. How easy/hard to change this decision later?

| Aspect | Flowable | Temporal | Custom (current) |
|---|---|---|---|
| **Workflow definitions** | BPMN — ISO 19510 standard. Portable to Camunda, Activiti, jBPM, etc. | Temporal-specific Java code. Portable to: nothing. | Custom `SagaDefinition` records. Portable to: nothing. |
| **Business logic** | `JavaDelegate` classes — plain Java, reusable | Activity methods — plain Java, reusable | Adapter handlers — plain Java, reusable |
| **Infrastructure to decommission** | Nothing — embedded, just drop DB tables | Go server container, gRPC ports, DB schema | Nothing |
| **Lock-in** | Low (ISO standard + Apache 2.0) | High (proprietary model) | Low (own code) |

**Summary:** In both cases, the business logic (adapter REST calls) stays plain Java and is reusable. The difference is in the orchestration layer: Flowable uses BPMN (ISO standard, portable to other BPMN engines), Temporal uses a proprietary programming model (not portable). Flowable is the more reversible choice.

The time spent discussing this in the arch board is appropriate — it affects infrastructure, security surface, and long-term portability. But it is not irreversible. The adapter code works with either framework.

---

## 2. Added attack surface

| Dimension | Custom (current) | Flowable (embedded) | Temporal |
|---|---|---|---|
| **Additional network ports** | 0 | 0 | +3 (gRPC, Web UI, health) |
| **Additional containers** | 0 | 0 | +1 Go server (min.), +1 Web UI (optional) |
| **Additional DB connections** | 0 | 0 (shared JVM, shared PG) | +1 (Temporal Server → PostgreSQL) |
| **New network paths** | 0 | 0 | Worker ↔ Server (gRPC), Server ↔ PostgreSQL |
| **Permissions** | None new | DB schema rights for `ACT_*` tables | DB rights for Temporal schema, gRPC port exposure, container rights |
| **Dependency risk** | Own code only | +~20MB Flowable JARs (Apache 2.0, 16-year track record) | +Go binary (MIT, 7-year track record) + SDK JARs |

**Summary:**

- **Flowable embedded adds virtually no attack surface** — no new ports, no new containers, no new network paths. The risk is limited to the Flowable library itself as a dependency (supply chain risk), mitigated by its Apache 2.0 license and 16-year track record.
- **Temporal measurably increases the attack surface** — new container, new ports, new network paths (gRPC), new DB connection. Each component needs to be secured, patched, and monitored.
- **Custom has the smallest attack surface** but also the fewest features.

---

## 3. Spring Boot: Is it required for Flowable?

**No.** Flowable runs in plain Java without Spring Boot. The engine is configured programmatically:

```java
ProcessEngine processEngine = ProcessEngineConfiguration
    .createStandaloneProcessEngineConfiguration()
    .setJdbcUrl("jdbc:postgresql://localhost:5432/configadapter")
    .setDatabaseSchemaUpdate("true")
    .setAsyncExecutorActivate(true)
    .buildProcessEngine();
```

Kafka integration works via the existing `KafkaEventHandler` — one line bridges Kafka events to Flowable:

```java
runtimeService.startProcessInstanceByKey("datasetSaga",
    Map.of("datasetId", event.getDatasetId()));
```

Spring Boot is only needed for two convenience features:
- **Kafka Event Registry** — auto-start processes from Kafka topics (not needed, manual bridge is simpler)
- **Actuator Metrics** — Prometheus endpoint (not needed until a Prometheus/Grafana stack is in place)

A full Spring Boot migration would take ~15-20 PT but is not required for Flowable adoption. It can be done later if those features become needed.

# Footprint & Message Flow: Flowable vs. Temporal

## 1. Footprint Comparison

| Dimension | Flowable (embedded) | Temporal |
|---|---|---|
| **Extra processes / containers** | 0 | +1 Go server (~100MB image), optional +1 Web UI |
| **Extra network ports** | 0 | +1 gRPC (7233), +1 Web UI (8080), +1 health (6933) |
| **JAR size in classpath** | ~20 MB | ~5 MB (SDK only, server is separate) |
| **Database tables** | ~36 tables (`ACT_*`) in existing PostgreSQL | ~20 tables in existing PostgreSQL (own schema) |
| **RAM (additional)** | Minimal — shares JVM with application | Go server: ~200-500 MB |
| **Kafka topics (additional)** | 0 (uses existing topics) | 0 (Temporal uses internal gRPC task queues, not Kafka) |
| **Network hops per step** | 0 (everything in-process) | +1 (Worker ↔ Temporal Server via gRPC per activity) |

**Summary:** Flowable has virtually no additional footprint — it runs inside the existing JVM and uses tables in the existing PostgreSQL. Temporal adds a separate server process with its own ports and database connections.

---

## 2. Message Flow

### Flowable (embedded)

```
Portal Backend
    │
    ▼ Kafka: dataset.create.requested
    │
┌───┴─────────────────────────────────────────────┐
│ Config-Adapter (single JVM)                     │
│                                                 │
│  KafkaEventHandler                              │
│       │                                         │
│       ▼                                         │
│  runtimeService.startProcessInstanceByKey()     │
│       │                                         │
│       ▼                                         │
│  ┌─ Flowable Engine (in-process) ─────────────┐ │
│  │                                             │ │
│  │  BPMN: Start                                │ │
│  │    │                                        │ │
│  │    ▼                                        │ │
│  │  ServiceTask: CreateFrostDelegate ──────────┼─┼──► REST: FROST API
│  │    │                                        │ │
│  │    ▼                                        │ │
│  │  ServiceTask: CreateApisixDelegate ─────────┼─┼──► REST: APISIX API
│  │    │                                        │ │
│  │    ▼                                        │ │
│  │  Gateway: hasPipelines?                     │ │
│  │    │ yes                                    │ │
│  │    ▼                                        │ │
│  │  ServiceTask: DeployNifiDelegate ───────────┼─┼──► REST: NiFi API
│  │    │                                        │ │
│  │    ▼                                        │ │
│  │  End ──► HistoryService (PostgreSQL)        │ │
│  │                                             │ │
│  └─────────────────────────────────────────────┘ │
│       │                                         │
│       ▼                                         │
│  KafkaProducer                                  │
│                                                 │
└───┬─────────────────────────────────────────────┘
    │
    ▼ Kafka: dataset.create.completed
    │
Portal Backend
```

Everything in a single process. Kafka only at entry and exit. In between: direct Java method calls within the JVM. State persisted to PostgreSQL (`ACT_*` tables).

### Temporal

```
Portal Backend
    │
    ▼ Kafka: dataset.create.requested
    │
┌───┴──────────────────────────┐
│ Config-Adapter (JVM)         │
│                              │
│  KafkaEventHandler           │
│       │                      │
│       ▼                      │
│  temporalClient              │
│    .startWorkflow() ─────────┼──── gRPC ────┐
│                              │              │
└──────────────────────────────┘              │
                                              ▼
                                   ┌─────────────────────┐
                                   │ Temporal Server (Go) │
                                   │                     │
                                   │  Workflow State      │
                                   │  Task Queues         │
                                   │  Event History       │
                                   │  (PostgreSQL)        │
                                   └──────┬──────────────┘
                                          │
                                    gRPC  │  (per activity)
                                          ▼
┌─────────────────────────────────────────────────────┐
│ Temporal Worker (JVM, can live inside Config-Adapter) │
│                                                     │
│  Activity: createFrostProject() ────────────────────┼──► REST: FROST API
│       │ result via gRPC                             │
│       ▼                                             │
│  Activity: createApisixRoute() ─────────────────────┼──► REST: APISIX API
│       │ result via gRPC                             │
│       ▼                                             │
│  Activity: deployNifiPipelines() ───────────────────┼──► REST: NiFi API
│       │ result via gRPC                             │
│       ▼                                             │
│  Activity: publishResult()                          │
│       │                                             │
└───────┼─────────────────────────────────────────────┘
        │
        ▼ Kafka: dataset.create.completed
        │
Portal Backend
```

Three components. Each activity step is a roundtrip: Worker → Temporal Server (persists state) → Worker. This provides durability (server can crash, worker can crash, state is safe), but adds network overhead.

### Key Difference

| | Flowable | Temporal |
|---|---|---|
| **During the saga** | Everything in-process, one network call per adapter | Per adapter step: 2 gRPC calls to server + 1 REST call to adapter |
| **State persistence** | After each async step directly to PostgreSQL | After each step via gRPC to Temporal Server → PostgreSQL |
| **On crash** | Flowable recovers from PostgreSQL on restart | Temporal Server holds state, worker reconnects automatically |
| **Latency per step** | REST call to adapter (~50ms) | gRPC to server + REST to adapter (~50ms + ~10ms overhead) |

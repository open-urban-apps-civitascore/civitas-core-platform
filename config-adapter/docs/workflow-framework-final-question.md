After evaluating 27 workflow/saga frameworks, applying K.O. filters (Open Source, self-hosted, Java, saga support), and performing detailed technical deep-dives on the remaining candidates, we are down to two viable options: **Flowable** and **Temporal**.

Both frameworks meet all our requirements. The key differentiator comes down to one question about observability and audit.

## Flowable vs. Temporal — Key Difference

| Aspect | Flowable | Temporal |
|---|---|---|
| **Audit approach** | HistoryService — stores which steps ran, duration, inputs/outputs in PostgreSQL. Queryable via Java API. No additional infrastructure needed. OTel can be added later if required. | Native OpenTelemetry — full distributed tracing with spans for workflows, activities, retries. Requires OTel Collector + Jaeger/Tempo + Grafana for visualization. |
| **BPMN / visual modeling** | ✅ BPMN standard, bpmn-js (MIT) embeddable in our own UI as Modeler + live status Viewer | ❌ Code-only, no visual modeling, separate dashboard (not embeddable) |
| **Infrastructure** | Embedded in existing JVM, reuses existing PostgreSQL, no extra container | +1 Go server, reuses existing PostgreSQL, Kafka bridge needed |
| **License** | Apache 2.0 | MIT |

Flowable is the preferred option unless full distributed tracing is a hard requirement.

## Remaining Question

**What does "OpenTelemetry" concretely mean for the saga audit requirements?**

- **(a) DB-based history is sufficient:** Knowing which saga steps ran, how long they took, and what the inputs/outputs were — queryable via API, stored in PostgreSQL, no additional infrastructure. → **Flowable**

- **(b) Full distributed tracing is required:** Trace propagation across services, visualized in Jaeger/Tempo/Grafana with the full OTel Collector stack. → **Temporal**

If the answer is (a), Flowable's built-in HistoryService becomes a strength — audit data without additional infrastructure. OTel can still be added later via the Java Agent or manual instrumentation in the delegates if requirements change.

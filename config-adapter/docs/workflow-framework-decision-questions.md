I've done extensive research into possible saga/workflow frameworks and compiled an overview of the available options with their respective pros and cons. However, I'm not yet in a position to make a well-founded recommendation — there are still too many open questions about our actual requirements and constraints. To help us make the best possible decision, I've put together the following set of questions.

## Questions for the Workflow Framework Decision

### 1. Which workflows are planned?

- **Which additional workflows are planned** beyond the Dataset Lifecycle (Create/Update/Delete)?
  - e.g. Datasource Lifecycle? User Onboarding (Realm + Roles + Groups)? Project Setup?
- **Is there a prioritized roadmap** for the workflows? Which ones come next, which ones later?
- **How many workflows** do we realistically expect within the next 6–12 months?

### 2. Workflow complexity & dependencies

- **Are all future workflows linear/sequential** (like Dataset: FROST → APISIX → RedPanda), or will there also be:
  - **Parallel steps** (e.g. APISIX + RedPanda simultaneously)?
  - **Branching/conditions** (more than just a single "conditional step")?
  - **Nested workflows** (one workflow triggering another)?
- **Which adapters are involved in which workflows?** Is there a matrix?
- **Will new adapters/backend systems be added** that don't exist yet?

### 3. Error handling & compensation

- **How strict must consistency be?** Is "best effort" sufficient (like the current Dataset Delete), or are there workflows that require strict transactional guarantees?
- **What happens on partial failures?** Should the entire workflow be rolled back (compensation), or is a partial state acceptable?
- **What should "manual intervention" look like?** Who gets notified, through which channel, with what SLA?
- **Are there workflows where a failed compensation is critical** (e.g. orphaned resources in Keycloak)?

### 4. Runtime behavior & SLAs

- **What are the latency requirements** per workflow? Must a Dataset Create complete in seconds, or are minutes acceptable?
- **What is the expected throughput volume?** How many workflows per hour/day?
- **Are there batch scenarios** where e.g. 100 datasets are created simultaneously?
- **Does the status of a running workflow need to be visible to the user** (UI tracking)?

### 5. Saga state & persistence

- **How long must the saga state be retained?** Only until completion, or also for auditing?
- **Are there compliance/audit requirements** that demand a persistent history of workflow steps?
- **Must the saga state be recoverable after a crash/restart?** (Currently planned via Kafka — is that sufficient?)

### 6. Idempotency & ordering

- **Can workflows for the same resource run concurrently** (e.g. Create followed immediately by Update for the same dataset)?
- **Do we need ordering guarantees** per resource (no Update before a Create has completed)?
- **How do we handle duplicates?** What happens if the same Create event arrives twice?

### 7. Caller interface

- **Who triggers the workflows?** Only the Portal Backend, or other systems as well?
- **Do we need a synchronous API** (REST endpoint that starts the workflow and returns a tracking ID), or is the Kafka event trigger sufficient?
- **What information does the caller need back?** Just success/failure, or detailed step-by-step status?

### 8. Non-functional constraints

- **Operations**: Who operates the framework? How much operational overhead is acceptable?
- **Infrastructure**: Must it integrate with the existing Kafka/Docker landscape? Is introducing an additional database (e.g. for workflow state) allowed?
- **Team expertise**: Which technologies does the team already know? Is ramp-up time planned for?
- **Licensing**: Must it be open source? Are there requirements (EUPL compatibility)?
- **On-premise**: Must everything run self-hosted, or are cloud components permitted?

### 9. Declarative vs. code-defined workflows

- **Are workflow definitions intended to be declarative/configurable** (e.g. YAML-based, so that new workflows can be defined without code changes)? Or will workflows always be implemented as Java code?
- This question has a direct impact on the framework choice and on security: declarative YAML definitions require an expression engine to resolve placeholders like `{{payload.x}}` or `{{steps.y.output.z}}`, which introduces a template injection attack surface (see #1272). Code-based frameworks (e.g. Temporal) pass data as type-safe Java objects between steps, eliminating this risk entirely.

### 10. Testing & deployment

- **How should multi-adapter workflows be tested?** Do we need a full integration test environment with all backends?
- **Can workflows be deployed independently**, or are there dependencies between workflow versions and adapter versions?

---

**The three most impactful questions for the decision:**

1. **How many and how complex will the upcoming workflows be?** → Sizes the solution
2. **How strict are the consistency requirements?** → Determines feature requirements for the framework
3. **What are the non-functional constraints?** (Operations, infrastructure, team expertise, licensing) → Filters the framework candidates

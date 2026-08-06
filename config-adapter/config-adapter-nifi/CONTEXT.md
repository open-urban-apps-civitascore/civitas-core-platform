# Config Adapter (NiFi) — Context Glossary

A glossary of the domain language used in this module's pipeline-flow subsystem.
Definitions only — no implementation details, no specs. When a term here conflicts with how
code or conversation uses it, the conflict must be resolved and this file updated.

## Pipeline Graph

The editor-authored graph of nodes and edges that is the binding description of one
pipeline's data flow. Wiring decides everything; node existence on the canvas decides
nothing. `start`/`end` are control anchors, not data flow.

## Node Kind / Role

Every graph node has a **kind** (the editor's type string, e.g. `dataSource`, `mapping`,
`frost`) and each kind has exactly one **role**: SOURCE, TRANSFORM, SINK, TRIGGER, or
CONTROL. The closed kind→role table is the single node-type vocabulary; traversal and
validation dispatch on roles, never on raw type strings.

## Flow Path

The linear data path derived from a pipeline graph: exactly one SOURCE node, the ordered
on-path TRANSFORM nodes, exactly one SINK node, plus the optional trigger binding. The
1-source/1-sink cardinality is a deliberate product restriction, not a mechanical
assumption — the walk handles any chain length.

## On-Path Transform

A functional node sitting between source and sink on the data path. Kinds are open-ended
and instances unbounded (N mappings in a chain are legal). Each transform kind owns its
node-payload parsing and its compiled contribution to the processor chain, behind the
transform-node-type descriptor seam.

**Chained mapping nodes are only shallowly supported.** Each node compiles in isolation,
against the paths it was authored with — nothing derives the shape its predecessor actually
emits, so a node's compilation cannot depend on what came before it. Two consequences worth
knowing before extending this: the handover between neighbours is checked on declared
structure URNs rather than on real shapes, and a node deriving a fan-out from its own source
paths is blind to a fan-out an earlier node already applied (the array is gone from the
record, so its `ForkRecord` fails loudly on the `failure` route). Carrying the emitted shape
along the chain is the change that would fix these at the root; a multi-node
mapping chain is a thin path rather than a supported general case.

## Stage

The deployable half of a source/sink/transform: binds resolved configuration at plan time
and mints NiFi processors from the curated fragment whitelist at build time. Stages are
hand-wired into a closed registry — deliberately not classpath-discovered, so minting flow
components stays a reviewed decision.

## HTTP Response Use

What an HTTP request in a sink's build region does with its response: read it as content (a
downstream EvaluateJsonPath needs the body), capture it into an attribute for the error sink,
capture and end, or end there. It is one decision with the relationship the request's success
continues on, never two — capturing the body suppresses the response FlowFile, so the continuation
follows from the use rather than being chosen beside it. A stage states the use; the relationship
follows.

## Sink Spec

The resolved, typed configuration of one pipeline's sink — one sealed variant per sink kind, each
carrying only its own fields with its own invariants (PostGIS: table name + primary-key columns;
FROST: the saga's project id plus the schema-derived match keys). Parsed from the raw catalog entry
by the sink's own stage; the deployment request stays sink-agnostic.

## Sink Pre-Region

A plan-time handoff from the transform compilation to the sink's build region — data only the
compilation can produce but only the sink consumes (the FROST entity plan for a mapped
FROST sink). The slot is sink-neutral; each sink validates in its build half that it received a
variant it can consume and rejects any other.

## Payload Form

The shape of the data flowing over an edge: `RAW_JSON`, `STA_ENVELOPE`, or `RECORDS`.
Sources declare their output form, sinks declare accepted forms; convertibility to RECORDS
(the implicit ConvertRecord) is declared once as a coercion table. The frontend editor
mirrors this vocabulary — both sides must give the same verdict on the same graph.

## Compiled Transform

The compiled unit of one on-path transform node, carried in flow order in the build spec.
Its chain index counts positions **within its own kind** (not globally across all
transforms), so inserting a node of another kind later never shifts existing NiFi
component ids — redeploy stability depends on this.

## Fan-out

One record per element of a source array, materialised as a `ForkRecord` ahead of a mapping
node's own processors. Derived from the mapping's **source** paths only: how many records a
payload carries is a question the target side cannot answer, since a target array selector
marks an entity tier (FROST) or selects into an array the record already carries (PostGIS).
Several source paths on one hierarchical line fork on the **innermost** array; outer levels
ride along as parent fields. A rule whose *target* keeps its array level is an **in-place
rewrite** instead — it needs its array to survive, so it cannot share a mapping with a fan-out.
Paths compiled against the forked record are **post-fork paths**: the element's own fields sit
at the root, so they are plain root-level selections rather than relative ones.

Shapes that would fan out into nothing, into ambiguity, or into silently duplicated data are
rejected at compile time; a fan-out that still yields zero records at runtime is routed to the
error sink rather than travelling the chain as an empty success.

## Trigger Binding

A cron node schedules the source's entry processor; it is not data flow. A source's
trigger port holds at most one schedule, and only sources that accept a schedule may be
triggered.

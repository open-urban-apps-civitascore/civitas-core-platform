# OGC SensorThings API (FROST) — core data model

JSON Schema (2020-12) representation of the [OGC SensorThings API](https://www.ogc.org/standards/sensorthings/)
data model as implemented by the Fraunhofer IOSB **FROST Server** — a single document
(`sta.schema.json`) with the nine entities as `$defs`. The document itself carries no shape
of its own (no `type`/`properties`/`$id` — just `$schema`/`title`/`description`/`$defs`), so
it is a pure container, not a tenth Element: importing it creates **one DataStructure
grouping nine Elements**, mirroring how Model Forge distinguishes a DataStructure (the JSON
Schema document) from the Elements it defines.

## Entities

| Entity | CORE URN |
|---|---|
| Thing | `urn:core:platform:civitas:element:sta:Thing:h2s9s5nlvw:1.0.0` |
| Location | `urn:core:platform:civitas:element:sta:Location:l2p8y4efpu:1.0.0` |
| HistoricalLocation | `urn:core:platform:civitas:element:sta:HistoricalLocation:kpu3mldt72:1.0.0` |
| Datastream | `urn:core:platform:civitas:element:sta:Datastream:s04brbdcz9:1.0.0` |
| Sensor | `urn:core:platform:civitas:element:sta:Sensor:m8i4hc3h56:1.0.0` |
| ObservedProperty | `urn:core:platform:civitas:element:sta:ObservedProperty:yduda2m190:1.0.0` |
| Observation | `urn:core:platform:civitas:element:sta:Observation:ulhry9fjx6:1.0.0` |
| FeatureOfInterest | `urn:core:platform:civitas:element:sta:FeatureOfInterest:an2vwx133n:1.0.0` |
| UnitOfMeasurement | `urn:core:platform:civitas:element:sta:UnitOfMeasurement:fy7igxkv4b:1.0.0` |

Each `$defs` entry keeps the same explicit CORE-URN `$id` it always had, so every entity is
still individually addressable and re-imports still land on the same identities; only the
file layout and the relation encoding (below) changed.

## Composition vs. association: `$ref` vs. `x-core-ref`

Of the nine entities, only **UnitOfMeasurement** has no independent identity in STA — it has
no own FROST endpoint and is always embedded verbatim in its owning Datastream. That is
genuine **composition**, so `Datastream.unitOfMeasurement` is the one relation still modelled
as a `$ref` (embed by value).

Every other cross-entity relation (`Thing.Datastreams`, `Datastream.Thing`,
`Datastream.Sensor`, `Observation.FeatureOfInterest`, …) links two independently identified,
independently addressable STA resources — each with its own FROST endpoint
(`/Things`, `/Sensors`, `/Datastreams`, …) and its own lifecycle. That is a UML
**association**, so these are modelled as `x-core-ref` foreign keys: a plain URN `string`
(or an array of them for `*` cardinalities), existence-checked against the registry rather
than embedded:

```json
"Thing": {
  "type": "string",
  "pattern": "^urn:",
  "x-core-ref": { "type": "urn:core:platform:civitas:element:sta:Thing:h2s9s5nlvw:1.0.0" }
}
```

See [Data Modelling § References between schemas](../../docs/concepts/data-modelling.md#references-between-schemas)
for the general rule.

## How references work

The navigation properties are modelled **bidirectionally**, exactly as in STA — e.g.
`Datastream.Thing` ⇄ `Thing.Datastreams`, `Observation.Datastream` ⇄
`Datastream.Observations`. The reference graph therefore contains **cycles**, which Model
Forge stores and resolves transparently.

`x-core-ref` foreign keys are existence-checked at import time, so — unlike plain `$ref` —
order would normally matter. It doesn't here: all nine entities are `$defs` of the **same**
`importSchema` request, and Model Forge treats every element co-imported in one request as a
valid foreign-key target regardless of internal ordering (the same allowance the startup
bootstrap corpus relies on). How Model Forge builds the full graph from content is described
in the concepts — see [ADR-09](../../docs/concepts/architecture-decisions.md).

## Loading as seed data

This schema ships inside the **Model Forge Admin UI** as bundled seed data
(`model-forge-admin-ui/src/main/resources/seed/sta/sta.schema.json`). The admin-ui imports it
on startup when seeding is enabled — **off by default**, because the UI points at
portal-backend's registry and must not seed the STA examples into it. Enable it only for a
standalone instance with its own database:

```yaml
# application.yml default is `false`
model-forge:
  admin-ui:
    seed:
      enabled: true
```

The seeder is idempotent — skipped once its first entity (`Thing`) logical URN already
resolves (the document itself has no `$id` to probe, being a pure container). It uses the
ordinary facade entry point, the same one any host would call:

```java
modelForge.importSchema(new ImportSchemaCommand(schema));
```

To load it into another host, feed the document to `ModelForge.importSchema` the same way.
After import, explore each entity and its views through the facade (`getArtifact`,
`getBundledView` for the bundled view, `getInlinedView` for the inlined view, `dependencies` /
`dependents` for the graph).

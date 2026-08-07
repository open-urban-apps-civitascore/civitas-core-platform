# GeoServer Config Adapter

Drives the [GeoServer Cloud](https://geoserver.org/geoserver-cloud/) management REST API — workspaces, datastores,
feature types, coverage stores, coverages, styles and layers. Two entry points: a config adapter applying one CloudEvent to one REST resource,
and a saga command handler publishing a dataset's PostGIS tables as WFS/WMS layers. Data-plane access to the WFS/WMS
endpoints is authorized by the gateway, not here.

## Config event operations

`targetResource` is the REST path relative to `/rest/`. The resource type is the last collection keyword at an even
path index; the last segment is the resource name unless it is itself a collection keyword. CREATE, UPDATE and
DELETE are accepted for every resolved type, with the body taken from `config.value`.

| Resource type | Collection path | `recurse=true` on DELETE |
|---|---|:--:|
| Workspace | `workspaces` | yes |
| Datastore | `workspaces/{ws}/datastores` | yes |
| Coverage store | `workspaces/{ws}/coveragestores` | yes |
| Feature type | `workspaces/{ws}/datastores/{ds}/featuretypes` | yes |
| Coverage | `workspaces/{ws}/coveragestores/{cs}/coverages` | yes |
| Style | `styles`, `workspaces/{ws}/styles` | no |
| Layer | `layers`, `workspaces/{ws}/layers` | no |

A store delete would otherwise leave orphaned layers, and a feature-type or coverage delete fails while its published
layer references it; styles and layers stay non-recursive so a shared style is never cascade-deleted.

Rejected: a `/`-delimited path segment outside `A-Za-z0-9_-` (excluding `.`/`..` traversal, empty segments,
percent-encoding, backslashes); a path resolving to no known collection type, including `layergroups` and
`namespaces`; UPDATE or DELETE without a resource name; CREATE or UPDATE without a `config.value`; any operation
other than the three above.

`geoserver.topics` selects from `de.civitascore.geo.<resource>.<action>`, where resource is `workspace`,
`datastore`, `featuretype` or `style` with actions `created`/`updated`/`deleted`, plus `layer` with `updated` and
`deleted` only — a layer appears implicitly when its feature type or coverage is published, so there is no
`layer.created`. Coverage stores and coverages have no topics and are reachable by path only. Results go to the
`resultTopic` from the request metadata, with type `de.civitascore.geo.processing.result`.

`config.value` carries a typed model whose `resourceType` is the Jackson discriminator, each rendering the
wrapped REST body: `geoserver-workspace` → `{"workspace": {…}}`, `geoserver-datastore` → `{"dataStore": {…,
"connectionParameters": {"entry": […]}}}`, `geoserver-featuretype` → `{"featureType": {…}}`, `geoserver-layer` →
`{"layer": {…}}` with `defaultStyle` wrapped as `{"name": …}`, `geoserver-style` → `{"style": {…}}` with
`languageVersion` as `{"version": …}` and `workspace` as `{"name": …}`.

## Saga operations

Dispatched in-process by the orchestrator; saga steps do not travel over Kafka. `layers` alone drives feature-type
publication and pruning; `datasinks` only resolves native table names and the native CRS.

| Operation | Effect | Compensation |
|---|---|---|
| `CREATE_WORKSPACE` | Isolated workspace; on fresh creation enables and titles the workspace-local WMS service | `DELETE_WORKSPACE` |
| `CREATE_DATASTORE` | PostGIS datastore inside the workspace | `DELETE_WORKSPACE` |
| `PROVISION_LAYERS` | Uploads `styles`, publishes a feature type per `layers` entry, assigns styles | `DELETE_WORKSPACE` |
| `PROVISION_WORKSPACE` | The three create steps in one call; no saga process dispatches it | — |
| `UPDATE_WORKSPACE` | Ensures workspace and datastore exist, converges feature types from `layers`, snapshots the current ones | `RESTORE_WORKSPACE` |
| `PRUNE_FEATURE_TYPES` | Deletes feature types that no `layers` entry names | — terminal, see Behaviour |
| `DELETE_WORKSPACE` | Recursive workspace delete | — |
| `RESTORE_WORKSPACE` | Deletes feature types absent from the snapshot, then restores the snapshot | — |

| Payload field | Role |
|---|---|
| `datasetId` | Required. Source of the workspace name. |
| `workspaceName` | Used verbatim when present and matching `a-z0-9_`; otherwise derived from `datasetId`. |
| `datasetName` | Title of the workspace-local WMS service. Falls back to the workspace name. |
| `datasinks[]` | `POSTGIS` entries only: `configuration.tableName` is the native-table candidate, `dataStructure` supplies the geometry's native CRS. |
| `layers[]` | One published feature type each: `layerName` (required), `nativeName`, `crs`, `geometryColumnRef`, `nativeBoundingBox`, `defaultStyle`, `alternativeStyles`. |
| `styles[]` | `{name, sldContent}`, uploaded before feature types are published. |

## Behaviour

- **Naming.** Workspace name = `datasetId` lowercased, characters outside `a-z0-9_` replaced by `_`, prefixed `ds_`
  when the result would start with a digit (it becomes an XML namespace prefix in OGC capabilities, and an NCName
  MUST NOT start with a digit). Datastore name appends `_postgis`; its schema *is* the workspace name, so GeoServer
  reads exactly the schema the PostGIS sink created. The mapping is lossy, so dataset ids MUST be unique under it.
- **Workspaces are isolated**, each with its own namespace, otherwise the WMS layer-by-name lookup fails and
  same-named layers across datasets collide. The workspace-local WMS service is enabled and titled with
  `datasetName`; failure to set it is logged, not fatal. WFS stays global — a workspace-local WFS created over REST
  has no service level, breaking its `GetCapabilities`.
- **"Already exists" converges.** GeoServer signals it on POST as HTTP 409, or as HTTP 500 whose body contains
  `already exists`; both are absorbed. Workspace and feature-type creation treat it as success; datastore creation
  and `UPDATE_WORKSPACE` feature types fall through to a `PUT`, so stale connection parameters and definitions
  converge instead of a re-POST reporting an unapplied success.
- **Styles.** A style POST for an existing name answers HTTP 403, not 409; the upsert branches on 403 and refreshes
  the SLD by `PUT`, and a genuine authorization 403 surfaces on that same `PUT`. Assignment to a layer is verified
  by read-back: a `PUT` with an unresolvable style reference answers HTTP 200 while keeping the previous style, so
  the layer is re-read and the step fails when the expected styles are absent.
- **The native CRS is set explicitly**, from the sink data structure's geometry — the value the PostGIS adapter
  turns into the column SRID. GeoServer does not detect it on REST feature-type creation, leaving the layer invalid
  under `REPROJECT_TO_DECLARED`. A supplied `nativeBoundingBox` is forwarded with only the lat/lon box
  recalculated; without one GeoServer computes both.
- **Deletes are recursive and idempotent**: workspace and feature-type deletes send `recurse=true` and treat HTTP
  404 as success, so a compensation chain is safe whether or not the geo branch ran. On the config-event path, 409
  on CREATE and 404 on DELETE are likewise successes; a 201 CREATE reads the resource name from the percent-decoded
  `Location` header, an absorbed 409 — which has none — from the request body.
- **`PRUNE_FEATURE_TYPES` is terminal and uncompensatable**: the `UPDATE_WORKSPACE` snapshot lists names without
  definitions, so a deleted feature type cannot be restored and the prune MUST run after the last failable step. An
  absent `layers` field means no layers remain, not "unknown", so an empty desired set prunes everything. An
  undeletable feature type is reported as `staleFeatureTypes` and the step still succeeds.
- **`RESTORE_WORKSPACE` skips rather than guesses**: with no snapshot it does nothing, rather than read the absence
  as an empty workspace and delete pre-existing feature types. Styles are not snapshotted; only a recursive
  `DELETE_WORKSPACE` removes them.
- **Credentials.** Basic Auth; `geoserver.admin.user` and `geoserver.admin.password` MUST both be non-blank or
  initialization fails. That password, `geoserver.postgis.password` and an incoming datastore model's `passwd`
  accept an `ENC(<base64>)` value, decrypted in memory at use; plaintext passes through. A missing master key is
  logged at startup and fails any `ENC(…)` value.

## Configuration

Property keys carry the `geoserver.` prefix. The values that ship, env vars and secret handling live in
[../DEPLOYMENT.md](../DEPLOYMENT.md).

| Property | Role |
|---|---|
| `geoserver.topics` | Required for config events; without it the adapter subscribes to nothing |
| `geoserver.url` | Base URL of the management REST API; trailing slashes stripped |
| `geoserver.public.url` | Base of the WFS/WMS URLs reported in saga results |
| `geoserver.admin.user` / `.password` | MUST both resolve non-blank or initialization fails; the password accepts `ENC(…)` |
| `geoserver.postgis.host` / `.port` / `.database` | Datastore connection the workspace publishes from |
| `geoserver.postgis.user` / `.password` | Required for saga steps; the password accepts `ENC(…)` |

No `geoserver.postgis.schema` is read: the datastore schema is the derived workspace name. The `postgis.*` keys
are read by the saga handler only.

## Error codes

| Code | Meaning | Retryable |
|---|---|---|
| `INVALID_PAYLOAD(1001)` | Missing operation, blank or unsafe `targetResource`, missing resource name, or absent `config.value` | no |
| `UNSUPPORTED_OPERATION(1004)` | Operation is not CREATE, UPDATE or DELETE | no |
| `INVALID_RESOURCE_TYPE(1005)` | Path resolves to no known collection type | no |
| `SERVICE_UNAVAILABLE(2002)` | GeoServer answered 5xx | yes |
| `NETWORK_ERROR(2003)` | The request did not reach GeoServer | yes |
| `GEOSERVER_RESOURCE_ERROR(3402)` | GeoServer answered 4xx, or the request failed unexpectedly | no |

A saga step emits no code: it returns a step failure carrying the HTTP status and a bounded excerpt of the response
body, from which the orchestrator drives compensation.

## Testing

```bash
mvn test -pl config-adapter-geoserver          # unit tests, no Docker
mvn -pl config-adapter-geoserver -am verify    # adds integration tests, requires Docker
```

Integration tests run against a GeoServer Cloud stack — PostGIS, RabbitMQ, discovery, config and REST
containers — started once per JVM by Testcontainers; image tags are pinned in the test sources, the shared ones
in `TestContainerImages`. Failsafe supplies `--add-opens java.base/java.net=ALL-UNNAMED`, which Jersey's PATCH
support needs.

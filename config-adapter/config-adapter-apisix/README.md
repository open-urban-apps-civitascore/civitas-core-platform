# APISIX Config Adapter

Adapter for [Apache APISIX](https://apisix.apache.org/) gateway configuration, reaching APISIX only
through the Admin API. An event-driven `ConfigAdapter` applies CRUD on **upstreams** and **routes** from
CloudEvents on Kafka; an `ApisixSagaHandler` provisions a dataset's **published-data routes** from saga
commands. Services, plugin configs, SSL certificates, consumers and global rules are provisioned outside
the adapter and referenced by id.

## Operations

### Config events

`targetResource` has the form `upstreams[/{id}]` or `routes[/{id}]` (either segment pair may sit inside a
longer path) and selects the resource kind and optional id; naming neither kind is fatal.

| Resource | CREATE | UPDATE | DELETE |
|---|---|---|---|
| Upstream | `POST /apisix/admin/upstreams` (APISIX assigns the id) | `PUT …/upstreams/{id}` | `DELETE …/upstreams/{id}` |
| Route | `POST /apisix/admin/routes` (APISIX assigns the id) | `PUT …/routes/{id}` | `DELETE …/routes/{id}` |

`config.value` is forwarded to APISIX verbatim. The adapter validates no upstream field and no plugin
configuration; APISIX does, and rejects an invalid body with HTTP 400. Body schema:
[Admin API reference](https://apisix.apache.org/docs/apisix/admin-api/).

`apisix.topics` selects from `de.civitascore.api.backend.{created,updated,deleted}` (upstreams) and
`de.civitascore.api.route.{created,updated,deleted}` (routes); each value is validated against the
framework's topic registry. Results go to the `resultTopic` from the incoming metadata, with type
`de.civitascore.api.processing.result` and source `de.civitascore.config-adapter.apisix`; a failure
carries `status: FAILURE`, an `errorCode` of the form `NAME(code)` and a safe `message`.

### Saga commands

Dataset routes are **not** driven by the topics above.

| Operation | Effect | Compensation |
|---|---|---|
| `CREATE_ROUTE` | Creates the dataset upstream(s) and one route per named-API slug; returns the slug-keyed `routeIds` map, `serviceId` and `publicUrl` | `DELETE_ROUTE` |
| `UPDATE_ROUTE` | Reads each slug route and re-applies the protected shape | `RESTORE_ROUTE` |
| `DELETE_ROUTE` | Deletes each slug route, then the dataset upstream(s) | — |
| `RESTORE_ROUTE` | Re-applies the protected shape to each slug route | — |

A named API whose `standard` is neither `STA` (FROST SensorThings) nor `OWS` (GeoServer WFS/WMS) is not
routable: `CREATE_ROUTE` fails on it before any gateway state is created. A blank standard means `STA`.

## Behaviour

Publication is per named API, not per dataset.

- **Up to two upstreams per dataset**, only the kinds a slug uses: the FROST-project upstream keyed by the
  bare `datasetId` (`STA` slugs) and the map-server upstream keyed by `{datasetId}-ows` (`OWS` slugs). Both
  single-node `roundrobin`; node, scheme and path come from the command's `upstreamUrl` for FROST and from
  `apisix.geoserver.url` for the map server.
- **One route per slug** at `/v1/datasets/{datasetId}/{slug}` and `…/{slug}/*`, with a deterministic id — a
  name-based UUID over `datasetId + "/" + slug`. The portal-backend persists the returned `routeIds` map
  onto each named API and builds each API's URL as `publicUrl` + `/{slug}`.
- No named APIs means no route and no upstream; `UPDATE_ROUTE`, `DELETE_ROUTE` and `RESTORE_ROUTE` are then
  no-ops.
- Every dataset route pins `hosts` to `apisix.api.host` and carries `service_id` from `apisix.service.id`
  plus `status: 1`. Host pinning and the `/v1/datasets/{id}` prefix — strictly more specific than a `/v1/*`
  catch-all — make dispatch deterministic without a second virtual host.
- `proxy-rewrite` maps gateway path to upstream path via
  `regex_uri: ["^{routePath}(/.*)?$", "{upstreamPath}$1"]`, and APISIX preserves the query string. The
  upstream path is the FROST project path for `STA` and `{geoserver path}/{workspace}/ows` for `OWS`, the
  workspace name deriving from the dataset id by the same rule the GeoServer adapter applies.
- `OWS` routes carry a `response-rewrite` filter rewriting GeoServer's self-referential capabilities URLs
  onto the route's external endpoint, and strip the request `Accept-Encoding`, because that filter matches
  raw response bytes and would otherwise pass a gzipped capabilities document through unchanged.
- `UPDATE_ROUTE` and `RESTORE_ROUTE` read the route back without the dataset's named-API metadata, so two
  labels carry that state: `civitas-frost-upstream-auth-header` names the credential header the adapter
  injected, so a re-apply removes a stale entry after a scheme or header-name change, and
  `civitas-named-api-standard` is `OWS` on map-service routes (absent means `STA`).

**Authorization.** Every published-data route is protected; there is no unprotected variant. Each carries
`plugin_config_id` from `apisix.plugin.config.id` and no `methods` filter — an existing one is removed,
because the per-request OPA decision is the gate. Open-data access is that decision, not a route shape:
OPA grants or denies the anonymous read from the dataset's open-data flag and denies writes and protected
datasets. The referenced plugin config MUST enforce OIDC with `unauth_action: pass` (an anonymous request
continues to OPA rather than being rejected at the gateway; a present bearer token is validated and its
claims forwarded) and OPA with `with_service: true` (OPA resolves the backend policy from the route's
`service_id`). A `service_id` matching no provisioned APISIX service makes OPA reject every dataset route
as `unknown_backend`, which is why `apisix.service.id` is required. The FROST project itself is private and
is reached with the upstream credential.

**Upstream credentials and stripped headers.** `STA` routes inject the FROST credential into
`proxy-rewrite.headers.set` — `Authorization: Basic <base64>` from `apisix.frost.basic.auth.*`, or the
configured API-key header from `apisix.frost.api.key[.header]`; Basic Auth wins when both are configured.
`OWS` routes carry no upstream credential, since GeoServer serves the workspace OWS endpoint anonymously,
and one left there by a different configuration is removed. `X-Allowed-Scope-Ids` and `X-Allowed-Pool-Ids`
are always in `proxy-rewrite.headers.remove` and cannot be disabled: OPA sets them and downstream services
trust them, so a client-supplied value MUST NOT reach the backend.
`apisix.proxy.rewrite.headers.remove` adds names on top of that baseline. APISIX resolves plugins by
route-over-plugin-config precedence, so the route-level `proxy-rewrite` overrides any strip list in the
shared plugin config — every header the gateway strips there MUST be mirrored in this property. Merging
preserves foreign `headers.set`, `headers.add` and `headers.remove` entries.

**Idempotency, drift and cleanup.** Deterministic ids make every saga write a `PUT` and therefore
retry-safe, and deletes tolerate a 404. In the config-event path, HTTP 409 on a CREATE and HTTP 404 on a
DELETE are absorbed as success.

- `CREATE_ROUTE` failing mid-provisioning removes the routes and upstreams it already created before
  propagating, so a failed step leaves no orphaned gateway state.
- `UPDATE_ROUTE` is all-or-nothing: a named API without a persisted route id, or a target route absent from
  the gateway, fails the step before the first write, since a partial change would leave a mixed
  authorization state. A compensation re-run skips an absent route.
- `DELETE_ROUTE` heals a drifted route map by deriving the deterministic id for any named API whose id is
  missing, and attempts both possible upstreams. A forward delete removing none of its target routes is
  logged as a warning: the gateway may hold routes under other ids.
- An upstream delete rejected with HTTP 400 and `route [...] is still using it now` reflects a worker's
  route-cache lag, not a client error: the saga path retries it three times with backoff, the config-event
  path classifies it retryable.

## Configuration

Keys carry the `apisix.` prefix. Env-var names, production values and gateway provisioning live in
[../DEPLOYMENT.md](../DEPLOYMENT.md).

| Property | Coded default | Used by |
|---|---|---|
| `apisix.topics` | — | events |
| `apisix.admin.url` | `http://localhost:9180` | both |
| `apisix.admin.key` | — **required** | both |
| `apisix.api.host` | — **required** | saga |
| `apisix.api.public.url` | — **required** | saga |
| `apisix.plugin.config.id` | — **required** | saga |
| `apisix.service.id` | — **required** | saga |
| `apisix.frost.basic.auth.username` | — | saga |
| `apisix.frost.basic.auth.password` | — | saga |
| `apisix.frost.api.key` | — | saga |
| `apisix.frost.api.key.header` | `X-API-Key` | saga |
| `apisix.geoserver.url` | `http://localhost:8080/geoserver` | saga |
| `apisix.proxy.rewrite.headers.remove` | — | saga |

Either `apisix.frost.basic.auth.username` (with its password) or `apisix.frost.api.key` MUST be set, and
`apisix.frost.api.key.header` MUST NOT be blank while an API key is set. A blank `apisix.admin.key` and
each missing required saga setting fail initialization with a message naming the key, so the adapter never
starts with a partial gateway configuration. `apisix.geoserver.url` is validated when an `OWS` slug is
routed, not at startup. Trailing slashes are stripped from `apisix.api.public.url` and
`apisix.geoserver.url`.

## Error codes

Config-event failures publish a `FAILURE` result with one of these codes; retryable ones go through the
framework's exponential backoff, fatal ones straight to the DLQ. Saga commands do not use these codes: a
failed step returns a command failure carrying the Admin API status and message, and the orchestrator
drives compensation.

| Code | Meaning | Retryable |
|---|---|---|
| `INVALID_RESOURCE_TYPE(1005)` | `targetResource` names neither `upstreams` nor `routes` | no |
| `APISIX_ROUTE_ERROR(3102)` | Admin API rejected a route call with HTTP 4xx other than the absorbed 409/404, or the call failed unexpectedly | no |
| `APISIX_UPSTREAM_ERROR(3103)` | Admin API rejected an upstream call with HTTP 4xx other than the absorbed 409/404, or the call failed unexpectedly | no |
| `SERVICE_UNAVAILABLE(2002)` | Admin API returned HTTP 5xx, or refused an upstream delete for a route that references it | yes |
| `NETWORK_ERROR(2003)` | Admin API unreachable | yes |

## Testing

```bash
mvn test -pl config-adapter-apisix                           # unit tests, no Docker
mvn verify -pl config-adapter-apisix                         # adds APISIX + etcd integration tests
mvn verify -pl config-adapter-apisix -Dit.test=ApisixRouteIT # a single integration test class
```

Integration tests start APISIX and etcd through Testcontainers, with image versions in the shared
`TestContainerImages` constants. They require the JVM argument
`-Djdk.httpclient.allowRestrictedHeaders=host`, which the module's failsafe configuration sets.

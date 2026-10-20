# NiFi extensions

The NiFi components the platform installs into its NiFi deployment. The config adapter emits flows
that use them; the components themselves know nothing about the adapter.

| Module | Holds |
|---|---|
| `nifi-frost-processors` | The code: `PutFrostRecord`, the ports and the batch builder |
| `nifi-frost-nar` | The NiFi archive that carries the FROST code into an installation |
| `nifi-http-processors` | The code: `GetHttpApiCall`, the request of one API call of an HTTP/REST Data source |
| `nifi-http-nar` | The NiFi archive that carries the HTTP code into an installation |

## Build

```bash
mvn verify                 # tests, Spotless, PMD, CPD, SpotBugs, the NAR and its bill of materials
mvn spotless:apply         # format before committing
mvn test -Dtest=ThingsPortTest
mvn verify -DskipITs       # skip the integration test when no Docker daemon is running
```

`PutFrostRecordIT` writes with each port against a real FROST-Server that it brings up itself
through Testcontainers. Without a Docker daemon it skips, so `mvn verify` still passes on a
workstation without one — check the failsafe report when you need to know whether it ran.

Each NAR is at `nifi-<name>-nar/target/nifi-<name>-nar-<version>.nar`.

**This tree compiles to Java 21**, while the rest of the repository is on 25. That is the NiFi
extension API level: every NiFi 2.x release, including the newest, compiles to release 21, and the
official image runs a JDK 21. A NAR built to a higher release fails to load, and NiFi skips a
component it cannot load without a message — the queue in front of it then fills up. The version
follows the NiFi baseline, not the repository.

## Install into the development NiFi

`start-portal-dev.sh` builds the NARs and copies them to `dev-environment/nifi/extensions`. That
directory is mounted as the NiFi container's NAR autoload directory, and NiFi loads a NAR dropped
there without a restart. To install a changed NAR into a running NiFi:

```bash
mvn -q package -DskipTests
cp nifi-*-nar/target/nifi-*-nar-*.nar ../dev-environment/nifi/extensions/
```

## Install into a deployed NiFi

The CI packages the NARs into the image `nifi-extensions`, tagged with the NAR version. It is never
run: an init container of the NiFi pod copies the NARs into a volume, and NiFi reads that volume as
a custom NAR library directory (`components/nifi` in the
[deployment repository](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-deployment)).
The NiFi image stays the one Apache publishes.

The version pinned there must be the bundle version the flow names — `put_frost_record.json` in the
config-adapter. NiFi resolves a component by its bundle coordinate, and a version it does not find
is a component it does not load.

**Install the NAR before a flow that uses it is deployed.** NiFi accepts a process group holding a
component it does not know, marks it invalid and never says so; the Pipeline then looks healthy and
writes nothing.

## PutFrostRecord

One FlowFile is one record and one atomicity group of a JSON batch request. The processor sends the
batch itself: it holds the FlowFiles and needs the answer to route them, which a downstream request
processor could not give back.

| Property | Means |
|---|---|
| `Port` | The logic. Three values, no default. |
| `FROST Base URL` | The SensorThings service root. |
| `FROST Project Id` | The Dataset's project; it scopes every lookup and every write. |
| `Web Client Service Provider` | The HTTP client. Its properties carry the TLS material, the proxy and the timeouts. |
| `Basic Auth Username` / `Basic Auth Password` | Optional, and either both or neither. |
| `Records per Request` | How many records one batch may carry, at most 100. |

Relationships: `success`, `failure`, `retry`. A record on `failure` carries `frost.error.entity`,
`frost.error.status` and `frost.error.message`. A record on `retry` is unchanged; NiFi's retry
settings on the relationship decide the backoff.

### The ports

| Port | Writes | Resolves |
|---|---|---|
| `Things` | One Thing, upserted on its `reference` | — |
| `Observations` | One measurement | `thingReference` and `datastreamReference`; a miss is a data error |
| `ThingTree` | A Thing with its Locations, its Datastreams with their Sensor and ObservedProperty, and their measurements | In an internal chain |

The reference block lives in the entity's `properties` bag, and in `parameters` on an Observation:
SensorThings gives the Observation that bag instead and rejects the other one.

The `ThingTree` port writes every member of a collection, upserted on its own `reference`. A
single Location without one takes the Thing's; several Locations each need their own. A reference
that stands twice in one collection rejects the record, because the second upsert would overwrite
the first.

An Observation with a `reference` of its own is upserted, so a second delivery of the same message
updates the measurement. Without one every delivery appends a new measurement.

### The batch idiom

A sub-request identifier is **not** unique, and that is what makes find-or-create work: the lookup
and the create it guards share one, so `$<id>` resolves to the entity that was found or to the one
that was created, and the requests after it do not branch. An identifier is scoped to its record,
because two records must not resolve each other's entities.

The consequence is an order that is not obvious: **the update comes before the create.** `if` names
a preceding request, so an update placed behind the create also fires when the create wrote the
entity, and patches it a second time.

```json
{"id": "r0-thing", "atomicityGroup": "r0", "method": "get",
 "url": "Projects(5)/Things?$select=id&$top=1&$filter=properties/reference eq 'A7'"}
{"id": "r0-thing-update", "atomicityGroup": "r0", "if": "$r0-thing",
 "method": "patch", "url": "$r0-thing", "body": {}}
{"id": "r0-thing", "atomicityGroup": "r0", "if": "not $r0-thing",
 "method": "post", "url": "Projects(5)/Things", "body": {}}
```

Every lookup is **scoped to the Dataset**. A reference is local to its Dataset, and two Datasets
may model the same device under the same one. Things are looked up inside the Dataset's FROST
project (`Projects(5)/Things`), Datastreams through their Thing's project
(`Thing/Projects/id eq 5`). Locations are looked up through the Thing just resolved:
`$r0-thing/Locations?$filter=properties/reference eq 'A7'`. A direct query on `/Locations` would
find the Location another Dataset wrote, patch it, and leave this Thing without one — and FROST
then refuses every measurement of that Thing, because it has no position to derive a
FeatureOfInterest from. A query on the project's Locations would miss a Location written before
the sink assigned Locations to the project, and create a second one.

The sink assigns every Location, Sensor and FeatureOfInterest it writes to the Dataset's project,
because the Dataset's STA API serves the project and lists only what the project holds. An update
assigns too: FROST adds the project to the ones the entity has. Datastreams, Observations and
ObservedProperties stay outside the project; the Projects plugin has no navigation to them.

A back-reference in a URL stands at the **start**, where FROST replaces it with the self link of
the entity: `$r0-thing` becomes `/Things(5)`, and `$r0-ds/Sensor` becomes `/Datastreams(7)/Sensor`.
Written inside a path segment — `Things($r0-thing)` — it is not replaced at all, and FROST looks
for an entity whose identifier is the text of the reference. In a body the reference is a JSON
string of its own, `"$r0-thing"`, which FROST replaces with the identifier value.

The URL of a sub-request carries **no percent-encoding**. FROST decodes the query string of an
HTTP request, but hands the URL of a batch item to the query parser as it stands, and that parser
knows a space as a separator and `%20` as three characters of a name. So the separators are
spaces, and a value inside a filter is quoted with its quotation mark doubled and nothing else
escaped — which is safe, because the string literal of the grammar ends at the next single
quotation mark.

Source: [JSON batch requests](https://fraunhoferiosb.github.io/FROST-Server/extensions/JsonBatchRequest.html).

### Which failures are retried

A record goes to `retry` when it may succeed later without a change, and to `failure` when it would
get the same answer again:

| Case | Relationship |
|---|---|
| Transport error, timeout, batch answer 5xx, 408 or 429 | `retry` |
| Datastream or Thing not found | `retry` |
| Thing without a Location, so FROST cannot generate a FeatureOfInterest | `retry` |
| 5xx for one sub-request | `retry` |
| Validation error, record without a reference, document FROST cannot read | `failure` |

The second and third rows are the case of two Pipelines on one Dataset: measurements may arrive
before the Pipeline that writes the Things and Datastreams has run. The third one needs a check of
its own. FROST refuses such a write with a 400 whose reason the batch drops — indistinguishable from
a broken record — so the `Observations` port asks first, `$r0-ds/Thing/Locations?$top=1`, and the
write waits for that answer. A measurement that brings its own FeatureOfInterest skips the check.

The retry itself is NiFi's: the flow marks `retry` as a retried relationship with growing back-off
(see `put_frost_record.json` in the config-adapter), and a record reaches the error sink only once
the attempts are spent. NiFi retries each FlowFile on its own, so the written records of a batch are
not sent again. It resets the attributes on every attempt; the `frost.error.*` attributes a record
carries into the error sink are those of the last one.

### What the tests prove, and what they do not

The unit tests run against a loopback endpoint that answers what they tell it to, so they prove the
document the processor builds and nothing about how a server reads it. `PutFrostRecordIT` closes
that gap for the three assumptions that are the batch endpoint's behaviour: it asserts entities in
FROST, not sub-requests in a document.

- A back-reference as the whole URL of a PATCH, `$r0-thing`, and as the first segment of a
  single-valued navigation, `$r0-ds/Sensor` — covered by the upsert tests and the ThingTree test.
  The unit tests pin the two shapes; that FROST resolves them is what the integration test adds.
- That the URL of a sub-request reaches the query parser undecoded. Every lookup of every port
  depends on it: a percent-encoded separator makes FROST reject the filter, and the record fails
  with a parse error instead of finding its entity.
- What a sub-request whose `if` did not hold leaves in the response. FROST answers it with its
  identifier, status 200 and the body `"Skipped due to if."`. The division reads that as "did not
  run" rather than as a write, so an upsert whose two halves were both skipped still reports the
  record as unwritten.
- What the rest of an atomicity group leaves in the response after one sub-request of the group
  failed. FROST answers each of them with status 400 and **no identifier**, and it answers a
  document it cannot read at all the same way. Those answers bind to no request; they carry the
  reason for a record that saw no write of its own. Rejecting the whole document over them would
  lose every record of the batch, written or not.

One assumption stays open: **the batch size a server accepts.** The specification names none, and
the cap of 100 here is a guard rather than a measurement. The integration test sends three records
in one batch, which proves the isolation of the groups, not the limit.

## GetHttpApiCall

A source processor: it takes no input, and each run sends one HTTP GET request for one API call of
an HTTP/REST Data source. It runs on the primary node alone, because each node of a cluster would
call the API otherwise, and one run at a time. The cron schedule of the flow decides when it runs.

| Property | Means |
|---|---|
| `Web Client Service Provider` | The HTTP client. Its properties carry the TLS material, the proxy, the timeouts and the redirect handling. |
| `Body Read Timeout` | The longest wait for the next bytes of the response body, `15 secs` by default. A longer wait is a transient error. The `Read Timeout` of the web client service ends when the response headers arrive. |
| `Base URL` | Scheme `http` or `https`, a host, an optional port and an optional path. No user info, no query, no fragment. A trailing `/` is removed. |
| `API Call` | The API call as JSON, in the form the portal stores it. |
| `Authentication Type` | `NONE` (default) or `BASIC`. |
| `Basic Auth Username` / `Basic Auth Password` | Required with `BASIC`, which also requires an `https` Base URL. The username contains no colon; the password is sensitive. |
| `Record Schema` | The JSON Schema of the Data structure of the Data source, with its imports inlined. Required. |

No property supports Expression Language.

```json
{
  "name": "Get all weather stations",
  "method": "GET",
  "path": "/stations",
  "queryParameters": [{ "name": "status", "value": "active" }],
  "headers": [{ "name": "Accept", "value": "application/json" }],
  "pagination": null
}
```

The processor checks the rules of the portal backend again, because it builds the request and the
credentials from them. `method` is `GET` or absent, `path` matches `^/[A-Za-z0-9._~:/-]*$`, a header
name is a token and not `Authorization`, `Proxy-Authorization` or `Cookie`, and `pagination` is
absent or null. A header must also be one the JDK HTTP client behind the web client service sends:
not `Connection`, `Content-Length`, `Expect`, `Host` or `Upgrade`, and a value of printable
ISO-8859-1 characters, spaces and tabs only. An unknown key fails. A broken rule makes the
processor invalid, and the explanation names the key path, for example `headers[1].name`, never the
value.

The query is percent-encoded with only the unreserved characters of RFC 3986 left as they are, so a
space is `%20` and `&`, `=` and `+` in a value stay part of the value. The parameters keep their
order, repeated names included. `HttpUriBuilder.addQueryParameter` is not used for this: NiFi's
implementation does not encode `&`, `=` and `+` in a value.

Relationships: `success`, `stop` and `failure`. There is no `retry`; the next cron run is the next
attempt. `stop` carries an error that the next run cannot repair: the flow stops the Pipeline on it.
`failure` carries an error that the next run can repair.

| Result | Relationship | Log level |
|---|---|---|
| 2xx, each record agrees with the `Record Schema` | `success`, the body unchanged, `mime.type` `application/json` | none |
| 2xx without a body, for example 204 | none, no FlowFile | debug |
| 3xx, 4xx except 408 and 429 | `stop` | error |
| A request that the web client refuses, status `0` | `stop` | error |
| 2xx, the body is not JSON, or is a JSON scalar | `stop` | error |
| 2xx, a record violates the `Record Schema` | `stop` | error |
| 408, 429, 5xx, any other status | `failure` | warn |
| Connection error, timeout, TLS error, body read error, status `0` | `failure` | warn |

A 3xx is fatal because the processor expects a web client service that does not follow
redirects (`Redirect Handling Strategy` = `IGNORED`), so a redirect shows a wrong host or path.

A FlowFile on `success` carries `http.status.code` and `http.request.path`. A FlowFile on `stop` or
`failure` is empty and carries `http.request.path`, `http.error.status` and `http.error.message`.
`http.error.status` is the status of the response, or `0` when no response came. The message is the
one the log entry and the bulletin show. It names the API call and its path, never the base URL:

```text
API call "Get stations": GET /stations failed with status 404
API call "Get stations": GET /stations failed: ConnectException
API call "Get stations": 2 of 50 records violate the Data structure: record 3: /lat type; record 7: required stationid
API call "Get stations": the response is a JSON string, not an object or an array
API call "Get stations": the response is not JSON
```

A transient message keeps the word `failed`: the runtime monitor of the NiFi config-adapter counts a
warn bulletin only when its text names a failure.

No attribute and no message contains the query, a header value, the username, the password or a
value of the response. An exception reaches no logger, because its message can hold the URI with its
query; the message names its class alone.

### Response validation

The processor reads a 2xx body into memory and parses it once. A JSON array holds one record in each
element, and `[]` holds none. A JSON object is one record. A JSON scalar, or a body that is not JSON,
goes to `stop`. Each record is validated against `Record Schema`; one invalid record sends the whole
response to `stop`, and no FlowFile goes to `success`. A body that passes is an object or an array of
objects, so the record reader behind the processor skips no data.

The cause names the number of invalid records and at most the first 3 violations: the index of the
record from 0, the JSON Pointer inside the record and the keyword. For `required`, it also names the
missing property, which comes from the schema. It never uses the message of the validator, because
some messages quote the value.

The validator is the networknt `json-schema-validator` in the version of Model Forge, with draft
2020-12 and the keyword and format settings of Model Forge. The `x-core-*` keywords and `crs` validate
nothing.

A `$ref` resolves inside the schema, to an embedded resource of the schema with its own `$id`, or to
one of the seven GeoJSON geometry schemas `https://geojson.org/schema/<Type>.json`. The NAR holds
copies of those, identical to the ones Model Forge vendors (`GeoJsonCopiesTest` checks that), and the
processor never fetches a schema over the network. Any other reference makes the processor invalid,
as does a `Record Schema` that is not a JSON object or that the validator cannot compile.

The request code knows no NiFi session, so the page loop of a later step can send each page through
it as a request of its own. A new authentication type adds a value of `Authentication Type`, its
properties and a class that implements `Authentication`.

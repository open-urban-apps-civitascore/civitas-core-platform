# NiFi extensions

The NiFi components the platform installs into its NiFi deployment. The config adapter emits flows
that use them; the components themselves know nothing about the adapter.

| Module | Holds |
|---|---|
| `nifi-frost-processors` | The code: `PutFrostRecord`, the ports and the batch builder |
| `nifi-frost-nar` | The NiFi archive that carries the FROST code into an installation |
| `nifi-http-processors` | The code: `GetHttpApiCall`, the request of one API call of an HTTP/REST Data source |
| `nifi-http-nar` | The NiFi archive that carries the HTTP code into an installation |
| `nifi-jdbc-drivers` | The PostgreSQL JDBC driver for the Pipelines' database connection pools, at `target/drivers/postgresql.jar` |

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

The CI packages the NARs and the JDBC drivers into the image `nifi-extensions`, tagged with the NAR
version. It is never run: an init container of the NiFi pod copies them into volumes, and NiFi reads
the NAR volume as a custom NAR library directory (`components/nifi` in the
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
an HTTP/REST Data source, or one request for each page when the API call has a pagination. It runs
on the primary node alone, because each node of a cluster would call the API otherwise, and one run
at a time. The cron schedule of the flow decides when it runs.

| Property | Means |
|---|---|
| `Web Client Service Provider` | The HTTP client. Its properties carry the TLS material, the proxy, the timeouts and the redirect handling. |
| `Body Read Timeout` | The longest wait for the next bytes of the response body, `15 secs` by default. A longer wait is a transient error. The `Read Timeout` of the web client service ends when the response headers arrive. |
| `Base URL` | Scheme `http` or `https`, a host, an optional port and an optional path. No user info, no query, no fragment. A trailing `/` is removed. |
| `API Call` | The API call as JSON, in the form the portal stores it. |
| `Authentication Type` | `NONE` (default), `BASIC`, `BEARER_TOKEN`, `API_TOKEN`, `OAUTH2` or `CLIENT_CERTIFICATE`: the authentication types of the portal. Each type other than `NONE` requires an `https` Base URL. See [Authentication](#authentication). |
| `Basic Auth Username` / `Basic Auth Password` | Required with `BASIC`. The username contains no colon; the password is sensitive. |
| `Bearer Token` | Required with `BEARER_TOKEN`. Sensitive. |
| `API Token Name` / `API Token Value` / `API Token Location` | Required with `API_TOKEN`. The location is `HEADER` or `QUERY`; the value is sensitive. |
| `OAuth2 Token URL` / `OAuth2 Client ID` / `OAuth2 Client Secret` / `OAuth2 Scope` | With `OAUTH2`; the scope is optional. The token URL is `https`, with a host and without user info, query and fragment. The client secret is sensitive. |
| `Record Schema` | The JSON Schema of the Data structure of the Data source, with its imports inlined. Required. |
| `Max Pages` | The maximum number of pages in one paginated run, `1000` by default. The config-adapter does not set it, and the portal does not show it. |

No property supports Expression Language.

```json
{
  "name": "Get all weather stations",
  "method": "GET",
  "path": "/stations",
  "queryParameters": [{ "name": "status", "value": "active" }],
  "headers": [{ "name": "Accept", "value": "application/json" }],
  "pagination": {
    "type": "PAGE_NUMBER",
    "pageParameter": "page",
    "pageSizeParameter": "size",
    "pageSize": 100,
    "startPage": 0,
    "endPage": null
  }
}
```

The processor checks the rules of the portal backend again, because it builds the request and the
credentials from them. `method` is `GET` or absent, `path` matches `^/[A-Za-z0-9._~:/-]*$`, a header
name is a token and not `Authorization`, `Proxy-Authorization` or `Cookie`. A `pagination` is absent,
null, or of the type `PAGE_NUMBER`, `CURSOR` or `LIMIT_OFFSET`:

- `PAGE_NUMBER` has a `pageParameter`, a `pageSizeParameter` and a `pageSize` of at least 1. An
  absent `startPage` is 0, and an `endPage` is at least the `startPage`.
- `CURSOR` has a `cursorPath`, a `cursorParameter` and a `cursorIn` of `QUERY` or `HEADER`. A
  `limitParameter` and a `limit` of at least 1 are optional, but one needs the other. A
  `cursorParameter` in a header follows the header name rules below.
- `LIMIT_OFFSET` has an `offsetParameter`. An absent `offset` is 0, and an `offset` is at least 0.
  A `limitParameter` and a `limit` of at least 1 are optional, but one needs the other. A
  `countPath` is optional; a blank one is absent.

A JSON path such as `cursorPath` or `countPath` starts with `$` and holds only member names
(`$.next_cursor`), quoted member names (`$['next cursor']`) and array indexes (`$.data[0].id`). This
is the grammar of the portal backend; the processor evaluates no filter, wildcard or recursive
descent. The names that a pagination sends differ from each other and from the names of the API call
that go to the same place: a query parameter name from each query parameter name, case-sensitive as HTTP query parameter
names are, and a header name from each header name, without case. A key of another pagination type
is ignored. A header must also be one the JDK HTTP client behind the web client service sends:
not `Connection`, `Content-Length`, `Expect`, `Host` or `Upgrade`, and a value of printable
ISO-8859-1 characters, spaces and tabs only. An unknown key fails. A broken rule makes the
processor invalid, and the explanation names the key path, for example `headers[1].name`, never the
value.

The query is percent-encoded with only the unreserved characters of RFC 3986 left as they are, so a
space is `%20` and `&`, `=` and `+` in a value stay part of the value. The parameters keep their
order, repeated names included. `HttpUriBuilder.addQueryParameter` is not used for this: NiFi's
implementation does not encode `&`, `=` and `+` in a value.

### Authentication

A Data source has one authentication for all its requests. The processor sends the credentials in
the same form as the connection test of the NiFi config-adapter (`HttpProber`), and the tests of
both sides cover the same cases under the same names (`GetHttpApiCallAuthenticationTest`,
`HttpProberAuthenticationTest`).

| Type | The request sends |
|---|---|
| `BASIC` | `Authorization: Basic base64(username:password)` |
| `BEARER_TOKEN` | `Authorization: Bearer <token>` |
| `API_TOKEN`, `HEADER` | A header `<name>: <value>` after the headers of the API call |
| `API_TOKEN`, `QUERY` | A query parameter `<name>=<value>` after the query parameters of the API call and of the page, percent-encoded as they are |
| `OAUTH2` | `Authorization: Bearer <access token>`, from the client credentials grant |
| `CLIENT_CERTIFICATE` | Nothing: the web client service presents the certificate in the TLS handshake |

**An API token in the query is visible in the access logs of the API host,** and of each proxy on
the way, because those logs hold the URL with its query. Use `HEADER` when the API accepts it.

The processor checks the rules of the portal backend again. A header token or a bearer token holds
only printable ISO-8859-1 characters, spaces and tabs. The name of a header token is a valid header
name and not one that the HTTP client sets (`Connection`, `Content-Length`, `Expect`, `Host`,
`Upgrade`); `Authorization`, `Proxy-Authorization` and `Cookie` are allowed. It differs, ignoring
case, from each header of the API call and of its pagination. The name of a query token differs from each query parameter
of the API call and of its pagination; the comparison is case-sensitive. An explanation never names
a value.

**OAuth2.** The processor gets the access token with the client credentials grant (RFC 6749,
section 4.4): a POST of `grant_type=client_credentials`, with `scope` when it is set, to the token
URL, with `Content-Type: application/x-www-form-urlencoded`, `Accept: application/json` and the
client authenticated by HTTP Basic over the percent-encoded client ID and secret. The token request
goes through the same web client service as the API requests: the same TLS, proxy and timeouts, and
no redirect, so the client secret goes to no other host. The processor reads at most the first
64 KiB of the token response.

The processor keeps the access token in memory, never in the NiFi state, which NiFi writes to disk.
It uses the token until 30 seconds before its `expires_in` ends, also across runs. Without
`expires_in`, or with an `expires_in` of 30 seconds or less, the token serves the current run only.
A stop of the processor drops the token. A 401 of the API with a kept token makes the processor
get one new token and send the request once more; a second 401 goes to `stop`. A 401 with a token
that was requested for that request goes to `stop` without a new token.

The status of the token endpoint has the classification of an API response: a 3xx and a 4xx other
than 408 and 429 go to `stop`, and a 5xx, a 408, a 429 and a connection error go to `failure`. Then
the processor does not call the API. A 2xx without a non-blank string `access_token` in a JSON
object goes to `stop`.

**Client certificate.** The processor has no property for it. The certificate and its private key
are properties of the SSL Context Service of the web client service, so a Data source with a client
certificate needs a web client service of its own. The config-adapter gives it a
`PEMEncodedSSLContextProvider` with the default CAs as trusted CAs. A TLS handshake that the server
ends with a certificate alert, or with `handshake_failure`, goes to `stop`. Any other failed
handshake, for example an untrusted server certificate, goes to `failure`. With TLS 1.3 the server can refuse the certificate after the handshake; the
JDK then reports either a refused handshake or a closed connection, and a closed connection goes to
`failure`, as the processor cannot tell it from a network error.

Relationships: `success`, `stop` and `failure`. There is no `retry`; the next cron run is the next
attempt. `stop` carries an error that the next run cannot repair: the flow stops the Pipeline on it.
`failure` carries an error that the next run can repair.

| Result | Relationship | Log level |
|---|---|---|
| 2xx, each record agrees with the `Record Schema` | `success`, the body unchanged, `mime.type` `application/json` | none |
| 2xx without a body or with only whitespace, for example 204 | none, no FlowFile | debug |
| 3xx, 4xx except 408 and 429, of the API or the OAuth2 token endpoint | `stop` | error |
| 2xx of the OAuth2 token endpoint without an access token | `stop` | error |
| A TLS handshake that refuses the client certificate, status `0` | `stop` | error |
| A request that the web client refuses, status `0` | `stop` | error |
| 2xx, the body is not JSON, or is a JSON scalar | `stop` | error |
| 2xx, a record violates the `Record Schema` | `stop` | error |
| 408, 429, 5xx, any other status, of the API or the OAuth2 token endpoint | `failure` | warn |
| Connection error, timeout, TLS error, body read error, status `0` | `failure` | warn |

A 3xx is fatal because the processor expects a web client service that does not follow
redirects (`Redirect Handling Strategy` = `IGNORED`), so a redirect shows a wrong host or path.

A FlowFile on `success` carries `http.status.code` and `http.request.path`, and with pagination
`http.page`: the page number, with cursor pagination the number of the request in the run from 1,
or with limit/offset pagination the offset. A FlowFile on `stop` or `failure` is empty and carries
`http.request.path`, `http.error.status` and `http.error.message`. `http.error.status` is the status
of the response, or `0` when no response came. The message is the one the log entry and the bulletin
show. It names the API call and its path, never the base URL, and on a page of a paginated run the
page number, with cursor pagination the request number, or with limit/offset pagination the offset.
A 401 or 403 names the authentication method. A failed token request names the host, the port and
the path of the token URL, and of the token response only an error code of RFC 6749, section 5.2.
A control character or line separator in the message appears as `\r`, `\n` or `\uXXXX`, so no part
of it can start a forged log line; every other character stays as entered:

```text
API call "Get stations": GET /stations failed with status 404
API call "Get stations": GET /stations failed with status 401 (Bearer Token)
API call "Get stations": GET /stations failed: ConnectException
API call "Get stations": GET /stations failed: TLS handshake with Client certificate failed (SSLHandshakeException)
API call "Get stations": OAuth2 token request to auth.example.org/realms/city/token failed with status 401 (invalid_client)
API call "Get stations": OAuth2 token request to auth.example.org/realms/city/token failed: ConnectException
API call "Get stations": OAuth2 token request to auth.example.org/realms/city/token failed: status 200 without an access token in a JSON object
API call "Get stations": 2 of 50 records violate the Data structure: record 3: /lat type; record 7: required stationid
API call "Get stations": the response is a JSON string, not an object or an array
API call "Get stations": the response is not JSON
API call "Get stations": GET /stations page 3 failed with status 403 (API Token)
API call "Get stations": GET /stations page 3 failed: ConnectException
API call "Get stations": page 3: 2 of 50 records violate the Data structure: record 3: /lat type; record 7: required stationid
API call "Get stations": page 3 is a JSON object, not an array
API call "Get stations": page 3 repeats page 2
API call "Get stations": no stop condition after 1000 pages (Max Pages)
API call "Get stations": GET /stations page 3 not sent, because the processor stopped
API call "Get items": GET /items request 3 failed with status 404
API call "Get items": request 3 is a JSON array, not an object
API call "Get items": request 3: the value at $.next_cursor is a JSON object, not a string or a number
API call "Get items": request 3: the value at $.next_cursor holds a character that a header value must not hold
API call "Get items": request 4 would send the same request as request 2
API call "Get items": GET /items offset 0 failed with status 404
API call "Get items": offset 100 is a JSON object, not an array
API call "Get items": offset 100: the value at $.meta.count is not a record count
API call "Get items": offset 100: the response holds more records than the limit
API call "Get items": offset 100: the next offset would be larger than 9223372036854775807
API call "Get items": offset 120 repeats offset 100
```

A transient message keeps the word `failed`: the runtime monitor of the NiFi config-adapter counts a
warn bulletin only when its text names a failure. The message of a stop has no such word, because a
stop is not an error of the Pipeline.

No attribute and no message contains the query, a header value, a credential (the username, the
password, a token, the client secret, an access token) or a value of the response, a cursor
included. The one exception is the offset of limit/offset pagination without a `limit`, which
carries record counts (see [Limit/offset pagination](#limitoffset-pagination)). An exception
reaches no logger, because its message can hold the URI with its query; the message names its class
alone.

### Response validation

The processor writes a 2xx body into its FlowFile and then reads it one record at a time, so only
one record is in memory. A JSON array holds one record in each element, and `[]` holds none. A JSON
object is one record. A JSON scalar, or a body that is not JSON, goes to `stop`. Each record is
validated against `Record Schema`; one invalid record sends the whole response to `stop`, and no
FlowFile goes to `success`. An array element that is not a JSON object is an invalid record with the
keyword `type`, also when `Record Schema` allows it. A body that passes is an object or an array of
objects, so the record reader behind the processor skips no data.

The cause names the number of invalid records and at most the first 3 violations: the index of the
record from 0, the JSON Pointer inside the record and the keyword. In the JSON Pointer, a property
name that no `properties` of the schema declares appears as `*`, because it comes from the response.
For `required`, it also names the missing property, which comes from the schema. It never uses the
message of the validator, because some messages quote the value.

The validator is the networknt `json-schema-validator` in the version of Model Forge, with draft
2020-12 and the keyword and format settings of Model Forge (`ModelForgeKeywordsTest` checks that the
keyword lists agree). The `x-core-*` keywords and `crs` validate nothing.

A `$ref` resolves inside the schema, to an embedded resource of the schema with its own `$id`, or to
one of the seven GeoJSON geometry schemas `https://geojson.org/schema/<Type>.json`. The NAR holds
copies of those, identical to the ones Model Forge vendors (`GeoJsonCopiesTest` checks that), and the
processor never fetches a schema over the network.

The processor compiles `Record Schema` when it starts. It does not check the schema when NiFi
validates the processor: Model Forge validates the Data structure. A `Record Schema` that is not
JSON, that the validator cannot compile, or that has any other reference stops the start: the
processor stays valid and scheduled but sends no request, NiFi raises an error bulletin
`Failed to properly initialize Processor` with the `InvalidRecordSchemaException` as its cause and
tries again after the `Administrative Yield Duration`. The runtime monitor publishes this bulletin
as an `ERROR` event of the Pipeline.

### Pagination

A pagination makes a run read the pages one after the other. Each page is a request of its own,
through the same request code as an API call without pagination. A page adds its parameters after
the query parameters of the API call, and its headers after the headers of the API call.

#### Page-number pagination

A `PAGE_NUMBER` pagination sends the page parameter and the page size parameter as query parameters.
The first page is `startPage`, and the page number increases by 1. The run stops after the first page
that meets one of these conditions, and the records of that page are part of the run:

- The page number is the `endPage`.
- The page has no records.
- The page has fewer records than `pageSize`.
- A page after the first page answers 400 or 404. Some APIs, for example WordPress, answer a page
  past the last page so, and not with `[]`. On the first page, 400 and 404 go to `stop`, as in the
  table above.

A page with more records than `pageSize` is not an error. The page number is a `long`, so a
`startPage` near the `int` maximum does not overflow.

A page must be a JSON array of records. An API call without pagination keeps the rules above: a JSON
object is one record, and `[]` gives one FlowFile `[]`.

| 2xx response of a page | Result |
|---|---|
| A JSON array, each record agrees with the `Record Schema` | one FlowFile on `success`, the body unchanged |
| `[]`, or no body | no FlowFile; the run stops there |
| A JSON object, a JSON scalar, or not JSON | `stop` |
| An array with a record that violates the `Record Schema` | `stop` |

**Known limit:** a `pageSize` larger than the maximum page size of the API ends the run after the
first page, because the API sends fewer records than `pageSize`. The processor cannot tell this from
a last page, so no error shows.

#### Cursor pagination

A `CURSOR` pagination takes the cursor of each request from the response before it. The first
request sends no cursor. Each next request sends the value at `cursorPath` of the previous response
under `cursorParameter`, as a query parameter or as a header by `cursorIn`. With a `limit`, each
request also sends `limitParameter` with the `limit` as a query parameter, before the cursor. With
`cursorPath` `$.next_cursor`, `cursorParameter` `cursor`, `cursorIn` `QUERY` and a limit of 20:

```text
GET /items?limit=20                 -> { "data": [...], "next_cursor": "abc123" }
GET /items?limit=20&cursor=abc123   -> { "data": [...], "next_cursor": "def456" }
GET /items?limit=20&cursor=def456   -> { "data": [...] }
```

A string cursor goes into the request as it is; the query percent-encodes it. A number cursor goes
in as its JSON text in the response, for example `1.50` as `1.50` and `1e2` as `1e2`. When the
value at `cursorPath` is absent, `null` or `""`, the run ends, and the records of that response are
part of the run.

Each response is a JSON object and one record, so the `Record Schema` describes the whole response,
cursor included. A Mapping takes the records out of it. The processor reads the cursor in the same
pass as the record validation, from the record that it validates.

| 2xx response of a request | Result |
|---|---|
| A JSON object that agrees with the `Record Schema`, with a string or number cursor | one FlowFile on `success`, the body unchanged; the run sends the next request |
| The same, with the cursor absent, `null` or `""` | one FlowFile on `success`; the run stops there |
| No body | no FlowFile; the run stops there |
| A cursor that is a JSON object, array or boolean | `stop` |
| A cursor in a header with a character outside printable ASCII | `stop` |
| A cursor that the run already sent | `stop` |
| A JSON array, a JSON scalar, or not JSON | `stop` |
| An object that violates the `Record Schema` | `stop` |

A 400 or 404 on a request after the first does not end the run: the cursor comes from the API, so
the request after it must exist. It goes to `stop`, as in the table above.

A cause and `http.page` name a request by its position in the run, for example `request 3`. They
never show the cursor, because the cursor is data of the API host. The processor compares each next
request with the requests of the run that it sent: a request with the same parameters and headers as
an earlier one goes to `stop` before the processor sends it, because the API would repeat its pages
without end.

#### Limit/offset pagination

A `LIMIT_OFFSET` pagination sends `offsetParameter` with the offset of each request as a query
parameter. The first request sends `offset`. With a `limit`, each request also sends
`limitParameter` with the `limit` as a query parameter, before the offset, and the offset of the next
request is the offset plus the `limit`. Without a `limit`, the requests send no limit parameter, the
API uses its default page size, and the offset of the next request is the offset plus the record
count of the response. With a limit of 20 and an `offset` of 80:

```text
GET /items?limit=20&offset=80    -> 20 records
GET /items?limit=20&offset=100   -> 20 records
GET /items?limit=20&offset=120   -> 7 records; the run stops there
```

The record count of a response:

- Without `countPath`, the response must be a JSON array. Each element is one record, as with
  page-number pagination, and the count is the number of elements.
- With `countPath`, the response must be a JSON object. It is one record, so the `Record Schema`
  describes the whole response. The value at `countPath` gives the record count: a whole number of
  at least 0, for example `5` or `5.0`, or an array, whose length is the count. **The count is the
  number of records in this response, not the total of the API.** The processor reads the count in
  the same pass as the record validation, from the record that it validates.

The run stops after the first response that meets one of these conditions, and the records of that
response are part of the run:

- With a `limit`, the response has fewer records than the `limit`.
- Without a `limit`, the response has no records.
- A request after the first answers 400 or 404, as with page-number pagination. Some APIs answer an
  offset past the end so. On the first request, 400 and 404 go to `stop`.

A response with more records than the `limit` goes to `stop`: the API then ignores the `limit`,
and the next offset would read some of its records again. The offset is a `long`, so an
`offset` near the `int` maximum does not overflow.

| 2xx response of a request | Result |
|---|---|
| Without `countPath`: a JSON array, each record agrees with the `Record Schema` | one FlowFile on `success`, the body unchanged |
| With `countPath`: a JSON object that agrees with the `Record Schema`, with a count above 0 | one FlowFile on `success`, the body unchanged |
| `[]`, a count of 0, or no body | no FlowFile; the run stops there |
| Without `countPath` a JSON object, with `countPath` a JSON array; a JSON scalar, or not JSON | `stop` |
| With a `limit`, more records than the `limit` | `stop` |
| A value at `countPath` that is absent, `null`, below 0, a fraction, a string, a boolean or an object | `stop` |
| A count that makes the next offset larger than 9223372036854775807 | `stop` |
| A record that violates the `Record Schema` | `stop` |

A cause and `http.page` name a request by its offset, for example `offset 100`, so a Data Steward
can send the same request. The offset follows from the configuration and from the record counts.
Without a limit, two offsets in a row thus give the record count of a response, which is the value
at `countPath` when one is set. A cause shows no other value of a response. An API that ignores
the offset sends the same page again, and the check for a repeated body below stops the run at the
second request.

**Known limit:** a `limit` larger than the maximum page size of the API ends the run after the first
page, because the API sends fewer records than the `limit`. The processor cannot tell this from a
last page, so no error shows. Without a `limit`, the run continues until a response has no records.

#### Every pagination

**A run delivers all its pages or none.** The page FlowFiles of a run stay in one session, which
the processor commits when the loop ends. An error on any page, fatal or transient, removes the page
FlowFiles of the run. The processor then sends one error FlowFile to `stop` or `failure`. Thus each
failed run gives one FlowFile. The next run starts again at the first page, with limit/offset
pagination at `offset`. The pages of a run wait
in the content repository until the commit. This
applies to what the processor sends: the Data sink writes the pages one FlowFile after the other,
so a Data sink error on a page leaves the pages before it in the Data storage.

A page whose body is the same as the body of an earlier page of the run goes to `stop`, also in a
run with an `endPage`, and none of the pages of the run reaches `success`. The same body means that
the API ignores the page parameter, the cursor or the offset, so the run would deliver the same
records again until the `endPage` or `Max Pages`. Thus such an API stops at the second page. The
processor compares a SHA-256 of each body, which it computes while it writes the body into its
FlowFile.

`Max Pages` limits a run, also one with an `endPage`. A run that reads `Max Pages` pages and meets no
stop condition goes to `stop`, and none of its pages reaches `success`. This guards against an API
that ignores the page parameter, the cursor or the offset and changes its response on each request,
for example with a timestamp in each record.

**Page limit:** a run reads at most `Max Pages` pages, 1000 by default. When the page at
`Max Pages` does not end the run, the run also goes to `stop`, because it cannot know that no
further page exists. The first run of an API with more pages stops the Pipeline. The config-adapter
does not set `Max Pages`, and each deployment of the Pipeline replaces a value set in the NiFi UI, so
only a code change raises the limit.

When NiFi stops the processor during a run, the processor sends no further page. It removes the
pages of the run and sends one FlowFile to `failure`, as for a transient error. Thus a stop of the
process group waits for one page at most, not for the whole run.

The request code knows no NiFi session. The page loop asks a `Pagination` for the first page, and
for the shape that each response must have: a JSON array of records, a JSON object that is one
record, or, without pagination, either. A `ResponseShape` of one object also names the JSON paths
whose values the pagination reads. From the record count and those values of a page, the
`Pagination` gives the next page, the end of the run, or a cause for `stop`. A page carries the query
parameters and the headers that its request adds. A new pagination method adds an implementation of
`Pagination`, and the page loop and the request code stay as they are.

A new authentication type adds a
value of `Authentication Type`, its properties and a class that implements `Authentication`.

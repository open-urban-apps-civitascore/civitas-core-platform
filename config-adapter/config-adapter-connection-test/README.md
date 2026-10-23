# Config Adapter Connection Test

Answers the portal's "test connection" of a data source. It connects to the source with the connector values of the form and reports whether the source answers. The test reads no data into the platform, starts no saga and stores nothing.

It is neither a `ConfigAdapter` nor a `SagaCommandHandler`. It runs as a workload of its own, from the config-adapter image with the main class `ConnectionTestApplication`. Thus it shares neither config-adapter's process nor its network identity. Deployment and properties: [DEPLOYMENT.md §2.5](../DEPLOYMENT.md#25-connection-test).

## Contract

| Topic | Message | Key |
|---|---|---|
| `de.civitascore.connectiontest.request` | `ConnectionTestRequest`: `requestId`, `expiresAt`, `connector`, `apiCall` | request id |
| `de.civitascore.connectiontest.result` | `ConnectionTestResult`: `requestId`, `outcome` (`SUCCESS`/`FAILURE`), `reason`, `message`, `rowCount`, `httpStatus`, `durationMillis` | request id |

- Both records are in `config-adapter-api` (`model.connectiontest`) and travel as plain JSON.
- The topic names are the defaults of `connectiontest.kafka.request-topic` / `result-topic`.
- `connector` is the connector document as the portal stores it, the same document a saga carries. Every secret in it is `ENC(...)`. A plaintext password is refused.

Delivery:

- A request is dropped when less than a second is left before its `expiresAt`, because portal-backend then stops waiting. After a crash of the service, a request it read shortly before can run again if time is left.
- A request above `connectiontest.max-concurrent` gets the answer `BUSY` at once.
- A test that runs longer than `connectiontest.timeout-seconds`, or longer than its request allows, gets the answer `TIMEOUT`. Its connections are then closed from outside. Exception: an MQTT connection that the broker has not answered yet ends by itself after 13 s (see the known differences).
- Each test that runs publishes one result. A test that runs while the service shuts down publishes none; portal-backend then answers 504.

| `reason` of a failure | When |
|---|---|
| `INVALID_CONNECTOR` | The values make no source the test can run, or the destination is refused. |
| `SOURCE_ERROR` | The source answered with an error (authentication, missing table, refused subscription, non-2xx status) or could not be reached. |
| `TIMEOUT` | The source did not answer in time. |
| `BUSY` | Too many tests run. Nothing was attempted. |
| `UNAVAILABLE` | The test could not run on the platform's side, for example because a password cannot be decrypted. |

## What each type tests

| Type | Client | Success |
|---|---|---|
| SQL | PostgreSQL JDBC driver, as NiFi's `QueryDatabaseTableRecord` | up to 5 rows read |
| MQTT 3 | HiveMQ client, MQTT 3.1.1 | the broker grants the subscription |
| MQTT 5 | HiveMQ client, as NiFi's `ConsumeMQTT` | the broker grants the subscription |
| HTTP | JDK `HttpClient`, request built as the `GetHttpApiCall` processor builds it | a 2xx status with a body the pipeline reads |

- **SQL runs no configured text as SQL.**
  - The table and each column go to the database only as bind parameters of `to_regclass(?)` and `parse_ident(?)`. These resolve them with the parser's quoting, case folding and search path.
  - The query uses the names that the catalog returns, quoted by `quote_ident`, with `LIMIT ?`. It runs in a read-only transaction that is always rolled back.
  - The `where` clause is free SQL and is **not** part of the test. The result says so.
  - The test drops the DSN's own `connectTimeout`, `socketTimeout` and `loginTimeout`, because the driver prefers them to the test's limits.
  - Each value is read as text, of which the database sends one character. Thus a table of wide rows (PostGIS polygons) costs little.
- **MQTT checks the SUBACK code.** A refused subscription is a failure. The test waits for no message. MQTT 5 asks for no retained messages, and messages that arrive before the disconnect are dropped. A broker with a Mosquitto `acl_file` grants a subscription and then delivers nothing to it, so such a test succeeds. The dynamic security plugin refuses the subscription in the SUBACK.
- **HTTP follows no redirect.** It reads the first 8 KB of the body to tell whether the pipeline reads it.
  - Basic authentication is sent only over https.
  - A non-2xx status is a failure that keeps the status.
  - A 2xx body is judged as the pipeline reads it. Not JSON, or compressed: failure, because the pipeline's web client does not unpack it. JSON without an object (`[]`, scalars): success, with a note that the pipeline reads no record from it.
- **A value with `${` or `#{` is refused**, as a release refuses it, because the pipeline engine would evaluate it. The message names the field, never the value.

`SqlConnectorValues` and `MqttConnectorValues` in `config-adapter-api` read the connector values. The NiFi source stages use the same parsers.

## Limits and secrets

- A message has at most 500 characters. A result holds no rows and no body of the source.
- Every decrypted secret is masked in the message.
- A message keeps the source's own answer (`password authentication failed`, `NOT_AUTHORIZED`, the HTTP status). A failure of the test's own client is named by its type, never by its text.
- An MQTT connection that fails after its connect timeout is `TIMEOUT`.
- Logs carry the request id, outcome and duration. At DEBUG they also carry the masked failure message.

## Destinations

- Network rules decide which cluster services a test reaches. Platform services do not admit the connection test workload. An egress rule on the workload keeps it off the node addresses and off namespaces without a default-deny policy ([DEPLOYMENT.md §2.5](../DEPLOYMENT.md#25-connection-test)).
- A source inside the cluster can be tested when its policy admits the workload.
- Before it connects, the test checks every address that the host resolves to. Always refused: loopback, link-local (with the cloud metadata address `169.254.169.254`), any-local and multicast addresses, and `localhost`. Metadata addresses of other providers, such as `100.100.100.200`, are refused only through `connectiontest.denied-networks`. An operator can refuse more networks with `connectiontest.denied-networks`.

The client resolves the name again when it connects. `GuardedResolver` is installed as the JVM's resolver through `java.net.spi.InetAddressResolverProvider`. For two minutes it checks every resolution of a name that a test let through, for every client, thread and spelling of the name. Thus the name cannot give the check a public address and the connection an internal one (DNS rebinding). All other names resolve unchanged through the built-in resolver.

BSI TR-03187 W-16 asks for an allowlist of destinations. Data sources are arbitrary hosts, so the test relies on the network position of its own workload. The same control applies to the pipeline in NiFi.

## Known differences from the pipeline

| Case | Test | Pipeline |
|---|---|---|
| Source inside the cluster | reaches it if the source's ingress policy admits the connection test workload | reaches it if the policy admits NiFi |
| MQTT connector with several broker URLs | tests the first URL only | fails over to the next URL |
| HTTP status 408, 429 or 5xx | a failure like any non-2xx status | retried as transient |
| HTTP 2xx with an empty body | success | no record |
| Broker or API with a private CA | fails: the JVM default CAs are trusted | depends on the NiFi pipeline truststore, a copy of the JDK `cacerts` |
| SQL `where` clause | not run | run |
| MQTT over WebSocket (`ws://`, `wss://`) | connects to the URL's path, `/` if none | MQTT 3: `/mqtt` if the URL has no path; MQTT 5: the path is dropped |
| HTTP API that answers slowly | the whole exchange must end within the test's 15 s | 15 s for the headers, then up to 15 s between body reads |
| MQTT broker that accepts TCP and never answers, with a request that leaves less than 15 s | answered as TIMEOUT when the request runs out; the connection attempt itself ends after 13 s | — |
| SQL column that is not a plain column name (`t.id`, `id AS x`, `id::text`, `id+1`) | refused | run as given |
| MQTT 3 broker that speaks only MQTT 3.1 | fails: the test speaks 3.1.1 | NiFi's Paho client falls back to 3.1 |
| PostgreSQL `sslmode=verify-ca`/`verify-full` | fails without a root certificate file, like the pipeline: PgJDBC does not use the JDK trust store | same |

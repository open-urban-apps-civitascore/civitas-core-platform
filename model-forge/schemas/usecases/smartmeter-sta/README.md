# Smart Meter (SML) → SensorThings

Smart electricity meters publish SML readouts to MQTT. The pipeline applies a plausibility filter,
enriches each readout with meter master data from PostgreSQL, and writes one STA Observation per
electrical quantity (Wirkleistung Bezug, Wirkleistung Lieferung, Spannung) to a FROST-Server.

| Artifact | File |
|---|---|
| DataSource (MQTT) | `datasource-sml-mqtt.json` |
| DataSource (SQL master data) | `datasource-meter-db.json` |
| DataSink (FROST) | `datasink-frost-observations.json` |
| Pipeline | `pipeline-smartmeter-import.json` |
| Mappings | `mapping-meter-to-sta-thing.json`, `mapping-sml-to-{activepowerplus,activepowerminus,voltage}.json` |
| Elements | `SMLReading`, `MeterMasterData` |

`SMLReading` reuses the `Measurement` element from the Luftqualität use case and the shared
`GeoPoint` from `../../examples/`, demonstrating cross-domain schema reuse by URN.

## Host-provided configuration

The CORE DataSource contract carries a `user` field and an encrypted `password`; the documents in
this catalogue name the login user only. Credentials are supplied by the host at deploy time —
the portal wraps sensitive values as `ENC(base64)` and the config-adapter decrypts them when the
flow is deployed — so no secret is committed here.

The lookup key that joins a readout to its meter (`meterId`) is a property of the enrich step, not
of the DataSource: it is declared as `lookupKey` on the pipeline's enrich node. The SQL DataSource
only names the table to poll.

## Datastream identity

The observation mappings copy the FROST Datastream `@iot.id` from the enriched master data
(`frostDatastreamIds.<quantity>`), which is populated once the meter has been synced to FROST. An
STA Observation must link to a Datastream, so every per-quantity mapping emits that link.

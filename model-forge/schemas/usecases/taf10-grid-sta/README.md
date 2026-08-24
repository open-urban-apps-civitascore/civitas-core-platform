# TAF10 low-voltage grid protection → SensorThings

Grid measurement points publish TAF10 low-voltage telemetry to MQTT. The pipeline applies a
plausibility filter, enriches each reading with grid-node master data (thresholds and controllable
assets) from PostgreSQL, and then splits into two concerns: archiving the electrical quantities as
STA Observations in FROST, and requesting a protective switching action when a threshold is
violated.

| Artifact | File |
|---|---|
| DataSource (MQTT) | `datasource-taf10-mqtt.json` |
| DataSource (SQL master data) | `datasource-gridnode-db.json` |
| DataSink (FROST) | `datasink-frost-observations.json` |
| DataSink (switching actions) | `datasink-grid-control-actions.json` |
| Pipeline | `pipeline-taf10-grid-import.json` |
| Observation mappings | `mapping-gridnode-to-sta-thing.json`, `mapping-taf10-to-{activepowerplus,activepowerminus,voltage}.json` |
| Action mappings | `mapping-taf10-to-load-shift-action.json`, `mapping-taf10-to-feedin-curtail-action.json`, `mapping-taf10-to-voltage-notify-action.json` |
| Elements | `TAF10GridReading`, `Measurement`, `GridNodeMasterData`, `SwitchingAction` |

## One filter per condition

The three TAF10 threshold violations call for different mitigations, so each has its own filter
node feeding its own mapping:

| Condition | Filter expression | `actionType` | Priority |
|---|---|---|---|
| Load peak | `activePowerPlus.value > maxLoadW` | `shift-controllable-load` | high |
| Feed-in peak | `activePowerMinus.value > maxFeedInW` | `curtail-pv-feed-in` | high |
| Voltage-band violation | `voltage.value < voltageMinV \|\| voltage.value > voltageMaxV` | `notify-operator` | critical |

Curtailing PV feed-in is the correct answer to a feed-in peak only. An import overload or an
under-/overvoltage must not produce a curtailment command.

## Host-provided configuration

The CORE DataSource contract carries a `user` field and an encrypted `password`; the documents in
this catalogue name the login user only. Credentials are supplied by the host at deploy time —
the portal wraps sensitive values as `ENC(base64)` and the config-adapter decrypts them when the
flow is deployed — so no secret is committed here.

The lookup key that joins a reading to its grid node (`gridNodeId`) is a property of the enrich
step, not of the DataSource: it is declared as `lookupKey` on the pipeline's enrich node. The SQL
DataSource only names the table to poll.

The CORE DataSink contract offers a FROST target and a PostGIS target; there is no generic HTTP
target. Requested switching actions are therefore persisted to a table (`grid_switching_actions`)
that the grid-control service consumes, rather than pushed to a REST endpoint.

## Datastream identity

The observation mappings copy the FROST Datastream `@iot.id` from the enriched master data
(`frostDatastreamIds.<quantity>`), which is populated once the grid node has been synced to FROST.
An STA Observation must link to a Datastream, so every per-quantity mapping emits that link.

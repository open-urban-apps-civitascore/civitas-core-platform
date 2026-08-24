# Luftqualität → SensorThings

Air-quality monitoring stations publish raw pollutant readings to MQTT. The pipeline filters
implausible readings, enriches each message with station master data from PostgreSQL, and writes
one STA Observation per pollutant (NO₂, PM2.5, CO) to a FROST-Server.

| Artifact | File |
|---|---|
| DataSource (MQTT) | `datasource-airquality-mqtt.json` |
| DataSource (SQL master data) | `datasource-station-db.json` |
| DataSink (FROST) | `datasink-frost-observations.json` |
| Pipeline | `pipeline-luftqualitaet-import.json` |
| Mappings | `mapping-station-to-sta-thing.json`, `mapping-airquality-{no2,pm25,co}.json` |
| Elements | `AirQualityRawReading`, `Measurement`, `StationMasterData`, `StationCalibration`, `STAThing`, `STALocation`, `STAObservation` |

`GeoPoint` is shared with the other use cases and lives once in `../../examples/GeoPoint.schema.json`;
the element schemas here reference it by URN.

## Host-provided configuration

The CORE DataSource contract carries a `user` field and an encrypted `password`; the documents in
this catalogue name the login user only. Credentials are supplied by the host at deploy time —
the portal wraps sensitive values as `ENC(base64)` and the config-adapter decrypts them when the
flow is deployed — so no secret is committed here.

The lookup key that joins a reading to its station (`stationId`) is a property of the enrich step,
not of the DataSource: it is declared as `lookupKey` on the pipeline's enrich node. The SQL
DataSource only names the table to poll.

## Datastream identity

The observation mappings copy the FROST Datastream `@iot.id` from the enriched master data
(`frostDatastreamIds.<pollutant>`), which is populated once the station has been synced to FROST.
An STA Observation must link to a Datastream, so every per-pollutant mapping emits that link.

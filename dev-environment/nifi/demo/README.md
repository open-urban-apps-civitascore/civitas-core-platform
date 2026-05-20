# NiFi Pipeline-Konzept — MVP

End-to-end proof that the Civitas pipeline concept
([concepts_pipeline](https://docs.core.civitasconnect.digital/) — Architecture
docs) works on Apache NiFi 2.9.0. One pipeline:
`MQTT → ConvertRecord → UpdateRecord (RecordPath mapping) → PutDatabaseRecord`,
landing rows in a PostGIS table with WKT-wrapped geometries.

**Was bewiesen ist:**

- NiFi 2.x nimmt einen Versioned-Flow-Snapshot per Multipart-Upload an und
  startet die Process Group.
- Der `UpdateRecord`-Mapping-Node mit `Replacement Value Strategy =
  record-path-value` wendet RecordPath-Ausdrücke pro Ziel-Feld an.
- `concat('POINT(', /lon, ' ', /lat, ')')` erzeugt validen WKT für eine
  `geometry(Point, 4326)`-Spalte — CRS wird durch die PostGIS-Typmod
  durchgesetzt, nicht durch den Wert.
- `toDate(/ts, "yyyy-MM-dd'T'HH:mm:ss'Z'")` parst den ISO-Timestamp in einen
  Record-Date-Typ, den `PutDatabaseRecord` als JDBC-Timestamp an die
  `timestamptz`-Spalte bindet.
- Sensitive Properties (PostGIS-Password) sind im Snapshot-Download
  abwesend; sie werden post-upload per `PUT /controller-services/{id}`
  gesetzt.

**Was nicht abgedeckt ist:** der adapter-seitige Snapshot-Build aus
Roh-Inputs (Template-Resolve, Placeholder-Substitution, Mapping-Injection
aus `mappingRules`). Der MVP versendet einen fertigen Snapshot; die
Build-Schicht ist die nächste Stufe.

---

## Layout

```
demo/
├── docker-compose.yml             # mosquitto + postgis sidecar (separat,
│                                  # NICHT in start-portal-dev.sh eingebunden)
├── init/01-schema.sql             # PostGIS-Extension + sensor_observations
├── mosquitto/mosquitto.conf       # anonymes 1883
├── MQTT_TO_POSTGIS_demo.snapshot.json   # der Flow (commited)
├── scripts/
│   ├── upload-snapshot.sh         # Multipart-Upload (Bruno kann das nicht)
│   ├── deploy.sh                  # all-in-one Alternative zum Bruno-Deploy
│   ├── cleanup.sh                 # Teardown
│   └── build-snapshot.sh          # Regenerator (REST-Konstruktion → Download)
└── bruno/
    ├── bruno.json
    ├── collection.bru             # collection-level Bearer
    ├── environments/local.bru     # nifiBaseUrl, creds, groupName
    ├── README-bruno-quirks.md     # cookie/CSRF + setEnvVar Falltüren
    ├── 01_deploy/                 # 7 Requests: token, find, set pw, enable, start
    ├── 02_verify/                 # 5 Requests: token, find, processor states, bulletins
    └── 03_cleanup/                # 7 Requests: token, find, stop, disable, delete
```

---

## Voraussetzungen

1. **NiFi** läuft (`dev-environment/nifi/docker-compose.yml`):
   ```bash
   cd dev-environment/nifi && docker compose up -d
   ```
   NiFi 2.9.0 auf `https://localhost:8443/nifi` (single-user `admin`,
   Password in `.env.example`).

2. **Demo-Sidecars** (Mosquitto + PostGIS) starten:
   ```bash
   cd dev-environment/nifi/demo && docker compose up -d
   ```
   - Mosquitto: `localhost:1883`, anonym, network `civitas-network`.
   - PostGIS: `localhost:5435`, DB `nifi_demo`, User/Pass `nifi`/`nifi-demo-password`,
     Tabelle `sensor_observations` mit `geom geometry(Point, 4326)` per Init-SQL.

   Beide Container hängen an `civitas-network`, damit NiFi sie als
   `civitas-nifi-demo-mosquitto:1883` und `civitas-nifi-demo-postgis:5432`
   erreichen kann.

3. **Mosquitto-Client** für die Test-Publishes (entweder host-installiert
   oder via `docker exec civitas-nifi-demo-mosquitto mosquitto_pub …`).

---

## Demo durchspielen

### Option A: nur Shell

```bash
cd dev-environment/nifi/demo
./scripts/cleanup.sh                  # alte Demo-PG abräumen
./scripts/upload-snapshot.sh          # Snapshot hochladen, liefert PG-UUID
./scripts/deploy.sh                   # patch password + enable CSs + start PG

sleep 10                              # ConsumeMQTT braucht ~10s zum Subscribe

docker exec civitas-nifi-demo-mosquitto mosquitto_pub -h localhost \
  -t "sensors/sensor-001/temp" \
  -m '{"lat":50.110,"lon":8.660,"temperature":21.3,"ts":"2026-05-20T18:30:00Z","station_id":"sensor-001"}'

docker exec -e PGPASSWORD=nifi-demo-password civitas-nifi-demo-postgis \
  psql -U nifi -d nifi_demo -c \
  "SELECT station_id, temperature, measurement_time, ST_AsText(geom), ST_SRID(geom) FROM sensor_observations;"

./scripts/cleanup.sh
```

Erwartete Zeile:
```
 station_id | temperature |    measurement_time    |    st_astext    | st_srid
------------+-------------+------------------------+-----------------+---------
 sensor-001 |        21.3 | 2026-05-20 18:30:00+00 | POINT(8.66 50.11)| 4326
```

### Option B: Bruno + Shell-Upload

`@usebruno/cli@3.3.0` kann das Multipart-Upload nicht authentifizieren
(siehe [`bruno/README-bruno-quirks.md`](bruno/README-bruno-quirks.md)), daher
bleibt der Upload-Schritt als Shell. Alles Übrige läuft in Bruno.

```bash
cd dev-environment/nifi/demo

./scripts/cleanup.sh
./scripts/upload-snapshot.sh                          # Upload

cd bruno
BRU="npx --yes @usebruno/cli@3.3.0 run"
$BRU 01_deploy --env local --insecure --disable-cookies   # token, set pw, enable, start

sleep 10
docker exec civitas-nifi-demo-mosquitto mosquitto_pub -h localhost \
  -t "sensors/sensor-001/temp" \
  -m '{"lat":50.110,"lon":8.660,"temperature":21.3,"ts":"2026-05-20T18:30:00Z","station_id":"sensor-001"}'

$BRU 02_verify --env local --insecure --disable-cookies   # processors RUNNING, bulletins clear
$BRU 03_cleanup --env local --insecure --disable-cookies  # stop, disable, delete
```

Das `--disable-cookies` ist Pflicht — Erklärung in
[`bruno/README-bruno-quirks.md`](bruno/README-bruno-quirks.md).

---

## Bekannte Fallstricke

- **`--disable-cookies` immer.** NiFi setzt einen Bearer-Cookie auf
  `/access/token`; Bruno's Cookie-Jar schickt ihn auf jede Folgeanfrage, und
  NiFis CSRF-Schutz weist PUT/POST/DELETE mit Cookie ohne CSRF-Token ab.
- **10 Sekunden Wartezeit zwischen Start und Publish.** ConsumeMQTT
  abonniert das Topic erst nach der Processor-Initialisierung. Sleep zu
  knapp → Messages werden vor dem Subscribe vom Broker mit QoS 0 verworfen.
- **Sensitive Properties.** Beim Snapshot-Download strippt NiFi die
  `Password`-Property. `01_deploy/05_set_dbcp_password.bru` (bzw. der
  entsprechende Schritt in `deploy.sh`) muss vor dem Enable laufen.
- **PostGIS-Sidecar-Port 5435** (nicht 5434 — letzteres ist von
  `dev-environment/geoserver` belegt).

---

## Den Snapshot regenerieren

Wenn sich an Bundle-Versionen, Property-Namen oder Flow-Topologie etwas
ändert:

```bash
./scripts/build-snapshot.sh ./MQTT_TO_POSTGIS_demo.snapshot.json
```

Das Skript konstruiert die PG via NiFi-REST (Controller-Services,
Processors mit RecordPath-Mapping, Connections), lädt sie via
`GET /process-groups/{id}/download` herunter und löscht die temporäre PG.
Es benötigt eine laufende NiFi-Instanz.

---

## Konzept-Referenzen

- [overview.md](https://docs.core.civitasconnect.digital/) — Einstieg, Datenfluss
- [explanation.md](https://docs.core.civitasconnect.digital/) — Grundprinzipien, Mapping-Konzept, Snapshot-im-Blob
- [reference/snapshot-format.md](https://docs.core.civitasconnect.digital/) — Snapshot-Aufbau, RecordPath-Funktionen, NiFi-REST-Endpoints

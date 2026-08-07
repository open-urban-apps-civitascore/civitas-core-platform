-- Row-level access control for FROST sensor data (Sensor Data Storage / SensorThings) via Trino.
--
-- FROST's "Projects" plugin stores every Civitas dataset's Things/Datastreams/Observations in one
-- shared `public` schema and one shared set of tables — unlike PostGIS, where each Civitas dataset
-- gets its own Postgres schema (so table-level access already equals dataset-level access). There
-- is no schema/table boundary here to gate on, so OPA needs a row-level filter instead.
--
-- These views collapse each entity's join back to PROJECTS (1 or 2 hops, depending on the table)
-- into a single `frost_project_id` column, so the OPA row-filter policy
-- (authz/rego/policy/trino_row_filters.rego) only ever has to compare one column, uniformly,
-- across every view — the join complexity lives here, once, in reviewable/EXPLAIN-able SQL, not
-- re-derived per table in Rego.
--
-- Deliberately excluded from this schema:
--   - USERS, ROLES, USER_PROJECT_ROLE, USER_ROLES: FROST's own internal auth tables. Civitas runs
--     FROST with plugins_projects_enableDefaultRules=false (no FROST-native auth) — these must stay
--     unreachable from Trino/Superset entirely, not just unscoped.
--   - OBS_PROPERTIES: has no project-junction table of its own (unlike THINGS/SENSORS/LOCATIONS/
--     FEATURES, which each have a direct `<entity>_PROJECTS` table). It's reachable only
--     transitively via DATASTREAMS, and a given ObservedProperty (e.g. "temperature") can
--     legitimately be reused across multiple projects' Datastreams, so it doesn't have a single
--     well-defined owning project the way the other entities do. Excluded rather than guessed at;
--     revisit if a real need for it surfaces.
--   - THINGS_LOCATIONS, LOCATIONS_HIST_LOCATIONS: pure link tables between two already-scoped
--     entities, carrying no data of their own beyond the two foreign keys.
--
-- Idempotent throughout (CREATE OR REPLACE VIEW / IF NOT EXISTS / duplicate_object guards) so this
-- is safe to re-run on every deploy. Must run AFTER FROST-Server itself (not just its database) is
-- up: FROST creates THINGS/PROJECTS/etc. lazily on its own startup
-- (persistence_autoUpdateDatabase=true), so this cannot be a docker-entrypoint-initdb.d script —
-- see the migration-runner service in dev-environment/frost/docker-compose.yaml.

CREATE SCHEMA IF NOT EXISTS frost_scoped;

-- Thin passthrough, no free-form PROPERTIES jsonb (unvetted arbitrary content).
CREATE OR REPLACE VIEW frost_scoped.projects_scoped AS
SELECT
    p."ID"::varchar AS frost_project_id,
    p."NAME",
    p."DESCRIPTION",
    p."ISPUBLIC"
FROM public."PROJECTS" p;

-- 1-hop: THINGS has its own junction table.
CREATE OR REPLACE VIEW frost_scoped.things_scoped AS
SELECT
    t.*,
    tp."PROJECT_ID"::varchar AS frost_project_id
FROM public."THINGS" t
JOIN public."THING_PROJECTS" tp ON tp."THING_ID" = t."ID";

-- 1-hop: DATASTREAMS.THING_ID joins THING_PROJECTS directly, no need to round-trip through THINGS.
-- OBSERVED_AREA is cast to WKT text, not passed through as-is: Trino's generic PostgreSQL JDBC
-- connector can't map the PostGIS `geometry` type (fails with "Trino type Geometry is not
-- compatible with read function ..." the moment a client — e.g. Superset introspecting a new
-- dataset — tries to read a row). DROP+CREATE, not the usual CREATE OR REPLACE: this enumerates
-- columns in a different order than the wildcard SELECT this view previously used, and Postgres
-- rejects a REPLACE that reorders/renames existing output columns.
DROP VIEW IF EXISTS frost_scoped.datastreams_scoped;

CREATE VIEW frost_scoped.datastreams_scoped AS
SELECT
    d."ID",
    d."NAME",
    d."DESCRIPTION",
    d."PROPERTIES",
    d."OBSERVATION_TYPE",
    d."PHENOMENON_TIME_START",
    d."PHENOMENON_TIME_END",
    d."RESULT_TIME_START",
    d."RESULT_TIME_END",
    ST_AsText(d."OBSERVED_AREA") AS "OBSERVED_AREA",
    d."SENSOR_ID",
    d."OBS_PROPERTY_ID",
    d."THING_ID",
    d."UNIT_NAME",
    d."UNIT_SYMBOL",
    d."UNIT_DEFINITION",
    d."LAST_FOI_ID",
    d."RESTRICTED",
    tp."PROJECT_ID"::varchar AS frost_project_id
FROM public."DATASTREAMS" d
JOIN public."THING_PROJECTS" tp ON tp."THING_ID" = d."THING_ID";

-- 2-hop (the deepest, and highest-volume table): OBSERVATIONS -> DATASTREAMS -> THING_PROJECTS.
CREATE OR REPLACE VIEW frost_scoped.observations_scoped AS
SELECT
    o.*,
    tp."PROJECT_ID"::varchar AS frost_project_id
FROM public."OBSERVATIONS" o
JOIN public."DATASTREAMS" ds ON ds."ID" = o."DATASTREAM_ID"
JOIN public."THING_PROJECTS" tp ON tp."THING_ID" = ds."THING_ID";

-- 1-hop: SENSORS has its own junction table.
CREATE OR REPLACE VIEW frost_scoped.sensors_scoped AS
SELECT
    s.*,
    sp."PROJECT_ID"::varchar AS frost_project_id
FROM public."SENSORS" s
JOIN public."SENSOR_PROJECTS" sp ON sp."SENSOR_ID" = s."ID";

-- 1-hop: LOCATIONS has its own junction table (LOCATION_PROJECTS) — simpler than routing through
-- the THINGS_LOCATIONS link table. GEOM is cast to WKT text for the same Trino/PostGIS-type reason
-- as datastreams_scoped.OBSERVED_AREA above. DROP+CREATE for the same reordered-columns reason too.
DROP VIEW IF EXISTS frost_scoped.locations_scoped;

CREATE VIEW frost_scoped.locations_scoped AS
SELECT
    l."ID",
    l."NAME",
    l."DESCRIPTION",
    l."PROPERTIES",
    l."ENCODING_TYPE",
    l."LOCATION",
    ST_AsText(l."GEOM") AS "GEOM",
    l."GEN_FOI_ID",
    l."RESTRICTED",
    lp."PROJECT_ID"::varchar AS frost_project_id
FROM public."LOCATIONS" l
JOIN public."LOCATION_PROJECTS" lp ON lp."LOCATION_ID" = l."ID";

-- 1-hop: FEATURES has its own junction table. GEOM is cast to WKT text for the same
-- Trino/PostGIS-type reason as datastreams_scoped.OBSERVED_AREA above. DROP+CREATE for the same
-- reordered-columns reason too.
DROP VIEW IF EXISTS frost_scoped.features_scoped;

CREATE VIEW frost_scoped.features_scoped AS
SELECT
    f."ID",
    f."NAME",
    f."DESCRIPTION",
    f."PROPERTIES",
    f."ENCODING_TYPE",
    f."FEATURE",
    ST_AsText(f."GEOM") AS "GEOM",
    f."RESTRICTED",
    fp."PROJECT_ID"::varchar AS frost_project_id
FROM public."FEATURES" f
JOIN public."FEATURE_PROJECTS" fp ON fp."FEATURE_ID" = f."ID";

-- 1-hop: HIST_LOCATIONS carries THING_ID directly, so it joins THING_PROJECTS the same way
-- DATASTREAMS does — no need to route through LOCATIONS_HIST_LOCATIONS/LOCATIONS.
CREATE OR REPLACE VIEW frost_scoped.hist_locations_scoped AS
SELECT
    hl.*,
    tp."PROJECT_ID"::varchar AS frost_project_id
FROM public."HIST_LOCATIONS" hl
JOIN public."THING_PROJECTS" tp ON tp."THING_ID" = hl."THING_ID";

-- Supporting indexes for the joins above. FK columns are usually already indexed by FROST's own
-- migrations; these are IF NOT EXISTS guards, not an assumption they're missing.
CREATE INDEX IF NOT EXISTS idx_thing_projects_thing_id ON public."THING_PROJECTS" ("THING_ID");
CREATE INDEX IF NOT EXISTS idx_sensor_projects_sensor_id ON public."SENSOR_PROJECTS" ("SENSOR_ID");
CREATE INDEX IF NOT EXISTS idx_location_projects_location_id ON public."LOCATION_PROJECTS" ("LOCATION_ID");
CREATE INDEX IF NOT EXISTS idx_feature_projects_feature_id ON public."FEATURE_PROJECTS" ("FEATURE_ID");
CREATE INDEX IF NOT EXISTS idx_datastreams_thing_id ON public."DATASTREAMS" ("THING_ID");
CREATE INDEX IF NOT EXISTS idx_observations_datastream_id ON public."OBSERVATIONS" ("DATASTREAM_ID");
CREATE INDEX IF NOT EXISTS idx_hist_locations_thing_id ON public."HIST_LOCATIONS" ("THING_ID");

-- Least-privilege role for Trino's "frost" catalog connection. Views are definer's-rights (the
-- default), so this role can read frost_scoped.* without ever being granted access to the
-- underlying public.* tables — that's the actual enforcement boundary: even a bug in the OPA
-- row-filter policy can't leak raw tables, because this role physically cannot read them.
-- Postgres has no native "CREATE ROLE IF NOT EXISTS"; guard against re-running with the
-- duplicate_object exception, mirroring PostgisSagaHandler's idempotency style. The password is
-- set separately below via plain ALTER ROLE: psql's `:'var'` substitution does not run inside a
-- dollar-quoted DO block body, so it can't be interpolated on the CREATE ROLE line itself.
DO $$
BEGIN
    CREATE ROLE frost_trino_reader LOGIN;
EXCEPTION
    WHEN duplicate_object THEN
        NULL;
END
$$;

ALTER ROLE frost_trino_reader WITH PASSWORD :'frost_trino_reader_password';

GRANT CONNECT ON DATABASE sensorthings TO frost_trino_reader;
GRANT USAGE ON SCHEMA frost_scoped TO frost_trino_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA frost_scoped TO frost_trino_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA frost_scoped GRANT SELECT ON TABLES TO frost_trino_reader;

-- Defense in depth: explicitly assert no access to public, in case some future change (e.g. a
-- broad `GRANT ALL ON DATABASE`) ever accidentally widens this role's privileges.
REVOKE ALL ON SCHEMA public FROM frost_trino_reader;

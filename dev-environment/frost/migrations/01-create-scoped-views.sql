-- Dataset-level access control for FROST sensor data (Sensor Data Storage / SensorThings) via Trino.
--
-- FROST's "Projects" plugin stores every Civitas dataset's Things/Datastreams/Observations in one
-- shared `public` schema and one shared set of tables. The scoped views below add frost_project_id;
-- per-dataset schemas wrap those views and filter each one to a single CORE dataset. OPA can then
-- authorize the schema directly, as it already does for PostGIS dataset schemas.
--
-- These views collapse each entity's join back to PROJECTS (1 or 2 hops, depending on the table)
-- into a single `frost_project_id` column. Per-dataset views reuse those joins rather than
-- re-deriving them in SQL or OPA.
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
-- connector can't map the PostGIS `geometry` type. Keep the explicit output column order stable so
-- CREATE OR REPLACE remains safe on repeated migration runs.
CREATE OR REPLACE VIEW frost_scoped.datastreams_scoped AS
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
-- as datastreams_scoped.OBSERVED_AREA above.
CREATE OR REPLACE VIEW frost_scoped.locations_scoped AS
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
-- Trino/PostGIS-type reason as datastreams_scoped.OBSERVED_AREA above.
CREATE OR REPLACE VIEW frost_scoped.features_scoped AS
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

-- Least-privilege role for Trino's FROST catalog connection. Dataset-specific views are
-- definer-rights (the default), so this role can read only those views without being granted
-- access to the underlying public.* tables or shared frost_scoped.* source views.
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
-- Defense in depth: explicitly assert no access to public, in case some future change (e.g. a
-- broad `GRANT ALL ON DATABASE`) ever accidentally widens this role's privileges.
REVOKE ALL ON SCHEMA public FROM frost_trino_reader;
REVOKE ALL ON SCHEMA frost_scoped FROM frost_trino_reader;
REVOKE ALL ON ALL TABLES IN SCHEMA frost_scoped FROM frost_trino_reader;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM frost_trino_reader;

CREATE TABLE IF NOT EXISTS frost_scoped.dataset_schema_registry (
    project_id text PRIMARY KEY,
    schema_name text NOT NULL UNIQUE
);

-- FROST project names end with the immutable CORE dataset UUID, for example "Road data (uuid)".
-- The FROST/Trino schema uses a readable, normalized dataset name plus the UUID's last eight
-- hexadecimal characters. PostGIS keeps its independent UUID schema.
CREATE OR REPLACE FUNCTION frost_scoped.dataset_schema_name(project_name text)
RETURNS text
LANGUAGE plpgsql
IMMUTABLE
SET search_path = pg_catalog
AS $$
DECLARE
    dataset_id text;
    dataset_name text;
    schema_name text;
    suffix text;
BEGIN
    dataset_id := (regexp_match(
        project_name,
        '\(([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\)$'
    ))[1];
    IF dataset_id IS NULL THEN
        RETURN NULL;
    END IF;

    dataset_name := regexp_replace(project_name, '\s*\([0-9A-Fa-f-]{36}\)$', '');
    schema_name := trim(
        both '_' from regexp_replace(
            translate(dataset_name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),
            '[^a-z0-9]+', '_', 'g'
        )
    );
    IF schema_name = '' THEN
        schema_name := 'dataset';
    END IF;
    schema_name := rtrim(left(schema_name, 54), '_');
    IF schema_name ~ '^[0-9]' THEN
        schema_name := 'ds_' || rtrim(left(schema_name, 51), '_');
    END IF;
    suffix := right(replace(lower(dataset_id), '-', ''), 8);
    RETURN schema_name || '_' || suffix;
END
$$;

CREATE OR REPLACE FUNCTION frost_scoped.ensure_dataset_views(project_id text, project_name text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
DECLARE
    schema_name text;
    previous_schema_name text;
    view_name text;
BEGIN
    schema_name := frost_scoped.dataset_schema_name($2);
    IF schema_name IS NULL THEN
        RETURN;
    END IF;

    SELECT registry.schema_name
      INTO previous_schema_name
      FROM frost_scoped.dataset_schema_registry registry
     WHERE registry.project_id = $1;

    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', schema_name);
    FOREACH view_name IN ARRAY ARRAY[
        'things_scoped', 'datastreams_scoped', 'observations_scoped', 'sensors_scoped',
        'locations_scoped', 'features_scoped', 'hist_locations_scoped'
    ] LOOP
        EXECUTE format(
            'CREATE OR REPLACE VIEW %I.%I AS '
            'SELECT * FROM frost_scoped.%I WHERE frost_project_id = %L',
            schema_name,
            view_name,
            view_name,
            $1
        );
        EXECUTE format('GRANT SELECT ON TABLE %I.%I TO frost_trino_reader', schema_name, view_name);
    END LOOP;

    EXECUTE format('GRANT USAGE ON SCHEMA %I TO frost_trino_reader', schema_name);

    INSERT INTO frost_scoped.dataset_schema_registry(project_id, schema_name)
    VALUES ($1, schema_name)
    ON CONFLICT ON CONSTRAINT dataset_schema_registry_pkey
    DO UPDATE SET schema_name = EXCLUDED.schema_name;

    -- Preserve previous schema names as compatibility aliases for saved Superset datasets. OPA
    -- authorizes UUID aliases by dataset ID and returns only the current name during discovery.
END
$$;

CREATE OR REPLACE FUNCTION frost_scoped.sync_dataset_views_for_project()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- Leave the (view-only) schema in place. OPA denies it once CORE no longer has this
        -- dataset, and retaining it avoids deleting objects used by saved Superset charts.
        DELETE FROM frost_scoped.dataset_schema_registry WHERE project_id = OLD."ID"::text;
        RETURN OLD;
    END IF;

    PERFORM frost_scoped.ensure_dataset_views(NEW."ID"::text, NEW."NAME");
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS sync_dataset_views_for_project ON public."PROJECTS";
CREATE TRIGGER sync_dataset_views_for_project
AFTER INSERT OR UPDATE OR DELETE ON public."PROJECTS"
FOR EACH ROW EXECUTE FUNCTION frost_scoped.sync_dataset_views_for_project();

-- Reconcile existing projects as well as projects created after this migration.
DO $$
DECLARE
    project record;
BEGIN
    FOR project IN SELECT "ID", "NAME" FROM public."PROJECTS" LOOP
        PERFORM frost_scoped.ensure_dataset_views(project."ID"::text, project."NAME");
    END LOOP;
END
$$;

REVOKE ALL ON FUNCTION frost_scoped.dataset_schema_name(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION frost_scoped.ensure_dataset_views(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION frost_scoped.sync_dataset_views_for_project() FROM PUBLIC;

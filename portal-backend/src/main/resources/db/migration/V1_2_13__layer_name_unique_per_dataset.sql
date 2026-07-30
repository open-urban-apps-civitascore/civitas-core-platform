-- Layer names are unique per dataset, not per sink. GeoServer publishes every layer of a dataset
-- into one workspace with one datastore and addresses feature types by layer name alone, so two
-- sinks of the same dataset carrying one name denote the same GeoServer resource: provisioning
-- dropped the second silently (409 treated as success) and an update overwrote the first, native
-- table included.

-- Resolve names the old per-sink constraint allowed: keep the oldest row's name, suffix the rest
-- with their own id. Renaming rather than deleting — the losing rows were never separately
-- published anyway, so this surfaces the hidden conflict instead of cementing it. The id is used
-- as the suffix because a counter could collide with a name already present in the dataset
-- ('a' + 'a' both renamed to 'a-2' when 'a-2' exists). Truncated to fit VARCHAR(255).
WITH ranked AS (SELECT id,
                       ROW_NUMBER() OVER (PARTITION BY dataset_id, layer_name
                           ORDER BY created_at, id) AS position
                FROM layers)
UPDATE layers l
SET layer_name = LEFT(l.layer_name, 218) || '-' || REPLACE(l.id::TEXT, '-', '')
FROM ranked
WHERE l.id = ranked.id
  AND ranked.position > 1;

ALTER TABLE layers
    DROP CONSTRAINT uk_layers_datasink_layer_name;

ALTER TABLE layers
    ADD CONSTRAINT uk_layers_dataset_layer_name UNIQUE (dataset_id, layer_name);

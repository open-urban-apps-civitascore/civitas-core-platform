-- FROST project provisioning is now conditional on the dataset having a FROST data sink. Datasets
-- released before that change got a project unconditionally and were never required to carry such a
-- sink, so without this backfill their teardown would skip the project and their re-release would be
-- rejected for lacking a sink they demonstrably do not need to be given by hand.
--
-- A non-null project_id is proof a project was provisioned, which makes the sink row a truthful
-- record of existing infrastructure rather than a new configuration choice.
INSERT INTO data_sinks (id, created_at, dataset_id, pipeline_id, data_sink_type, configuration)
SELECT gen_random_uuid(), NOW(), d.id, NULL, 'FROST', '{}'::jsonb
FROM datasets d
WHERE d.project_id IS NOT NULL
  AND NOT EXISTS (SELECT 1
                  FROM data_sinks s
                  WHERE s.dataset_id = d.id
                    AND s.data_sink_type = 'FROST');

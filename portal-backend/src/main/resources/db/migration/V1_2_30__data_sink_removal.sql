-- A Data sink can be the target of several Pipelines.
CREATE TABLE pipeline_data_sinks
(
    pipeline_id  UUID NOT NULL,
    data_sink_id UUID NOT NULL,
    CONSTRAINT pk_pipeline_data_sinks PRIMARY KEY (pipeline_id, data_sink_id),
    CONSTRAINT fk_pipeline_data_sinks_on_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_data_sinks_on_data_sink FOREIGN KEY (data_sink_id) REFERENCES data_sinks (id) ON DELETE CASCADE
);
CREATE INDEX idx_pipeline_data_sinks_data_sink ON pipeline_data_sinks (data_sink_id);
INSERT INTO pipeline_data_sinks (pipeline_id, data_sink_id)
SELECT pipeline_id, id FROM data_sinks WHERE pipeline_id IS NOT NULL;

-- A removed Data sink whose storage exists stays as PENDING_UNPROVISION until a release removes the
-- storage.
ALTER TABLE data_sinks
    ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT ck_data_sinks_state CHECK (state IN ('ACTIVE', 'PENDING_UNPROVISION')),
    ADD CONSTRAINT ck_data_sinks_pending_provisioned CHECK (state = 'ACTIVE' OR provisioned);

-- The backing Data sink kinds of the OWS and STA API kinds at this version.
CREATE TEMPORARY TABLE api_backing_kinds (standard, data_sink_type) AS
VALUES ('OWS', 'POSTGIS'),
       ('STA', 'FROST');

-- The API of a released Dataset holds the route that its unrelease removes, so it stays, and the
-- newest provisioned Data sink without a Pipeline stays active to back it when no linked one does.
CREATE TEMPORARY TABLE kept_unlinked_sinks AS
SELECT DISTINCT ON (s.dataset_id, s.data_sink_type) s.id, s.dataset_id, s.data_sink_type
FROM data_sinks s
         JOIN datasets d ON d.id = s.dataset_id
WHERE d.status = 'AVAILABLE'
  AND s.pipeline_id IS NULL
  AND s.provisioned
  AND EXISTS (SELECT 1
              FROM named_apis a
                       JOIN api_backing_kinds b ON b.standard = a.standard
              WHERE a.dataset_id = s.dataset_id
                AND b.data_sink_type = s.data_sink_type)
  AND NOT EXISTS (SELECT 1
                  FROM data_sinks linked
                  WHERE linked.dataset_id = s.dataset_id
                    AND linked.data_sink_type = s.data_sink_type
                    AND linked.pipeline_id IS NOT NULL)
ORDER BY s.dataset_id, s.data_sink_type, s.created_at DESC, s.id DESC;

DO
$$
    DECLARE
        kept RECORD;
    BEGIN
        FOR kept IN SELECT id, dataset_id, data_sink_type FROM kept_unlinked_sinks
            LOOP
                RAISE NOTICE 'Dataset % keeps % Data sink % without a Pipeline active to back its API', kept.dataset_id, kept.data_sink_type, kept.id;
            END LOOP;
    END
$$;

-- Any other Data sink without a Pipeline counts as removed. Its Model Forge configuration cannot be
-- removed from SQL and stays behind.
DELETE FROM layers l
USING data_sinks s
WHERE l.datasink_id = s.id
  AND s.pipeline_id IS NULL
  AND s.id NOT IN (SELECT id FROM kept_unlinked_sinks);

UPDATE data_sinks
SET state = 'PENDING_UNPROVISION'
WHERE pipeline_id IS NULL
  AND provisioned
  AND id NOT IN (SELECT id FROM kept_unlinked_sinks);

DELETE FROM data_sinks
WHERE pipeline_id IS NULL
  AND NOT provisioned;

-- Drops the foreign key fk_data_sinks_on_pipeline and the index idx_data_sinks_pipeline with it.
ALTER TABLE data_sinks DROP COLUMN pipeline_id;

-- At most one OWS and one STA API per Dataset. Deferred, because a request that replaces such an
-- API inserts the new row before it deletes the old one.
ALTER TABLE named_apis
    ADD COLUMN singleton_key VARCHAR(16),
    ADD CONSTRAINT uk_named_api_dataset_singleton_key UNIQUE (dataset_id, singleton_key)
        DEFERRABLE INITIALLY DEFERRED;

UPDATE named_apis n
SET singleton_key = n.standard
FROM (SELECT DISTINCT ON (dataset_id, standard) id
      FROM named_apis
      WHERE standard IN ('OWS', 'STA')
      ORDER BY dataset_id, standard, created_at, id) oldest
WHERE n.id = oldest.id;

-- A duplicate keeps its route; deleting it is the Data Architect's decision.
DO
$$
    DECLARE
        duplicate RECORD;
    BEGIN
        FOR duplicate IN
            SELECT dataset_id, standard, count(*) AS apis
            FROM named_apis
            WHERE standard IN ('OWS', 'STA')
            GROUP BY dataset_id, standard
            HAVING count(*) > 1
            LOOP
                RAISE NOTICE 'Dataset % has % % APIs; only the oldest is unique', duplicate.dataset_id, duplicate.apis, duplicate.standard;
            END LOOP;
    END
$$;

-- A Layer goes with the API that publishes it.
ALTER TABLE layers
    ADD COLUMN named_api_id UUID,
    ADD CONSTRAINT fk_layers_on_named_api FOREIGN KEY (named_api_id) REFERENCES named_apis (id) ON DELETE CASCADE;
CREATE INDEX idx_layers_named_api ON layers (named_api_id);

UPDATE layers l
SET named_api_id = a.id
FROM named_apis a
WHERE a.dataset_id = l.dataset_id
  AND a.singleton_key = 'OWS';

-- An API without an active Data sink of a backing kind blocks the next release.
DO
$$
    DECLARE
        removed RECORD;
    BEGIN
        FOR removed IN
            DELETE FROM named_apis a
                USING datasets d
                WHERE d.id = a.dataset_id
                    AND d.status <> 'AVAILABLE'
                    AND a.standard IN (SELECT standard FROM api_backing_kinds)
                    AND NOT EXISTS (SELECT 1
                                    FROM data_sinks s
                                             JOIN api_backing_kinds b ON b.data_sink_type = s.data_sink_type
                                    WHERE s.dataset_id = a.dataset_id
                                      AND s.state = 'ACTIVE'
                                      AND b.standard = a.standard)
                RETURNING a.id, a.dataset_id, a.standard
            LOOP
                RAISE NOTICE 'Dataset % lost its % API %: no active Data sink backs it', removed.dataset_id, removed.standard, removed.id;
            END LOOP;
    END
$$;

DROP TABLE kept_unlinked_sinks;
DROP TABLE api_backing_kinds;

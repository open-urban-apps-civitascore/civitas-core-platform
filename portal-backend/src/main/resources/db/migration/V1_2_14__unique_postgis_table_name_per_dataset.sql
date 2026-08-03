-- All POSTGIS sinks of a dataset provision into one shared schema, so two sinks carrying the same
-- tableName denote one physical table: the first one provisioned shapes its columns, the second
-- inherits them and its writes fail at runtime with missing-column errors while provisioning still
-- reports success. Compared case-insensitively.

-- Pre-existing duplicates are reported rather than resolved: renaming a sink silently would point it
-- at a different table than the one its consumers already read, and no automatic choice of victim is
-- defensible without knowing which sink is live.
DO
$$
    DECLARE
        duplicate_groups BIGINT;
    BEGIN
        SELECT count(*)
        INTO duplicate_groups
        FROM (SELECT 1
              FROM data_sinks
              WHERE data_sink_type = 'POSTGIS'
                AND JSONB_TYPEOF(configuration -> 'tableName') = 'string'
              GROUP BY dataset_id, LOWER(configuration ->> 'tableName')
              HAVING count(*) > 1) d;

        IF duplicate_groups > 0 THEN
            RAISE EXCEPTION
                'Cannot enforce POSTGIS tableName uniqueness: % (dataset_id, tableName) group(s) already hold duplicates', duplicate_groups
                USING HINT = 'Rename or delete the conflicting sinks, then re-run the migration. Query: SELECT dataset_id, LOWER(configuration ->> ''tableName'') AS table_name, count(*) FROM data_sinks WHERE data_sink_type = ''POSTGIS'' AND JSONB_TYPEOF(configuration -> ''tableName'') = ''string'' GROUP BY 1, 2 HAVING count(*) > 1;';
        END IF;
    END
$$;

CREATE UNIQUE INDEX uk_data_sinks_dataset_postgis_table_name
    ON data_sinks (dataset_id, LOWER(configuration ->> 'tableName'))
    WHERE data_sink_type = 'POSTGIS'
      AND JSONB_TYPEOF(configuration -> 'tableName') = 'string';

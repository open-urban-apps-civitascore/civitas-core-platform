-- Rows in the shape of V1_2_29: a Data sink has at most one Pipeline, a Layer has no API.

-- A Dataset with a linked sink, an unlinked one and an OWS API.
INSERT INTO datasets (id, name, created_at)
VALUES ('30000000-0000-4000-8000-000000000001', 'Roads', now());

INSERT INTO pipelines (id, name, created_at, dataset_id)
VALUES ('30000000-0000-4000-8000-000000000002', 'Roads pipeline', now(),
        '30000000-0000-4000-8000-000000000001');

INSERT INTO data_sinks (id, created_at, data_sink_type, dataset_id, pipeline_id, provisioned)
VALUES ('31000000-0000-4000-8000-000000000001', now(), 'POSTGIS',
        '30000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002', true),
       ('31000000-0000-4000-8000-000000000002', now(), 'POSTGIS',
        '30000000-0000-4000-8000-000000000001', NULL, false);

INSERT INTO named_apis (id, created_at, dataset_id, name, slug, standard)
VALUES ('32000000-0000-4000-8000-000000000001', now(), '30000000-0000-4000-8000-000000000001',
        'Maps', 'maps', 'OWS');

INSERT INTO layers (id, created_at, dataset_id, datasink_id, layer_name)
VALUES ('33000000-0000-4000-8000-000000000001', now(), '30000000-0000-4000-8000-000000000001',
        '31000000-0000-4000-8000-000000000001', 'roads');

-- A Dataset with a Layer but no OWS API.
INSERT INTO datasets (id, name, created_at)
VALUES ('40000000-0000-4000-8000-000000000001', 'Sensors', now());

INSERT INTO data_sinks (id, created_at, data_sink_type, dataset_id, pipeline_id, provisioned)
VALUES ('41000000-0000-4000-8000-000000000001', now(), 'POSTGIS',
        '40000000-0000-4000-8000-000000000001', NULL, false);

INSERT INTO layers (id, created_at, dataset_id, datasink_id, layer_name)
VALUES ('43000000-0000-4000-8000-000000000001', now(), '40000000-0000-4000-8000-000000000001',
        '41000000-0000-4000-8000-000000000001', 'unserved');

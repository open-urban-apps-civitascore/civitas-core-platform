-- Rows in the shape of V1_2_29: a Data sink has at most one Pipeline, a Layer has no API.

-- A Dataset with a linked sink, two unlinked ones and an OWS API.
INSERT INTO datasets (id, name, created_at)
VALUES ('30000000-0000-4000-8000-000000000001', 'Roads', now());

INSERT INTO pipelines (id, name, created_at, dataset_id)
VALUES ('30000000-0000-4000-8000-000000000002', 'Roads pipeline', now(),
        '30000000-0000-4000-8000-000000000001');

INSERT INTO data_sinks (id, created_at, data_sink_type, dataset_id, pipeline_id, provisioned)
VALUES ('31000000-0000-4000-8000-000000000001', now(), 'POSTGIS',
        '30000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002', true),
       ('31000000-0000-4000-8000-000000000002', now(), 'POSTGIS',
        '30000000-0000-4000-8000-000000000001', NULL, true),
       ('31000000-0000-4000-8000-000000000003', now(), 'POSTGIS',
        '30000000-0000-4000-8000-000000000001', NULL, false);

INSERT INTO named_apis (id, created_at, dataset_id, name, slug, standard)
VALUES ('32000000-0000-4000-8000-000000000001', now(), '30000000-0000-4000-8000-000000000001',
        'Maps', 'maps', 'OWS');

INSERT INTO layers (id, created_at, dataset_id, datasink_id, layer_name)
VALUES ('33000000-0000-4000-8000-000000000001', now(), '30000000-0000-4000-8000-000000000001',
        '31000000-0000-4000-8000-000000000001', 'roads'),
       ('33000000-0000-4000-8000-000000000002', now(), '30000000-0000-4000-8000-000000000001',
        '31000000-0000-4000-8000-000000000002', 'old-roads'),
       ('33000000-0000-4000-8000-000000000003', now(), '30000000-0000-4000-8000-000000000001',
        '31000000-0000-4000-8000-000000000003', 'draft-roads');

-- A Dataset with two STA APIs, a CUSTOM one and a Layer but no OWS API.
INSERT INTO datasets (id, name, created_at)
VALUES ('40000000-0000-4000-8000-000000000001', 'Sensors', now());

INSERT INTO pipelines (id, name, created_at, dataset_id)
VALUES ('40000000-0000-4000-8000-000000000002', 'Sensors pipeline', now(),
        '40000000-0000-4000-8000-000000000001');

INSERT INTO data_sinks (id, created_at, data_sink_type, dataset_id, pipeline_id, provisioned)
VALUES ('41000000-0000-4000-8000-000000000001', now(), 'FROST',
        '40000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000002', false);

INSERT INTO named_apis (id, created_at, dataset_id, name, slug, standard)
VALUES ('42000000-0000-4000-8000-000000000001', now() - INTERVAL '1 day',
        '40000000-0000-4000-8000-000000000001', 'Traffic', 'traffic', 'STA'),
       ('42000000-0000-4000-8000-000000000002', now(), '40000000-0000-4000-8000-000000000001',
        'Weather', 'weather', 'STA'),
       ('42000000-0000-4000-8000-000000000003', now(), '40000000-0000-4000-8000-000000000001',
        'Custom', 'custom', 'CUSTOM');

INSERT INTO layers (id, created_at, dataset_id, datasink_id, layer_name)
VALUES ('43000000-0000-4000-8000-000000000001', now(), '40000000-0000-4000-8000-000000000001',
        '41000000-0000-4000-8000-000000000001', 'unserved');

-- A Dataset in draft whose OWS API only an unlinked Data sink backed, and a CUSTOM API.
INSERT INTO datasets (id, name, created_at)
VALUES ('50000000-0000-4000-8000-000000000001', 'Parks', now());

INSERT INTO data_sinks (id, created_at, data_sink_type, dataset_id, pipeline_id, provisioned)
VALUES ('51000000-0000-4000-8000-000000000001', now(), 'POSTGIS',
        '50000000-0000-4000-8000-000000000001', NULL, true);

INSERT INTO named_apis (id, created_at, dataset_id, name, slug, standard)
VALUES ('52000000-0000-4000-8000-000000000001', now(), '50000000-0000-4000-8000-000000000001',
        'Parks maps', 'parks-maps', 'OWS'),
       ('52000000-0000-4000-8000-000000000002', now(), '50000000-0000-4000-8000-000000000001',
        'Parks custom', 'parks-custom', 'CUSTOM');

-- A released Dataset whose OWS API with a live route only unlinked Data sinks back.
INSERT INTO datasets (id, name, created_at, status)
VALUES ('60000000-0000-4000-8000-000000000001', 'Lights', now(), 'AVAILABLE');

INSERT INTO data_sinks (id, created_at, data_sink_type, dataset_id, pipeline_id, provisioned)
VALUES ('61000000-0000-4000-8000-000000000001', now() - INTERVAL '1 day', 'POSTGIS',
        '60000000-0000-4000-8000-000000000001', NULL, true),
       ('61000000-0000-4000-8000-000000000002', now(), 'POSTGIS',
        '60000000-0000-4000-8000-000000000001', NULL, true);

INSERT INTO named_apis (id, created_at, dataset_id, name, slug, standard, route_id)
VALUES ('62000000-0000-4000-8000-000000000001', now(), '60000000-0000-4000-8000-000000000001',
        'Lights maps', 'lights-maps', 'OWS', 'lights-maps-route');

INSERT INTO layers (id, created_at, dataset_id, datasink_id, layer_name)
VALUES ('63000000-0000-4000-8000-000000000001', now(), '60000000-0000-4000-8000-000000000001',
        '61000000-0000-4000-8000-000000000002', 'lights');

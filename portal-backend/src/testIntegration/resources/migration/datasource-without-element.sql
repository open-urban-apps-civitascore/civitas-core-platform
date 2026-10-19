-- Data source configurations as the registry held them before the document named the Data
-- structure version in `element`.

INSERT INTO data_structures (id, name, created_at, data_structure_status, model_logical_urn)
VALUES ('10000000-0000-4000-8000-000000000001', 'Weather reading', now(), 'AVAILABLE',
        'urn:core:platform:civitas:element:common:10000000-0000-4000-8000-000000000001');

INSERT INTO data_structure_versions (id, created_at, data_structure_version_status,
                                     data_structure_version_source, data_structure_id, version,
                                     model_urn)
VALUES ('10000000-0000-4000-8000-000000000002', now(), 'AVAILABLE', 'OWN',
        '10000000-0000-4000-8000-000000000001', '1.0.0',
        'urn:core:platform:civitas:element:common:10000000-0000-4000-8000-000000000001:1.0.0');

-- 01: a Data source whose configuration has two versions; 02: one without a Data structure
-- version; 03: one whose document already names its Data structure version.
INSERT INTO data_sources (id, name, created_at, data_structure_version_id,
                          configuration_logical_urn, configuration_urn)
VALUES ('20000000-0000-4000-8000-000000000001', 'Weather sensors', now(),
        '10000000-0000-4000-8000-000000000002',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001:1.1.0'),
       ('20000000-0000-4000-8000-000000000002', 'Unstructured sensors', now(), NULL,
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000002',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000002:1.0.0'),
       ('20000000-0000-4000-8000-000000000003', 'Stamped sensors', now(),
        '10000000-0000-4000-8000-000000000002',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000003',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000003:1.0.0');

INSERT INTO model_forge.artifact (id, logical_urn, artifact_type, name, current_version, created_at, updated_at)
VALUES ('a0000000-0000-4000-8000-000000000001', 'urn:core:platform:civitas:element:common:10000000-0000-4000-8000-000000000001', 'element', 'Weather reading', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000002', 'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001', 'datasource', 'Weather sensors', '1.1.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000003', 'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000002', 'datasource', 'Unstructured sensors', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000004', 'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000003', 'datasource', 'Stamped sensors', '1.0.0', now(), now());

INSERT INTO model_forge.artifact_version (id, artifact_id, version, primary_format, created_at)
VALUES ('b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', '1.0.0', 'jsonschema', now()),
       ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002', '1.0.0', 'core-json', now() - interval '1 hour'),
       ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002', '1.1.0', 'core-json', now()),
       ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000003', '1.0.0', 'core-json', now()),
       ('b0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000004', '1.0.0', 'core-json', now());

INSERT INTO model_forge.artifact_representation (id, version_id, format, content_type, content_jsonb, content_hash, created_at)
VALUES ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'jsonschema', 'application/json',
        '{"$id": "urn:core:platform:civitas:element:common:10000000-0000-4000-8000-000000000001", "type": "object", "title": "Weather reading"}',
        'fixture', now()),
       ('c0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000002', 'core-json', 'application/json',
        '{"connectionType": "mqtt", "urls": ["tcp://broker:1883"], "topics": ["weather/#"]}',
        'fixture', now()),
       ('c0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000003', 'core-json', 'application/json',
        '{"connectionType": "mqtt", "urls": ["tcp://broker:1883"], "topics": ["weather/+"]}',
        'fixture', now()),
       ('c0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000004', 'core-json', 'application/json',
        '{"connectionType": "sql", "driver": "postgres", "dsn": "postgres://host/db"}',
        'fixture', now()),
       ('c0000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000005', 'core-json', 'application/json',
        '{"connectionType": "mqtt", "element": "urn:core:platform:civitas:element:common:10000000-0000-4000-8000-000000000001:1.0.0"}',
        'fixture', now());

INSERT INTO model_forge.artifact_reference (id, from_version_id, target_urn, target_artifact_id, target_version_id, reference_type, reference_name, sort_order, created_at)
VALUES ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000005',
        'urn:core:platform:civitas:element:common:10000000-0000-4000-8000-000000000001:1.0.0',
        'a0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001',
        'datasource-element', NULL, 0, now());

-- A Pipeline whose version 1.0.0 pins the configurations of Data sources 01 and 03 and of a Data
-- sink, and whose version 1.1.0 already names Data source 01 through `:latest`.
INSERT INTO model_forge.artifact (id, logical_urn, artifact_type, name, current_version, created_at, updated_at)
VALUES ('a0000000-0000-4000-8000-000000000005', 'urn:core:platform:civitas:pipeline:common:40000000-0000-4000-8000-000000000001', 'pipeline', 'Weather flow', '1.1.0', now(), now());

INSERT INTO model_forge.artifact_version (id, artifact_id, version, primary_format, created_at)
VALUES ('b0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000005', '1.0.0', 'core-json', now() - interval '1 hour'),
       ('b0000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000005', '1.1.0', 'core-json', now());

INSERT INTO model_forge.artifact_representation (id, version_id, format, content_type, content_jsonb, content_hash, created_at)
VALUES ('c0000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000006', 'core-json', 'application/json',
        '{"nodes": [{"id": "src", "kind": "source", "sourceRef": "urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001:1.0.0"},
                    {"id": "enrich", "kind": "enrich", "lookupKey": "id", "lookupSourceRef": "urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000003:1.0.0"},
                    {"id": "sink", "kind": "sink", "sinkRef": "urn:core:platform:civitas:datasink:common:30000000-0000-4000-8000-000000000001:1.0.0"}],
          "edges": [{"id": "e1", "source": "src", "target": "enrich"}, {"id": "e2", "source": "enrich", "target": "sink"}],
          "x-ui-styles": {"nodes": [{"id": "src", "data": {"configurationUrn": "urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001:1.0.0"}}]}}',
        'fixture', now()),
       ('c0000000-0000-4000-8000-000000000007', 'b0000000-0000-4000-8000-000000000007', 'core-json', 'application/json',
        '{"nodes": [{"id": "src", "kind": "source", "sourceRef": "urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001:latest"}], "edges": []}',
        'fixture', now());

INSERT INTO model_forge.artifact_reference (id, from_version_id, target_urn, target_artifact_id, target_version_id, reference_type, reference_name, sort_order, created_at)
VALUES ('d0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000006',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001:1.0.0',
        'a0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000002',
        'pipeline-node', 'source', 0, now()),
       ('d0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000006',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000003:1.0.0',
        'a0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000005',
        'pipeline-node', 'enrich', 1, now()),
       ('d0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000006',
        'urn:core:platform:civitas:datasink:common:30000000-0000-4000-8000-000000000001:1.0.0',
        NULL, NULL, 'pipeline-node', 'sink', 2, now()),
       ('d0000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000007',
        'urn:core:platform:civitas:datasource:common:20000000-0000-4000-8000-000000000001:latest',
        'a0000000-0000-4000-8000-000000000002', NULL, 'pipeline-node', 'source', 0, now());

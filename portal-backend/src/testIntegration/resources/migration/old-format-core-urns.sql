-- Registry and portal rows in the shape develop wrote them before CORE URNs carried UUIDs:
-- urn:core:<scope>:<owner>:<type>:<domain>:<name>:<disambiguator>[:<version>].

-- portal: a Data structure renamed after its first version, so the URN name segment is stale
INSERT INTO data_structures (id, name, created_at, data_structure_status, model_logical_urn)
VALUES ('10000000-0000-4000-8000-000000000001', 'Municipal building', now(), 'AVAILABLE',
        'urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c');

INSERT INTO data_structure_versions (id, created_at, data_structure_version_status,
                                     data_structure_version_source, data_structure_id, version,
                                     model_urn, imported_structure_urns)
VALUES ('10000000-0000-4000-8000-000000000002', now(), 'AVAILABLE', 'OWN',
        '10000000-0000-4000-8000-000000000001', '1.0.0',
        'urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c:1.0.0', NULL),
       ('10000000-0000-4000-8000-000000000003', now(), 'DRAFT', 'OWN',
        '10000000-0000-4000-8000-000000000001', '2.0.0',
        'urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c:2.0.0',
        ARRAY ['urn:core:platform:civitas:datastructure:frost:ThingTree:r32e0hmgn1:1.0.0']);

INSERT INTO datasets (id, name, created_at, manifest_logical_urn)
VALUES ('20000000-0000-4000-8000-000000000001', 'Weather stations', now(),
        'urn:core:platform:civitas:dataset:common:Weather-stations:64m6fdznxm');

INSERT INTO pipelines (id, name, created_at, dataset_id, model_logical_urn, model_urn)
VALUES ('20000000-0000-4000-8000-000000000002', 'Weather pipeline', now(),
        '20000000-0000-4000-8000-000000000001',
        'urn:core:platform:civitas:pipeline:common:Weather-pipeline:sgblpbvlyt',
        'urn:core:platform:civitas:pipeline:common:Weather-pipeline:sgblpbvlyt:1.0.0');

INSERT INTO data_sinks (id, created_at, data_sink_type, dataset_id, configuration_logical_urn,
                        configuration_urn)
VALUES ('20000000-0000-4000-8000-000000000003', now(), 'POSTGIS',
        '20000000-0000-4000-8000-000000000001',
        'urn:core:platform:civitas:datasink:common:urnmig_weather:k5o5ph7s8b',
        'urn:core:platform:civitas:datasink:common:urnmig_weather:k5o5ph7s8b:1.0.0');

INSERT INTO data_sources (id, name, created_at, configuration_logical_urn, configuration_urn)
VALUES ('20000000-0000-4000-8000-000000000004', 'Weather sensors', now(),
        'urn:core:platform:civitas:datasource:common:Weather-sensors:3au0ijr4hx',
        'urn:core:platform:civitas:datasource:common:Weather-sensors:3au0ijr4hx:1.0.0');

INSERT INTO published_structures (id, created_at, port, logical_urn, versioned_urn, content_hash)
VALUES ('30000000-0000-4000-8000-000000000001', now(), 'ThingTree',
        'urn:core:platform:civitas:datastructure:frost:ThingTree:r32e0hmgn1',
        'urn:core:platform:civitas:datastructure:frost:ThingTree:r32e0hmgn1:1.0.0',
        'b5bb9d8014a0f9b1d61e21e796d78dccdf1352f23cd32812f4850b878ae4944c');

INSERT INTO model_forge.artifact (id, logical_urn, artifact_type, name, current_version, created_at, updated_at)
VALUES ('a0000000-0000-4000-8000-000000000001', 'urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c', 'datastructure', 'Building', '2.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000002', 'urn:core:platform:civitas:element:common:Building:5syb7zgvhv', 'element', 'Building', '2.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000003', 'urn:core:platform:civitas:element:common:Address:kofkwessmc', 'element', 'Address', '2.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000004', 'urn:core:platform:civitas:datastructure:frost:ThingTree:r32e0hmgn1', 'datastructure', 'ThingTree', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000005', 'urn:core:platform:civitas:element:common:Thing:1ibvx1qk2i', 'element', 'Thing', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000006', 'urn:core:platform:civitas:pipeline:common:Weather-pipeline:sgblpbvlyt', 'pipeline', 'Weather-pipeline', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000007', 'urn:core:platform:civitas:mapping:common:Reading-to-Station:6k6cbtnnzp', 'mapping', 'Reading-to-Station', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000008', 'urn:core:platform:civitas:datasink:common:urnmig_weather:k5o5ph7s8b', 'datasink', 'urnmig_weather', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000009', 'urn:core:platform:civitas:datasource:common:Weather-sensors:3au0ijr4hx', 'datasource', 'Weather-sensors', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000010', 'urn:core:platform:civitas:dataset:common:Weather-stations:64m6fdznxm', 'dataset', 'Weather-stations', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000011', 'urn:core:platform:civitas:element:xoev-lokation:Lokation.0002_Bundesland:anhsxznpn9', 'element', 'Lokation.0002_Bundesland', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000012', 'urn:core:platform:civitas:element:common:WeatherObserved:q1w2e3r4t5', 'element', 'WeatherObserved', '1.0.0', now(), now());

INSERT INTO model_forge.artifact_version (id, artifact_id, version, primary_format, created_at)
VALUES ('b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', '1.0.0', 'core-json', now() - interval '2 hours'),
       ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001', '2.0.0', 'core-json', now() - interval '1 hour'),
       ('b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002', '1.0.0', 'jsonschema', now() - interval '2 hours'),
       ('b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000002', '2.0.0', 'jsonschema', now() - interval '1 hour'),
       ('b0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000003', '1.0.0', 'jsonschema', now() - interval '2 hours'),
       ('b0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000003', '2.0.0', 'jsonschema', now() - interval '1 hour'),
       ('b0000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000004', '1.0.0', 'core-json', now() - interval '3 hours'),
       ('b0000000-0000-4000-8000-000000000008', 'a0000000-0000-4000-8000-000000000005', '1.0.0', 'jsonschema', now() - interval '3 hours'),
       ('b0000000-0000-4000-8000-000000000009', 'a0000000-0000-4000-8000-000000000006', '1.0.0', 'core-json', now()),
       ('b0000000-0000-4000-8000-000000000010', 'a0000000-0000-4000-8000-000000000007', '1.0.0', 'core-json', now()),
       ('b0000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000008', '1.0.0', 'core-json', now()),
       ('b0000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000009', '1.0.0', 'core-json', now()),
       ('b0000000-0000-4000-8000-000000000013', 'a0000000-0000-4000-8000-000000000010', '1.0.0', 'core-json', now()),
       ('b0000000-0000-4000-8000-000000000014', 'a0000000-0000-4000-8000-000000000011', '1.0.0', 'xsd', now()),
       ('b0000000-0000-4000-8000-000000000015', 'a0000000-0000-4000-8000-000000000012', '1.0.0', 'jsonschema', now());

INSERT INTO model_forge.artifact_representation (id, version_id, format, content_type, content_jsonb, content_text, content_hash, created_at)
VALUES ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'core-json', 'application/json',
        '{"$id": "urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c", "title": "Building", "$defs": {"Building": {"$ref": "urn:core:platform:civitas:element:common:Building:5syb7zgvhv:1.0.0"}, "Address": {"$ref": "urn:core:platform:civitas:element:common:Address:kofkwessmc:1.0.0"}}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000002', 'core-json', 'application/json',
        '{"$id": "urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c", "title": "Building", "$defs": {"Building": {"$ref": "urn:core:platform:civitas:element:common:Building:5syb7zgvhv:2.0.0"}, "Address": {"$ref": "urn:core:platform:civitas:element:common:Address:kofkwessmc:2.0.0"}}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000003', 'jsonschema', 'application/json',
        '{"$id": "urn:core:platform:civitas:element:common:Building:5syb7zgvhv", "type": "object", "title": "Building", "properties": {"name": {"type": "string"}, "address": {"$ref": "urn:core:platform:civitas:element:common:Address:kofkwessmc"}}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000004', 'jsonschema', 'application/json',
        '{"$id": "urn:core:platform:civitas:element:common:Building:5syb7zgvhv", "type": "object", "title": "Building", "properties": {"name": {"type": "string"}, "floors": {"type": "integer"}, "address": {"$ref": "urn:core:platform:civitas:element:common:Address:kofkwessmc"}}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000005', 'jsonschema', 'application/json',
        '{"$id": "urn:core:platform:civitas:element:common:Address:kofkwessmc", "type": "object", "title": "Address", "properties": {"street": {"type": "string"}}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000006', 'jsonschema', 'application/json',
        '{"$id": "urn:core:platform:civitas:element:common:Address:kofkwessmc", "type": "object", "title": "Address", "properties": {"street": {"type": "string"}, "zip": {"type": "string"}}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000007', 'b0000000-0000-4000-8000-000000000007', 'core-json', 'application/json',
        '{"$id": "urn:core:platform:civitas:datastructure:frost:ThingTree:r32e0hmgn1", "title": "ThingTree", "$defs": {"Thing": {"$ref": "urn:core:platform:civitas:element:common:Thing:1ibvx1qk2i:1.0.0"}}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000008', 'b0000000-0000-4000-8000-000000000008', 'jsonschema', 'application/json',
        '{"$id": "urn:core:platform:civitas:element:common:Thing:1ibvx1qk2i", "type": "object", "title": "Thing"}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000009', 'b0000000-0000-4000-8000-000000000009', 'core-json', 'application/json',
        '{"id": "urn:core:platform:civitas:pipeline:common:Weather-pipeline:sgblpbvlyt:1.0.0", "nodes": [{"id": "n1", "kind": "source", "sourceRef": "urn:core:platform:civitas:datasource:common:Weather-sensors:3au0ijr4hx:1.0.0"}, {"id": "n2", "kind": "mapping", "mappingRef": "urn:core:platform:civitas:mapping:common:Reading-to-Station:6k6cbtnnzp:1.0.0"}, {"id": "n3", "kind": "sink", "sinkRef": "urn:core:platform:civitas:datasink:common:urnmig_weather:k5o5ph7s8b:1.0.0"}], "x-ui-styles": {"nodes": [{"id": "n3", "data": {"dataStructureUrn": "urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c:2.0.0"}}]}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000010', 'b0000000-0000-4000-8000-000000000010', 'core-json', 'application/json',
        '{"id": "urn:core:platform:civitas:mapping:common:Reading-to-Station:6k6cbtnnzp:1.0.0", "title": "Reading-to-Station", "source": "urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c:1.0.0", "target": "urn:core:platform:civitas:datastructure:frost:ThingTree:r32e0hmgn1:1.0.0", "fields": {}}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000011', 'b0000000-0000-4000-8000-000000000011', 'core-json', 'application/json',
        '{"id": "urn:core:platform:civitas:datasink:common:urnmig_weather:k5o5ph7s8b:1.0.0", "element": "urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c:2.0.0", "tableName": "urnmig_weather", "connectionType": "postgis"}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000012', 'b0000000-0000-4000-8000-000000000012', 'core-json', 'application/json',
        '{"id": "urn:core:platform:civitas:datasource:common:Weather-sensors:3au0ijr4hx:1.0.0", "connectionType": "mqtt"}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000013', 'b0000000-0000-4000-8000-000000000013', 'core-json', 'application/json',
        '{"id": "urn:core:platform:civitas:dataset:common:Weather-stations:64m6fdznxm", "title": "Weather stations", "pipelineRefs": ["urn:core:platform:civitas:pipeline:common:Weather-pipeline:sgblpbvlyt"], "datastructureRefs": ["urn:core:platform:civitas:datastructure:common:Building:ko7w61bw4c", "urn:core:platform:civitas:datastructure:common:Deleted:zzzzzzzzzz"]}',
        NULL, 'old', now()),
       ('c0000000-0000-4000-8000-000000000014', 'b0000000-0000-4000-8000-000000000014', 'xsd', 'application/xml',
        NULL, '<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:xoev-de:xlokation"/>', 'old', now()),
       ('c0000000-0000-4000-8000-000000000015', 'b0000000-0000-4000-8000-000000000015', 'jsonschema', 'application/json',
        '{"$id": "urn:core:platform:civitas:element:common:WeatherObserved:q1w2e3r4t5", "type": "object", "title": "WeatherObserved"}',
        NULL, 'old', now());

INSERT INTO model_forge.artifact_reference (id, from_version_id, target_urn, target_artifact_id, reference_type, reference_name, sort_order, created_at)
VALUES ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002', 'urn:core:platform:civitas:element:common:Building:5syb7zgvhv:2.0.0', 'a0000000-0000-4000-8000-000000000002', 'datastructure-ref', 'element', 0, now()),
       ('d0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000004', 'urn:core:platform:civitas:element:common:Address:kofkwessmc', 'a0000000-0000-4000-8000-000000000003', 'schema-ref', NULL, 0, now()),
       ('d0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000009', 'urn:core:platform:civitas:mapping:common:Reading-to-Station:6k6cbtnnzp:1.0.0', 'a0000000-0000-4000-8000-000000000007', 'pipeline-node', NULL, 0, now()),
       ('d0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000013', 'urn:core:platform:civitas:datastructure:common:Deleted:zzzzzzzzzz', NULL, 'dataset-ref', NULL, 0, now()),
       ('d0000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000003', 'urn:core:platform:civitas:element:common:Address:kofkwessmc:latest', 'a0000000-0000-4000-8000-000000000003', 'schema-ref', NULL, 0, now());

-- An imported model: the Data structure row stores the root Element's URN, and the grouping
-- took the Element's name and disambiguator.
INSERT INTO data_structures (id, name, created_at, data_structure_status, model_logical_urn)
VALUES ('10000000-0000-4000-8000-000000000004', 'Imported sensor', now(), 'AVAILABLE',
        'urn:core:platform:civitas:element:common:Sensor:s3ns0rs3ns');

INSERT INTO model_forge.artifact (id, logical_urn, artifact_type, name, current_version, created_at, updated_at)
VALUES ('a0000000-0000-4000-8000-000000000013', 'urn:core:platform:civitas:datastructure:common:Sensor:s3ns0rs3ns', 'datastructure', 'Sensor', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000014', 'urn:core:platform:civitas:element:common:Sensor:s3ns0rs3ns', 'element', 'Sensor', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000015', 'urn:core:platform:civitas:datastructure:xoev-lokation:xlokation:k9k9k9k9k9', 'datastructure', 'xlokation', '1.0.0', now(), now()),
       ('a0000000-0000-4000-8000-000000000016', 'urn:core:platform:civitas:element:xoev-lokation:Bundesland:8beah3uhsv', 'element', 'Bundesland', '1.0.0', now(), now());

-- An XRepository import through the JSON path: a grouping listing XSD types converted to JSON
-- Schema, each with a name-derived disambiguator.
INSERT INTO model_forge.artifact_version (id, artifact_id, version, primary_format, created_at)
VALUES ('b0000000-0000-4000-8000-000000000016', 'a0000000-0000-4000-8000-000000000015', '1.0.0', 'core-json', now());

INSERT INTO model_forge.artifact_representation (id, version_id, format, content_type, content_jsonb, content_text, content_hash, created_at)
VALUES ('c0000000-0000-4000-8000-000000000016', 'b0000000-0000-4000-8000-000000000016', 'core-json', 'application/json',
        '{"$id": "urn:core:platform:civitas:datastructure:xoev-lokation:xlokation:k9k9k9k9k9", "$defs": {"Bundesland": {"$ref": "urn:core:platform:civitas:element:xoev-lokation:Bundesland:8beah3uhsv:1.0.0"}}}',
        NULL, 'old', now());

CREATE EXTENSION IF NOT EXISTS postgis;

-- Test schema that simulates what the CIVITAS portal backend provisions
-- when a dataset with POSTGIS DataSink is released (DRAFT → AVAILABLE).
-- Schema name mirrors the convention: dataset_{uuid}

CREATE SCHEMA IF NOT EXISTS dataset_test_uuid_001;

CREATE TABLE dataset_test_uuid_001.sensor_locations (
    sensor_id   TEXT PRIMARY KEY,
    name        TEXT,
    pm25        NUMERIC(6,2),
    no2         NUMERIC(6,2),
    temperature NUMERIC(5,2),
    geom        geometry(Point, 4326) NOT NULL
);

INSERT INTO dataset_test_uuid_001.sensor_locations VALUES
    ('sensor-001', 'Hauptbahnhof',      12.4, 28.1, 18.5, ST_SetSRID(ST_MakePoint(13.3777, 52.5251), 4326)),
    ('sensor-002', 'Alexanderplatz',    18.7, 35.2, 17.9, ST_SetSRID(ST_MakePoint(13.4132, 52.5219), 4326)),
    ('sensor-003', 'Tempelhof',          8.1, 19.4, 19.1, ST_SetSRID(ST_MakePoint(13.4033, 52.4731), 4326)),
    ('sensor-004', 'Spandau',            6.2, 14.7, 20.0, ST_SetSRID(ST_MakePoint(13.2000, 52.5353), 4326)),
    ('sensor-005', 'Marzahn',           14.9, 31.8, 17.2, ST_SetSRID(ST_MakePoint(13.5435, 52.5456), 4326));

-- Second table to test multi-DataSink scenario (Polygon geometry)
CREATE TABLE dataset_test_uuid_001.districts (
    district_id   TEXT PRIMARY KEY,
    name          TEXT,
    population    INTEGER,
    area_km2      NUMERIC(8,2),
    geom          geometry(Polygon, 4326) NOT NULL
);

INSERT INTO dataset_test_uuid_001.districts VALUES
    ('mitte',   'Mitte',   384172, 39.47,
     ST_SetSRID(ST_MakePolygon(ST_GeomFromText(
       'LINESTRING(13.38 52.50, 13.42 52.50, 13.42 52.54, 13.38 52.54, 13.38 52.50)'
     )), 4326)),
    ('charlottenburg', 'Charlottenburg', 342332, 64.72,
     ST_SetSRID(ST_MakePolygon(ST_GeomFromText(
       'LINESTRING(13.28 52.49, 13.35 52.49, 13.35 52.53, 13.28 52.53, 13.28 52.49)'
     )), 4326));

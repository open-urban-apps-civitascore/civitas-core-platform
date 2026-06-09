CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS sensor_observations (
  station_id        TEXT             NOT NULL,
  temperature       DOUBLE PRECISION NOT NULL,
  measurement_time  TIMESTAMPTZ      NOT NULL,
  geom              GEOMETRY(Point, 4326) NOT NULL
);

CREATE INDEX IF NOT EXISTS sensor_observations_geom_gix
  ON sensor_observations USING GIST (geom);

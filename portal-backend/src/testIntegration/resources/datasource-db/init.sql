CREATE TABLE IF NOT EXISTS sensors (
    id SERIAL PRIMARY KEY,
    sensor_name TEXT NOT NULL,
    sensor_description TEXT NOT NULL
);

INSERT INTO sensors (sensor_name, sensor_description)
VALUES ('SQL Sensor', 'Created from PostgreSQL');

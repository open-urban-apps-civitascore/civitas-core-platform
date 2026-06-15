-- Create dedicated database and user for the Flowable saga engine.
-- Runs once on first container start (postgres-entrypoint convention).
-- For an existing volume, recreate with: docker compose down -v && docker compose up -d
CREATE USER flowable WITH PASSWORD 'flowable';
CREATE DATABASE flowable OWNER flowable;
GRANT ALL PRIVILEGES ON DATABASE flowable TO flowable;

-- Drop the legacy pipeline-scoped `apis` column.
-- Named APIs are now dataset-scoped artifacts (see V1_1_1__payload_api_named_apis.sql).
ALTER TABLE pipelines DROP COLUMN IF EXISTS apis;

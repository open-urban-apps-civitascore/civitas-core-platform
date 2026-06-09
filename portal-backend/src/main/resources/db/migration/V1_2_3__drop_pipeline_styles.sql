-- Drop the legacy pipeline `styles` column.
-- The pipeline's visual layout is now carried inside the editor-built `model`
-- document (per-node `x-ui-position` annotations), so the separate column is
-- obsolete. The `styles` field was already removed from the Pipeline entity,
-- leaving this column orphaned.
ALTER TABLE pipelines DROP COLUMN IF EXISTS styles;

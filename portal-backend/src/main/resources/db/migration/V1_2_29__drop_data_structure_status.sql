-- Drop the data structure lifecycle status; only its versions carry one.
ALTER TABLE data_structures DROP COLUMN IF EXISTS data_structure_status;

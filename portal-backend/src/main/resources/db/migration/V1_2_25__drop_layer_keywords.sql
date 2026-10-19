-- Drop the unused layer keywords.
ALTER TABLE layers DROP COLUMN IF EXISTS keywords;

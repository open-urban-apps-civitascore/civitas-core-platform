-- Remove the layer keywords, which no published layer uses.
ALTER TABLE layers DROP COLUMN IF EXISTS keywords;

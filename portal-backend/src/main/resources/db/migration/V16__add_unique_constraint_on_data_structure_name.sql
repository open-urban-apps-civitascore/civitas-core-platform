-- Remove duplicate data_structures rows that violate the upcoming unique constraint.
-- For each group of (id, name) duplicates, keep the row with the earliest
-- created_at timestamp and delete all others.
DELETE FROM data_structures
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY name
                   ORDER BY created_at ASC
               ) AS rn
        FROM data_structures
    ) ranked
    WHERE rn > 1
);

-- Add a unique constraint so that a version string must be unique within the same DataStructure.
ALTER TABLE data_structures
    ADD CONSTRAINT uq_data_structures_name
    UNIQUE (name);


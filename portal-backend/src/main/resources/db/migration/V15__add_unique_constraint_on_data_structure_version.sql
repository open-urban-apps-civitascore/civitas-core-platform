-- Remove duplicate data_structure_versions rows that violate the upcoming unique constraint.
-- For each group of (data_structure_id, version) duplicates, keep the row with the earliest
-- created_at timestamp and delete all others.
DELETE FROM data_structure_versions
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY data_structure_id, version
                   ORDER BY created_at ASC
               ) AS rn
        FROM data_structure_versions
    ) ranked
    WHERE rn > 1
);

-- Add a unique constraint so that a version string must be unique within the same DataStructure.
ALTER TABLE data_structure_versions
    ADD CONSTRAINT uq_data_structure_versions_data_structure_version
    UNIQUE (data_structure_id, version);


ALTER TABLE dashboards
    ADD COLUMN standalone_mode INTEGER NOT NULL DEFAULT 1 CHECK (standalone_mode BETWEEN 0 AND 3),
    ADD COLUMN show_filters BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN filter_expansion VARCHAR(16) NOT NULL DEFAULT 'DEFAULT'
        CHECK (filter_expansion IN ('DEFAULT', 'COLLAPSED', 'EXPANDED'));

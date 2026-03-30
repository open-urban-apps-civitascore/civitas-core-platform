-- Rename inconsistent constraint prefix from uq_ to uk_ to match all other entities.
ALTER TABLE data_sources RENAME CONSTRAINT uq_data_sources_name TO uk_data_sources_name;

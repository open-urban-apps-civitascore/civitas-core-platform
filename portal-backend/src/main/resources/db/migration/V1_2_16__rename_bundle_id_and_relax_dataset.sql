-- The column never held a CORE URN. It carries a catalogue-owned bundle identifier
-- (e.g. 'urn:openurbanapps:usecase:verkehrszaehlung'), which Model Forge's UrnParser
-- would reject: in this codebase "urn" means an 8-segment CORE URN minted or parsed by
-- the registry. Naming it bundle_urn borrowed a term with a precise meaning for
-- something that does not satisfy it — and made readers look for an identifier
-- elsewhere. Renamed while the table is young.
ALTER TABLE bundle_installations RENAME COLUMN bundle_urn TO bundle_id;

-- The dataset moves out of the header and into an artifact line (artifact_type
-- 'DATA_SET'). Two reasons: an install need not produce a dataset at all — the
-- single-structure import does not, which is why it could record no provenance until
-- now — and as a line the dataset carries its own CREATED/REUSED action, which
-- reference counting on uninstall needs.
--
-- The columns stay as a denormalised read-model field for the bundle path (the install
-- list renders the dataset name without joining the lines). The line is the source of
-- truth; once the read side takes the name from there, these two can be dropped.
ALTER TABLE bundle_installations ALTER COLUMN data_set_id DROP NOT NULL;
ALTER TABLE bundle_installations ALTER COLUMN data_set_name DROP NOT NULL;

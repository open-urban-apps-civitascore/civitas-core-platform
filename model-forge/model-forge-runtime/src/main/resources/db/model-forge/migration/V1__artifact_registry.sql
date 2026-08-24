-- PostgreSQL-backed artifact registry.
--
-- Stores the logical identity, immutable versions, per-version format representations,
-- dependency edges and the XSD namespace index for every CORE artifact. Embedded
-- DTOs are projections built at the boundary from `artifact` + the selected
-- version + its representation; they are never ORM entities.

create schema if not exists model_forge;
set search_path to model_forge;

-- ── artifact ────────────────────────────────────────────────────────────────
-- Logical, version-independent identity. `logical_urn` is the CORE URN with the
-- version segment stripped and is the stable public identity. The format is NOT part
-- of the identity — an Element is the same artifact whether authored as JSON
-- Schema or XSD; the format lives per version/representation below.
create table artifact (
    id              uuid        primary key,
    logical_urn     text        not null unique,
    artifact_type   text        not null,   -- element | datastructure | dataset | mapping | pipeline | datasource | datasink
    name            text        not null,   -- CORE URN name segment
    title           text,
    description     text,
    current_version text,                    -- newest written version (the default read)
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);

create index idx_artifact_type       on artifact (artifact_type);
create index idx_artifact_name        on artifact (name);
create index idx_artifact_updated_at  on artifact (updated_at);

-- ── artifact_version ──────────────────────────────────────────────────────────
-- One concrete version of an artifact. `primary_format` is the authored format of the
-- version (jsonschema | core-json | xsd) and drives the DTO `format` field and the
-- default of GET /schema. `title`/`description` are versioned metadata: a rename creates
-- a new version with an updated title, format-independently (so XSD versions are renamable
-- too); older versions keep their own title. The version's content lives in
-- artifact_representation.
create table artifact_version (
    id               uuid        primary key,
    artifact_id      uuid        not null references artifact(id) on delete cascade,
    version          text        not null,
    primary_format   text        not null,   -- jsonschema | core-json | xsd
    title            text,
    description      text,
    created_at       timestamptz not null,
    created_by       text,
    unique (artifact_id, version)
);

create index idx_artifact_version_artifact on artifact_version (artifact_id);

-- ── artifact_representation ─────────────────────────────────────────────────────
-- Format-specific content for one version. A version has exactly one *authored*
-- (`generation='stored'`) representation; derived representations (e.g. JSON Schema
-- generated from an XSD) may be persisted later with `generation='generated'`.
-- JSON content goes into `content_jsonb`; raw XSD goes into `content_text`.
-- `content_hash` makes repeated imports of identical content idempotent.
create table artifact_representation (
    id            uuid        primary key,
    version_id    uuid        not null references artifact_version(id) on delete cascade,
    format        text        not null,   -- jsonschema | core-json | xsd
    content_type  text,
    content_jsonb jsonb,
    content_text  text,
    content_hash  text,
    generation    text        not null default 'stored',  -- stored | generated
    created_at    timestamptz not null,
    unique (version_id, format)
);

create index idx_artifact_representation_version on artifact_representation (version_id);
create index idx_artifact_representation_gin      on artifact_representation using gin (content_jsonb);

-- ── artifact_reference ──────────────────────────────────────────────────────────
-- Dependency edges for one artifact version. The COMPLETE reference graph is stored,
-- including cycles. `target_urn` is stored verbatim as authored (pinned `…:1.0.0` or the
-- `…:latest` token), never normalised to the logical form. `target_artifact_id` resolves to
-- the target artifact's logical identity; `target_version_id` is set for a pinned reference
-- once that concrete version exists (null for `latest`, logical, or a not-yet-imported pin).
-- Both FKs are nullable / `on delete set null` so an edge survives the removal of its target.
create table artifact_reference (
    id                 uuid        primary key,
    from_version_id    uuid        not null references artifact_version(id) on delete cascade,
    target_urn         text        not null,
    target_artifact_id uuid        references artifact(id) on delete set null,
    target_version_id  uuid        references artifact_version(id) on delete set null,
    reference_type     text        not null,  -- schema-ref | association-ref | dataset-ref | pipeline-node | xsd-import | mapping-source | mapping-target | datasource-element | datasink-element | datastructure-ref
    reference_name     text,
    sort_order         int,
    created_at         timestamptz not null
);

create index idx_artifact_reference_from            on artifact_reference (from_version_id);
create index idx_artifact_reference_target_urn       on artifact_reference (target_urn);
create index idx_artifact_reference_target_artifact  on artifact_reference (target_artifact_id);
create index idx_artifact_reference_target_version   on artifact_reference (target_version_id);

-- ── xsd_namespace ───────────────────────────────────────────────────────────────
-- Namespace-to-artifact lookup for XSD-backed Elements, scoped to the version
-- whose XSD representation declares the namespace. Durable replacement for the formerly
-- in-memory namespace index, so xs:import resolution no longer depends on a startup scan.
create table xsd_namespace (
    namespace   text        primary key,
    artifact_id uuid        not null references artifact(id) on delete cascade,
    version_id  uuid        references artifact_version(id) on delete set null,
    updated_at  timestamptz not null
);

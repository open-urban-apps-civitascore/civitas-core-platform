package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.urn.UrnParser;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRUD over the {@code model_forge.artifact_reference} table.
 *
 * <p>The COMPLETE reference graph is stored, including cycles. {@code target_urn} is kept
 * verbatim (pinned {@code …:1.0.0} or the {@code …:latest} token). {@code target_artifact_id}
 * resolves to the target's logical identity; {@code target_version_id} resolves a pinned
 * reference to its concrete version. Both are back-filled when a previously-missing target
 * model_forge.artifact ({@link #linkDanglingTargets}) or target version ({@link #linkDanglingVersions})
 * is later created.
 */
class ArtifactReferenceRepository {

    private final JdbcClient jdbc;

    ArtifactReferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Replaces all reference rows for {@code fromVersionId} with {@code refs} (in order).
     *
     * <p>{@code target_urn} is stored verbatim. {@code target_artifact_id} is resolved from the
     * reference's <em>logical</em> URN (so a pinned/{@code latest} reference still links to the
     * target model_forge.artifact); {@code target_version_id} is resolved only for a pinned reference whose
     * concrete version already exists ({@code latest}/logical → null, back-filled later).
     */
    void replaceForVersion(UUID fromVersionId, List<ReferenceRow> refs) {
        jdbc.sql("delete from model_forge.artifact_reference where from_version_id = :v")
            .param("v", fromVersionId)
            .update();
        Instant now = Instant.now();
        for (ReferenceRow r : refs) {
            String logical = UrnParser.logicalUrn(r.targetUrn());
            String version = UrnParser.versionFromUrn(r.targetUrn());
            // Only a concrete pinned version resolves to a target_version_id; latest/logical do not.
            String pinVersion = (version == null || UrnParser.LATEST.equals(version)) ? null : version;
            jdbc.sql("""
                    insert into model_forge.artifact_reference
                        (id, from_version_id, target_urn, target_artifact_id, target_version_id,
                         reference_type, reference_name, sort_order, created_at)
                    values
                        (:id, :from, :targetUrn,
                         (select id from model_forge.artifact where logical_urn = :logical),
                         (select av.id from model_forge.artifact_version av
                            join model_forge.artifact a on a.id = av.artifact_id
                           where a.logical_urn = :logical and av.version = :pinVersion),
                         :type, :name, :sort, :createdAt)
                    """)
                .param("id", UUID.randomUUID())
                .param("from", fromVersionId)
                .param("targetUrn", r.targetUrn())
                .param("logical", logical)
                .param("pinVersion", pinVersion)
                .param("type", r.referenceType())
                .param("name", r.referenceName())
                .param("sort", r.sortOrder())
                .param("createdAt", RegistryTime.toOffset(now))
                .update();
        }
    }

    /**
     * Logical URNs of artifacts holding at least one reference edge onto the given artifact —
     * <em>every</em> reference type blocks its target's deletion (full referential integrity: an
     * artifact that any other artifact still points at must not disappear, or the reference would
     * dangle). A grouping edge ({@code datastructure-ref}) protects its member exactly like a hard
     * dependency does. Only the artifact's own versions (self-references, e.g. cycles inside one
     * document) are exempt — a document cannot block its own deletion.
     */
    List<String> blockingDependents(String targetLogicalUrn) {
        return jdbc.sql("""
                select distinct a.logical_urn
                  from model_forge.artifact_reference r
                  join model_forge.artifact_version fv on fv.id = r.from_version_id
                  join model_forge.artifact a          on a.id = fv.artifact_id
                 where r.target_artifact_id = (select id from model_forge.artifact
                                                where logical_urn = :logical)
                   and a.logical_urn <> :logical
                 order by a.logical_urn
                """)
            .param("logical", targetLogicalUrn)
            .query(String.class)
            .list();
    }

    /** Full reference rows of a version, in stored order — used to copy edges onto a renamed version. */
    List<ReferenceRow> rowsForVersion(UUID fromVersionId) {
        return jdbc.sql("""
                select target_urn, reference_type, reference_name, sort_order
                  from model_forge.artifact_reference
                 where from_version_id = :v order by sort_order nulls last, target_urn
                """)
            .param("v", fromVersionId)
            .query((rs, n) -> new ReferenceRow(
                rs.getString("target_urn"),
                rs.getString("reference_type"),
                rs.getString("reference_name"),
                (Integer) rs.getObject("sort_order")))
            .list();
    }

    /** Target URNs referenced by a version, in stored order. */
    List<String> targetUrns(UUID fromVersionId) {
        return jdbc.sql("""
                select target_urn from model_forge.artifact_reference
                 where from_version_id = :v order by sort_order nulls last, target_urn
                """)
            .param("v", fromVersionId)
            .query(String.class)
            .list();
    }

    /**
     * Resolves edges that pointed at a not-yet-imported model_forge.artifact now that it exists. Matches every
     * reference whose <em>logical</em> target is {@code logicalUrn} — the verbatim {@code target_urn}
     * may be the logical form, a pinned {@code …:version}, or {@code …:latest}.
     */
    void linkDanglingTargets(String logicalUrn, UUID targetArtifactId) {
        // The logical URN's name segment is client-supplied ($id) and survives verbatim, so it may
        // contain LIKE metacharacters ('_' / '%'). Escape them (and the escape char itself) and
        // declare an explicit ESCAPE so the prefix match is literal — otherwise a name with '_'/'%'
        // could match the wrong target. The exact-match branch covers the non-versioned logical URN.
        String prefix = logicalUrn.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%") + ":%";
        jdbc.sql("""
                update model_forge.artifact_reference set target_artifact_id = :aid
                 where target_artifact_id is null
                   and (target_urn = :logical or target_urn like :prefix escape '\\')
                """)
            .param("aid", targetArtifactId)
            .param("logical", logicalUrn)
            .param("prefix", prefix)
            .update();
    }

    /** A mapping that references an Element, paired with the mapping's other endpoint. */
    record MappingRoleRef(String mappingUrn, String otherUrn) {}

    /**
     * Mappings (current versions) that reference the model_forge.artifact {@code dsArtifactId} via
     * {@code matchType} ({@code mapping-source} or {@code mapping-target}), each paired with the
     * verbatim target URN of the mapping's complementary edge ({@code otherType}). Drives the
     * {@code maps-to} / {@code mapped-from} relations: an Element → the mappings that consume
     * or produce it, plus the structure on the other side of each mapping.
     */
    List<MappingRoleRef> mappingsByRole(UUID dsArtifactId, String matchType, String otherType) {
        return jdbc.sql("""
                select m.logical_urn as m_logical, mv.version as m_version, o.target_urn as other_urn
                  from model_forge.artifact_reference rs
                  join model_forge.artifact_version mv on mv.id = rs.from_version_id
                  join model_forge.artifact m on m.id = mv.artifact_id and m.current_version = mv.version
                  left join model_forge.artifact_reference o
                    on o.from_version_id = mv.id and o.reference_type = :otherType
                 where rs.reference_type = :matchType
                   and rs.target_artifact_id = :dsId
                 order by m.name asc, m.logical_urn asc
                """)
            .param("matchType", matchType)
            .param("otherType", otherType)
            .param("dsId", dsArtifactId)
            .query((rs, n) -> new MappingRoleRef(
                UrnParser.withVersion(rs.getString("m_logical"), rs.getString("m_version")),
                rs.getString("other_urn")))
            .list();
    }

    /**
     * Back-fills {@code target_version_id} for pinned references to {@code pinnedUrn}
     * ({@code logical:version}) now that that concrete version exists.
     */
    void linkDanglingVersions(String pinnedUrn, UUID targetVersionId) {
        jdbc.sql("""
                update model_forge.artifact_reference set target_version_id = :vid
                 where target_urn = :urn and target_version_id is null
                """)
            .param("vid", targetVersionId)
            .param("urn", pinnedUrn)
            .update();
    }
}

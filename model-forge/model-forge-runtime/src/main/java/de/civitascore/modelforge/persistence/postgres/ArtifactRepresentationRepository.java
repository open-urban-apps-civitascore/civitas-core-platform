package de.civitascore.modelforge.persistence.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CRUD over the {@code model_forge.artifact_representation} table (per-version, per-format content). */
class ArtifactRepresentationRepository {

    private final JdbcClient jdbc;

    ArtifactRepresentationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The representation of {@code versionId} in {@code format}, if stored. */
    Optional<ArtifactRepresentationRow> find(UUID versionId, String format) {
        return jdbc.sql("""
                select id, version_id, format, content_type, content_jsonb, content_text,
                       content_hash, generation, created_at
                  from model_forge.artifact_representation
                 where version_id = :vid and format = :f
                """)
            .param("vid", versionId)
            .param("f", format)
            .query(ArtifactRepresentationRepository::map)
            .optional();
    }

    /** Formats stored for {@code versionId} (does not include derivable formats). */
    List<String> listFormats(UUID versionId) {
        return jdbc.sql("select format from model_forge.artifact_representation where version_id = :vid order by format")
            .param("vid", versionId)
            .query(String.class)
            .list();
    }

    void insert(ArtifactRepresentationRow r) {
        jdbc.sql("""
                insert into model_forge.artifact_representation
                    (id, version_id, format, content_type, content_jsonb, content_text,
                     content_hash, generation, created_at)
                values
                    (:id, :vid, :format, :contentType, cast(:json as jsonb), :text,
                     :hash, :generation, :createdAt)
                """)
            .param("id", r.id())
            .param("vid", r.versionId())
            .param("format", r.format())
            .param("contentType", r.contentType())
            .param("json", r.contentJson())
            .param("text", r.contentText())
            .param("hash", r.contentHash())
            .param("generation", r.generation())
            .param("createdAt", RegistryTime.toOffset(r.createdAt()))
            .update();
    }

    private static ArtifactRepresentationRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ArtifactRepresentationRow(
            rs.getObject("id", UUID.class),
            rs.getObject("version_id", UUID.class),
            rs.getString("format"),
            rs.getString("content_type"),
            rs.getString("content_jsonb"),
            rs.getString("content_text"),
            rs.getString("content_hash"),
            rs.getString("generation"),
            RegistryTime.toInstant(rs.getObject("created_at", OffsetDateTime.class)));
    }
}

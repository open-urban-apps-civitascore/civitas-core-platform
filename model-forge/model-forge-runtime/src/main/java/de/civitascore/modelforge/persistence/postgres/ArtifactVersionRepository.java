package de.civitascore.modelforge.persistence.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CRUD over the {@code model_forge.artifact_version} table (per-version metadata; content lives in representations). */
class ArtifactVersionRepository {

    /** Lightweight version listing projection. */
    record VersionInfo(String version, Instant createdAt) {}

    private final JdbcClient jdbc;

    ArtifactVersionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ArtifactVersionRow> find(UUID artifactId, String version) {
        return jdbc.sql("""
                select id, artifact_id, version, primary_format, title, description,
                       created_at, created_by
                  from model_forge.artifact_version
                 where artifact_id = :aid and version = :v
                """)
            .param("aid", artifactId)
            .param("v", version)
            .query(ArtifactVersionRepository::map)
            .optional();
    }

    void insert(ArtifactVersionRow v) {
        jdbc.sql("""
                insert into model_forge.artifact_version
                    (id, artifact_id, version, primary_format, title, description,
                     created_at, created_by)
                values
                    (:id, :aid, :v, :primaryFormat, :title, :description,
                     :createdAt, :createdBy)
                """)
            .param("id", v.id())
            .param("aid", v.artifactId())
            .param("v", v.version())
            .param("primaryFormat", v.primaryFormat())
            .param("title", v.title())
            .param("description", v.description())
            .param("createdAt", RegistryTime.toOffset(v.createdAt()))
            .param("createdBy", v.createdBy())
            .update();
    }

    private static ArtifactVersionRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ArtifactVersionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("artifact_id", UUID.class),
            rs.getString("version"),
            rs.getString("primary_format"),
            rs.getString("title"),
            rs.getString("description"),
            RegistryTime.toInstant(rs.getObject("created_at", OffsetDateTime.class)),
            rs.getString("created_by"));
    }
}

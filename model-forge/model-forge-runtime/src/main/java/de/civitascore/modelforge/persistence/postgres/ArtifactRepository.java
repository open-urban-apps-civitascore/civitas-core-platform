package de.civitascore.modelforge.persistence.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CRUD over the {@code model_forge.artifact} table (logical identity + current-version pointer). */
class ArtifactRepository {

    private final JdbcClient jdbc;

    ArtifactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ArtifactRow> findByLogicalUrn(String logicalUrn) {
        return jdbc.sql("""
                select id, logical_urn, artifact_type, name, title, description,
                       current_version, created_at, updated_at
                  from model_forge.artifact where logical_urn = :urn
                """)
            .param("urn", logicalUrn)
            .query(ArtifactRepository::map)
            .optional();
    }

    /** All logical URNs of the given functional type, ordered by name. */
    List<String> listLogicalUrnsByType(String artifactType) {
        return jdbc.sql("select logical_urn from model_forge.artifact where artifact_type = :t order by name")
            .param("t", artifactType)
            .query(String.class)
            .list();
    }

    /** Every artifact's logical URN, of every type, ordered by name. */
    List<String> listAllLogicalUrns() {
        return jdbc.sql("select logical_urn from model_forge.artifact order by name")
            .query(String.class)
            .list();
    }

    /**
     * Logical URNs of the given type whose CURRENT version has a stored representation in
     * {@code format} (e.g. elements whose current version stores an XSD). Used by the
     * {@code format=xsd} list filter, where only versions that actually store XSD qualify.
     */
    List<String> listLogicalUrnsWithRepresentationFormat(String artifactType, String format) {
        return jdbc.sql("""
                select a.logical_urn
                  from model_forge.artifact a
                  join model_forge.artifact_version av
                    on av.artifact_id = a.id and av.version = a.current_version
                  join model_forge.artifact_representation ar
                    on ar.version_id = av.id and ar.format = :f
                 where a.artifact_type = :t
                 order by a.name
                """)
            .param("t", artifactType)
            .param("f", format)
            .query(String.class)
            .list();
    }

    void insert(ArtifactRow a) {
        jdbc.sql("""
                insert into model_forge.artifact
                    (id, logical_urn, artifact_type, name, title, description,
                     current_version, created_at, updated_at)
                values
                    (:id, :urn, :type, :name, :title, :description,
                     :currentVersion, :createdAt, :updatedAt)
                """)
            .param("id", a.id())
            .param("urn", a.logicalUrn())
            .param("type", a.artifactType())
            .param("name", a.name())
            .param("title", a.title())
            .param("description", a.description())
            .param("currentVersion", a.currentVersion())
            .param("createdAt", RegistryTime.toOffset(a.createdAt()))
            .param("updatedAt", RegistryTime.toOffset(a.updatedAt()))
            .update();
    }

    /** Advances the current-version pointer (and bumps {@code updated_at}). */
    void updateCurrentVersion(UUID id, String currentVersion, java.time.Instant updatedAt) {
        jdbc.sql("update model_forge.artifact set current_version = :v, updated_at = :u where id = :id")
            .param("v", currentVersion)
            .param("u", RegistryTime.toOffset(updatedAt))
            .param("id", id)
            .update();
    }

    /** Deletes the model_forge.artifact (versions, representations, references and namespace rows cascade). */
    boolean deleteByLogicalUrn(String logicalUrn) {
        return jdbc.sql("delete from model_forge.artifact where logical_urn = :urn")
            .param("urn", logicalUrn)
            .update() > 0;
    }

    private static ArtifactRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ArtifactRow(
            rs.getObject("id", UUID.class),
            rs.getString("logical_urn"),
            rs.getString("artifact_type"),
            rs.getString("name"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("current_version"),
            RegistryTime.toInstant(rs.getObject("created_at", OffsetDateTime.class)),
            RegistryTime.toInstant(rs.getObject("updated_at", OffsetDateTime.class)));
    }
}

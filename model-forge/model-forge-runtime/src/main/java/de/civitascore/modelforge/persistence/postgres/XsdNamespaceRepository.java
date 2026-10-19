package de.civitascore.modelforge.persistence.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable namespace-to-model_forge.artifact lookup for XSD-backed Elements. */
class XsdNamespaceRepository {

    private final JdbcClient jdbc;

    XsdNamespaceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void upsert(String namespace, UUID artifactId, UUID versionId, Instant updatedAt) {
        jdbc.sql("""
                insert into model_forge.xsd_namespace (namespace, artifact_id, version_id, updated_at)
                values (:ns, :aid, :vid, :updatedAt)
                on conflict (namespace)
                do update set artifact_id = excluded.artifact_id,
                              version_id  = excluded.version_id,
                              updated_at  = excluded.updated_at
                """)
            .param("ns", namespace)
            .param("aid", artifactId)
            .param("vid", versionId)
            .param("updatedAt", RegistryTime.toOffset(updatedAt))
            .update();
    }

    /** Points this artifact's indexed namespaces at another of its versions. */
    void repointVersion(UUID artifactId, UUID versionId, Instant updatedAt) {
        jdbc.sql("""
                update model_forge.xsd_namespace
                   set version_id = :vid, updated_at = :updatedAt
                 where artifact_id = :aid
                """)
            .param("vid", versionId)
            .param("updatedAt", RegistryTime.toOffset(updatedAt))
            .param("aid", artifactId)
            .update();
    }

    /**
     * Removes every namespace row owned by {@code artifactId}. Called before re-upserting an
     * model_forge.artifact's current namespace so a changed/removed {@code targetNamespace} cannot leave a
     * stale namespace → model_forge.artifact mapping behind.
     */
    void deleteByArtifactId(UUID artifactId) {
        jdbc.sql("delete from model_forge.xsd_namespace where artifact_id = :aid")
            .param("aid", artifactId)
            .update();
    }

    /** Clears the whole namespace index — used to rebuild it from scratch. */
    void deleteAll() {
        jdbc.sql("delete from model_forge.xsd_namespace").update();
    }

    /** The logical URN of the XSD model_forge.artifact that declares {@code namespace}. */
    Optional<String> findLogicalUrnByNamespace(String namespace) {
        return jdbc.sql("""
                select a.logical_urn from model_forge.xsd_namespace n
                  join model_forge.artifact a on a.id = n.artifact_id
                 where n.namespace = :ns
                """)
            .param("ns", namespace)
            .query(String.class)
            .optional();
    }
}

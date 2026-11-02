package de.civitascore.modelforge.persistence.postgres;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

/**
 * The highest version ever assigned per logical URN ({@code model_forge.version_high_water}). It
 * survives the deletion of versions and of the artifact, so a number is never assigned twice.
 */
class VersionHighWaterRepository {

    private final JdbcClient jdbc;

    VersionHighWaterRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> find(String logicalUrn) {
        return jdbc.sql("select version from model_forge.version_high_water where logical_urn = :urn")
            .param("urn", logicalUrn)
            .query(String.class)
            .optional();
    }

    /**
     * Records {@code version} as assigned. The caller holds the artifact's write lock, so the read
     * and the write below cannot interleave with another writer of the same URN.
     */
    void raise(String logicalUrn, String version) {
        if (find(logicalUrn).filter(highest -> SemVer.compare(highest, version) >= 0).isPresent()) {
            return;
        }
        jdbc.sql("""
                insert into model_forge.version_high_water (logical_urn, version)
                values (:urn, :version)
                on conflict (logical_urn) do update set version = excluded.version
                """)
            .param("urn", logicalUrn)
            .param("version", version)
            .update();
    }
}

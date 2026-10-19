package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * Query for the schema view (bundled or inlined) of a single artifact.
 *
 * @param maxDepth caps cross-document {@code $ref} expansion to this many hops, bounding the
 *     output size of a deep dependency graph; {@code null} (the default via the single-arg
 *     constructor) inlines/bundles as deep as the graph goes.
 */
public record SchemaViewQuery(ArtifactId artifactId, Integer maxDepth) {

    public SchemaViewQuery {
        Objects.requireNonNull(artifactId, "artifactId");
    }

    public SchemaViewQuery(ArtifactId artifactId) {
        this(artifactId, null);
    }
}

package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * Query for dependencies of a single artifact.
 *
 * @param maxDepth caps traversal to this many hops for {@link de.civitascore.modelforge.facade.ModelForge#dependencies}
 *     and {@link de.civitascore.modelforge.facade.ModelForge#dependents}; {@code null} (the default
 *     via the single-arg constructor) returns only direct edges, as before this field existed.
 *     {@link de.civitascore.modelforge.facade.ModelForge#mapsTo}/{@link de.civitascore.modelforge.facade.ModelForge#mappedFrom}
 *     ignore it — their two-hop Element-Mapping-Element view has no notion of traversal depth.
 */
public record DependencyQuery(ArtifactId artifactId, Integer maxDepth) {

    public DependencyQuery {
        Objects.requireNonNull(artifactId, "artifactId");
    }

    public DependencyQuery(ArtifactId artifactId) {
        this(artifactId, null);
    }
}

package de.civitascore.modelforge.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of {@link de.civitascore.modelforge.facade.ModelForge#importArtifact(ImportArtifactCommand)}.
 *
 * @param artifactId the <em>versioned</em> pin the registry resolved: the newly created version,
 *     or the existing current version when the import was an idempotent re-import
 * @param created {@code true} when this call created the artifact; {@code false} when an identical
 *     artifact already existed and was reused without a write
 * @param dependencies the artifact's outgoing reference edges grouped by stored reference type,
 *     as in {@link ArtifactWriteResult#dependencies()}
 */
public record ImportArtifactResult(
    ArtifactId artifactId,
    boolean created,
    Map<String, List<ArtifactId>> dependencies
) {

    public ImportArtifactResult {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(dependencies, "dependencies");
        Map<String, List<ArtifactId>> copy = new LinkedHashMap<>();
        dependencies.forEach((rel, targets) -> copy.put(rel, List.copyOf(targets)));
        dependencies = Collections.unmodifiableMap(copy);
    }
}

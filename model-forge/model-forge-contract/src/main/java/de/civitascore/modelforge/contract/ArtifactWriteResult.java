package de.civitascore.modelforge.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of writing one artifact ({@code createArtifact}/{@code saveArtifact}).
 *
 * <p>{@code artifactId} is the <em>versioned</em> pin the registry assigned — the id a host
 * stores to reference exactly this version later. Model Forge is the sole version authority:
 * the pin comes from the write itself (the registry returns the assigned version with the
 * write, no read-back), so it is valid even inside a surrounding, not-yet-committed host
 * transaction.
 *
 * <p>{@code dependencies} are the written version's outgoing reference edges, grouped by the
 * stored reference type in document order — e.g. {@code mapping-source}/{@code mapping-target}
 * for a Mapping, {@code pipeline-node} for a Pipeline, {@code datasource-element}/
 * {@code datasink-element} for endpoint payload bindings, {@code dataset-ref} for DataSet members
 * (uniformly, whatever the member's kind — that is carried in the reference name),
 * {@code datastructure-ref} for DataStructure members, {@code schema-ref}/{@code xsd-import}
 * for Elements. A host can
 * mirror these lists into its own persistence; they are recomputed on every write, so
 * re-reading them after each save keeps the mirror current.
 */
public record ArtifactWriteResult(
    ArtifactId artifactId,
    Map<String, List<ArtifactId>> dependencies
) {

    public ArtifactWriteResult {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(dependencies, "dependencies");
        Map<String, List<ArtifactId>> copy = new LinkedHashMap<>();
        dependencies.forEach((rel, targets) -> copy.put(rel, List.copyOf(targets)));
        dependencies = Collections.unmodifiableMap(copy);
    }
}

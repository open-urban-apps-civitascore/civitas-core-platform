package de.civitascore.modelforge.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of importing one schema document into Model Forge.
 *
 * <p>{@code rootArtifactId} is a <em>versioned</em> URN: the concrete version the registry assigned
 * to the imported root artifact. It is the pin a host stores (e.g. on a {@code DataStructureVersion})
 * to reference exactly this version later — reading it back with {@link ArtifactId} resolves to this
 * same version. Model Forge is the sole version authority: the version is assigned inside the write
 * itself and returned with it (no read-back), so the pin is valid even when the import runs inside a
 * surrounding, not-yet-committed host transaction. The version segment reflects what the registry
 * actually assigned, not any version the caller may have supplied; extract it with
 * {@link de.civitascore.modelforge.urn.UrnParser#versionFromUrn(String)}. {@code importedArtifactIds}
 * carries the same versioned identity for every artifact created by this import.
 *
 * <p>{@code dependencies} are the root artifact's outgoing reference edges, grouped by the stored
 * reference type ({@code schema-ref}, {@code xsd-import}, …) in document order — the same lists
 * {@link ArtifactWriteResult#dependencies()} carries for the other artifact kinds, so a host can
 * mirror them into its own persistence.
 */
public record ImportResult(
    ArtifactId rootArtifactId,
    List<ArtifactId> importedArtifactIds,
    Map<String, List<ArtifactId>> dependencies
) {

    public ImportResult {
        Objects.requireNonNull(rootArtifactId, "rootArtifactId");
        importedArtifactIds = List.copyOf(Objects.requireNonNull(importedArtifactIds, "importedArtifactIds"));
        Objects.requireNonNull(dependencies, "dependencies");
        Map<String, List<ArtifactId>> copy = new LinkedHashMap<>();
        dependencies.forEach((rel, targets) -> copy.put(rel, List.copyOf(targets)));
        dependencies = Collections.unmodifiableMap(copy);
    }
}

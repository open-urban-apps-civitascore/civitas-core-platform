package de.civitascore.modelforge.contract;

import java.util.List;
import java.util.Objects;

/**
 * One stored Element whose schema does not conform to JSON Schema 2020-12, with the diagnostics
 * that say why.
 *
 * <p>Such an Element predates the conformance check on the write paths: it is readable, and reads
 * are deliberately left working, so the registry has to be asked which stored artifacts a re-save
 * would now refuse.
 */
public record NonConformingArtifact(
    ArtifactId artifactId,
    String title,
    List<Diagnostic> diagnostics
) {

    public NonConformingArtifact {
        Objects.requireNonNull(artifactId, "artifactId");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}

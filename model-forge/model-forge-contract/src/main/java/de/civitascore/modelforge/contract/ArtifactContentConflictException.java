package de.civitascore.modelforge.contract;

/**
 * Thrown when {@link de.civitascore.modelforge.facade.ModelForge#importArtifact(ImportArtifactCommand)}
 * finds the declared identity already stored with <em>different</em> content. An import never
 * silently overwrites or re-versions someone else's artifact: an identical re-import is idempotent
 * (reuse), a changed one must be an explicit versioning decision taken through
 * {@link de.civitascore.modelforge.facade.ModelForge#saveArtifact(SaveArtifactCommand)}.
 */
public class ArtifactContentConflictException extends ModelForgeException {

    private final String logicalUrn;

    public ArtifactContentConflictException(String logicalUrn) {
        super("Artifact '" + logicalUrn + "' already exists with different content — the envelope "
            + "import refuses to overwrite it. Version an existing artifact explicitly via "
            + "saveArtifact.");
        this.logicalUrn = logicalUrn;
    }

    /** The logical URN whose stored content differs from the envelope's. */
    public String logicalUrn() {
        return logicalUrn;
    }
}

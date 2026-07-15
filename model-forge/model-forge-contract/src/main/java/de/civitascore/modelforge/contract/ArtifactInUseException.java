package de.civitascore.modelforge.contract;

import java.util.List;

/**
 * Thrown when {@link de.civitascore.modelforge.facade.ModelForge#deleteArtifact(ArtifactId)}
 * would break the model: other artifacts still reference the one being deleted. Deleting it
 * would leave dangling references, so the delete is rejected — remove or update the listed
 * dependents first. Grouping edges (a DataStructure listing its members) do not block.
 */
public class ArtifactInUseException extends ModelForgeException {

    private final List<String> dependents;

    public ArtifactInUseException(String artifactUrn, List<String> dependents) {
        super("Cannot delete " + artifactUrn + " — still referenced by: "
            + String.join(", ", dependents));
        this.dependents = List.copyOf(dependents);
    }

    /** Logical URNs of the artifacts whose references block the delete. */
    public List<String> dependents() {
        return dependents;
    }
}

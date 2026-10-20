package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

/**
 * Query for {@link de.civitascore.modelforge.facade.ModelForge#foreignReferences}.
 *
 * @param dataStructureId the UUID of the DataStructure the model belongs to (required)
 * @param document a document about to be stored, or {@code null} to ask about {@code stored} alone
 * @param stored a stored version; with a {@code document}, the references it holds as written are
 *     left out
 */
public record ForeignReferencesQuery(UUID dataStructureId, JsonNode document, ArtifactId stored) {

    public ForeignReferencesQuery {
        Objects.requireNonNull(dataStructureId, "dataStructureId");
        if (document == null && stored == null) {
            throw new IllegalArgumentException("A document or a stored version is required");
        }
    }
}

package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

/**
 * Query for the references of a DataStructure model that leave the DataStructure, see
 * {@link de.civitascore.modelforge.facade.ModelForge#foreignReferences(ForeignReferencesQuery)}.
 *
 * @param dataStructureId the UUID of the DataStructure the model belongs to, as passed to
 *     {@link ImportSchemaCommand#dataStructureId()} (required)
 * @param document a document about to be stored for that DataStructure; {@code null} asks about
 *     {@code stored} alone
 * @param stored a stored version of the DataStructure's model. With a {@code document}, the answer
 *     leaves out every reference this version already holds, written the same way; without one,
 *     it is every reference of this version. {@code null} with a {@code document} means nothing is
 *     stored yet.
 */
public record ForeignReferencesQuery(UUID dataStructureId, JsonNode document, ArtifactId stored) {

    public ForeignReferencesQuery {
        Objects.requireNonNull(dataStructureId, "dataStructureId");
        if (document == null && stored == null) {
            throw new IllegalArgumentException("A document or a stored version is required");
        }
    }
}

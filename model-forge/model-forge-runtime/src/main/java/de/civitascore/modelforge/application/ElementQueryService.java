package de.civitascore.modelforge.application;

import de.civitascore.modelforge.core.port.ArtifactRegistry;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Read side of the Element API behind the facade's {@code getArtifact}. Schema views
 * (bundled/inlined) live in {@link ViewService}; graph queries are served by
 * {@link de.civitascore.modelforge.graph.DependencyGraphService} through the facade;
 * cross-artifact search lives in the registry's {@code searchArtifacts}.
 *
 * <p>The write side lives in {@link ElementCommandService}.
 */
public class ElementQueryService {

    private final ArtifactRegistry registry;

    public ElementQueryService(ArtifactRegistry registry) {
        this.registry = registry;
    }

    /** Raw stored JSON Schema for a JSON Schema URN. */
    public Optional<JsonNode> rawJsonSchema(String urn) {
        return registry.fetch(urn);
    }
}

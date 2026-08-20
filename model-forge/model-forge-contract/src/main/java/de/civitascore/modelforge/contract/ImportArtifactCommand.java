package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Command to import one artifact from a CORE artifact envelope
 * ({@code artifact-envelope.schema.json}): the transport form that separates the caller-declared
 * logical identity ({@code artifactId}), the content-format hint ({@code artifactType}) and the
 * initial content ({@code firstVersion.content.content}).
 *
 * <p>Unlike {@link CreateArtifactCommand}, the declared identity is <em>kept</em> — this is the
 * {@code importSchema} identity exception generalised to the opaque CORE kinds, for artifacts
 * whose identity is managed by an external authority (a catalogue, a standard) and must resolve
 * to the same URN on every instance that imports them.
 *
 * @param envelope the envelope document (required); validated against the envelope schema
 */
public record ImportArtifactCommand(JsonNode envelope) {

    public ImportArtifactCommand {
        Objects.requireNonNull(envelope, "envelope");
    }
}

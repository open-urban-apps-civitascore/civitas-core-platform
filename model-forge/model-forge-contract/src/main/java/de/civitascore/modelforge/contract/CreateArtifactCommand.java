package de.civitascore.modelforge.contract;

import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * Command to create a <em>new</em> Mapping, Pipeline, DataSource, DataSink, DataSet or
 * DataStructure. The caller supplies only a display {@code name}; Model Forge mints the
 * artifact's URN ({@code sanitize(name)-<uuid>} name segment) and returns it — store the
 * returned id and use {@link SaveArtifactCommand} with it for follow-up versions.
 *
 * <p>Elements are created through {@code importSchema}, which mints URNs the same way and
 * additionally splits {@code $defs} into their own Elements.
 *
 * @param kind    artifact kind to create; {@code ELEMENT} is rejected (Elements, including
 *                XSD-backed ones, are created through {@code importSchema})
 * @param name    display name the URN name segment is derived from (must not be blank)
 * @param content the artifact document; its {@code id} field is overwritten with the minted URN
 */
public record CreateArtifactCommand(
    ArtifactKind kind,
    String name,
    JsonNode content
) {

    public CreateArtifactCommand {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}

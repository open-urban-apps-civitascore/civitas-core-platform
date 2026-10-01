package de.civitascore.modelforge.contract;

import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Command to create a <em>new</em> Mapping, Pipeline, DataSource, DataSink, DataSet or
 * DataStructure. Model Forge builds the artifact's URN from {@code id} and returns it — store the
 * returned id and use {@link SaveArtifactCommand} with it for follow-up versions.
 *
 * <p>Elements are created through {@code importSchema}, which additionally splits {@code $defs}
 * into their own Elements.
 *
 * @param kind    artifact kind to create; {@code ELEMENT} is rejected (Elements, including
 *                XSD-backed ones, are created through {@code importSchema})
 * @param name    display name of the artifact (must not be blank); it is not part of the URN, so a
 *                later rename leaves every reference intact
 * @param content the artifact document; its {@code id} field is overwritten with the URN
 * @param dataSet optional CORE URN of a DataSet to link the new artifact into as a member; when
 *                non-null, Model Forge adds it (and, for a Pipeline, the artifacts it references) to
 *                that DataSet's manifest. {@code null} means no DataSet membership (the default).
 * @param id      the UUID the URN carries, typically the id of the host entity the artifact backs;
 *                {@code null} lets Model Forge mint a random one
 */
public record CreateArtifactCommand(
    ArtifactKind kind,
    String name,
    JsonNode content,
    String dataSet,
    UUID id
) {

    public CreateArtifactCommand {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** Create under a minted UUID, linked into {@code dataSet} when it is non-null. */
    public CreateArtifactCommand(ArtifactKind kind, String name, JsonNode content, String dataSet) {
        this(kind, name, content, dataSet, null);
    }

    /** Create under a minted UUID, without linking the artifact into any DataSet. */
    public CreateArtifactCommand(ArtifactKind kind, String name, JsonNode content) {
        this(kind, name, content, null, null);
    }
}

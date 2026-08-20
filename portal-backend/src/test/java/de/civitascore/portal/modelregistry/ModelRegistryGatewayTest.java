package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.facade.ModelForge;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Unit tests for the gateway's content comparisons. Pinned here is the contrast between the two
 * variants: the install turnstile's {@link ModelRegistryGateway#isUnchangedIgnoringUiStyles} must
 * never count {@code x-ui-styles} layout as content, while the update path's {@link
 * ModelRegistryGateway#isUnchanged} must — otherwise layout edits would never be persisted. A
 * mock-based service test cannot show that difference.
 */
class ModelRegistryGatewayTest {

  private static final String VERSIONED_URN =
      "urn:core:standard:openurbanapps:datastructure:environment:airqualitystation:default:1.0.0";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ModelForge modelForge = mock(ModelForge.class);
  private final ModelRegistryGateway gateway = new ModelRegistryGateway(modelForge, objectMapper);

  /** A stored document as the registry serves it: stamped, typed, without styles yet. */
  private ObjectNode storedDocument() {
    ObjectNode stored = objectMapper.createObjectNode();
    stored.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    stored.put("$id", VERSIONED_URN);
    stored.put("type", "object");
    return stored;
  }

  private void stubStored(ObjectNode stored) {
    when(modelForge.getArtifact(new ArtifactId(VERSIONED_URN)))
        .thenReturn(Optional.of(new ArtifactView(new ArtifactId(VERSIONED_URN), stored)));
  }

  @Test
  void isUnchangedIgnoringUiStyles_treatsALayoutOnlyDifferenceAsIdentical() {
    ObjectNode stored = storedDocument();
    stored.putObject("x-ui-styles").putObject("nodes").putObject("n1").put("x", 42);
    stubStored(stored);

    assertThat(gateway.isUnchangedIgnoringUiStyles(VERSIONED_URN, Map.of("type", "object")))
        .isTrue();
  }

  @Test
  void isUnchangedIgnoringUiStyles_stillSeesAContentDifference() {
    stubStored(storedDocument());

    assertThat(gateway.isUnchangedIgnoringUiStyles(VERSIONED_URN, Map.of("type", "string")))
        .isFalse();
  }

  @Test
  void isUnchanged_keepsSeeingALayoutDifference() {
    ObjectNode stored = storedDocument();
    stored.putObject("x-ui-styles").putObject("nodes").putObject("n1").put("x", 42);
    stubStored(stored);

    assertThat(gateway.isUnchanged(VERSIONED_URN, Map.of("type", "object"), null)).isFalse();
  }
}

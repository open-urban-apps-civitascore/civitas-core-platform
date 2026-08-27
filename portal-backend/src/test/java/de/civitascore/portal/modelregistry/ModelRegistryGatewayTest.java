/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.ArtifactWriteResult;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelRegistryGateway — registry failures do not escape as host-unknown exceptions")
class ModelRegistryGatewayTest {

  private static final String DATASET_URN =
      "urn:core:platform:civitas:dataset:common:set:abcdefghij";
  private static final String MEMBER_URN = "urn:core:platform:civitas:element:common:el:abcdefghij";

  @Mock private ModelForge modelForge;

  @InjectMocks private ModelRegistryGateway gateway;

  @BeforeEach
  void injectRealObjectMapper() {
    ReflectionTestUtils.setField(gateway, "objectMapper", new ObjectMapper());
  }

  @Test
  @DisplayName("rejecting an unlinkable member surfaces as invalid input, not an unmapped failure")
  void linkToDataSetTranslatesRejection() {
    doThrow(new IllegalArgumentException("Not a DataSet-member artifact: " + MEMBER_URN))
        .when(modelForge)
        .linkToDataSet(any(), any());

    assertThatThrownBy(() -> gateway.linkToDataSet(DATASET_URN, MEMBER_URN))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining(MEMBER_URN);
  }

  @Test
  @DisplayName("the same translation applies when removing a member")
  void unlinkFromDataSetTranslatesRejection() {
    doThrow(new IllegalArgumentException("Not a DataSet-member artifact: " + MEMBER_URN))
        .when(modelForge)
        .unlinkFromDataSet(any(), any());

    assertThatThrownBy(() -> gateway.unlinkFromDataSet(DATASET_URN, MEMBER_URN))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining(MEMBER_URN);
  }

  @Test
  @DisplayName("a rejected document surfaces as invalid input, carrying the registry's diagnostics")
  void schemaRejectionTranslatesWithDiagnostics() {
    doThrow(
            new ValidationFailedException(
                "Artifact does not satisfy its CORE schema",
                List.of(
                    new Diagnostic(
                        DiagnosticSeverity.ERROR, "must be an object", "type", "/fields"))))
        .when(modelForge)
        .createArtifact(any());

    assertThatThrownBy(
            () ->
                gateway.storePayload(
                    PayloadKind.MAPPING, Optional.empty(), "m", Map.of("fields", 1), null))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("/fields")
        .hasMessageContaining("must be an object");
  }

  @Test
  @DisplayName("deleting an artifact others still reference surfaces as a conflict")
  void inUseDeleteTranslatesToConflict() {
    doThrow(
            new ArtifactInUseException(
                MEMBER_URN, List.of("urn:core:platform:civitas:pipeline:common:p:abcdefghij")))
        .when(modelForge)
        .deleteArtifact(any());

    assertThatThrownBy(() -> gateway.deletePayload(MEMBER_URN))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("still referenced by");
  }

  @Test
  @DisplayName("a supplied URN outside the registry's own namespace is refused")
  void foreignNamespaceUrnIsRefused() {
    ReflectionTestUtils.setField(gateway, "urnScope", "platform");
    ReflectionTestUtils.setField(gateway, "urnOwner", "civitas");
    ReflectionTestUtils.setField(gateway, "urnDomain", "common");

    assertThatThrownBy(
            () ->
                gateway.storePayload(
                    PayloadKind.MAPPING,
                    Optional.of("urn:core:evil:attacker:mapping:hijack:Injected:0000000000"),
                    "m",
                    Map.of(),
                    null))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("outside this registry's namespace");

    verifyNoInteractions(modelForge);
  }

  @Test
  @DisplayName("a URN inside the registry's namespace is accepted")
  void ownNamespaceUrnIsAccepted() {
    ReflectionTestUtils.setField(gateway, "urnScope", "platform");
    ReflectionTestUtils.setField(gateway, "urnOwner", "civitas");
    ReflectionTestUtils.setField(gateway, "urnDomain", "common");
    when(modelForge.saveArtifact(any()))
        .thenReturn(
            new ArtifactWriteResult(
                new ArtifactId("urn:core:platform:civitas:mapping:common:M:abcdefghij:1.1.0"),
                Map.of()));

    assertThat(
            gateway
                .storePayload(
                    PayloadKind.MAPPING,
                    Optional.of("urn:core:platform:civitas:mapping:common:M:abcdefghij"),
                    "M",
                    Map.of(),
                    null)
                .version())
        .isEqualTo("1.1.0");
  }
}

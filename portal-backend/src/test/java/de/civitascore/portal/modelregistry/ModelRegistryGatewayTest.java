/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.portal.util.InvalidInputException;
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
}

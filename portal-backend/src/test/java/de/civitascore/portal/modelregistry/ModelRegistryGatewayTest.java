/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.DependencyClosureView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.portal.util.InvalidInputException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

  @Nested
  @DisplayName("the participating closure")
  class ParticipatingClosure {

    private static final String PIPELINE_URN =
        "urn:core:platform:civitas:pipeline:common:Ingest:aaaaaaaaaa:1.0.0";
    private static final String STRUCTURE_URN =
        "urn:core:platform:civitas:datastructure:common:Sensor:bbbbbbbbbb:1.0.0";
    private static final String GHOST_URN =
        "urn:core:platform:civitas:element:common:Ghost:cccccccccc:1.0.0";

    @Test
    @DisplayName("asks the registry for a bounded walk and passes the depth through")
    void asksForABoundedWalk() {
      when(modelForge.closure(any()))
          .thenReturn(
              new DependencyClosureView(new ArtifactId(PIPELINE_URN), List.of(), List.of(), false));

      gateway.closure(PIPELINE_URN, 6);

      ArgumentCaptor<DependencyQuery> query = ArgumentCaptor.forClass(DependencyQuery.class);
      verify(modelForge).closure(query.capture());
      assertThat(query.getValue().maxDepth()).isEqualTo(6);
      assertThat(query.getValue().artifactId().value()).isEqualTo(PIPELINE_URN);
    }

    @Test
    @DisplayName("passes on that the depth bound stopped the walk short")
    void carriesTruncation() {
      when(modelForge.closure(any()))
          .thenReturn(
              new DependencyClosureView(
                  new ArtifactId(PIPELINE_URN),
                  List.of(new ArtifactId(STRUCTURE_URN)),
                  List.of(),
                  true));

      assertThat(gateway.closure(PIPELINE_URN, 10).truncated()).isTrue();
    }

    @Test
    @DisplayName("reports what the flow reaches and what of it is missing")
    void reportsReachedAndMissing() {
      when(modelForge.closure(any()))
          .thenReturn(
              new DependencyClosureView(
                  new ArtifactId(PIPELINE_URN),
                  List.of(new ArtifactId(STRUCTURE_URN), new ArtifactId(GHOST_URN)),
                  List.of(new ArtifactId(GHOST_URN)),
                  false));

      ModelRegistryGateway.ArtifactClosure closure = gateway.closure(PIPELINE_URN, 10);

      assertThat(closure.artifacts()).containsExactly(STRUCTURE_URN, GHOST_URN);
      assertThat(closure.unresolved()).containsExactly(GHOST_URN);
    }

    @Test
    @DisplayName("walks nothing for a blank artifact or a non-positive depth")
    void nothingToWalk() {
      assertThat(gateway.closure(null, 10).artifacts()).isEmpty();
      assertThat(gateway.closure("  ", 10).artifacts()).isEmpty();
      assertThat(gateway.closure(PIPELINE_URN, 0).artifacts()).isEmpty();
      verify(modelForge, never()).closure(any());
    }
  }
}

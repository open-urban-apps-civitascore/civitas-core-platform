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
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.portal.util.InvalidInputException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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

    private DependencyGraphView viewOf(String source, String... targets) {
      List<DependencyGraphView.Node> nodes =
          Stream.concat(Stream.of(source), Arrays.stream(targets))
              .map(urn -> new DependencyGraphView.Node(new ArtifactId(urn), "label"))
              .toList();
      return new DependencyGraphView(nodes, List.of());
    }

    @Test
    @DisplayName("asks for a bounded walk, which is the cycle-safe one")
    void asksForABoundedWalk() {
      when(modelForge.dependencies(any())).thenReturn(viewOf(PIPELINE_URN));

      gateway.transitiveDependencyUrns(PIPELINE_URN, 6);

      // A DependencyQuery without a depth returns direct edges only and never reaches the
      // cycle-guarded transitive walk, so the depth being carried is what makes the walk both
      // transitive and terminating.
      ArgumentCaptor<DependencyQuery> query = ArgumentCaptor.forClass(DependencyQuery.class);
      verify(modelForge).dependencies(query.capture());
      assertThat(query.getValue().maxDepth()).isEqualTo(6);
      assertThat(query.getValue().artifactId().value()).isEqualTo(PIPELINE_URN);
    }

    @Test
    @DisplayName("returns the reached artifacts without the flow's own entry artifact")
    void excludesTheStartingArtifact() {
      when(modelForge.dependencies(any())).thenReturn(viewOf(PIPELINE_URN, STRUCTURE_URN));

      assertThat(gateway.transitiveDependencyUrns(PIPELINE_URN, 10)).containsExactly(STRUCTURE_URN);
    }

    @Test
    @DisplayName("walks nothing for a blank artifact or a non-positive depth")
    void nothingToWalk() {
      assertThat(gateway.transitiveDependencyUrns(null, 10)).isEmpty();
      assertThat(gateway.transitiveDependencyUrns("  ", 10)).isEmpty();
      assertThat(gateway.transitiveDependencyUrns(PIPELINE_URN, 0)).isEmpty();
      verify(modelForge, never()).dependencies(any());
    }

    @Test
    @DisplayName("reports whether the registry holds an artifact")
    void reportsExistence() {
      when(modelForge.getArtifact(new ArtifactId(STRUCTURE_URN)))
          .thenReturn(
              Optional.of(
                  new ArtifactView(
                      new ArtifactId(STRUCTURE_URN), new ObjectMapper().createObjectNode())));

      assertThat(gateway.exists(STRUCTURE_URN)).isTrue();
      assertThat(gateway.exists(null)).isFalse();
      assertThat(gateway.exists("")).isFalse();
    }

    @Test
    @DisplayName("reports an artifact the registry does not hold as absent")
    void reportsAbsence() {
      when(modelForge.getArtifact(new ArtifactId(STRUCTURE_URN))).thenReturn(Optional.empty());

      assertThat(gateway.exists(STRUCTURE_URN)).isFalse();
    }

    @Test
    @DisplayName("names an artifact's kind from its URN")
    void namesTheArtifactKind() {
      assertThat(gateway.artifactType(STRUCTURE_URN)).isEqualTo("datastructure");
      assertThat(gateway.artifactType(PIPELINE_URN)).isEqualTo("pipeline");
    }
  }
}

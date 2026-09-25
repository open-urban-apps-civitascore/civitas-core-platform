/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.DependencyClosureView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.facade.ModelForge;
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
@DisplayName("ModelRegistryGateway")
class ModelRegistryGatewayTest {

  @Mock private ModelForge modelForge;

  @InjectMocks private ModelRegistryGateway gateway;

  @BeforeEach
  void injectRealObjectMapper() {
    ReflectionTestUtils.setField(gateway, "objectMapper", new ObjectMapper());
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

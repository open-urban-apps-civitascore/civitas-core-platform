package de.civitascore.portal.service.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.util.PipelineClosureTooLargeException;
import de.civitascore.portal.util.PipelineClosureValidationException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineClosureValidator")
class PipelineClosureValidatorTest {

  private static final String PIPELINE_URN =
      "urn:core:platform:civitas:pipeline:common:Ingest:aaaaaaaaaa:1.0.0";
  private static final String STRUCTURE_URN =
      "urn:core:platform:civitas:datastructure:common:Sensor:bbbbbbbbbb:1.0.0";
  private static final String ELEMENT_URN =
      "urn:core:platform:civitas:element:common:Address:cccccccccc:1.0.0";

  @Mock private ModelRegistryGateway modelRegistryGateway;

  /** Records what it was handed, so dispatch and batching can be asserted. */
  private static final class RecordingCheck implements ClosureNodeCheck {
    private final String artifactType;
    private final List<List<ClosureNode>> calls = new ArrayList<>();
    private List<ClosureFinding> findings = List.of();

    private RecordingCheck(String artifactType) {
      this.artifactType = artifactType;
    }

    @Override
    public String artifactType() {
      return artifactType;
    }

    @Override
    public List<ClosureFinding> check(List<ClosureNode> nodes) {
      calls.add(List.copyOf(nodes));
      return findings;
    }
  }

  private PipelineClosureValidator validator(List<ClosureNodeCheck> checks) {
    return validator(checks, 10, 500);
  }

  private PipelineClosureValidator validator(
      List<ClosureNodeCheck> checks, int maxDepth, int maxArtifacts) {
    return new PipelineClosureValidator(
        modelRegistryGateway,
        new PipelineClosureValidationProperties(maxDepth, maxArtifacts),
        checks);
  }

  private static Pipeline pipeline(UUID id, String modelUrn) {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(id);
    pipeline.setModelUrn(modelUrn);
    return pipeline;
  }

  private void closureOf(String pipelineUrn, String... urns) {
    lenient()
        .when(modelRegistryGateway.transitiveDependencyUrns(eq(pipelineUrn), anyInt()))
        .thenReturn(new LinkedHashSet<>(List.of(urns)));
  }

  private void allResolve() {
    lenient().when(modelRegistryGateway.exists(anyString())).thenReturn(true);
    lenient()
        .when(modelRegistryGateway.artifactType(anyString()))
        .thenAnswer(call -> call.getArgument(0, String.class).split(":")[4]);
  }

  @Nested
  @DisplayName("the walk")
  class TheWalk {

    @Test
    @DisplayName("bounds the traversal depth it asks the registry for")
    void passesTheConfiguredDepth() {
      UUID pipelineId = UUID.randomUUID();
      when(modelRegistryGateway.transitiveDependencyUrns(PIPELINE_URN, 7)).thenReturn(Set.of());

      validator(List.of(), 7, 500).validate(List.of(pipeline(pipelineId, PIPELINE_URN)));

      verify(modelRegistryGateway).transitiveDependencyUrns(PIPELINE_URN, 7);
    }

    @Test
    @DisplayName("skips a pipeline whose flow has not been authored yet")
    void skipsPipelineWithoutAModel() {
      validator(List.of()).validate(List.of(pipeline(UUID.randomUUID(), null)));

      verify(modelRegistryGateway, never()).transitiveDependencyUrns(any(), anyInt());
    }

    @Test
    @DisplayName("examines each pipeline's own flow, so several are all walked")
    void walksEveryPipeline() {
      String otherPipelineUrn = "urn:core:platform:civitas:pipeline:common:Second:dddddddddd:1.0.0";
      closureOf(PIPELINE_URN, STRUCTURE_URN);
      closureOf(otherPipelineUrn, ELEMENT_URN);
      allResolve();
      RecordingCheck check = new RecordingCheck("datastructure");

      validator(List.of(check))
          .validate(
              List.of(
                  pipeline(UUID.randomUUID(), PIPELINE_URN),
                  pipeline(UUID.randomUUID(), otherPipelineUrn)));

      verify(modelRegistryGateway).transitiveDependencyUrns(PIPELINE_URN, 10);
      verify(modelRegistryGateway).transitiveDependencyUrns(otherPipelineUrn, 10);
    }
  }

  @Nested
  @DisplayName("resolvability")
  class Resolvability {

    @Test
    @DisplayName("reports an artifact the registry no longer holds as not available")
    void unresolvedArtifactIsReported() {
      UUID pipelineId = UUID.randomUUID();
      closureOf(PIPELINE_URN, ELEMENT_URN);
      when(modelRegistryGateway.exists(ELEMENT_URN)).thenReturn(false);

      assertThatThrownBy(
              () -> validator(List.of()).validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .isInstanceOf(PipelineClosureValidationException.class)
          .satisfies(
              thrown ->
                  assertThat(((PipelineClosureValidationException) thrown).getFindings())
                      .containsExactly(ClosureFinding.notAvailable(pipelineId, ELEMENT_URN)));
    }

    @Test
    @DisplayName("holds an artifact of a kind no check covers to resolvability alone")
    void resolvableArtifactOfUncheckedKindPasses() {
      closureOf(PIPELINE_URN, ELEMENT_URN);
      allResolve();

      assertThatCode(
              () ->
                  validator(List.of(new RecordingCheck("datastructure")))
                      .validate(List.of(pipeline(UUID.randomUUID(), PIPELINE_URN))))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not hand an unresolved artifact to its kind's check")
    void unresolvedArtifactIsNotChecked() {
      closureOf(PIPELINE_URN, STRUCTURE_URN);
      when(modelRegistryGateway.exists(STRUCTURE_URN)).thenReturn(false);
      RecordingCheck check = new RecordingCheck("datastructure");

      assertThatThrownBy(
              () ->
                  validator(List.of(check))
                      .validate(List.of(pipeline(UUID.randomUUID(), PIPELINE_URN))))
          .isInstanceOf(PipelineClosureValidationException.class);

      assertThat(check.calls).as("nothing can be asked of an artifact that is not there").isEmpty();
    }
  }

  @Nested
  @DisplayName("dispatch to a kind's check")
  class Dispatch {

    @Test
    @DisplayName("hands every node of a kind over in one call, across all pipelines")
    void batchesNodesOfOneKindAcrossPipelines() {
      String otherPipelineUrn = "urn:core:platform:civitas:pipeline:common:Second:dddddddddd:1.0.0";
      String otherStructureUrn =
          "urn:core:platform:civitas:datastructure:common:Other:eeeeeeeeee:1.0.0";
      UUID firstPipeline = UUID.randomUUID();
      UUID secondPipeline = UUID.randomUUID();
      closureOf(PIPELINE_URN, STRUCTURE_URN, ELEMENT_URN);
      closureOf(otherPipelineUrn, otherStructureUrn);
      allResolve();
      RecordingCheck check = new RecordingCheck("datastructure");

      validator(List.of(check))
          .validate(
              List.of(
                  pipeline(firstPipeline, PIPELINE_URN),
                  pipeline(secondPipeline, otherPipelineUrn)));

      assertThat(check.calls).as("one call, not one per artifact or per pipeline").hasSize(1);
      assertThat(check.calls.getFirst())
          .containsExactly(
              new ClosureNode(firstPipeline, STRUCTURE_URN, "datastructure"),
              new ClosureNode(secondPipeline, otherStructureUrn, "datastructure"));
    }

    @Test
    @DisplayName("gives a check only the nodes of its own kind")
    void dispatchesByArtifactType() {
      closureOf(PIPELINE_URN, STRUCTURE_URN, ELEMENT_URN);
      allResolve();
      RecordingCheck structures = new RecordingCheck("datastructure");
      RecordingCheck mappings = new RecordingCheck("mapping");

      validator(List.of(structures, mappings))
          .validate(List.of(pipeline(UUID.randomUUID(), PIPELINE_URN)));

      assertThat(structures.calls.getFirst())
          .singleElement()
          .extracting(ClosureNode::artifactUrn)
          .isEqualTo(STRUCTURE_URN);
      assertThat(mappings.calls).as("no mapping was reached, so its check is not called").isEmpty();
    }

    @Test
    @DisplayName("reports findings from every kind's check together in one failure")
    void collectsFindingsAcrossChecks() {
      UUID pipelineId = UUID.randomUUID();
      String mappingUrn = "urn:core:platform:civitas:mapping:common:Temp:ffffffffff:1.0.0";
      closureOf(PIPELINE_URN, STRUCTURE_URN, mappingUrn);
      allResolve();
      RecordingCheck structures = new RecordingCheck("datastructure");
      structures.findings = List.of(ClosureFinding.notReleased(pipelineId, STRUCTURE_URN));
      RecordingCheck mappings = new RecordingCheck("mapping");
      mappings.findings =
          List.of(ClosureFinding.invalid(pipelineId, mappingUrn, List.of("broken")));

      assertThatThrownBy(
              () ->
                  validator(List.of(structures, mappings))
                      .validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .isInstanceOf(PipelineClosureValidationException.class)
          .satisfies(
              thrown ->
                  assertThat(((PipelineClosureValidationException) thrown).getFindings())
                      .as("one attempt names everything that needs repairing")
                      .hasSize(2));
    }
  }

  @Nested
  @DisplayName("the artifact bound")
  class TheArtifactBound {

    @Test
    @DisplayName("refuses a flow reaching more artifacts than the bound")
    void refusesAnOversizedClosure() {
      UUID pipelineId = UUID.randomUUID();
      Set<String> tooMany =
          IntStream.range(0, 4)
              .mapToObj(i -> "urn:core:platform:civitas:element:common:E" + i + ":gggggggggg:1.0.0")
              .collect(Collectors.toCollection(LinkedHashSet::new));
      when(modelRegistryGateway.transitiveDependencyUrns(PIPELINE_URN, 10)).thenReturn(tooMany);

      assertThatThrownBy(
              () ->
                  validator(List.of(), 10, 3).validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .isInstanceOf(PipelineClosureTooLargeException.class)
          .satisfies(
              thrown ->
                  assertThat(((PipelineClosureTooLargeException) thrown).getOversizedClosures())
                      .containsExactly(
                          new PipelineClosureTooLargeException.OversizedClosure(pipelineId, 4, 3)));
    }

    @Test
    @DisplayName("examines no artifact of an oversized flow")
    void examinesNothingWhenOversized() {
      Set<String> tooMany =
          new LinkedHashSet<>(
              List.of(
                  STRUCTURE_URN,
                  ELEMENT_URN,
                  "urn:core:platform:civitas:element:common:Third:hhhhhhhhhh:1.0.0"));
      when(modelRegistryGateway.transitiveDependencyUrns(PIPELINE_URN, 10)).thenReturn(tooMany);
      RecordingCheck check = new RecordingCheck("datastructure");

      assertThatThrownBy(
              () ->
                  validator(List.of(check), 10, 2)
                      .validate(List.of(pipeline(UUID.randomUUID(), PIPELINE_URN))))
          .isInstanceOf(PipelineClosureTooLargeException.class);

      verify(modelRegistryGateway, never()).exists(anyString());
      assertThat(check.calls).isEmpty();
    }

    @Test
    @DisplayName("refuses rather than reporting the flows it did manage to examine")
    void oversizeHidesPartialFindings() {
      String otherPipelineUrn = "urn:core:platform:civitas:pipeline:common:Second:dddddddddd:1.0.0";
      closureOf(PIPELINE_URN, ELEMENT_URN);
      when(modelRegistryGateway.exists(ELEMENT_URN)).thenReturn(false);
      when(modelRegistryGateway.transitiveDependencyUrns(otherPipelineUrn, 10))
          .thenReturn(
              new LinkedHashSet<>(
                  List.of(
                      STRUCTURE_URN,
                      "urn:core:platform:civitas:element:common:Third:hhhhhhhhhh:1.0.0")));

      assertThatThrownBy(
              () ->
                  validator(List.of(), 10, 1)
                      .validate(
                          List.of(
                              pipeline(UUID.randomUUID(), PIPELINE_URN),
                              pipeline(UUID.randomUUID(), otherPipelineUrn))))
          .as("a partial answer would read as the complete picture")
          .isInstanceOf(PipelineClosureTooLargeException.class);
    }
  }

  @Test
  @DisplayName("passes a dataset with no pipelines")
  void noPipelinesIsNothingToValidate() {
    assertThatCode(() -> validator(List.of()).validate(List.of())).doesNotThrowAnyException();
    assertThatCode(() -> validator(List.of()).validate(null)).doesNotThrowAnyException();
  }
}

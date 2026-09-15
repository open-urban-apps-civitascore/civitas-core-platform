package de.civitascore.portal.service.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.util.PipelineClosureValidationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineClosureValidator")
class PipelineClosureValidatorTest {

  private static final String PIPELINE_URN =
      "urn:core:platform:civitas:pipeline:common:Ingest:aaaaaaaaaa:1.0.0";
  private static final String OTHER_PIPELINE_URN =
      "urn:core:platform:civitas:pipeline:common:Second:dddddddddd:1.0.0";
  private static final String STRUCTURE_URN =
      "urn:core:platform:civitas:datastructure:common:Sensor:bbbbbbbbbb:1.0.0";
  private static final String ELEMENT_URN =
      "urn:core:platform:civitas:element:common:Address:cccccccccc:1.0.0";

  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;

  private final UUID pipelineId = UUID.randomUUID();

  private PipelineClosureValidator validator() {
    return validator(10);
  }

  private PipelineClosureValidator validator(int maxDepth) {
    return new PipelineClosureValidator(
        modelRegistryGateway,
        dataStructureVersionRepository,
        scopeAccessAuthorizer,
        new PipelineClosureValidationProperties(maxDepth));
  }

  private static Pipeline pipeline(UUID id, String modelUrn) {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(id);
    pipeline.setModelUrn(modelUrn);
    return pipeline;
  }

  /** What the registry reports for a flow: everything it reaches, and what it no longer holds. */
  private void closureOf(String pipelineUrn, Set<String> artifacts, Set<String> unresolved) {
    lenient()
        .when(modelRegistryGateway.closure(eq(pipelineUrn), anyInt()))
        .thenReturn(new ModelRegistryGateway.ArtifactClosure(artifacts, unresolved));
  }

  private static DataStructureVersion version(
      String modelUrn, DataStructureVersionStatus versionStatus, DataStructureStatus parentStatus) {
    DataStructure parent = new DataStructure();
    parent.setId(UUID.randomUUID());
    parent.setDataStructureStatus(parentStatus);
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setModelUrn(modelUrn);
    version.setDataStructure(parent);
    version.setDataStructureVersionStatus(versionStatus);
    return version;
  }

  private static DataStructureVersion released(String modelUrn) {
    return version(modelUrn, DataStructureVersionStatus.AVAILABLE, DataStructureStatus.AVAILABLE);
  }

  private static List<ClosureFinding> findingsOf(Throwable thrown) {
    return ((PipelineClosureValidationException) thrown).getFindings();
  }

  private List<Pipeline> pipelines() {
    return List.of(pipeline(pipelineId, PIPELINE_URN));
  }

  private void denyEveryStructure() {
    doThrow(new AccessDeniedException("denied"))
        .when(scopeAccessAuthorizer)
        .authorizeReferences(any(), any());
  }

  @Nested
  @DisplayName("the walk")
  class TheWalk {

    @Test
    @DisplayName("asks the registry only as deep as it is configured to")
    void passesTheConfiguredDepth() {
      closureOf(PIPELINE_URN, Set.of(), Set.of());

      validator(7).validate(List.of(pipeline(pipelineId, PIPELINE_URN)));

      verify(modelRegistryGateway).closure(PIPELINE_URN, 7);
    }

    @Test
    @DisplayName("decides a structure's readability once, however many versions reach it")
    void readabilityIsDecidedOncePerStructure() {
      DataStructure shared = new DataStructure();
      shared.setId(UUID.randomUUID());
      shared.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      DataStructureVersion first = released(STRUCTURE_URN);
      first.setDataStructure(shared);
      DataStructureVersion second = released(ELEMENT_URN);
      second.setDataStructure(shared);

      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN, ELEMENT_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(List.of(first, second));

      validator().validate(pipelines());

      verify(scopeAccessAuthorizer, times(1)).authorizeReferences(any(), any());
    }

    @Test
    @DisplayName("skips a pipeline whose flow has not been authored yet")
    void skipsPipelineWithoutAModel() {
      validator().validate(List.of(pipeline(pipelineId, null)));

      verify(modelRegistryGateway, never()).closure(any(), anyInt());
    }

    @Test
    @DisplayName("passes a dataset with no pipelines")
    void noPipelinesIsNothingToValidate() {
      assertThatCode(() -> validator().validate(List.of())).doesNotThrowAnyException();
      assertThatCode(() -> validator().validate(null)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("what a participating artifact must satisfy")
  class WhatAParticipatingArtifactMustSatisfy {

    @Test
    @DisplayName("an artifact the registry no longer holds blocks the flow")
    void unresolvedArtifactBlocks() {
      closureOf(PIPELINE_URN, Set.of(ELEMENT_URN), Set.of(ELEMENT_URN));

      assertThatThrownBy(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .isInstanceOf(PipelineClosureValidationException.class)
          .satisfies(
              thrown ->
                  assertThat(findingsOf(thrown))
                      .containsExactly(ClosureFinding.notAvailable(pipelineId)));
    }

    @Test
    @DisplayName(
        "an artifact the platform holds no version record for is held to resolvability alone")
    void anArtifactWithoutAVersionRecordPasses() {
      closureOf(PIPELINE_URN, Set.of(ELEMENT_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any())).thenReturn(List.of());

      assertThatCode(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .as("a member element, a mapping and a sink configuration all fall here")
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a released, readable data structure passes")
    void aReleasedStructurePasses() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(List.of(released(STRUCTURE_URN)));

      assertThatCode(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a data structure still in draft blocks the flow")
    void aDraftStructureBlocks() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(
              List.of(
                  version(
                      STRUCTURE_URN,
                      DataStructureVersionStatus.DRAFT,
                      DataStructureStatus.AVAILABLE)));

      assertThatThrownBy(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .satisfies(
              thrown ->
                  assertThat(findingsOf(thrown))
                      .containsExactly(ClosureFinding.notReleased(pipelineId, STRUCTURE_URN)));
    }

    @Test
    @DisplayName("a released version of a data structure still in draft blocks the flow")
    void aReleasedVersionOfADraftStructureBlocks() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(
              List.of(
                  version(
                      STRUCTURE_URN,
                      DataStructureVersionStatus.AVAILABLE,
                      DataStructureStatus.DRAFT)));

      assertThatThrownBy(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .satisfies(
              thrown ->
                  assertThat(findingsOf(thrown))
                      .containsExactly(ClosureFinding.notReleased(pipelineId, STRUCTURE_URN)));
    }

    @Test
    @DisplayName("one released record is enough, since a flow pins the artifact")
    void oneReleasedRecordIsEnough() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(
              List.of(
                  version(
                      STRUCTURE_URN, DataStructureVersionStatus.DRAFT, DataStructureStatus.DRAFT),
                  released(STRUCTURE_URN)));

      assertThatCode(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .as("two records pin one artifact when their content is identical")
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reports every flow's findings together, not the first")
    void collectsFindingsAcrossPipelines() {
      UUID otherPipeline = UUID.randomUUID();
      closureOf(PIPELINE_URN, Set.of(ELEMENT_URN), Set.of(ELEMENT_URN));
      closureOf(OTHER_PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of(STRUCTURE_URN));

      assertThatThrownBy(
              () ->
                  validator()
                      .validate(
                          List.of(
                              pipeline(pipelineId, PIPELINE_URN),
                              pipeline(otherPipeline, OTHER_PIPELINE_URN))))
          .satisfies(
              thrown ->
                  assertThat(findingsOf(thrown))
                      .as("one attempt names everything that needs repairing")
                      .containsExactly(
                          ClosureFinding.notAvailable(pipelineId),
                          ClosureFinding.notAvailable(otherPipeline)));
    }
  }

  @Nested
  @DisplayName("withholding what the caller may not know")
  class Withholding {

    @Test
    @DisplayName("a withheld artifact is named nowhere in the reply")
    void aWithheldArtifactIsNamedNowhereInTheReply() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(List.of(released(STRUCTURE_URN)));
      denyEveryStructure();

      assertThat(findingsOf(catchThrowable(() -> validator().validate(pipelines()))))
          .singleElement()
          .satisfies(
              finding -> {
                assertThat(finding.reason()).isEqualTo(ClosureFinding.Reason.NOT_AVAILABLE);
                assertThat(finding.artifactUrn())
                    .as("a CORE URN spells the model's name in one of its segments")
                    .isNull();
              });
    }

    @Test
    @DisplayName("a draft the caller may read is named, because they can act on it")
    void aReadableDraftIsNamed() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(
              List.of(
                  version(
                      STRUCTURE_URN,
                      DataStructureVersionStatus.DRAFT,
                      DataStructureStatus.AVAILABLE)));

      assertThat(findingsOf(catchThrowable(() -> validator().validate(pipelines()))))
          .containsExactly(ClosureFinding.notReleased(pipelineId, STRUCTURE_URN));
    }

    @Test
    @DisplayName("many withheld artifacts of one flow collapse into a single entry")
    void manyWithheldArtifactsCollapseIntoOneEntry() {
      closureOf(
          PIPELINE_URN, Set.of(STRUCTURE_URN, ELEMENT_URN), Set.of(STRUCTURE_URN, ELEMENT_URN));

      assertThat(findingsOf(catchThrowable(() -> validator().validate(pipelines()))))
          .as("entries that name no artifact are indistinguishable, so repeating them says nothing")
          .containsExactly(ClosureFinding.notAvailable(pipelineId));
    }

    @Test
    @DisplayName("an unreadable artifact is reported in the same terms as an unresolved one")
    void unreadableAndUnresolvedAreIndistinguishable() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of(STRUCTURE_URN));
      List<ClosureFinding> onUnresolved =
          findingsOf(
              catchThrowable(
                  () -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN)))));

      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(List.of(released(STRUCTURE_URN)));
      denyEveryStructure();
      List<ClosureFinding> onUnreadable =
          findingsOf(
              catchThrowable(
                  () -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN)))));

      assertThat(onUnreadable)
          .as("the difference would be an existence oracle over artifact URNs")
          .isEqualTo(onUnresolved);
    }

    @Test
    @DisplayName("says nothing of the lifecycle of a structure the caller may not read")
    void withholdsDraftStateFromAnUnauthorizedCaller() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(dataStructureVersionRepository.findAllByModelUrnIn(any()))
          .thenReturn(
              List.of(
                  version(
                      STRUCTURE_URN, DataStructureVersionStatus.DRAFT, DataStructureStatus.DRAFT)));
      denyEveryStructure();

      assertThatThrownBy(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .satisfies(
              thrown ->
                  assertThat(findingsOf(thrown))
                      .as("a draft reason would confirm the structure exists")
                      .containsExactly(ClosureFinding.notAvailable(pipelineId)));
    }
  }
}

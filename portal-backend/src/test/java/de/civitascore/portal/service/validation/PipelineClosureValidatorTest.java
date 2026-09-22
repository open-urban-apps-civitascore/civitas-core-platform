package de.civitascore.portal.service.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.service.GoverningVersionLookup;
import de.civitascore.portal.util.PipelineClosureValidationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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
  @Mock private GoverningVersionLookup governingVersions;
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;

  private final UUID pipelineId = UUID.randomUUID();

  private PipelineClosureValidator validator() {
    return validator(10);
  }

  @org.junit.jupiter.api.BeforeEach
  void stubLogicalUrn() {
    lenient()
        .when(modelRegistryGateway.logicalUrn(anyString()))
        .thenAnswer(call -> logicalOf(call.getArgument(0)));
  }

  private PipelineClosureValidator validator(int maxDepth) {
    return new PipelineClosureValidator(
        modelRegistryGateway,
        governingVersions,
        scopeAccessAuthorizer,
        new PipelineClosureValidationProperties(maxDepth));
  }

  private static Pipeline pipeline(UUID id, String modelUrn) {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(id);
    pipeline.setModelUrn(modelUrn);
    return pipeline;
  }

  private static DataSource dataSource(DataSourceStatus status) {
    DataSource dataSource = new DataSource();
    dataSource.setId(UUID.randomUUID());
    dataSource.setDataSourceStatus(status);
    return dataSource;
  }

  /** What the registry reports for a flow: everything it reaches, and what it no longer holds. */
  private void closureOf(String pipelineUrn, Set<String> artifacts, Set<String> unresolved) {
    closureOf(pipelineUrn, artifacts, unresolved, false);
  }

  /** As above, with the registry reporting that the depth bound stopped the walk short. */
  private void closureOf(
      String pipelineUrn, Set<String> artifacts, Set<String> unresolved, boolean truncated) {
    lenient()
        .when(modelRegistryGateway.closure(eq(pipelineUrn), anyInt()))
        .thenReturn(new ModelRegistryGateway.ArtifactClosure(artifacts, unresolved, truncated));
  }

  private static DataStructureVersion version(
      String modelUrn, DataStructureVersionStatus versionStatus, DataStructureStatus parentStatus) {
    DataStructure parent = new DataStructure();
    parent.setId(UUID.randomUUID());
    parent.setDataStructureStatus(parentStatus);
    parent.setModelLogicalUrn(logicalOf(modelUrn));
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setModelUrn(modelUrn);
    version.setVersion(versionOf(modelUrn));
    version.setDataStructure(parent);
    version.setDataStructureVersionStatus(versionStatus);
    return version;
  }

  /** What the resolver reports: each record keyed by the pin the flow recorded. */
  private static Map<String, List<DataStructureVersion>> governedBy(
      List<DataStructureVersion> versions) {
    return versions.stream().collect(Collectors.groupingBy(DataStructureVersion::getModelUrn));
  }

  private static String logicalOf(String urn) {
    return urn.substring(0, urn.lastIndexOf(':'));
  }

  private static String versionOf(String urn) {
    return urn.substring(urn.lastIndexOf(':') + 1);
  }

  private static DataStructureVersion released(String modelUrn) {
    return version(modelUrn, DataStructureVersionStatus.AVAILABLE, DataStructureStatus.AVAILABLE);
  }

  private static List<UUID> blockedPipelinesOf(Throwable thrown) {
    return ((PipelineClosureValidationException) thrown).getOffendingPipelineIds();
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
      shared.setModelLogicalUrn(logicalOf(STRUCTURE_URN));
      DataStructureVersion first = released(STRUCTURE_URN);
      first.setDataStructure(shared);
      DataStructureVersion second = released(STRUCTURE_URN);
      second.setDataStructure(shared);

      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any())).thenReturn(governedBy(List.of(first, second)));

      validator().validate(pipelines());

      verify(scopeAccessAuthorizer, times(1)).authorizeReferences(any(), any());
    }

    @Test
    @DisplayName("reads no Assignment to judge a data source, whatever its status")
    void dataSourceStatusNeedsNoAuthorization() {
      Pipeline pipeline = pipeline(pipelineId, PIPELINE_URN);
      pipeline.setDataSources(Set.of(dataSource(DataSourceStatus.DRAFT)));
      closureOf(PIPELINE_URN, Set.of(), Set.of());

      assertThatThrownBy(() -> validator().validate(List.of(pipeline)))
          .isInstanceOf(PipelineClosureValidationException.class);

      verify(scopeAccessAuthorizer, never()).authorizeReferences(eq(ScopeType.DATASOURCE), any());
    }

    @Test
    @DisplayName("skips a pipeline whose flow has not been authored yet")
    void skipsPipelineWithoutAModel() {
      validator().validate(List.of(pipeline(pipelineId, null)));

      verify(modelRegistryGateway, never()).closure(any(), anyInt());
    }

    @Test
    @DisplayName("a flow reaching past the bound is refused although all it examined was sound")
    void aTruncatedWalkBlocks() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of(), true);
      when(governingVersions.governingAll(any()))
          .thenReturn(governedBy(List.of(released(STRUCTURE_URN))));

      assertThat(blockedPipelinesOf(catchThrowable(() -> validator().validate(pipelines()))))
          .as("what the walk did not see is the reason, not what it saw")
          .containsExactly(pipelineId);
    }

    @Test
    @DisplayName("a flow that fits inside the bound is not held against it")
    void anUntruncatedWalkPasses() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(governedBy(List.of(released(STRUCTURE_URN))));

      assertThatCode(() -> validator().validate(pipelines())).doesNotThrowAnyException();
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
          .satisfies(thrown -> assertThat(blockedPipelinesOf(thrown)).containsExactly(pipelineId));
    }

    @Test
    @DisplayName(
        "an artifact the platform holds no version record for is held to resolvability alone")
    void anArtifactWithoutAVersionRecordPasses() {
      closureOf(PIPELINE_URN, Set.of(ELEMENT_URN), Set.of());
      when(governingVersions.governingAll(any())).thenReturn(governedBy(List.of()));

      assertThatCode(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .as("a member element, a mapping and a sink configuration all fall here")
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a released, readable data structure passes")
    void aReleasedStructurePasses() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(governedBy(List.of(released(STRUCTURE_URN))));

      assertThatCode(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a data source still in draft blocks the flow")
    void aDraftDataSourceBlocks() {
      DataSource dataSource = dataSource(DataSourceStatus.DRAFT);
      dataSource.setName("withheld-source-name");
      Pipeline pipeline = pipeline(pipelineId, PIPELINE_URN);
      pipeline.setDataSources(Set.of(dataSource));
      closureOf(PIPELINE_URN, Set.of(), Set.of());

      assertThatThrownBy(() -> validator().validate(List.of(pipeline)))
          .isInstanceOf(PipelineClosureValidationException.class)
          .hasMessageNotContaining(dataSource.getId().toString())
          .hasMessageNotContaining(dataSource.getName())
          .satisfies(thrown -> assertThat(blockedPipelinesOf(thrown)).containsExactly(pipelineId));
    }

    @Test
    @DisplayName("a released data source passes")
    void anAvailableDataSourcePasses() {
      Pipeline pipeline = pipeline(pipelineId, PIPELINE_URN);
      pipeline.setDataSources(Set.of(dataSource(DataSourceStatus.AVAILABLE)));
      closureOf(PIPELINE_URN, Set.of(), Set.of());

      assertThatCode(() -> validator().validate(List.of(pipeline))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a data structure still in draft blocks the flow")
    void aDraftStructureBlocks() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(
              governedBy(
                  List.of(
                      version(
                          STRUCTURE_URN,
                          DataStructureVersionStatus.DRAFT,
                          DataStructureStatus.AVAILABLE))));

      assertThatThrownBy(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .satisfies(thrown -> assertThat(blockedPipelinesOf(thrown)).containsExactly(pipelineId));
    }

    @Test
    @DisplayName("a released version of a data structure still in draft blocks the flow")
    void aReleasedVersionOfADraftStructureBlocks() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(
              governedBy(
                  List.of(
                      version(
                          STRUCTURE_URN,
                          DataStructureVersionStatus.AVAILABLE,
                          DataStructureStatus.DRAFT))));

      assertThatThrownBy(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .satisfies(thrown -> assertThat(blockedPipelinesOf(thrown)).containsExactly(pipelineId));
    }

    @Test
    @DisplayName("one released record is enough, since a flow pins the artifact")
    void oneReleasedRecordIsEnough() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(
              governedBy(
                  List.of(
                      version(
                          STRUCTURE_URN,
                          DataStructureVersionStatus.DRAFT,
                          DataStructureStatus.DRAFT),
                      released(STRUCTURE_URN))));

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
                  assertThat(blockedPipelinesOf(thrown))
                      .as("one attempt names everything that needs repairing")
                      .containsExactly(pipelineId, otherPipeline));
    }
  }

  @Nested
  @DisplayName("withholding what the caller may not know")
  class Withholding {

    private List<ILoggingEvent> captureWarnings(Runnable validation) {
      Logger logger = (Logger) LoggerFactory.getLogger(PipelineClosureValidator.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      try {
        validation.run();
      } finally {
        logger.detachAppender(appender);
      }
      return appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    }

    @Test
    @DisplayName("a withheld artifact is named nowhere in the reply")
    void aWithheldArtifactIsNamedNowhereInTheReply() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(governedBy(List.of(released(STRUCTURE_URN))));
      denyEveryStructure();

      assertThat(blockedPipelinesOf(catchThrowable(() -> validator().validate(pipelines()))))
          .as("a CORE URN spells the model's name, so the reply names the pipeline only")
          .containsExactly(pipelineId);
    }

    @Test
    @DisplayName("a draft the caller may read is named, because they can act on it")
    void aReadableDraftIsNamed() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(
              governedBy(
                  List.of(
                      version(
                          STRUCTURE_URN,
                          DataStructureVersionStatus.DRAFT,
                          DataStructureStatus.AVAILABLE))));

      assertThat(blockedPipelinesOf(catchThrowable(() -> validator().validate(pipelines()))))
          .containsExactly(pipelineId);
    }

    @Test
    @DisplayName("many withheld artifacts of one flow collapse into a single entry")
    void manyWithheldArtifactsCollapseIntoOneEntry() {
      closureOf(
          PIPELINE_URN, Set.of(STRUCTURE_URN, ELEMENT_URN), Set.of(STRUCTURE_URN, ELEMENT_URN));

      assertThat(blockedPipelinesOf(catchThrowable(() -> validator().validate(pipelines()))))
          .as("entries that name no artifact are indistinguishable, so repeating them says nothing")
          .containsExactly(pipelineId);
    }

    @Test
    @DisplayName("an unreadable artifact is reported in the same terms as an unresolved one")
    void unreadableAndUnresolvedAreIndistinguishable() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of(STRUCTURE_URN));
      List<UUID> onUnresolved =
          blockedPipelinesOf(
              catchThrowable(
                  () -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN)))));

      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN), Set.of());
      when(governingVersions.governingAll(any()))
          .thenReturn(governedBy(List.of(released(STRUCTURE_URN))));
      denyEveryStructure();
      List<UUID> onUnreadable =
          blockedPipelinesOf(
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
      when(governingVersions.governingAll(any()))
          .thenReturn(
              governedBy(
                  List.of(
                      version(
                          STRUCTURE_URN,
                          DataStructureVersionStatus.DRAFT,
                          DataStructureStatus.DRAFT))));
      denyEveryStructure();

      assertThatThrownBy(() -> validator().validate(List.of(pipeline(pipelineId, PIPELINE_URN))))
          .satisfies(
              thrown ->
                  assertThat(blockedPipelinesOf(thrown))
                      .as("a draft reason would confirm the structure exists")
                      .containsExactly(pipelineId));
    }

    @Test
    @DisplayName("a draft data source is identified in the warning log")
    void draftDataSourceIsIdentifiedInTheWarningLog() {
      DataSource dataSource = dataSource(DataSourceStatus.DRAFT);
      Pipeline pipeline = pipeline(pipelineId, PIPELINE_URN);
      pipeline.setDataSources(Set.of(dataSource));
      closureOf(PIPELINE_URN, Set.of(), Set.of());

      List<ILoggingEvent> warnings =
          captureWarnings(
              () ->
                  assertThatThrownBy(() -> validator().validate(List.of(pipeline)))
                      .isInstanceOf(PipelineClosureValidationException.class));

      assertThat(warnings)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anyMatch(
              message ->
                  message.contains(dataSource.getId().toString())
                      && message.contains("still a draft"));
    }

    @Test
    @DisplayName("no artifact URN survives into the message, whatever blocked the flow")
    void noArtifactUrnReachesTheMessage() {
      closureOf(PIPELINE_URN, Set.of(STRUCTURE_URN, ELEMENT_URN), Set.of(ELEMENT_URN));
      when(governingVersions.governingAll(any()))
          .thenReturn(governedBy(List.of(released(STRUCTURE_URN))));
      denyEveryStructure();

      assertThat(catchThrowable(() -> validator().validate(pipelines())))
          .as("the message is what a caller reads, so a URN appended here undoes the withholding")
          .hasMessageNotContaining("urn:core:")
          .hasMessageNotContaining("Sensor")
          .hasMessageNotContaining("Address");
    }
  }
}

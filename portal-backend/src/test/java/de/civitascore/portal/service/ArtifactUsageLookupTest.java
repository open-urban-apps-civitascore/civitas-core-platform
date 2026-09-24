package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.ReferrerReleaseState;
import de.civitascore.portal.service.ArtifactUsageLookup.ArtifactUsage;
import de.civitascore.portal.service.ArtifactUsageLookup.ReferrerKind;
import de.civitascore.portal.service.ArtifactUsageLookup.ReleasedReferrer;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactUsageLookup")
class ArtifactUsageLookupTest {

  private static final String MODEL_URN =
      "urn:core:platform:civitas:element:common:Reading:8kq2n4p1vd:1.0.0";
  private static final String PIPELINE_URN =
      "urn:core:platform:civitas:pipeline:common:Flow:1aa2bb3cc4";
  private static final String OTHER_PIPELINE_URN =
      "urn:core:platform:civitas:pipeline:common:Other:5dd6ee7ff8";
  private static final String SINK_URN =
      "urn:core:platform:civitas:datasink:common:Store:4rrb1hifsm";
  private static final String DATA_SET_URN =
      "urn:core:platform:civitas:dataset:common:Readings:7pp2kd9sle";
  private static final String MAPPING_URN =
      "urn:core:platform:civitas:mapping:common:Map:5tt3mnq2wa";
  private static final String ELEMENT_URN =
      "urn:core:platform:civitas:element:common:Station:9zz8yy7xx6";
  private static final String STRUCTURE_URN =
      "urn:core:platform:civitas:datastructure:common:Station:9zz8yy7xx6";

  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataStructureRepository dataStructureRepository;

  @InjectMocks private ArtifactUsageLookup lookup;

  @BeforeEach
  void stubUrnParsing() {
    lenient()
        .when(modelRegistryGateway.artifactType(anyString()))
        .thenAnswer(inv -> inv.<String>getArgument(0).split(":")[4]);
  }

  private static DataSource dataSource(DataSourceStatus status) {
    DataSource dataSource = new DataSource();
    dataSource.setId(UUID.randomUUID());
    dataSource.setDataSourceStatus(status);
    return dataSource;
  }

  private static DataStructureVersion version(
      DataStructureStatus structureStatus, DataStructureVersionStatus versionStatus) {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    structure.setDataStructureStatus(structureStatus);
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setModelUrn(MODEL_URN);
    version.setDataStructureVersionStatus(versionStatus);
    version.setDataStructure(structure);
    structure.setDataStructureVersions(Set.of(version));
    return version;
  }

  private static DataStructureVersion releasedVersion() {
    return version(DataStructureStatus.AVAILABLE, DataStructureVersionStatus.AVAILABLE);
  }

  @Nested
  @DisplayName("Data source")
  class DataSourceUsage {

    @Test
    @DisplayName("A DRAFT data source a pipeline references is in use and classifies no referrer")
    void draftInUse_classifiesNoReferrer() {
      DataSource dataSource = dataSource(DataSourceStatus.DRAFT);
      when(pipelineRepository.existsByDataSourcesId(dataSource.getId())).thenReturn(true);

      ArtifactUsage usage = lookup.of(dataSource);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      verify(pipelineRepository, never()).findIdsByDataSourceIdWithReleasedDataSet(any());
    }

    @Test
    @DisplayName(
        "An AVAILABLE data source a pipeline of an AVAILABLE dataset uses is released-used")
    void availableUsedByReleasedDataset_isInUseByReleased() {
      DataSource dataSource = dataSource(DataSourceStatus.AVAILABLE);
      UUID pipelineId = UUID.randomUUID();
      when(pipelineRepository.existsByDataSourcesId(dataSource.getId())).thenReturn(true);
      when(pipelineRepository.findIdsByDataSourceIdWithReleasedDataSet(dataSource.getId()))
          .thenReturn(List.of(pipelineId));

      ArtifactUsage usage = lookup.of(dataSource);

      assertThat(usage.inUseByReleased()).isTrue();
      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.PIPELINE, pipelineId.toString()));
    }

    @Test
    @DisplayName("An AVAILABLE data source only draft datasets use is in use but not released-used")
    void availableUsedByDraftsOnly_isNotInUseByReleased() {
      DataSource dataSource = dataSource(DataSourceStatus.AVAILABLE);
      when(pipelineRepository.existsByDataSourcesId(dataSource.getId())).thenReturn(true);
      when(pipelineRepository.findIdsByDataSourceIdWithReleasedDataSet(dataSource.getId()))
          .thenReturn(List.of());

      ArtifactUsage usage = lookup.of(dataSource);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("An AVAILABLE data source nothing references classifies no referrer")
    void availableNotInUse_classifiesNoReferrer() {
      DataSource dataSource = dataSource(DataSourceStatus.AVAILABLE);

      ArtifactUsage usage = lookup.of(dataSource);

      assertThat(usage.inUse()).isFalse();
      verify(pipelineRepository, never()).findIdsByDataSourceIdWithReleasedDataSet(any());
    }
  }

  @Nested
  @DisplayName("Data structure version")
  class VersionUsage {

    @Test
    @DisplayName("A DRAFT version a data source pins is in use and asks the registry nothing")
    void draftPinned_asksNoRegistry() {
      DataStructureVersion version =
          version(DataStructureStatus.AVAILABLE, DataStructureVersionStatus.DRAFT);
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(version.getId())))
          .thenReturn(true);

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      verifyNoInteractions(modelRegistryGateway);
    }

    @Test
    @DisplayName("An AVAILABLE version of a DRAFT structure classifies no referrer")
    void releasedVersionOfDraftStructure_classifiesNoReferrer() {
      DataStructureVersion version =
          version(DataStructureStatus.DRAFT, DataStructureVersionStatus.AVAILABLE);
      when(modelRegistryGateway.isReferenced(MODEL_URN)).thenReturn(true);

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      verify(modelRegistryGateway, never()).referencesTo(any());
    }

    @Test
    @DisplayName("A version an AVAILABLE data source pins is released-used")
    void pinnedByAvailableDataSource_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      UUID dataSourceId = UUID.randomUUID();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of());
      when(dataSourceRepository.findIdsByDataStructureVersionIdInAndStatus(
              Set.of(version.getId()), DataSourceStatus.AVAILABLE))
          .thenReturn(List.of(dataSourceId));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.DATA_SOURCE, dataSourceId.toString()));
    }

    @Test
    @DisplayName("A version only a DRAFT data source pins is in use but not released-used")
    void pinnedByDraftDataSourceOnly_isNotInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of());
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(version.getId())))
          .thenReturn(true);

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("A version a sink of a READY dataset references is not released-used")
    void referencedBySinkOfReadyDataset_isNotInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
      when(dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(Set.of(SINK_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(SINK_URN, false)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("A version a sink of an AVAILABLE dataset references is released-used")
    void referencedBySinkOfAvailableDataset_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
      when(dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(Set.of(SINK_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(SINK_URN, true)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.DATA_SINK, SINK_URN));
    }

    @Test
    @DisplayName("A mapping counts as released when a pipeline of an AVAILABLE dataset uses it")
    void mappingUsedByReleasedPipeline_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(MAPPING_URN));
      when(modelRegistryGateway.referencesTo(MAPPING_URN)).thenReturn(List.of(PIPELINE_URN));
      when(pipelineRepository.findReleaseStatesByModelLogicalUrnIn(Set.of(PIPELINE_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(PIPELINE_URN, true)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.MAPPING, MAPPING_URN));
    }

    @Test
    @DisplayName("A mapping counts as released when a released dataset references it")
    void mappingUsedByReleasedDataSet_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(MAPPING_URN));
      when(modelRegistryGateway.referencesTo(MAPPING_URN)).thenReturn(List.of(DATA_SET_URN));
      when(dataSetRepository.findReleaseStatesByManifestLogicalUrnIn(Set.of(DATA_SET_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(DATA_SET_URN, true)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.MAPPING, MAPPING_URN));
    }

    @Test
    @DisplayName("A mapping only a pipeline of a DRAFT dataset uses is not released")
    void mappingUsedByDraftPipeline_isNotInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(MAPPING_URN));
      when(modelRegistryGateway.referencesTo(MAPPING_URN)).thenReturn(List.of(PIPELINE_URN));
      when(pipelineRepository.findReleaseStatesByModelLogicalUrnIn(Set.of(PIPELINE_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(PIPELINE_URN, false)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("An element of an AVAILABLE structure counts as released through its structure")
    void elementOfReleasedStructure_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(ELEMENT_URN));
      when(modelRegistryGateway.hostModelUrnsOf(ELEMENT_URN))
          .thenReturn(Set.of(ELEMENT_URN, STRUCTURE_URN));
      when(dataStructureRepository.findReleaseStatesByModelLogicalUrnIn(
              Set.of(ELEMENT_URN, STRUCTURE_URN), DataStructureStatus.AVAILABLE))
          .thenReturn(List.of(new ReferrerReleaseState(STRUCTURE_URN, true)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.DATA_STRUCTURE, ELEMENT_URN));
    }

    // Without this a DRAFT host found under one candidate URN would still block the unrelease.
    @Test
    @DisplayName("An element whose only host row is DRAFT is not released")
    void elementOfDraftStructure_isNotReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(ELEMENT_URN));
      when(modelRegistryGateway.hostModelUrnsOf(ELEMENT_URN))
          .thenReturn(Set.of(ELEMENT_URN, STRUCTURE_URN));
      when(dataStructureRepository.findReleaseStatesByModelLogicalUrnIn(
              Set.of(ELEMENT_URN, STRUCTURE_URN), DataStructureStatus.AVAILABLE))
          .thenReturn(List.of(new ReferrerReleaseState(ELEMENT_URN, false)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
    }

    // Without this the unrelease would pass for a referrer the platform cannot judge.
    @Test
    @DisplayName("A referrer with no platform record counts as released")
    void referrerWithoutRow_countsAsReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(PIPELINE_URN));
      when(pipelineRepository.findReleaseStatesByModelLogicalUrnIn(Set.of(PIPELINE_URN)))
          .thenReturn(List.of());

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.UNKNOWN, PIPELINE_URN));
    }

    @Test
    @DisplayName("Referrers of one kind are judged in one database query")
    void referrersOfOneKind_useOneQuery() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.referencesTo(MODEL_URN))
          .thenReturn(List.of(PIPELINE_URN, OTHER_PIPELINE_URN));
      when(pipelineRepository.findReleaseStatesByModelLogicalUrnIn(any()))
          .thenReturn(
              List.of(
                  new ReferrerReleaseState(PIPELINE_URN, false),
                  new ReferrerReleaseState(OTHER_PIPELINE_URN, true)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.PIPELINE, OTHER_PIPELINE_URN));
      verify(pipelineRepository, times(1)).findReleaseStatesByModelLogicalUrnIn(any());
    }
  }

  @Nested
  @DisplayName("Data structure")
  class StructureUsage {

    @Test
    @DisplayName("A DRAFT structure is answered without classifying a referrer")
    void draftStructure_classifiesNoReferrer() {
      DataStructureVersion version =
          version(DataStructureStatus.DRAFT, DataStructureVersionStatus.DRAFT);
      when(modelRegistryGateway.isReferenced(MODEL_URN)).thenReturn(true);

      ArtifactUsage usage = lookup.of(version.getDataStructure());

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      verify(modelRegistryGateway, never()).referencesTo(any());
    }

    @Test
    @DisplayName("A structure is released-used when a released entity references one version")
    void releasedReferrerOfOneVersion_isInUseByReleased() {
      DataStructure structure = new DataStructure();
      structure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      DataStructureVersion released = new DataStructureVersion();
      released.setId(UUID.randomUUID());
      released.setModelUrn(MODEL_URN);
      released.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      released.setDataStructure(structure);
      DataStructureVersion draft = new DataStructureVersion();
      draft.setId(UUID.randomUUID());
      draft.setModelUrn("urn:core:platform:civitas:element:common:Reading:8kq2n4p1vd:2.0.0");
      draft.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      draft.setDataStructure(structure);
      structure.setDataStructureVersions(Set.of(released, draft));

      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
      when(dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(Set.of(SINK_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(SINK_URN, true)));

      ArtifactUsage usage = lookup.of(structure);

      assertThat(usage.inUseByReleased()).isTrue();
      verify(modelRegistryGateway, never()).referencesTo(draft.getModelUrn());
    }

    @Test
    @DisplayName("Each version is answered by its own referrers")
    void ofEachVersion_answersEachVersionSeparately() {
      DataStructure structure = new DataStructure();
      structure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      DataStructureVersion released = new DataStructureVersion();
      released.setId(UUID.randomUUID());
      released.setModelUrn(MODEL_URN);
      released.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      released.setDataStructure(structure);
      DataStructureVersion draft = new DataStructureVersion();
      draft.setId(UUID.randomUUID());
      draft.setModelUrn("urn:core:platform:civitas:element:common:Reading:8kq2n4p1vd:2.0.0");
      draft.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      draft.setDataStructure(structure);
      DataStructureVersion unused = new DataStructureVersion();
      unused.setId(UUID.randomUUID());
      unused.setModelUrn("urn:core:platform:civitas:element:common:Reading:8kq2n4p1vd:3.0.0");
      unused.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      unused.setDataStructure(structure);
      structure.setDataStructureVersions(Set.of(released, draft, unused));

      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
      when(dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(Set.of(SINK_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(SINK_URN, true)));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(draft.getId())))
          .thenReturn(true);

      Map<UUID, ArtifactUsage> usageByVersion = lookup.ofEachVersion(structure);

      assertThat(usageByVersion.get(released.getId()).inUseByReleased()).isTrue();
      assertThat(usageByVersion.get(draft.getId()).inUse()).isTrue();
      assertThat(usageByVersion.get(draft.getId()).inUseByReleased()).isFalse();
      assertThat(usageByVersion.get(unused.getId()).inUse()).isFalse();
    }
  }

  @Nested
  @DisplayName("Unrelease refusal")
  class UnreleaseRefusal {

    @Test
    @DisplayName("Passes when only drafts reference the artifact")
    void noReleasedReferrer_passes() {
      ArtifactUsage usage = new ArtifactUsage(true, List.of());

      assertThatCode(() -> usage.requireNoReleasedReferrer("DataStructure", UUID.randomUUID()))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Refuses naming the first released referrer and blocks on all of them")
    void releasedReferrer_refuses() {
      UUID id = UUID.randomUUID();
      ArtifactUsage usage =
          new ArtifactUsage(
              true,
              List.of(
                  new ReleasedReferrer(ReferrerKind.DATA_SINK, SINK_URN),
                  new ReleasedReferrer(ReferrerKind.PIPELINE, PIPELINE_URN)));

      assertThatThrownBy(() -> usage.requireNoReleasedReferrer("DataStructure", id))
          .isInstanceOfSatisfying(
              ResourceInUseException.class,
              refusal -> {
                assertThat(refusal.getResourceType()).isEqualTo("DataStructure");
                assertThat(refusal.getResourceId()).isEqualTo(id);
                assertThat(refusal.getBlockedBy()).containsExactly(SINK_URN, PIPELINE_URN);
              })
          .hasMessage(
              "Cannot unrelease DataStructure because it is referenced by a Data sink of a"
                  + " released Dataset.");
    }
  }
}

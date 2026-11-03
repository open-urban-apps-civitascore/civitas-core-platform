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
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.ReferrerReleaseState;
import de.civitascore.portal.service.ArtifactUsageLookup.ArtifactUsage;
import de.civitascore.portal.service.ArtifactUsageLookup.DataStructureUsage;
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
      "urn:core:platform:civitas:element:common:e5eaeb37-4c14-8bb9-903b-4f86897bfcea:1.0.0";
  private static final String PIPELINE_URN =
      "urn:core:platform:civitas:pipeline:common:d49e7eb0-f7c4-81d0-bf09-3a2197886614";
  private static final String OTHER_PIPELINE_URN =
      "urn:core:platform:civitas:pipeline:common:f94c417e-54f2-8eec-83d3-5ca534e71438";
  private static final String SINK_URN =
      "urn:core:platform:civitas:datasink:common:762a05c3-4783-8446-b3cb-8240eee1227a";
  private static final String DATA_SET_URN =
      "urn:core:platform:civitas:dataset:common:4d4ccbcb-dd32-8b43-ac2d-162aeefb3ef9";
  private static final String MAPPING_URN =
      "urn:core:platform:civitas:mapping:common:b2ecab01-2f5a-8963-ac6a-2f86b3c8205a";
  private static final String ELEMENT_URN =
      "urn:core:platform:civitas:element:common:f97a1a6e-b512-8210-b734-315d0c6a75cd";
  private static final String STRUCTURE_URN =
      "urn:core:platform:civitas:datastructure:common:f97a1a6e-b512-8210-b734-315d0c6a75cd";

  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private PipelineRepository pipelineRepository;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataStructureRepository dataStructureRepository;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;

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

  private static DataStructureVersion version(DataStructureVersionStatus versionStatus) {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setModelUrn(MODEL_URN);
    version.setDataStructureVersionStatus(versionStatus);
    version.setDataStructure(structure);
    structure.setDataStructureVersions(Set.of(version));
    return version;
  }

  private static DataStructureVersion releasedVersion() {
    return version(DataStructureVersionStatus.AVAILABLE);
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
    @DisplayName("A DRAFT version a data source pins blocks on the data source and the registry")
    void draftPinned_blocksOnDataSourceAndRegistryReferrers() {
      DataStructureVersion version = version(DataStructureVersionStatus.DRAFT);
      UUID dataSourceId = UUID.randomUUID();
      when(dataSourceRepository.findIdsByDataStructureVersionId(version.getId()))
          .thenReturn(List.of(dataSourceId));
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      assertThat(usage.referrers()).containsExactly(SINK_URN, "DataSource " + dataSourceId);
    }

    @Test
    @DisplayName("A version an AVAILABLE data source pins is released-used")
    void pinnedByAvailableDataSource_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      UUID dataSourceId = UUID.randomUUID();
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of());
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
      UUID dataSourceId = UUID.randomUUID();
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of());
      when(dataSourceRepository.findIdsByDataStructureVersionId(version.getId()))
          .thenReturn(List.of(dataSourceId));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      assertThat(usage.referrers()).containsExactly("DataSource " + dataSourceId);
    }

    @Test
    @DisplayName("A version a sink of a READY dataset references is not released-used")
    void referencedBySinkOfReadyDataset_isNotInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
      when(dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(Set.of(SINK_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(SINK_URN, false)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      assertThat(usage.referrers()).containsExactly(SINK_URN);
    }

    @Test
    @DisplayName("A version a sink of an AVAILABLE dataset references is released-used")
    void referencedBySinkOfAvailableDataset_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
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
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(MAPPING_URN));
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
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(MAPPING_URN));
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
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(MAPPING_URN));
      when(modelRegistryGateway.referencesTo(MAPPING_URN)).thenReturn(List.of(PIPELINE_URN));
      when(pipelineRepository.findReleaseStatesByModelLogicalUrnIn(Set.of(PIPELINE_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(PIPELINE_URN, false)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("An element of a structure with a released version counts as released through it")
    void elementOfReleasedStructure_isInUseByReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(ELEMENT_URN));
      when(modelRegistryGateway.hostModelUrnsOf(ELEMENT_URN))
          .thenReturn(Set.of(ELEMENT_URN, STRUCTURE_URN));
      when(dataStructureRepository.findReleaseStatesByModelLogicalUrnIn(
              Set.of(ELEMENT_URN, STRUCTURE_URN), DataStructureVersionStatus.AVAILABLE))
          .thenReturn(List.of(new ReferrerReleaseState(STRUCTURE_URN, true)));

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.releasedReferrers())
          .containsExactly(new ReleasedReferrer(ReferrerKind.DATA_STRUCTURE, ELEMENT_URN));
    }

    // Without this an unreleased host found under one candidate URN still blocks the unrelease.
    @Test
    @DisplayName("An element whose only host row has no released version is not released")
    void elementOfUnreleasedStructure_isNotReleased() {
      DataStructureVersion version = releasedVersion();
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(ELEMENT_URN));
      when(modelRegistryGateway.hostModelUrnsOf(ELEMENT_URN))
          .thenReturn(Set.of(ELEMENT_URN, STRUCTURE_URN));
      when(dataStructureRepository.findReleaseStatesByModelLogicalUrnIn(
              Set.of(ELEMENT_URN, STRUCTURE_URN), DataStructureVersionStatus.AVAILABLE))
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
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(PIPELINE_URN));
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
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN))
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
    @DisplayName(
        "A structure without a released version is answered without classifying a referrer")
    void structureWithoutReleasedVersion_classifiesNoReferrer() {
      DataStructureVersion version = version(DataStructureVersionStatus.DRAFT);
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));

      ArtifactUsage usage = lookup.of(version.getDataStructure());

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      assertThat(usage.referrers()).containsExactly(SINK_URN);
      verifyNoInteractions(dataSinkRepository);
    }

    @Test
    @DisplayName("A structure is released-used when a released entity references one version")
    void releasedReferrerOfOneVersion_isInUseByReleased() {
      DataStructure structure = new DataStructure();
      DataStructureVersion released = new DataStructureVersion();
      released.setId(UUID.randomUUID());
      released.setModelUrn(MODEL_URN);
      released.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      released.setDataStructure(structure);
      DataStructureVersion draft = new DataStructureVersion();
      draft.setId(UUID.randomUUID());
      draft.setModelUrn(
          "urn:core:platform:civitas:element:common:e5eaeb37-4c14-8bb9-903b-4f86897bfcea:2.0.0");
      draft.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      draft.setDataStructure(structure);
      structure.setDataStructureVersions(Set.of(released, draft));

      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
      when(modelRegistryGateway.versionReferencesTo(draft.getModelUrn())).thenReturn(List.of());
      when(dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(Set.of(SINK_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(SINK_URN, true)));

      ArtifactUsage usage = lookup.of(structure);

      assertThat(usage.inUseByReleased()).isTrue();
    }

    @Test
    @DisplayName("Each version is answered by its own referrers")
    void ofStructureAndVersions_answersEachVersionSeparately() {
      DataStructure structure = new DataStructure();
      DataStructureVersion released = new DataStructureVersion();
      released.setId(UUID.randomUUID());
      released.setModelUrn(MODEL_URN);
      released.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      released.setDataStructure(structure);
      DataStructureVersion draft = new DataStructureVersion();
      draft.setId(UUID.randomUUID());
      draft.setModelUrn(
          "urn:core:platform:civitas:element:common:e5eaeb37-4c14-8bb9-903b-4f86897bfcea:2.0.0");
      draft.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      draft.setDataStructure(structure);
      DataStructureVersion unused = new DataStructureVersion();
      unused.setId(UUID.randomUUID());
      unused.setModelUrn(
          "urn:core:platform:civitas:element:common:e5eaeb37-4c14-8bb9-903b-4f86897bfcea:3.0.0");
      unused.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      unused.setDataStructure(structure);
      structure.setDataStructureVersions(Set.of(released, draft, unused));

      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));
      when(modelRegistryGateway.versionReferencesTo(draft.getModelUrn())).thenReturn(List.of());
      when(modelRegistryGateway.versionReferencesTo(unused.getModelUrn())).thenReturn(List.of());
      when(dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(Set.of(SINK_URN)))
          .thenReturn(List.of(new ReferrerReleaseState(SINK_URN, true)));
      when(dataSourceRepository.findIdsByDataStructureVersionId(draft.getId()))
          .thenReturn(List.of(UUID.randomUUID()));
      when(dataSourceRepository.findIdsByDataStructureVersionId(released.getId()))
          .thenReturn(List.of());
      when(dataSourceRepository.findIdsByDataStructureVersionId(unused.getId()))
          .thenReturn(List.of());

      Map<UUID, ArtifactUsage> usageByVersion = lookup.ofStructureAndVersions(structure).versions();

      assertThat(usageByVersion.get(released.getId()).inUseByReleased()).isTrue();
      assertThat(usageByVersion.get(draft.getId()).inUse()).isTrue();
      assertThat(usageByVersion.get(draft.getId()).inUseByReleased()).isFalse();
      assertThat(usageByVersion.get(unused.getId()).inUse()).isFalse();
    }
  }

  @Nested
  @DisplayName("Model line shared with another version")
  class SharedModelLine {

    private static final String MODEL_LOGICAL_URN =
        "urn:core:platform:civitas:element:common:e5eaeb37-4c14-8bb9-903b-4f86897bfcea";

    private final DataStructureVersion version = version(DataStructureVersionStatus.DRAFT);

    private DataStructureVersion siblingPinnedTo(String pin) {
      DataStructureVersion sibling = new DataStructureVersion();
      sibling.setId(UUID.randomUUID());
      sibling.setModelUrn(pin);
      sibling.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      sibling.setDataStructure(version.getDataStructure());
      version.getDataStructure().setDataStructureVersions(Set.of(version, sibling));
      lenient()
          .when(
              dataStructureVersionRepository.findAllByDataStructure_IdIn(
                  List.of(version.getDataStructure().getId())))
          .thenReturn(List.of(version, sibling));
      return sibling;
    }

    @Test
    @DisplayName("A version whose major another version also holds is in use by that version")
    void anotherVersionInTheSameMajor_holdsTheVersion() {
      siblingPinnedTo(MODEL_LOGICAL_URN + ":1.1.0");

      ArtifactUsage usage = lookup.of(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
      assertThat(usage.referrers()).containsExactly(MODEL_LOGICAL_URN + ":1.1.0");
    }

    @Test
    @DisplayName("A version in another major does not hold the version")
    void anotherVersionInAnotherMajor_doesNotHoldTheVersion() {
      siblingPinnedTo(MODEL_LOGICAL_URN + ":2.0.0");

      assertThat(lookup.of(version).inUse()).isFalse();
    }

    @Test
    @DisplayName("The delete refusal says that only the data structure delete removes the versions")
    void requireNotInUse_whenOnlyTheLineIsShared_namesTheDataStructureDelete() {
      siblingPinnedTo(MODEL_LOGICAL_URN + ":1.1.0");

      assertThatThrownBy(() -> lookup.requireNotInUse(version, "DataStructureVersion"))
          .isInstanceOfSatisfying(
              ResourceInUseException.class,
              refusal ->
                  assertThat(refusal.getBlockedBy()).containsExactly(MODEL_LOGICAL_URN + ":1.1.0"))
          .hasMessage(
              "Cannot delete DataStructureVersion because another version of its data structure"
                  + " shares its model; only deleting the whole data structure deletes such"
                  + " versions.");
    }

    @Test
    @DisplayName("The delete refusal of a version used from outside blocks on everything")
    void requireNotInUse_whenAlsoReferenced_blocksOnAllReferrers() {
      siblingPinnedTo(MODEL_LOGICAL_URN + ":1.1.0");
      when(modelRegistryGateway.versionReferencesTo(MODEL_URN)).thenReturn(List.of(SINK_URN));

      assertThatThrownBy(() -> lookup.requireNotInUse(version, "DataStructureVersion"))
          .isInstanceOfSatisfying(
              ResourceInUseException.class,
              refusal ->
                  assertThat(refusal.getBlockedBy())
                      .containsExactly(SINK_URN, MODEL_LOGICAL_URN + ":1.1.0"))
          .hasMessage("Cannot delete DataStructureVersion because it is in use.");
    }

    @Test
    @DisplayName("Versions that share a line hold each other but not their data structure")
    void versionsSharingALine_holdEachOtherButNotTheirDataStructure() {
      DataStructureVersion sibling = siblingPinnedTo(MODEL_LOGICAL_URN + ":1.1.0");

      DataStructureUsage usage = lookup.ofStructureAndVersions(version.getDataStructure());

      assertThat(usage.structure().inUse()).isFalse();
      assertThat(usage.versions().get(version.getId()).referrers())
          .containsExactly(MODEL_LOGICAL_URN + ":1.1.0");
      assertThat(usage.versions().get(sibling.getId()).referrers()).containsExactly(MODEL_URN);
      assertThat(lookup.of(version.getDataStructure()).inUse()).isFalse();
      verify(dataStructureVersionRepository, never()).findAllByDataStructure_IdIn(any());
    }
  }

  @Nested
  @DisplayName("Unrelease refusal")
  class UnreleaseRefusal {

    @Test
    @DisplayName("Passes when only drafts reference the artifact")
    void noReleasedReferrer_passes() {
      ArtifactUsage usage = new ArtifactUsage(true, List.of(), List.of());

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
              List.of(),
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

  @Nested
  @DisplayName("Delete guard")
  class DeleteGuard {

    @Test
    @DisplayName("An artifact anything uses refuses its delete and blocks on its referrers")
    void requireNotInUse_whenInUse_shouldThrow() {
      ArtifactUsage usage = new ArtifactUsage(true, List.of(SINK_URN, MAPPING_URN), List.of());
      UUID id = UUID.randomUUID();

      assertThatThrownBy(() -> usage.requireNotInUse("DataStructureVersion", id))
          .isInstanceOfSatisfying(
              ResourceInUseException.class,
              refusal -> assertThat(refusal.getBlockedBy()).containsExactly(SINK_URN, MAPPING_URN))
          .hasMessageContaining("DataStructureVersion");
    }

    @Test
    @DisplayName("An artifact nothing uses may be deleted")
    void requireNotInUse_whenUnused_shouldPass() {
      ArtifactUsage usage = new ArtifactUsage(false, List.of(), List.of());

      assertThatCode(() -> usage.requireNotInUse("DataStructureVersion", UUID.randomUUID()))
          .doesNotThrowAnyException();
    }
  }
}

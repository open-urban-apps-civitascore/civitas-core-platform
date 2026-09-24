package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataStructureService Unit Tests")
class DataStructureServiceTest {

  private static final String MODEL_URN =
      "urn:core:platform:civitas:element:common:Reading:8kq2n4p1vd:1.0.0";
  private static final String BLOCKER_URN =
      "urn:core:platform:civitas:datasink:common:Store:4rrb1hifsm";

  @Mock private DataStructureRepository dataStructureRepository;
  @Mock private DataStructureMapper dataStructureMapper;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private ArtifactUsageLookup artifactUsageLookup;

  @InjectMocks private DataStructureService dataStructureService;

  @Nested
  @DisplayName("Unrelease inUse guard")
  class UnreleaseInUseTests {

    private DataStructure releasedStructure(UUID dsId) {
      DataStructureVersion version = new DataStructureVersion();
      version.setId(UUID.randomUUID());
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      ds.setDataStructureVersions(Set.of(version));
      return ds;
    }

    @Test
    @DisplayName("Should block unrelease when a released entity references a version")
    void shouldBlockUnreleaseWhenInUseByReleased() {
      UUID dsId = UUID.randomUUID();
      DataStructure ds = releasedStructure(dsId);

      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(artifactUsageLookup.of(ds))
          .thenReturn(
              new ArtifactUsageLookup.ArtifactUsage(
                  true,
                  List.of(
                      new ArtifactUsageLookup.ReleasedReferrer(
                          ArtifactUsageLookup.ReferrerKind.DATA_SINK, BLOCKER_URN))));

      assertThatThrownBy(() -> dataStructureService.unrelease(dsId))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("a Data sink of a released Dataset")
          .satisfies(
              ex ->
                  assertThat(((ResourceInUseException) ex).getBlockedBy())
                      .containsExactly(BLOCKER_URN));
    }

    @Test
    @DisplayName("Should allow unrelease when only drafts reference a version")
    void shouldAllowUnreleaseWhenInUseByDraftsOnly() {
      UUID dsId = UUID.randomUUID();
      DataStructure ds = releasedStructure(dsId);

      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(artifactUsageLookup.of(ds))
          .thenReturn(new ArtifactUsageLookup.ArtifactUsage(true, List.of()));
      when(dataStructureRepository.save(ds)).thenReturn(ds);

      dataStructureService.unrelease(dsId);

      verify(dataStructureRepository).save(ds);
    }
  }

  @Nested
  @DisplayName("Delete inUse guard")
  class DeleteInUseTests {

    @Test
    @DisplayName("Should block delete when any version is in use")
    void shouldBlockDeleteWhenVersionInUse() {
      UUID dsId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureRepository.existsById(dsId)).thenReturn(true);
      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(versionId)))
          .thenReturn(true);

      assertThatThrownBy(() -> dataStructureService.deleteById(dsId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should block delete when the registry still holds a reference")
    void shouldBlockDeleteWhenVersionStillReferenced() {
      UUID dsId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);
      version.setModelUrn(MODEL_URN);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureRepository.existsById(dsId)).thenReturn(true);
      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(versionId)))
          .thenReturn(false);
      when(modelRegistryGateway.referencesTo(MODEL_URN)).thenReturn(List.of(BLOCKER_URN));

      assertThatThrownBy(() -> dataStructureService.deleteById(dsId))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should allow delete when no version is in use")
    void shouldAllowDeleteWhenNoVersionInUse() {
      UUID dsId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();

      DataStructureVersion version = new DataStructureVersion();
      version.setId(versionId);

      DataStructure ds = new DataStructure();
      ds.setId(dsId);
      ds.setDataStructureVersions(Set.of(version));

      when(dataStructureRepository.existsById(dsId)).thenReturn(true);
      when(dataStructureRepository.findById(dsId)).thenReturn(Optional.of(ds));
      when(dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(versionId)))
          .thenReturn(false);

      dataStructureService.deleteById(dsId);

      verify(dataStructureRepository).deleteById(dsId);
    }
  }

  @Nested
  @DisplayName("Version assignment on update")
  class VersionAssignmentTests {

    // Mirrors Spring Boot's default, which lets clients that still send the field keep working.
    private final JsonMapper jsonMapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private DataStructure structureWithOneVersion(DataStructureStatus status) {
      DataStructureVersion version = new DataStructureVersion();
      version.setId(UUID.randomUUID());

      DataStructure ds = new DataStructure();
      ds.setId(UUID.randomUUID());
      ds.setDataStructureStatus(status);
      ds.setDataStructureVersions(Set.of(version));
      return ds;
    }

    private DataStructureInputDTO legacyInputWithEmptyVersionIds() {
      return jsonMapper.readValue(
          "{\"name\":\"Struct\",\"dataStructureVersionIds\":[]}", DataStructureInputDTO.class);
    }

    @Test
    @DisplayName("Draft update keeps the versions a client lists no longer")
    void draftUpdateKeepsVersions() {
      DataStructure ds = structureWithOneVersion(DataStructureStatus.DRAFT);
      when(dataStructureRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
      when(dataStructureRepository.save(any())).thenAnswer(call -> call.getArgument(0));

      DataStructure updated =
          dataStructureService.update(ds.getId(), legacyInputWithEmptyVersionIds());

      assertThat(updated.getDataStructureVersions()).hasSize(1);
    }

    @Test
    @DisplayName("Released metadata update keeps the versions a client lists no longer")
    void releasedMetaUpdateKeepsVersions() {
      DataStructure ds = structureWithOneVersion(DataStructureStatus.AVAILABLE);
      when(dataStructureRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
      when(dataStructureRepository.save(any())).thenAnswer(call -> call.getArgument(0));

      DataStructure updated =
          dataStructureService.updateReleasedMeta(ds.getId(), legacyInputWithEmptyVersionIds());

      assertThat(updated.getDataStructureVersions()).hasSize(1);
    }
  }
}

package de.civitascore.portal.service.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataStructureClosureCheck")
class DataStructureClosureCheckTest {

  private static final String STRUCTURE_URN =
      "urn:core:platform:civitas:datastructure:common:Sensor:bbbbbbbbbb:1.0.0";

  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  private DataStructureClosureCheck check;
  private final UUID pipelineId = UUID.randomUUID();

  private DataStructureClosureCheck check() {
    if (check == null) {
      check =
          new DataStructureClosureCheck(
              dataStructureVersionRepository, scopeAccessAuthorizer, modelRegistryGateway);
    }
    return check;
  }

  private static DataStructureVersion version(
      UUID structureId,
      DataStructureVersionStatus versionStatus,
      DataStructureStatus parentStatus) {
    DataStructure parent = new DataStructure();
    parent.setId(structureId);
    parent.setDataStructureStatus(parentStatus);
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    version.setDataStructure(parent);
    version.setDataStructureVersionStatus(versionStatus);
    return version;
  }

  private DataStructureVersion released(UUID structureId) {
    return version(
        structureId, DataStructureVersionStatus.AVAILABLE, DataStructureStatus.AVAILABLE);
  }

  private List<ClosureNode> node(String urn) {
    return List.of(new ClosureNode(pipelineId, urn, "datastructure"));
  }

  private void modelCompiles() {
    lenient()
        .when(modelRegistryGateway.fetchModel(anyString()))
        .thenReturn(Optional.of(new ModelRegistryGateway.RegistryDocument(Map.of("a", 1), null)));
    lenient().when(modelRegistryGateway.validateSchema(any())).thenReturn(List.of());
  }

  @Test
  @DisplayName("answers for the datastructure artifact type")
  void answersForDataStructures() {
    assertThat(check().artifactType()).isEqualTo("datastructure");
  }

  @Test
  @DisplayName("passes a released, readable structure whose model compiles")
  void passesAReleasedStructure() {
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(List.of(released(UUID.randomUUID())));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());
    modelCompiles();

    assertThat(check().check(node(STRUCTURE_URN))).isEmpty();
  }

  @Test
  @DisplayName("reports a structure that is still a draft as not released")
  void reportsADraftStructure() {
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(
            List.of(
                version(
                    UUID.randomUUID(),
                    DataStructureVersionStatus.DRAFT,
                    DataStructureStatus.AVAILABLE)));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());

    assertThat(check().check(node(STRUCTURE_URN)))
        .containsExactly(ClosureFinding.notReleased(pipelineId, STRUCTURE_URN));
  }

  @Test
  @DisplayName("reports a released version of a draft structure as not released")
  void reportsAReleasedVersionOfADraftStructure() {
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(
            List.of(
                version(
                    UUID.randomUUID(),
                    DataStructureVersionStatus.AVAILABLE,
                    DataStructureStatus.DRAFT)));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());

    assertThat(check().check(node(STRUCTURE_URN)))
        .containsExactly(ClosureFinding.notReleased(pipelineId, STRUCTURE_URN));
  }

  @Test
  @DisplayName("reports a structure whose stored model no longer compiles as invalid")
  void reportsAnUncompilableModel() {
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(List.of(released(UUID.randomUUID())));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());
    when(modelRegistryGateway.fetchModel(STRUCTURE_URN))
        .thenReturn(Optional.of(new ModelRegistryGateway.RegistryDocument(Map.of("a", 1), null)));
    when(modelRegistryGateway.validateSchema(any())).thenReturn(List.of("not a schema"));

    assertThat(check().check(node(STRUCTURE_URN)))
        .containsExactly(
            ClosureFinding.invalid(pipelineId, STRUCTURE_URN, List.of("not a schema")));
  }

  @Test
  @DisplayName("reports a structure whose model has gone away as not available")
  void reportsAModelThatVanishedMidCheck() {
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(List.of(released(UUID.randomUUID())));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());
    when(modelRegistryGateway.fetchModel(STRUCTURE_URN)).thenReturn(Optional.empty());

    assertThat(check().check(node(STRUCTURE_URN)))
        .as("an unreadable model must not pass as one that compiled cleanly")
        .containsExactly(ClosureFinding.notAvailable(pipelineId, STRUCTURE_URN));
  }

  @Test
  @DisplayName("decides authorization once for every structure rather than once per structure")
  void authorizesInOneDecision() {
    String otherUrn = "urn:core:platform:civitas:datastructure:common:Other:eeeeeeeeee:1.0.0";
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(List.of(released(UUID.randomUUID())));
    when(dataStructureVersionRepository.findAllByModelUrn(otherUrn))
        .thenReturn(List.of(released(UUID.randomUUID())));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());
    modelCompiles();

    check()
        .check(
            List.of(
                new ClosureNode(pipelineId, STRUCTURE_URN, "datastructure"),
                new ClosureNode(pipelineId, otherUrn, "datastructure")));

    verify(scopeAccessAuthorizer).unauthorizedReferences(any(), any());
  }

  @Test
  @DisplayName("passes when one of several records pinning the artifact is released and readable")
  void oneReleasedRecordIsEnough() {
    UUID releasedStructure = UUID.randomUUID();
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(
            List.of(
                version(
                    UUID.randomUUID(), DataStructureVersionStatus.DRAFT, DataStructureStatus.DRAFT),
                released(releasedStructure)));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());
    modelCompiles();

    assertThat(check().check(node(STRUCTURE_URN)))
        .as("a flow pins the artifact, not one of the platform's records of it")
        .isEmpty();
  }

  @Test
  @DisplayName("reports the artifact as not released when no record pinning it is")
  void everyRecordDraftIsNotReleased() {
    when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
        .thenReturn(
            List.of(
                version(
                    UUID.randomUUID(),
                    DataStructureVersionStatus.DRAFT,
                    DataStructureStatus.AVAILABLE),
                version(
                    UUID.randomUUID(),
                    DataStructureVersionStatus.AVAILABLE,
                    DataStructureStatus.DRAFT)));
    when(scopeAccessAuthorizer.unauthorizedReferences(any(), any())).thenReturn(Set.of());

    assertThat(check().check(node(STRUCTURE_URN)))
        .containsExactly(ClosureFinding.notReleased(pipelineId, STRUCTURE_URN));
  }

  @Nested
  @DisplayName("withholding what the caller may not know")
  class Withholding {

    @Test
    @DisplayName("reports a structure the platform has no record of as not available")
    void reportsAnUnknownStructure() {
      when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN)).thenReturn(List.of());

      assertThat(check().check(node(STRUCTURE_URN)))
          .containsExactly(ClosureFinding.notAvailable(pipelineId, STRUCTURE_URN));
    }

    @Test
    @DisplayName("reports a structure the caller may not read as not available")
    void reportsAnUnauthorizedStructure() {
      UUID structureId = UUID.randomUUID();
      when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
          .thenReturn(List.of(released(structureId)));
      when(scopeAccessAuthorizer.unauthorizedReferences(
              ScopeType.DATASTRUCTURE, List.of(structureId)))
          .thenReturn(Set.of(structureId));

      assertThat(check().check(node(STRUCTURE_URN)))
          .containsExactly(ClosureFinding.notAvailable(pipelineId, STRUCTURE_URN));
    }

    @Test
    @DisplayName("an unreadable structure and one the platform does not know are indistinguishable")
    void unauthorizedAndUnknownAreIndistinguishable() {
      UUID structureId = UUID.randomUUID();
      DataStructureClosureCheck unknown =
          new DataStructureClosureCheck(
              dataStructureVersionRepository, scopeAccessAuthorizer, modelRegistryGateway);
      when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN)).thenReturn(List.of());
      List<ClosureFinding> onUnknown = unknown.check(node(STRUCTURE_URN));

      reset(dataStructureVersionRepository, scopeAccessAuthorizer);
      DataStructureClosureCheck unauthorized =
          new DataStructureClosureCheck(
              dataStructureVersionRepository, scopeAccessAuthorizer, modelRegistryGateway);
      when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
          .thenReturn(List.of(released(structureId)));
      when(scopeAccessAuthorizer.unauthorizedReferences(any(), any()))
          .thenReturn(Set.of(structureId));
      List<ClosureFinding> onUnauthorized = unauthorized.check(node(STRUCTURE_URN));

      assertThat(onUnauthorized)
          .as("the difference would be an existence oracle over structure URNs")
          .isEqualTo(onUnknown);
    }

    @Test
    @DisplayName(
        "says nothing of the lifecycle or the model of a structure the caller may not read")
    void withholdsDraftStateFromAnUnauthorizedCaller() {
      UUID structureId = UUID.randomUUID();
      when(dataStructureVersionRepository.findAllByModelUrn(STRUCTURE_URN))
          .thenReturn(
              List.of(
                  version(
                      structureId, DataStructureVersionStatus.DRAFT, DataStructureStatus.DRAFT)));
      when(scopeAccessAuthorizer.unauthorizedReferences(any(), any()))
          .thenReturn(Set.of(structureId));

      assertThat(check().check(node(STRUCTURE_URN)))
          .as("a draft reason would confirm the structure exists")
          .containsExactly(ClosureFinding.notAvailable(pipelineId, STRUCTURE_URN));
      verify(modelRegistryGateway, never()).fetchModel(anyString());
    }
  }
}

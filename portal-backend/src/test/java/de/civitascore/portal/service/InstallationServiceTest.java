package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link InstallationService}. The services of the platform and the registry are
 * mocks, so these tests do not show the rules of the platform. {@code
 * InstallationControllerIntegrationTest} shows them.
 */
@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

  private static final String PACKAGE_ID = "tree-cadastre";
  private static final String TITLE = "title";

  /** A Data structure URN of another instance, as a package from that instance carries it. */
  private static final String FOREIGN_URN =
      "urn:core:platform:civitas:datastructure:common:0b9e5b0e-6a3f-4c1e-9d2a-7f3c2e1a5b4d";

  private static final UUID STRUCTURE_ID = UUID.fromString("44444444-4444-4000-8000-000000000004");
  private static final String LOGICAL_URN =
      "urn:core:platform:civitas:datastructure:common:44444444-4444-4000-8000-000000000004";
  private static final String VERSIONED_URN = LOGICAL_URN + ":1.0.0";

  @Mock private InstallationRepository installationRepository;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  @InjectMocks private InstallationService installationService;

  @Captor private ArgumentCaptor<DataStructureInputDTO> structureCaptor;
  @Captor private ArgumentCaptor<DataStructureVersionInputDTO> versionCaptor;

  @Test
  void install_whenPackageIsInstalled_throwsUniqueConstraintViolation() {
    when(installationRepository.existsByPackageId(PACKAGE_ID)).thenReturn(true);

    assertThatThrownBy(() -> installationService.install(request(tree())))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("is already installed");

    verify(installationRepository, never()).saveAndFlush(any());
    verifyNoInteractions(dataStructureService, dataStructureVersionService, modelRegistryGateway);
  }

  @Test
  void install_whenUniqueIndexRefusesTheRecord_createsNothing() {
    // The check passes, because the other install has not committed yet. The index refuses the row.
    when(installationRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uq_installations_package"));

    assertThatThrownBy(() -> installationService.install(request(tree())))
        .isInstanceOf(DataIntegrityViolationException.class);

    verifyNoInteractions(dataStructureService, dataStructureVersionService, modelRegistryGateway);
  }

  @Test
  void install_whenSecondMemberHasNoDescription_changesNothing() {
    PackageMemberInputDTO park = member("park", "Park", null, Map.of(TITLE, "Park"));

    assertThatThrownBy(() -> installationService.install(request(tree(), park)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("needs a description");

    verify(installationRepository, never()).saveAndFlush(any());
    verifyNoInteractions(dataStructureService, dataStructureVersionService, modelRegistryGateway);
  }

  @Test
  void install_whenTitleIsLongerThanTheNameLimit_changesNothing() {
    String title = "T".repeat(InstalledArtifact.NAME_LENGTH + 1);
    PackageMemberInputDTO member = member(FOREIGN_URN, null, "Trees", Map.of(TITLE, title));

    assertThatThrownBy(() -> installationService.install(request(member)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("must not exceed");

    verify(installationRepository, never()).saveAndFlush(any());
    verifyNoInteractions(dataStructureService, dataStructureVersionService, modelRegistryGateway);
  }

  @Test
  void install_whenModelHasRootId_storesTheModelWithoutIt() {
    stubCreate();
    Map<String, Object> model = model();

    installationService.install(request(member(FOREIGN_URN, "Tree", "Trees", model)));

    verify(dataStructureVersionService).create(versionCaptor.capture());
    Map<String, Object> expected = new LinkedHashMap<>(model);
    expected.remove("$id");
    assertThat(versionCaptor.getValue().getModel()).isEqualTo(expected).doesNotContainKey("$id");
    assertThat(versionCaptor.getValue().getDataStructureId()).isEqualTo(STRUCTURE_ID);
    assertThat(model).containsEntry("$id", FOREIGN_URN);
  }

  @Test
  void install_whenMemberIsInstalled_recordsTheCopyAndItsOrigin() {
    stubCreate();

    Installation installation = installationService.install(request(tree()));

    assertThat(installation.getPackageId()).isEqualTo(PACKAGE_ID);
    assertThat(installation.getPackageVersion()).isEqualTo("1.0.0");
    assertThat(installation.getArtifacts())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getArtifactType()).isEqualTo(InstalledArtifactType.DATA_STRUCTURE);
              assertThat(line.getName()).isEqualTo("Tree");
              assertThat(line.getShellId()).isEqualTo(STRUCTURE_ID);
              assertThat(line.getUrn()).isEqualTo(LOGICAL_URN);
              assertThat(line.getVersionedUrn()).isEqualTo(VERSIONED_URN);
              assertThat(line.getOrigin()).isEqualTo(FOREIGN_URN);
            });
  }

  @Test
  void install_whenNameIsBlank_usesTheDocumentTitle() {
    stubCreate();

    installationService.install(request(member(FOREIGN_URN, " ", "Trees", model())));

    verify(dataStructureService).create(structureCaptor.capture());
    assertThat(structureCaptor.getValue().getName()).isEqualTo("Tree cadastre");
  }

  @Test
  void findByIdOrThrow_whenInstallationDoesNotExist_throwsResourceNotFound() {
    UUID unknownId = UUID.fromString("99999999-9999-4000-8000-000000000009");
    when(installationRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> installationService.findByIdOrThrow(unknownId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private void stubCreate() {
    DataStructure structure = new DataStructure();
    structure.setId(STRUCTURE_ID);
    when(dataStructureService.create(any())).thenReturn(structure);
    DataStructureVersion version = new DataStructureVersion();
    version.setModelUrn(VERSIONED_URN);
    when(dataStructureVersionService.create(any())).thenReturn(version);
    when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(LOGICAL_URN);
    when(installationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  /** A Data structure document as a package from another instance carries it. */
  private static Map<String, Object> model() {
    return Map.of(
        "$schema",
        "https://json-schema.org/draft/2020-12/schema",
        "$id",
        FOREIGN_URN,
        TITLE,
        "Tree cadastre",
        "$defs",
        Map.of("Tree", Map.of("type", "object")));
  }

  private static PackageMemberInputDTO tree() {
    return member(FOREIGN_URN, "Tree", "Trees of the city", model());
  }

  private static PackageMemberInputDTO member(
      String urn, String name, String description, Map<String, Object> content) {
    PackageMemberInputDTO member = new PackageMemberInputDTO();
    member.setKind(PackageMemberKind.DATASTRUCTURE);
    member.setUrn(urn);
    member.setName(name);
    member.setDescription(description);
    member.setContent(content);
    return member;
  }

  private static InstallationInputDTO request(PackageMemberInputDTO... members) {
    PackageManifestInputDTO manifest = new PackageManifestInputDTO();
    manifest.setId(PACKAGE_ID);
    manifest.setVersion("1.0.0");
    manifest.setMembers(List.of(members));
    InstallationInputDTO input = new InstallationInputDTO();
    input.setPackageManifest(manifest);
    return input;
  }
}

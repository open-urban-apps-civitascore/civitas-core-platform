package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.BundleInstallation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.repository.BundleInstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link InstallationService}: the entity-to-DTO mapping (audit columns surfacing as
 * installedBy/createdAt, enum-typed artifact lines) and the structure-only uninstall slice —
 * reference counting over the journal, the REUSED no-delete rule, and the terminal uninstalledAt
 * write.
 */
@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

  @Mock private BundleInstallationRepository bundleInstallationRepository;
  @Mock private DataStructureService dataStructureService;
  @InjectMocks private InstallationService installationService;

  @Test
  void findAll_mapsHeaderAuditAndArtifactLines() {
    BundleInstallation installation = new BundleInstallation();
    installation.setId(UUID.randomUUID());
    installation.setCreatedAt(LocalDateTime.of(2026, 8, 14, 12, 0));
    installation.setCreatedBy(UUID.randomUUID());
    installation.setBundleId("urn:catalog:openurbanapps:usecase:verkehrszaehlung");
    installation.setBundleVersion("1.0.0");
    installation.setDataSetId(UUID.randomUUID());
    installation.setDataSetName("Verkehrszählung");
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(InstalledArtifactType.DATA_STRUCTURE);
    line.setName("Zählstelle");
    line.setShellId(UUID.randomUUID());
    line.setUrn("urn:core:city:openurbanapps:datastructure:mobility:zaehlstelle:default");
    line.setAction(InstalledArtifactAction.CREATED);
    installation.addArtifact(line);
    Pageable pageable = PageRequest.of(0, 20);
    when(bundleInstallationRepository.findAll(pageable))
        .thenReturn(new PageImpl<>(List.of(installation), pageable, 1));

    Page<InstallationOutputDTO> page = installationService.findAll(pageable);

    assertThat(page.getTotalElements()).isEqualTo(1);
    InstallationOutputDTO output = page.getContent().getFirst();
    assertThat(output.getId()).isEqualTo(installation.getId());
    assertThat(output.getCreatedAt()).isEqualTo(installation.getCreatedAt());
    assertThat(output.getInstalledBy()).isEqualTo(installation.getCreatedBy());
    assertThat(output.getBundleId())
        .isEqualTo("urn:catalog:openurbanapps:usecase:verkehrszaehlung");
    assertThat(output.getDataSetName()).isEqualTo("Verkehrszählung");
    assertThat(output.getArtifacts())
        .singleElement()
        .satisfies(
            artifact -> {
              assertThat(artifact.getArtifactType())
                  .isEqualTo(InstalledArtifactType.DATA_STRUCTURE);
              assertThat(artifact.getAction()).isEqualTo(InstalledArtifactAction.CREATED);
              assertThat(artifact.getName()).isEqualTo("Zählstelle");
              assertThat(artifact.getShellId()).isEqualTo(line.getShellId());
            });
  }

  /**
   * A mapping line is registry-only: urn set, shellId null. Pins that the missing shell id is a
   * legitimate, mappable state rather than an accident to be defended against.
   */
  @Test
  void findAll_mapsRegistryOnlyMappingLineWithoutShellId() {
    BundleInstallation installation = new BundleInstallation();
    installation.setId(UUID.randomUUID());
    installation.setCreatedAt(LocalDateTime.of(2026, 8, 14, 12, 0));
    installation.setDataSetId(UUID.randomUUID());
    installation.setDataSetName("Verkehrszählung");
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(InstalledArtifactType.MAPPING);
    line.setName("Zählung → Observation");
    line.setUrn("urn:core:city:openurbanapps:mapping:mobility:zaehlungtoobservation:default");
    line.setAction(InstalledArtifactAction.CREATED);
    installation.addArtifact(line);
    Pageable pageable = PageRequest.of(0, 20);
    when(bundleInstallationRepository.findAll(pageable))
        .thenReturn(new PageImpl<>(List.of(installation), pageable, 1));

    Page<InstallationOutputDTO> page = installationService.findAll(pageable);

    assertThat(page.getContent().getFirst().getArtifacts())
        .singleElement()
        .satisfies(
            artifact -> {
              assertThat(artifact.getArtifactType()).isEqualTo(InstalledArtifactType.MAPPING);
              assertThat(artifact.getShellId()).isNull();
              assertThat(artifact.getUrn())
                  .isEqualTo(
                      "urn:core:city:openurbanapps:mapping:mobility:zaehlungtoobservation:default");
            });
  }

  /**
   * A structure-only installation with one CREATED line, as the single-structure import writes it.
   */
  private static BundleInstallation structureInstallation(InstalledArtifactAction action) {
    BundleInstallation installation = new BundleInstallation();
    installation.setId(UUID.randomUUID());
    installation.setBundleId("urn:openurbanapps:datastructure:airqualitystation");
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(InstalledArtifactType.DATA_STRUCTURE);
    line.setName("Luftqualitäts-Messstation");
    line.setShellId(UUID.randomUUID());
    line.setUrn("urn:core:city:openurbanapps:datastructure:environment:airqualitystation:default");
    line.setAction(action);
    installation.addArtifact(line);
    return installation;
  }

  @Test
  void uninstall_deletesCreatedStructureAndMarksTheInstallation() {
    BundleInstallation installation = structureInstallation(InstalledArtifactAction.CREATED);
    UUID shellId = installation.getArtifacts().getFirst().getShellId();
    when(bundleInstallationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    when(bundleInstallationRepository.countOtherActiveInstallationsReferencing(
            installation.getArtifacts().getFirst().getUrn(), installation.getId()))
        .thenReturn(0L);
    when(dataStructureService.existsById(shellId)).thenReturn(true);

    installationService.uninstall(installation.getId());

    verify(dataStructureService).deleteById(shellId);
    // The journal survives — only the terminal timestamp is set.
    verify(bundleInstallationRepository).save(installation);
    assertThat(installation.getUninstalledAt()).isNotNull();
  }

  /** CREATED here, REUSED by another active install → 409, nothing deleted, nothing marked. */
  @Test
  void uninstall_whenAnotherActiveInstallationReferencesTheStructure_rejects() {
    BundleInstallation installation = structureInstallation(InstalledArtifactAction.CREATED);
    when(bundleInstallationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    when(bundleInstallationRepository.countOtherActiveInstallationsReferencing(
            installation.getArtifacts().getFirst().getUrn(), installation.getId()))
        .thenReturn(1L);

    assertThatThrownBy(() -> installationService.uninstall(installation.getId()))
        .isInstanceOf(ResourceInUseException.class)
        .hasMessageContaining("other active installation");

    verify(dataStructureService, never()).deleteById(any());
    verify(bundleInstallationRepository, never()).save(any());
  }

  /** A REUSED line deletes nothing — this install only withdraws its claim. */
  @Test
  void uninstall_reusedLine_withdrawsTheClaimWithoutDeleting() {
    BundleInstallation installation = structureInstallation(InstalledArtifactAction.REUSED);
    when(bundleInstallationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));

    installationService.uninstall(installation.getId());

    verify(dataStructureService, never()).deleteById(any());
    assertThat(installation.getUninstalledAt()).isNotNull();
    verify(bundleInstallationRepository).save(installation);
  }

  /** Use-case bundles wait for the reverse-order teardown increment — refuse, do not half-do. */
  @Test
  void uninstall_useCaseInstallation_rejectsWithClearMessage() {
    BundleInstallation installation = structureInstallation(InstalledArtifactAction.CREATED);
    InstalledArtifact dataSetLine = new InstalledArtifact();
    dataSetLine.setArtifactType(InstalledArtifactType.DATA_SET);
    dataSetLine.setName("Verkehrszählung");
    dataSetLine.setAction(InstalledArtifactAction.CREATED);
    installation.addArtifact(dataSetLine);
    when(bundleInstallationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));

    assertThatThrownBy(() -> installationService.uninstall(installation.getId()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("structure-only");

    verify(dataStructureService, never()).deleteById(any());
    verify(bundleInstallationRepository, never()).save(any());
  }

  @Test
  void uninstall_alreadyUninstalled_rejects() {
    BundleInstallation installation = structureInstallation(InstalledArtifactAction.CREATED);
    installation.setUninstalledAt(LocalDateTime.of(2026, 8, 15, 9, 0));
    when(bundleInstallationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));

    assertThatThrownBy(() -> installationService.uninstall(installation.getId()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("already uninstalled");
  }

  /** Deleted by hand already? The journal records history; mark uninstalled instead of failing. */
  @Test
  void uninstall_whenShellAlreadyGone_stillMarksUninstalled() {
    BundleInstallation installation = structureInstallation(InstalledArtifactAction.CREATED);
    UUID shellId = installation.getArtifacts().getFirst().getShellId();
    when(bundleInstallationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    when(bundleInstallationRepository.countOtherActiveInstallationsReferencing(
            installation.getArtifacts().getFirst().getUrn(), installation.getId()))
        .thenReturn(0L);
    when(dataStructureService.existsById(shellId)).thenReturn(false);

    installationService.uninstall(installation.getId());

    verify(dataStructureService, never()).deleteById(any());
    assertThat(installation.getUninstalledAt()).isNotNull();
    verify(bundleInstallationRepository).save(installation);
  }
}

package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.BundleInstallation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.repository.BundleInstallationRepository;
import java.time.LocalDateTime;
import java.util.List;
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
 * Unit tests for {@link InstallationService}: the entity-to-DTO mapping, including the audit
 * columns surfacing as installedBy/createdAt and the enum-typed artifact lines.
 */
@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

  @Mock private BundleInstallationRepository bundleInstallationRepository;
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
}

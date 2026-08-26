package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

  @Mock private InstallationRepository installationRepository;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSinkService dataSinkService;
  @Mock private PipelineService pipelineService;
  @Mock private DataSetService dataSetService;
  @Mock private MappingService mappingService;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @InjectMocks private InstallationService installationService;

  @Test
  void findAll_mapsHeaderAuditAndArtifactLines() {
    Installation installation = new Installation();
    installation.setId(UUID.randomUUID());
    installation.setCreatedAt(LocalDateTime.of(2026, 8, 14, 12, 0));
    installation.setCreatedBy(UUID.randomUUID());
    installation.setCatalogEntryId("urn:catalog:openurbanapps:usecase:verkehrszaehlung");
    installation.setCatalogEntryVersion("1.0.0");
    installation.setDataSetId(UUID.randomUUID());
    installation.setDataSetName("Verkehrszählung");
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(InstalledArtifactType.DATA_STRUCTURE);
    line.setName("Zählstelle");
    line.setShellId(UUID.randomUUID());
    line.setUrn("urn:core:standard:openurbanapps:datastructure:mobility:zaehlstelle:default");
    line.setAction(InstalledArtifactAction.CREATED);
    installation.addArtifact(line);
    Pageable pageable = PageRequest.of(0, 20);
    when(installationRepository.findAll(pageable))
        .thenReturn(new PageImpl<>(List.of(installation), pageable, 1));

    Page<InstallationOutputDTO> page = installationService.findAll(pageable);

    assertThat(page.getTotalElements()).isEqualTo(1);
    InstallationOutputDTO output = page.getContent().getFirst();
    assertThat(output.getId()).isEqualTo(installation.getId());
    assertThat(output.getCreatedAt()).isEqualTo(installation.getCreatedAt());
    assertThat(output.getInstalledBy()).isEqualTo(installation.getCreatedBy());
    assertThat(output.getCatalogEntryId())
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
    Installation installation = new Installation();
    installation.setId(UUID.randomUUID());
    installation.setCreatedAt(LocalDateTime.of(2026, 8, 14, 12, 0));
    installation.setDataSetId(UUID.randomUUID());
    installation.setDataSetName("Verkehrszählung");
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(InstalledArtifactType.MAPPING);
    line.setName("Zählung → Observation");
    line.setUrn("urn:core:standard:openurbanapps:mapping:mobility:zaehlungtoobservation:default");
    line.setAction(InstalledArtifactAction.CREATED);
    installation.addArtifact(line);
    Pageable pageable = PageRequest.of(0, 20);
    when(installationRepository.findAll(pageable))
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
                      "urn:core:standard:openurbanapps:mapping:mobility:zaehlungtoobservation:default");
            });
  }

  /**
   * A structure-only installation with one CREATED line, as the single-structure import writes it.
   */
  private static Installation structureInstallation(InstalledArtifactAction action) {
    Installation installation = new Installation();
    installation.setId(UUID.randomUUID());
    installation.setCatalogEntryId("urn:openurbanapps:datastructure:airqualitystation");
    InstalledArtifact line = new InstalledArtifact();
    line.setArtifactType(InstalledArtifactType.DATA_STRUCTURE);
    line.setName("Luftqualitäts-Messstation");
    line.setShellId(UUID.randomUUID());
    line.setUrn(
        "urn:core:standard:openurbanapps:datastructure:environment:airqualitystation:default");
    line.setAction(action);
    installation.addArtifact(line);
    return installation;
  }

  @Test
  void uninstall_deletesCreatedStructureAndMarksTheInstallation() {
    Installation installation = structureInstallation(InstalledArtifactAction.CREATED);
    UUID shellId = installation.getArtifacts().getFirst().getShellId();
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    when(installationRepository.countOtherActiveInstallationsReferencing(
            installation.getArtifacts().getFirst().getUrn(), installation.getId()))
        .thenReturn(0L);
    when(dataStructureService.existsById(shellId)).thenReturn(true);

    installationService.uninstall(installation.getId());

    verify(dataStructureService).deleteById(shellId);
    // The journal survives — only the terminal timestamp is set.
    verify(installationRepository).save(installation);
    assertThat(installation.getUninstalledAt()).isNotNull();
  }

  /**
   * CREATED here but still claimed by another active install → KEPT, like a shared package
   * dependency: it disappears only when the last claim goes. The uninstall itself proceeds.
   */
  @Test
  void uninstall_keepsStructureStillClaimedByAnotherActiveInstallation() {
    Installation installation = structureInstallation(InstalledArtifactAction.CREATED);
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    when(installationRepository.countOtherActiveInstallationsReferencing(
            installation.getArtifacts().getFirst().getUrn(), installation.getId()))
        .thenReturn(1L);

    installationService.uninstall(installation.getId());

    verify(dataStructureService, never()).deleteById(any());
    assertThat(installation.getUninstalledAt()).isNotNull();
    verify(installationRepository).save(installation);
  }

  /** A REUSED line deletes nothing — this install only withdraws its claim. */
  @Test
  void uninstall_reusedLine_withdrawsTheClaimWithoutDeleting() {
    Installation installation = structureInstallation(InstalledArtifactAction.REUSED);
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));

    installationService.uninstall(installation.getId());

    verify(dataStructureService, never()).deleteById(any());
    assertThat(installation.getUninstalledAt()).isNotNull();
    verify(installationRepository).save(installation);
  }

  /** One provenance line, CREATED. */
  private static InstalledArtifact line(
      InstalledArtifactType type, String name, UUID shellId, String urn) {
    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(type);
    artifact.setName(name);
    artifact.setShellId(shellId);
    artifact.setUrn(urn);
    artifact.setAction(InstalledArtifactAction.CREATED);
    return artifact;
  }

  private static Optional<ModelRegistryGateway.RegistryDocument> presentDocument() {
    return Optional.of(new ModelRegistryGateway.RegistryDocument(Map.of(), null));
  }

  /**
   * The full bundle teardown: reverse touch order, the explicit manifest repair (upstream
   * postDelete bypass), and the unrelease-before-delete mirror for the source the import had
   * released.
   */
  @Test
  void uninstall_bundleTearsDownInReverseOrderWithManifestRepair() {
    Installation installation = new Installation();
    installation.setId(UUID.randomUUID());
    installation.setCatalogEntryId("urn:openurbanapps:usecase:verkehrszaehlung");
    InstalledArtifact structure =
        line(
            InstalledArtifactType.DATA_STRUCTURE,
            "Verkehrszählung",
            UUID.randomUUID(),
            "urn:core:standard:openurbanapps:datastructure:mobility:verkehrszaehlung:default");
    InstalledArtifact source =
        line(InstalledArtifactType.DATA_SOURCE, "Zählstellen-Feed", UUID.randomUUID(), null);
    InstalledArtifact mapping =
        line(
            InstalledArtifactType.MAPPING,
            "Zählung zu Messung",
            null,
            "urn:core:standard:openurbanapps:mapping:mobility:zaehlungzumessung:default");
    InstalledArtifact dataSetLine =
        line(
            InstalledArtifactType.DATA_SET,
            "Verkehrszählung",
            UUID.randomUUID(),
            "urn:core:platform:civitas:dataset:common:Verkehrsz-hlung:x1y2z3a4b5");
    InstalledArtifact sink =
        line(
            InstalledArtifactType.DATA_SINK,
            "Verkehrsmessung-Tabelle",
            UUID.randomUUID(),
            "urn:core:platform:civitas:datasink:common:verkehrsmessung:c6d7e8f9g0");
    InstalledArtifact pipeline =
        line(
            InstalledArtifactType.PIPELINE,
            "Zählung zu Messung",
            UUID.randomUUID(),
            "urn:core:platform:civitas:pipeline:common:flow:h1i2j3k4l5");
    List.of(structure, source, mapping, dataSetLine, sink, pipeline)
        .forEach(installation::addArtifact);
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));

    DataSet dataSet = new DataSet();
    dataSet.setId(dataSetLine.getShellId());
    dataSet.setName("Verkehrszählung");
    dataSet.setDataSetStatus(DataSetStatus.DRAFT);
    when(dataSetService.findById(dataSetLine.getShellId())).thenReturn(Optional.of(dataSet));
    when(installationRepository.countOtherActiveInstallationsReferencing(
            any(), eq(installation.getId())))
        .thenReturn(0L);
    when(pipelineService.existsById(pipeline.getShellId())).thenReturn(true);
    when(dataSinkService.existsById(sink.getShellId())).thenReturn(true);
    when(dataSetService.existsById(dataSetLine.getShellId())).thenReturn(true);
    // The upstream bypass leaves the manifest behind — fetchPayload still finds it.
    when(modelRegistryGateway.fetchPayload(dataSetLine.getUrn())).thenReturn(presentDocument());
    when(modelRegistryGateway.fetchPayload(mapping.getUrn())).thenReturn(presentDocument());
    DataSource sourceEntity = new DataSource();
    sourceEntity.setId(source.getShellId());
    sourceEntity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    when(dataSourceService.findById(source.getShellId())).thenReturn(Optional.of(sourceEntity));
    when(dataStructureService.existsById(structure.getShellId())).thenReturn(true);

    installationService.uninstall(installation.getId());

    InOrder order =
        inOrder(
            pipelineService,
            dataSinkService,
            dataSetService,
            modelRegistryGateway,
            mappingService,
            dataSourceService,
            dataStructureService);
    order.verify(pipelineService).deleteById(pipeline.getShellId());
    order.verify(dataSinkService).deleteById(sink.getShellId());
    order.verify(dataSetService).deleteById(dataSetLine.getShellId());
    order.verify(modelRegistryGateway).deleteDataSet(dataSetLine.getUrn());
    order.verify(mappingService).delete(mapping.getUrn(), false);
    order.verify(dataSourceService).unrelease(source.getShellId());
    order.verify(dataSourceService).deleteById(source.getShellId());
    order.verify(dataStructureService).deleteById(structure.getShellId());
    assertThat(installation.getUninstalledAt()).isNotNull();
    verify(installationRepository).save(installation);
  }

  /** READY needs an unstage first; refuse before touching anything. */
  @Test
  void uninstall_whenDataSetNotDraft_refusesWithActionableMessage() {
    Installation installation = new Installation();
    installation.setId(UUID.randomUUID());
    InstalledArtifact dataSetLine =
        line(InstalledArtifactType.DATA_SET, "Verkehrszählung", UUID.randomUUID(), null);
    installation.addArtifact(dataSetLine);
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    DataSet dataSet = new DataSet();
    dataSet.setDataSetStatus(DataSetStatus.READY);
    when(dataSetService.findById(dataSetLine.getShellId())).thenReturn(Optional.of(dataSet));

    assertThatThrownBy(() -> installationService.uninstall(installation.getId()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("unstage");

    verify(dataSetService, never()).deleteById(any());
    verify(installationRepository, never()).save(any());
  }

  /** Provisioned infrastructure needs the asynchronous saga teardown — a later increment. */
  @Test
  void uninstall_whenDataSetProvisioned_refuses() {
    Installation installation = new Installation();
    installation.setId(UUID.randomUUID());
    InstalledArtifact dataSetLine =
        line(InstalledArtifactType.DATA_SET, "Verkehrszählung", UUID.randomUUID(), null);
    installation.addArtifact(dataSetLine);
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    DataSet dataSet = new DataSet();
    dataSet.setDataSetStatus(DataSetStatus.DRAFT);
    dataSet.setProvisioned(true);
    when(dataSetService.findById(dataSetLine.getShellId())).thenReturn(Optional.of(dataSet));

    assertThatThrownBy(() -> installationService.uninstall(installation.getId()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("unrelease");

    verify(dataSetService, never()).deleteById(any());
    verify(installationRepository, never()).save(any());
  }

  @Test
  void uninstall_alreadyUninstalled_rejects() {
    Installation installation = structureInstallation(InstalledArtifactAction.CREATED);
    installation.setUninstalledAt(LocalDateTime.of(2026, 8, 15, 9, 0));
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));

    assertThatThrownBy(() -> installationService.uninstall(installation.getId()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("already uninstalled");
  }

  /** Deleted by hand already? The journal records history; mark uninstalled instead of failing. */
  @Test
  void uninstall_whenShellAlreadyGone_stillMarksUninstalled() {
    Installation installation = structureInstallation(InstalledArtifactAction.CREATED);
    UUID shellId = installation.getArtifacts().getFirst().getShellId();
    when(installationRepository.findById(installation.getId()))
        .thenReturn(Optional.of(installation));
    when(installationRepository.countOtherActiveInstallationsReferencing(
            installation.getArtifacts().getFirst().getUrn(), installation.getId()))
        .thenReturn(0L);
    when(dataStructureService.existsById(shellId)).thenReturn(false);

    installationService.uninstall(installation.getId());

    verify(dataStructureService, never()).deleteById(any());
    assertThat(installation.getUninstalledAt()).isNotNull();
    verify(installationRepository).save(installation);
  }
}

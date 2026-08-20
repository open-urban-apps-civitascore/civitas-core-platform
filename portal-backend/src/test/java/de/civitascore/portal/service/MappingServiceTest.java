package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.RegistryDocument;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.LinkedHashMap;
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

/**
 * Unit tests for {@link MappingService}: a thin pass-through that stores/versions a CORE Mapping
 * document in Model Forge and reads it back. The registry is mocked; these tests pin the
 * pass-through contract (create vs. version, envelope handling, UI-layout split) without a
 * database.
 */
@ExtendWith(MockitoExtension.class)
class MappingServiceTest {

  private static final String LOGICAL_URN =
      "urn:core:platform:civitas:mapping:common:mapping:abc1234567";
  private static final String VERSIONED_URN = LOGICAL_URN + ":1.0.0";

  @Mock private ModelRegistryGateway registry;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @InjectMocks private MappingService mappingService;

  @Captor private ArgumentCaptor<Optional<String>> logicalUrnCaptor;
  @Captor private ArgumentCaptor<String> nameCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> contentCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> stylesCaptor;

  private static ModelPin pin() {
    return new ModelPin(LOGICAL_URN, VERSIONED_URN, "1.0.0");
  }

  private void stubStore() {
    when(registry.storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any()))
        .thenReturn(pin());
  }

  @Test
  void store_whenLogicalUrnNull_createsNewArtifactAndReturnsPins() {
    stubStore();

    ModelPin result =
        mappingService.store(
            null, Map.of("fields", Map.of("$.id", Map.of("op", "copy", "sourcePath", "$.id"))));

    assertThat(result.versionedUrn()).isEqualTo(VERSIONED_URN);
    assertThat(result.logicalUrn()).isEqualTo(LOGICAL_URN);
    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), logicalUrnCaptor.capture(), any(), any(), any());
    assertThat(logicalUrnCaptor.getValue()).isEmpty();
  }

  @Test
  void store_whenLogicalUrnGiven_versionsExistingArtifact() {
    stubStore();

    mappingService.store(LOGICAL_URN, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), logicalUrnCaptor.capture(), any(), any(), any());
    assertThat(logicalUrnCaptor.getValue()).contains(LOGICAL_URN);
  }

  @Test
  void store_stripsLogicalUrnAndSplitsPositionsIntoStyles() {
    stubStore();
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("logicalUrn", LOGICAL_URN);
    doc.put("fields", Map.of("$.geom", Map.of("op", "geoPoint", "lat", "$.lat", "lon", "$.lon")));
    doc.put("positions", Map.of("$.geom", Map.of("x", 1, "y", 2)));

    mappingService.store(LOGICAL_URN, doc);

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), any(), any(), contentCaptor.capture(), stylesCaptor.capture());
    assertThat(contentCaptor.getValue()).doesNotContainKeys("logicalUrn", "positions");
    assertThat(contentCaptor.getValue()).containsKey("fields");
    assertThat(stylesCaptor.getValue()).containsKey("positions");
  }

  @Test
  void store_whenTitlePresent_usesItAsArtifactName() {
    stubStore();

    mappingService.store(null, Map.of("title", "My Mapping", "fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), nameCaptor.capture(), any(), any());
    assertThat(nameCaptor.getValue()).isEqualTo("My Mapping");
  }

  @Test
  void store_whenTitleMissing_defaultsNameToMapping() {
    stubStore();

    mappingService.store(null, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), nameCaptor.capture(), any(), any());
    assertThat(nameCaptor.getValue()).isEqualTo("mapping");
  }

  @Test
  void store_whenDocNull_doesNotThrowAndStoresEmptyContent() {
    stubStore();

    mappingService.store(null, null);

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), any(), contentCaptor.capture(), any());
    assertThat(contentCaptor.getValue()).isEmpty();
  }

  @Test
  void exists_whenRegistryHasTheArtifact_isTrue() {
    when(registry.fetchPayload(LOGICAL_URN))
        .thenReturn(Optional.of(new RegistryDocument(Map.of("fields", Map.of()), null)));

    assertThat(mappingService.exists(LOGICAL_URN)).isTrue();
  }

  @Test
  void exists_whenRegistryHasNothing_isFalse() {
    when(registry.fetchPayload(LOGICAL_URN)).thenReturn(Optional.empty());

    assertThat(mappingService.exists(LOGICAL_URN)).isFalse();
  }

  /**
   * The reuse decision must be made on exactly the document {@link MappingService#store} would
   * write; a divergent split here would make every re-install of an unchanged bundle look like a
   * conflict.
   */
  @Test
  void isUnchanged_comparesTheSameContentAndStylesSplitThatStoreWrites() {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("logicalUrn", LOGICAL_URN);
    doc.put("fields", Map.of("$.count", Map.of("op", "toInt", "input", "$.vehicleCount")));
    doc.put("positions", Map.of("$.count", Map.of("x", 1, "y", 2)));
    when(registry.isUnchanged(eq(LOGICAL_URN), any(), any())).thenReturn(true);

    assertThat(mappingService.isUnchanged(LOGICAL_URN, doc)).isTrue();

    verify(registry).isUnchanged(eq(LOGICAL_URN), contentCaptor.capture(), stylesCaptor.capture());
    assertThat(contentCaptor.getValue()).doesNotContainKeys("logicalUrn", "positions");
    assertThat(contentCaptor.getValue()).containsKey("fields");
    assertThat(stylesCaptor.getValue()).containsKey("positions");
  }

  @Test
  void isUnchanged_whenRegistryReportsADifference_isFalse() {
    when(registry.isUnchanged(eq(LOGICAL_URN), any(), any())).thenReturn(false);

    assertThat(mappingService.isUnchanged(LOGICAL_URN, Map.of("fields", Map.of()))).isFalse();
  }

  @Test
  void get_whenPresent_returnsContentFromRegistry() {
    Map<String, Object> content = Map.of("fields", Map.of("$.id", Map.of("op", "copy")));
    when(registry.fetchPayload(VERSIONED_URN))
        .thenReturn(Optional.of(new RegistryDocument(content, null)));

    assertThat(mappingService.get(VERSIONED_URN)).contains(content);
  }

  @Test
  void get_whenMissing_returnsEmpty() {
    when(registry.fetchPayload("urn:core:platform:civitas:mapping:common:missing:zzz9999999"))
        .thenReturn(Optional.empty());

    assertThat(mappingService.get("urn:core:platform:civitas:mapping:common:missing:zzz9999999"))
        .isEmpty();
  }

  /** The read-back mirrors the POST/PUT body shape: the UI layout re-merges as `positions`. */
  @Test
  void get_mergesTheUiLayoutBackIntoTheDocument() {
    Map<String, Object> positions = Map.of("$.geom", Map.of("x", 1, "y", 2));
    when(registry.fetchPayload(VERSIONED_URN))
        .thenReturn(
            Optional.of(
                new RegistryDocument(Map.of("fields", Map.of()), Map.of("positions", positions))));

    Optional<Map<String, Object>> result = mappingService.get(VERSIONED_URN);

    assertThat(result).isPresent();
    assertThat(result.get()).containsEntry("positions", positions);
  }

  /**
   * The document's structure references resolve to installed shells (the sink-output pattern), so
   * the editor can load the referenced schemas without a URN lookup of its own.
   */
  @Test
  void get_resolvesStructureReferencesToInstalledShells() {
    String sourceUrn = "urn:core:standard:openurbanapps:datastructure:test:quelle:aaa1111111";
    String targetUrn = "urn:core:standard:openurbanapps:datastructure:test:ziel:bbb2222222";
    when(registry.fetchPayload(VERSIONED_URN))
        .thenReturn(
            Optional.of(
                new RegistryDocument(
                    Map.of("source", sourceUrn, "target", targetUrn, "fields", Map.of()), null)));
    when(registry.logicalUrn(sourceUrn)).thenReturn(sourceUrn);
    when(registry.logicalUrn(targetUrn)).thenReturn(targetUrn);
    DataStructureVersion sourceVersion = installedVersion("1.0.0");
    DataStructureVersion targetVersion = installedVersion("2.1.0");
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(sourceUrn + ":"))
        .thenReturn(Optional.of(sourceVersion));
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(targetUrn + ":"))
        .thenReturn(Optional.of(targetVersion));

    Map<String, Object> result = mappingService.get(VERSIONED_URN).orElseThrow();

    assertThat(result.get("sourceVersion"))
        .isEqualTo(
            Map.of(
                "id",
                sourceVersion.getId(),
                "dataStructureId",
                sourceVersion.getDataStructure().getId(),
                "version",
                "1.0.0"));
    assertThat(result.get("targetVersion"))
        .isEqualTo(
            Map.of(
                "id",
                targetVersion.getId(),
                "dataStructureId",
                targetVersion.getDataStructure().getId(),
                "version",
                "2.1.0"));
  }

  /** A reference nothing is installed for simply leaves its key out — no error, no null entry. */
  @Test
  void get_leavesUnresolvableStructureReferencesOut() {
    String sourceUrn = "urn:core:standard:openurbanapps:datastructure:test:quelle:aaa1111111";
    when(registry.fetchPayload(VERSIONED_URN))
        .thenReturn(
            Optional.of(
                new RegistryDocument(Map.of("source", sourceUrn, "fields", Map.of()), null)));
    when(registry.logicalUrn(sourceUrn)).thenReturn(sourceUrn);
    when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(sourceUrn + ":"))
        .thenReturn(Optional.empty());

    Map<String, Object> result = mappingService.get(VERSIONED_URN).orElseThrow();

    assertThat(result).doesNotContainKeys("sourceVersion", "targetVersion");
  }

  private static DataStructureVersion installedVersion(String version) {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    DataStructureVersion installed = new DataStructureVersion();
    installed.setId(UUID.randomUUID());
    installed.setVersion(version);
    installed.setDataStructure(structure);
    return installed;
  }

  @Test
  void delete_whenNotForced_delegatesToPlainDeletePayload() {
    mappingService.delete(VERSIONED_URN, false);

    verify(registry).deletePayload(VERSIONED_URN);
    verify(registry, never()).deleteArtifact(any(), anyBoolean(), anyBoolean());
  }

  @Test
  void delete_whenForced_forceDeletesAndUnlinksFromDataSets() {
    mappingService.delete(VERSIONED_URN, true);

    // force delete: no cascade, force=true (unlinks the mapping from any DataSets, ignores refs).
    verify(registry).deleteArtifact(VERSIONED_URN, false, true);
    verify(registry, never()).deletePayload(any());
  }
}

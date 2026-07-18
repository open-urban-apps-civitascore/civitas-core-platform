package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.RegistryDocument;
import de.civitascore.portal.modelregistry.PayloadKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
}

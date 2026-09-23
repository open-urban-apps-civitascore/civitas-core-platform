package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.RegistryDocument;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link MappingService}: a thin pass-through that stores/versions a CORE Mapping
 * document in Model Forge and reads it back, scoped to the DataSet the mapping belongs to. The
 * registry is mocked; these tests pin the pass-through contract (create vs. version, envelope
 * handling, UI-layout split) and the DataSet scoping without a database.
 */
@ExtendWith(MockitoExtension.class)
class MappingServiceTest {

  private static final UUID DATA_SET_ID = UUID.fromString("11111111-1111-4000-8000-000000000001");
  private static final String MANIFEST_URN =
      "urn:core:platform:civitas:dataset:common:Traffic:ds01234567";

  private static final String LOGICAL_URN =
      "urn:core:platform:civitas:mapping:common:mapping:abc1234567";
  private static final String VERSIONED_URN = LOGICAL_URN + ":1.0.0";

  /** A mapping of a different DataSet — never a member of the manifest under test. */
  private static final String FOREIGN_URN =
      "urn:core:platform:civitas:mapping:common:other:zzz9999999";

  @Mock private ModelRegistryGateway registry;
  @Mock private DataSetService dataSetService;

  private MappingService mappingService;

  @BeforeEach
  void createService() {
    // The namespace the registry mints into; a body-supplied URN is checked against it.
    mappingService = new MappingService(registry, dataSetService, "platform", "civitas", "common");

    DataSet dataSet = new DataSet();
    dataSet.setManifestLogicalUrn(MANIFEST_URN);
    lenient().when(dataSetService.findByIdOrThrow(DATA_SET_ID)).thenReturn(dataSet);

    // Mirrors UrnParser: a logical URN is the versioned one without its trailing version segment.
    lenient()
        .when(registry.logicalUrn(anyString()))
        .thenAnswer(
            invocation -> {
              String urn = invocation.getArgument(0);
              return urn.matches(".*:\\d+\\.\\d+\\.\\d+$")
                  ? urn.substring(0, urn.lastIndexOf(':'))
                  : urn;
            });
  }

  @Captor private ArgumentCaptor<Optional<String>> logicalUrnCaptor;
  @Captor private ArgumentCaptor<String> nameCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> contentCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> stylesCaptor;
  @Captor private ArgumentCaptor<String> dataSetCaptor;

  private static ModelPin pin() {
    return new ModelPin(LOGICAL_URN, VERSIONED_URN, "1.0.0");
  }

  private void stubStore() {
    when(registry.storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any(), any()))
        .thenReturn(pin());
  }

  /** The manifest holds {@code memberUrns} as its mapping members. */
  private void stubMembers(String... memberUrns) {
    when(registry.dependencyUrnsOfType(MANIFEST_URN, "mapping")).thenReturn(List.of(memberUrns));
  }

  @Test
  void store_whenLogicalUrnNull_createsNewArtifactAndReturnsPins() {
    stubStore();

    ModelPin result =
        mappingService.store(
            DATA_SET_ID,
            null,
            Map.of("fields", Map.of("$.id", Map.of("op", "copy", "sourcePath", "$.id"))));

    assertThat(result.versionedUrn()).isEqualTo(VERSIONED_URN);
    assertThat(result.logicalUrn()).isEqualTo(LOGICAL_URN);
    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), logicalUrnCaptor.capture(), any(), any(), any(), any());
    assertThat(logicalUrnCaptor.getValue()).isEmpty();
  }

  @Test
  @DisplayName("a created mapping is linked into the DataSet's manifest as it is stored")
  void store_whenCreating_linksIntoTheDataSetManifest() {
    stubStore();

    mappingService.store(DATA_SET_ID, null, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any(), dataSetCaptor.capture());
    assertThat(dataSetCaptor.getValue()).isEqualTo(MANIFEST_URN);
  }

  @Test
  void store_whenLogicalUrnGiven_versionsExistingArtifact() {
    stubMembers(VERSIONED_URN);
    stubStore();

    mappingService.store(DATA_SET_ID, LOGICAL_URN, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), logicalUrnCaptor.capture(), any(), any(), any(), any());
    assertThat(logicalUrnCaptor.getValue()).contains(LOGICAL_URN);
  }

  @Test
  void store_stripsLogicalUrnAndSplitsPositionsIntoStyles() {
    stubMembers(VERSIONED_URN);
    stubStore();
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("logicalUrn", LOGICAL_URN);
    doc.put("fields", Map.of("$.geom", Map.of("op", "geoPoint", "lat", "$.lat", "lon", "$.lon")));
    doc.put("positions", Map.of("$.geom", Map.of("x", 1, "y", 2)));

    mappingService.store(DATA_SET_ID, LOGICAL_URN, doc);

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING),
            any(),
            any(),
            contentCaptor.capture(),
            stylesCaptor.capture(),
            any());
    assertThat(contentCaptor.getValue()).doesNotContainKeys("logicalUrn", "positions");
    assertThat(contentCaptor.getValue()).containsKey("fields");
    assertThat(stylesCaptor.getValue()).containsKey("positions");
  }

  @Test
  void store_whenTitlePresent_usesItAsArtifactName() {
    stubStore();

    mappingService.store(DATA_SET_ID, null, Map.of("title", "My Mapping", "fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), nameCaptor.capture(), any(), any(), any());
    assertThat(nameCaptor.getValue()).isEqualTo("My Mapping");
  }

  @Test
  void store_whenTitleMissing_defaultsNameToMapping() {
    stubStore();

    mappingService.store(DATA_SET_ID, null, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), nameCaptor.capture(), any(), any(), any());
    assertThat(nameCaptor.getValue()).isEqualTo("mapping");
  }

  @Test
  void store_whenDocNull_doesNotThrowAndStoresEmptyContent() {
    stubStore();

    mappingService.store(DATA_SET_ID, null, null);

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), any(), contentCaptor.capture(), any(), any());
    assertThat(contentCaptor.getValue()).isEmpty();
  }

  @Test
  void get_whenPresent_returnsContentFromRegistry() {
    stubMembers(VERSIONED_URN);
    Map<String, Object> content = Map.of("fields", Map.of("$.id", Map.of("op", "copy")));
    when(registry.fetchPayload(VERSIONED_URN))
        .thenReturn(Optional.of(new RegistryDocument(content, null)));

    assertThat(mappingService.get(DATA_SET_ID, VERSIONED_URN)).contains(content);
  }

  @Test
  void get_whenMissing_returnsEmpty() {
    stubMembers(FOREIGN_URN);
    when(registry.fetchPayload(FOREIGN_URN)).thenReturn(Optional.empty());

    assertThat(mappingService.get(DATA_SET_ID, FOREIGN_URN)).isEmpty();
  }

  @Test
  @DisplayName("a delete goes to the registry, which refuses it while anything still references it")
  void delete_delegatesToThePlainDeletePayload() {
    stubMembers(VERSIONED_URN);

    mappingService.delete(DATA_SET_ID, VERSIONED_URN);

    verify(registry).deletePayload(VERSIONED_URN);
    verify(registry, never()).deleteArtifact(any(), anyBoolean());
  }

  @Test
  @DisplayName("membership holds for a logical URN addressing a versioned member")
  void membershipIsVersionAgnostic() {
    stubMembers(VERSIONED_URN);
    when(registry.fetchPayload(LOGICAL_URN))
        .thenReturn(Optional.of(new RegistryDocument(Map.of("fields", Map.of()), null)));

    assertThat(mappingService.get(DATA_SET_ID, LOGICAL_URN)).isPresent();
  }

  @Test
  @DisplayName("reading a mapping of another DataSet is indistinguishable from a missing one")
  void get_whenNotAMemberOfThisDataSet_returnsEmptyWithoutReadingIt() {
    stubMembers(VERSIONED_URN);

    assertThat(mappingService.get(DATA_SET_ID, FOREIGN_URN)).isEmpty();

    verify(registry, never()).fetchPayload(FOREIGN_URN);
  }

  @Test
  @DisplayName("deleting a mapping of another DataSet is refused and deletes nothing")
  void delete_whenNotAMemberOfThisDataSet_isRefused() {
    stubMembers(VERSIONED_URN);

    assertThatThrownBy(() -> mappingService.delete(DATA_SET_ID, FOREIGN_URN))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(registry, never()).deleteArtifact(any(), anyBoolean());
    verify(registry, never()).deletePayload(any());
  }

  @Test
  @DisplayName("versioning a mapping of another DataSet is refused before anything is stored")
  void store_whenVersioningANonMember_isRefused() {
    stubMembers(VERSIONED_URN);

    assertThatThrownBy(() -> mappingService.store(DATA_SET_ID, FOREIGN_URN, Map.of()))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a DataSet without a manifest cannot hold mappings")
  void store_whenDataSetHasNoManifest_isRejected() {
    when(dataSetService.findByIdOrThrow(DATA_SET_ID)).thenReturn(new DataSet());

    assertThatThrownBy(() -> mappingService.store(DATA_SET_ID, null, Map.of()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("no manifest");

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any());
  }

  private static final String SOURCE_URN =
      "urn:core:platform:civitas:element:common:MaplSource:src1234567:1.0.0";
  private static final String TARGET_URN =
      "urn:core:platform:civitas:element:common:MaplTarget:tgt1234567:1.0.0";

  private void stubResolvable(String... urns) {
    for (String urn : urns) {
      lenient()
          .when(registry.fetchModel(urn))
          .thenReturn(Optional.of(new RegistryDocument(Map.of("type", "object"), null)));
    }
  }

  @Test
  @DisplayName("a mapping whose endpoints resolve is stored")
  void store_whenEndpointsResolve_isAccepted() {
    stubResolvable(SOURCE_URN, TARGET_URN);
    stubStore();

    mappingService.store(
        DATA_SET_ID, null, Map.of("source", SOURCE_URN, "target", TARGET_URN, "fields", Map.of()));

    verify(registry).storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a source that does not resolve is refused before anything is stored")
  void store_whenSourceDoesNotResolve_isRefused() {
    when(registry.fetchModel("urn:core:platform:civitas:element:common:Ghost:zzz9999999:1.0.0"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                mappingService.store(
                    DATA_SET_ID,
                    null,
                    Map.of(
                        "source",
                        "urn:core:platform:civitas:element:common:Ghost:zzz9999999:1.0.0",
                        "fields",
                        Map.of())))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not available");

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a target pinning a version that does not exist is refused")
  void store_whenTargetVersionDoesNotResolve_isRefused() {
    stubResolvable(SOURCE_URN);
    String missingVersion = "urn:core:platform:civitas:element:common:MaplTarget:tgt1234567:9.9.9";
    when(registry.fetchModel(missingVersion)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                mappingService.store(
                    DATA_SET_ID,
                    null,
                    Map.of("source", SOURCE_URN, "target", missingVersion, "fields", Map.of())))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not available");

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a draft that has not picked its structures yet is still storable")
  void store_whenEndpointsAbsent_isAccepted() {
    stubStore();

    mappingService.store(DATA_SET_ID, null, Map.of("fields", Map.of()));

    verify(registry).storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any(), any());
    verify(registry, never()).fetchModel(any());
  }

  @Test
  @DisplayName("a URN naming another namespace is refused before anything is stored")
  void foreignNamespaceUrnIsRefused() {
    assertThatThrownBy(
            () ->
                mappingService.store(
                    DATA_SET_ID,
                    "urn:core:evil:attacker:mapping:hijack:Injected:0000000000",
                    Map.of()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("own namespace");

    verifyNoInteractions(registry);
  }

  @Test
  @DisplayName("a URN of this registry's own namespace is accepted")
  void ownNamespaceUrnIsAccepted() {
    stubMembers(VERSIONED_URN);
    stubStore();

    assertThat(mappingService.store(DATA_SET_ID, LOGICAL_URN, Map.of("fields", Map.of())).version())
        .isEqualTo("1.0.0");
  }

  @Test
  @DisplayName("versioning without a URN is refused rather than forking a second mapping")
  void store_whenVersioningWithoutAUrn_isNotSilentlyACreate() {
    // The controller rejects a PUT without logicalUrn; the service itself still treats a null URN
    // as
    // a create, which is what POST relies on. This pins that the two paths stay distinguishable.
    stubStore();

    ModelPin created = mappingService.store(DATA_SET_ID, null, Map.of("fields", Map.of()));

    assertThat(created.logicalUrn()).isEqualTo(LOGICAL_URN);
    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), logicalUrnCaptor.capture(), any(), any(), any(), any());
    assertThat(logicalUrnCaptor.getValue()).isEmpty();
  }

  @Test
  @DisplayName("a create carries no URN, so the namespace rule does not apply")
  void createNeedsNoUrn() {
    stubStore();

    assertThat(mappingService.store(DATA_SET_ID, null, Map.of("fields", Map.of()))).isNotNull();
  }

  @Test
  @DisplayName("the list names every mapping of the DataSet once, by logical URN")
  void list_returnsTheManifestsMappingsAsLogicalUrns() {
    stubMembers(VERSIONED_URN, LOGICAL_URN, FOREIGN_URN + ":2.0.0");

    assertThat(mappingService.list(DATA_SET_ID)).containsExactly(LOGICAL_URN, FOREIGN_URN);
  }

  @Test
  @DisplayName("a DataSet with no mapping lists none")
  void list_whenTheManifestHoldsNoMapping_isEmpty() {
    stubMembers();

    assertThat(mappingService.list(DATA_SET_ID)).isEmpty();
  }
}

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
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.RegistryDocument;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.service.MappingService.MappingSummary;
import de.civitascore.portal.service.MappingService.StoredMapping;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
      "urn:core:platform:civitas:dataset:common:dae07e27-2d14-8b0c-a5c3-d3f788db5acd";

  private static final UUID MAPPING_ID = UUID.fromString("0bdbebd9-e766-86ff-bec5-f04da0a8abdd");
  private static final String LOGICAL_URN =
      "urn:core:platform:civitas:mapping:common:" + MAPPING_ID;
  private static final String VERSIONED_URN = LOGICAL_URN + ":1.0.0";

  /** A mapping of a different DataSet — never a member of the manifest under test. */
  private static final UUID FOREIGN_ID = UUID.fromString("835fdb09-4274-85a1-b063-a3e3127d3dc6");

  /** A second mapping of the DataSet under test. */
  private static final UUID OTHER_ID = UUID.fromString("5e1f0c2a-9d3b-8a47-b6e2-7c4d1f9a0b38");

  private static final String OTHER_LOGICAL_URN =
      "urn:core:platform:civitas:mapping:common:" + OTHER_ID;
  private static final String OTHER_URN = OTHER_LOGICAL_URN + ":2.0.0";

  private static final int ID_SEGMENT = 6;
  private static final int VERSION_SEGMENT = 7;

  @Mock private ModelRegistryGateway registry;
  @Mock private DataSetService dataSetService;

  private MappingService mappingService;

  @BeforeEach
  void createService() {
    mappingService = new MappingService(registry, dataSetService);

    DataSet dataSet = new DataSet();
    dataSet.setManifestLogicalUrn(MANIFEST_URN);
    lenient().when(dataSetService.findByIdOrThrow(DATA_SET_ID)).thenReturn(dataSet);

    // Mirror UrnParser on the URN layout
    // urn:core:<scope>:<owner>:<type>:<domain>:<uuid>[:<version>].
    lenient()
        .when(registry.logicalUrn(anyString()))
        .thenAnswer(invocation -> logical(invocation.getArgument(0)));
    lenient()
        .when(registry.idOf(anyString()))
        .thenAnswer(invocation -> UUID.fromString(segments(invocation.getArgument(0))[ID_SEGMENT]));
    lenient()
        .when(registry.pinOf(anyString()))
        .thenAnswer(
            invocation -> {
              String urn = invocation.getArgument(0);
              return new ModelPin(logical(urn), urn, segments(urn)[VERSION_SEGMENT]);
            });
  }

  private static String[] segments(String urn) {
    return urn.split(":");
  }

  private static String logical(String urn) {
    return segments(urn).length > VERSION_SEGMENT ? urn.substring(0, urn.lastIndexOf(':')) : urn;
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
    when(registry.storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any(), any(), any()))
        .thenReturn(pin());
  }

  /** The manifest holds {@code memberUrns} as its mapping members. */
  private void stubMembers(String... memberUrns) {
    when(registry.dependencyUrnsOfType(MANIFEST_URN, "mapping")).thenReturn(List.of(memberUrns));
  }

  @Test
  void create_storesANewArtifactAndReturnsItsPin() {
    stubStore();

    StoredMapping result =
        mappingService.create(
            DATA_SET_ID,
            Map.of("fields", Map.of("$.id", Map.of("op", "copy", "sourcePath", "$.id"))));

    assertThat(result.pin()).isEqualTo(pin());
    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), logicalUrnCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(logicalUrnCaptor.getValue()).isEmpty();
  }

  @Test
  void create_returnsTheIdOfTheMintedUrn() {
    stubStore();

    StoredMapping result = mappingService.create(DATA_SET_ID, Map.of("fields", Map.of()));

    assertThat(result.mappingId()).isEqualTo(MAPPING_ID);
  }

  @Test
  @DisplayName("a created mapping is linked into the DataSet's manifest as it is stored")
  void create_linksIntoTheDataSetManifest() {
    stubStore();

    mappingService.create(DATA_SET_ID, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), any(), any(), any(), any(), any(), dataSetCaptor.capture());
    assertThat(dataSetCaptor.getValue()).isEqualTo(MANIFEST_URN);
  }

  @Test
  void update_versionsTheLogicalUrnOfTheMember() {
    stubMembers(VERSIONED_URN);
    stubStore();

    mappingService.update(DATA_SET_ID, MAPPING_ID, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), logicalUrnCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(logicalUrnCaptor.getValue()).contains(LOGICAL_URN);
  }

  @Test
  void update_splitsPositionsIntoStylesAndReturnsThemWithTheContent() {
    stubMembers(VERSIONED_URN);
    stubStore();
    Map<String, Object> positions = Map.of("$.geom", Map.of("x", 1, "y", 2));
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("fields", Map.of("$.geom", Map.of("op", "geoPoint", "lat", "$.lat", "lon", "$.lon")));
    doc.put("positions", positions);

    StoredMapping result = mappingService.update(DATA_SET_ID, MAPPING_ID, doc);

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING),
            any(),
            any(),
            any(),
            contentCaptor.capture(),
            stylesCaptor.capture(),
            any());
    assertThat(contentCaptor.getValue()).doesNotContainKey("positions").containsKey("fields");
    assertThat(stylesCaptor.getValue()).containsEntry("positions", positions);
    assertThat(result.document()).containsEntry("positions", positions).containsKey("fields");
  }

  @Test
  void create_whenTitlePresent_usesItAsArtifactName() {
    stubStore();

    mappingService.create(DATA_SET_ID, Map.of("title", "My Mapping", "fields", Map.of()));

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), any(), any(), nameCaptor.capture(), any(), any(), any());
    assertThat(nameCaptor.getValue()).isEqualTo("My Mapping");
  }

  @Test
  void create_whenTitleMissing_defaultsNameToMapping() {
    stubStore();

    mappingService.create(DATA_SET_ID, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(
            eq(PayloadKind.MAPPING), any(), any(), nameCaptor.capture(), any(), any(), any());
    assertThat(nameCaptor.getValue()).isEqualTo("mapping");
  }

  @Test
  void get_whenMappingIdNamesAMember_returnsItsCurrentPinAndDocument() {
    String currentVersion = LOGICAL_URN + ":1.2.0";
    stubMembers(OTHER_URN, currentVersion);
    Map<String, Object> fields = Map.of("$.id", Map.of("op", "copy"));
    Map<String, Object> positions = Map.of("$.id", Map.of("x", 1, "y", 2));
    when(registry.fetchPayload(currentVersion))
        .thenReturn(
            Optional.of(
                new RegistryDocument(Map.of("fields", fields), Map.of("positions", positions))));

    StoredMapping result = mappingService.get(DATA_SET_ID, MAPPING_ID);

    assertThat(result.mappingId()).isEqualTo(MAPPING_ID);
    assertThat(result.pin()).isEqualTo(new ModelPin(LOGICAL_URN, currentVersion, "1.2.0"));
    assertThat(result.document())
        .containsEntry("fields", fields)
        .containsEntry("positions", positions);
  }

  @Test
  void get_whenThePayloadIsGone_throwsNotFound() {
    stubMembers(VERSIONED_URN);
    when(registry.fetchPayload(VERSIONED_URN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mappingService.get(DATA_SET_ID, MAPPING_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // Without it, a caller authorized on one Dataset could read the Mappings of another.
  @Test
  void get_whenMappingIdIsNoMemberOfThisDataSet_throwsNotFoundWithoutReadingIt() {
    stubMembers(VERSIONED_URN);

    assertThatThrownBy(() -> mappingService.get(DATA_SET_ID, FOREIGN_ID))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(registry, never()).fetchPayload(any());
  }

  @Test
  @DisplayName("a delete goes to the registry, which refuses it while anything still references it")
  void delete_deletesTheLogicalUrnOfTheMember() {
    stubMembers(VERSIONED_URN);

    mappingService.delete(DATA_SET_ID, MAPPING_ID);

    verify(registry).deletePayload(LOGICAL_URN);
    verify(registry, never()).deleteArtifact(any(), anyBoolean());
  }

  @Test
  void delete_whenMappingIdIsNoMemberOfThisDataSet_throwsNotFound() {
    stubMembers(VERSIONED_URN);

    assertThatThrownBy(() -> mappingService.delete(DATA_SET_ID, FOREIGN_ID))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(registry, never()).deleteArtifact(any(), anyBoolean());
    verify(registry, never()).deletePayload(any());
  }

  @Test
  void update_whenMappingIdIsNoMemberOfThisDataSet_throwsNotFoundAndStoresNothing() {
    stubMembers(VERSIONED_URN);

    assertThatThrownBy(
            () -> mappingService.update(DATA_SET_ID, FOREIGN_ID, Map.of("fields", Map.of())))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a DataSet without a manifest cannot hold mappings")
  void create_whenDataSetHasNoManifest_isRejected() {
    when(dataSetService.findByIdOrThrow(DATA_SET_ID)).thenReturn(new DataSet());

    assertThatThrownBy(() -> mappingService.create(DATA_SET_ID, Map.of()))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("no manifest");

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any(), any());
  }

  private static final String SOURCE_URN =
      "urn:core:platform:civitas:element:common:07ee560b-697b-87ca-a0ac-aa2a0e481164:1.0.0";
  private static final String TARGET_URN =
      "urn:core:platform:civitas:element:common:4431ae01-c275-8ce0-9250-14c0de69c06a:1.0.0";

  private void stubResolvable(String... urns) {
    for (String urn : urns) {
      lenient()
          .when(registry.fetchModel(urn))
          .thenReturn(Optional.of(new RegistryDocument(Map.of("type", "object"), null)));
    }
  }

  @Test
  @DisplayName("a mapping whose endpoints resolve is stored")
  void create_whenEndpointsResolve_isAccepted() {
    stubResolvable(SOURCE_URN, TARGET_URN);
    stubStore();

    mappingService.create(
        DATA_SET_ID, Map.of("source", SOURCE_URN, "target", TARGET_URN, "fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a source that does not resolve is refused before anything is stored")
  void create_whenSourceDoesNotResolve_isRefused() {
    String missing =
        "urn:core:platform:civitas:element:common:99d9ab54-d6f3-8c7e-a746-f62a4a18fae6:1.0.0";
    when(registry.fetchModel(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> mappingService.create(DATA_SET_ID, Map.of("source", missing, "fields", Map.of())))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not available");

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a target pinning a version that does not exist is refused")
  void create_whenTargetVersionDoesNotResolve_isRefused() {
    stubResolvable(SOURCE_URN);
    String missingVersion =
        "urn:core:platform:civitas:element:common:4431ae01-c275-8ce0-9250-14c0de69c06a:9.9.9";
    when(registry.fetchModel(missingVersion)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                mappingService.create(
                    DATA_SET_ID,
                    Map.of("source", SOURCE_URN, "target", missingVersion, "fields", Map.of())))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("not available");

    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any(), any());
  }

  // The config-adapter refuses an unpinned endpoint only at deploy time, after the release.
  @ParameterizedTest
  @ValueSource(
      strings = {
        "urn:core:platform:civitas:element:common:07ee560b-697b-87ca-a0ac-aa2a0e481164",
        "urn:core:platform:civitas:element:common:07ee560b-697b-87ca-a0ac-aa2a0e481164:latest",
        "urn:core:platform:civitas:element:common:07ee560b-697b-87ca-a0ac-aa2a0e481164:1.0"
      })
  @DisplayName("an endpoint not pinned to a SemVer version is refused before anything is stored")
  void create_whenEndpointNotPinned_isRefused(String unpinned) {
    assertThatThrownBy(
            () ->
                mappingService.create(DATA_SET_ID, Map.of("source", unpinned, "fields", Map.of())))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("pinned to a version");

    verify(registry, never()).fetchModel(any());
    verify(registry, never()).storePayload(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("a draft that has not picked its structures yet is still storable")
  void create_whenEndpointsAbsent_isAccepted() {
    stubStore();

    mappingService.create(DATA_SET_ID, Map.of("fields", Map.of()));

    verify(registry)
        .storePayload(eq(PayloadKind.MAPPING), any(), any(), any(), any(), any(), any());
    verify(registry, never()).fetchModel(any());
  }

  @Test
  void list_returnsIdLogicalUrnAndTitleOfEachMember() {
    stubMembers(VERSIONED_URN, LOGICAL_URN + ":1.1.0", OTHER_URN);
    when(registry.fetchPayload(VERSIONED_URN))
        .thenReturn(
            Optional.of(
                new RegistryDocument(Map.of("title", "Sensors", "fields", Map.of()), null)));
    when(registry.fetchPayload(OTHER_URN))
        .thenReturn(Optional.of(new RegistryDocument(Map.of("fields", Map.of()), null)));

    assertThat(mappingService.list(DATA_SET_ID))
        .containsExactly(
            new MappingSummary(MAPPING_ID, LOGICAL_URN, "Sensors"),
            new MappingSummary(OTHER_ID, OTHER_LOGICAL_URN, null));
  }

  @Test
  @DisplayName("a DataSet with no mapping lists none")
  void list_whenTheManifestHoldsNoMapping_isEmpty() {
    stubMembers();

    assertThat(mappingService.list(DATA_SET_ID)).isEmpty();
  }
}

package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DataSinkServiceTest {

  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private DataSinkMapper dataSinkMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private LayerRepository layerRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;

  @InjectMocks private DataSinkService dataSinkService;

  private static final String STORED_LOGICAL_URN =
      "urn:core:platform:civitas:data-sink:common:test";
  private static final String STORED_VERSIONED_URN = STORED_LOGICAL_URN + ":1.0.0";
  private static final String ELEMENT_URN =
      "urn:core:platform:civitas:element:common:observation:1.0.0";

  @BeforeEach
  void stubPayloadStore() {
    // Storing a configuration returns the registry-assigned pin. Lenient so tests that never
    // store one (validation-failure paths, FROST) don't trip strict stubbing.
    lenient()
        .when(modelRegistryGateway.storePayload(any(), any(), any(), any(), any()))
        .thenReturn(
            new ModelRegistryGateway.ModelPin(STORED_LOGICAL_URN, STORED_VERSIONED_URN, "1.0.0"));
    // The element soft reference resolves to an existing model by default; individual "not
    // found" tests override this with an empty Optional. Lenient so tests that never reference
    // an element (unknown-key / missing-element failure paths) don't trip strict stubbing.
    lenient()
        .when(modelRegistryGateway.fetchModel(ELEMENT_URN))
        .thenReturn(Optional.of(new ModelRegistryGateway.RegistryDocument(Map.of(), null)));
    // The element's owning DataStructure resolves (and the caller is authorized) by default;
    // authorization-failure tests override this. Lenient so element-free paths (FROST empty,
    // validation failures) don't trip strict stubbing. authorizeReferences is a void no-op on the
    // mock, which is the authorized outcome.
    lenient()
        .when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(any()))
        .thenReturn(Optional.of(dataStructureVersion(UUID.randomUUID())));
  }

  private DataSet dataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("ds");
    return ds;
  }

  private DataStructureVersion dataStructureVersion(UUID id) {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    structure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
    DataStructureVersion dsv = new DataStructureVersion();
    dsv.setId(id);
    dsv.setDataStructure(structure);
    dsv.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
    return dsv;
  }

  @Nested
  @DisplayName("findByIdAndDataSetOrThrow()")
  class FindByIdAndDataSet {

    @Test
    @DisplayName("Should return sink when it belongs to the requested dataset")
    void shouldReturnSinkForMatchingDataset() {
      UUID dataSetId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();

      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setDataSet(dataSet(dataSetId));

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(sink));

      DataSink result = dataSinkService.findByIdAndDataSetOrThrow(sinkId, dataSetId);

      assertThat(result).isSameAs(sink);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when the sink's dataset does not match")
    void shouldThrowWhenDatasetMismatch() {
      UUID sinkId = UUID.randomUUID();

      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setDataSet(dataSet(UUID.randomUUID()));

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(sink));

      assertThatThrownBy(() -> dataSinkService.findByIdAndDataSetOrThrow(sinkId, UUID.randomUUID()))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("postConvertToEntity()")
  class PostConvertToEntity {

    @Test
    @DisplayName("Should resolve dataSet from input and set it on the entity")
    void shouldResolveDataSet() {
      UUID dataSetId = UUID.randomUUID();
      DataSet dataSet = dataSet(dataSetId);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();

      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSink result = dataSinkService.create(input);

      assertThat(result.getDataSet()).isSameAs(dataSet);
      assertThat(result.getPipeline()).isNull();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when dataSet is not found")
    void shouldThrowWhenDataSetNotFound() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Configuration storage in the model registry (stage 4)")
  class ConfigurationStorage {

    @Test
    @DisplayName("POSTGIS create stores the configuration and mirrors the assigned pin")
    void postgisCreateStoresConfigurationAndMirrorsPin() {
      UUID dataSetId = UUID.randomUUID();
      Map<String, Object> configuration =
          Map.of("tableName", "sensor_readings", "element", ELEMENT_URN);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(configuration);

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSink result = dataSinkService.create(input);

      // The element URN soft reference is preserved verbatim, and the host enriches the payload
      // with
      // connectionType (from the sink type). Model Forge owns self-description — it stamps $schema
      // +
      // id on write (EmbeddedModelForgeOperations.saveArtifact), so the host does NOT add them
      // here.
      // Map equality is order-independent.
      verify(modelRegistryGateway)
          .storePayload(
              eq(PayloadKind.DATA_SINK),
              eq(Optional.empty()),
              eq("sensor_readings"),
              eq(
                  Map.of(
                      "tableName", "sensor_readings",
                      "element", ELEMENT_URN,
                      "connectionType", "postgis")),
              isNull());
      assertThat(result.getConfigurationLogicalUrn()).isEqualTo(STORED_LOGICAL_URN);
      assertThat(result.getConfigurationUrn()).isEqualTo(STORED_VERSIONED_URN);
    }

    @Test
    @DisplayName("FROST create stores {connectionType:frost} and pins its configurationUrn")
    void frostCreatePinsConnectionType() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSink result = dataSinkService.create(input);

      // A FROST sink is a first-class CORE DataSink artifact whose only content is its stamped
      // connectionType (all a passthrough FROST sink needs), so it obtains a configurationUrn a
      // pipeline can reference by sinkRef. Model Forge stamps $schema + id on write.
      verify(modelRegistryGateway)
          .storePayload(
              eq(PayloadKind.DATA_SINK),
              eq(Optional.empty()),
              any(),
              eq(Map.of("connectionType", "frost")),
              isNull());
      assertThat(result.getConfigurationLogicalUrn()).isEqualTo(STORED_LOGICAL_URN);
      assertThat(result.getConfigurationUrn()).isEqualTo(STORED_VERSIONED_URN);
    }

    @Test
    @DisplayName("Delete removes the backing configuration artifact from the registry")
    void deleteRemovesConfigurationArtifact() {
      UUID sinkId = UUID.randomUUID();
      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setDataSet(dataSet(UUID.randomUUID()));
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfigurationLogicalUrn(STORED_LOGICAL_URN);
      sink.setConfigurationUrn(STORED_VERSIONED_URN);

      when(dataSinkRepository.existsById(sinkId)).thenReturn(true);
      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(sink));
      when(layerRepository.existsByDataSinkId(sinkId)).thenReturn(false);

      dataSinkService.deleteById(sinkId);

      verify(dataSinkRepository).deleteById(sinkId);
      verify(modelRegistryGateway).deletePayload(STORED_LOGICAL_URN);
    }
  }

  @Nested
  @DisplayName("Referenced DataStructure authorization")
  class ReferenceAuthorization {

    private DataSinkInputDTO postgisInput(UUID dataSetId) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(Map.of("tableName", "sensor_readings", "element", ELEMENT_URN));
      return input;
    }

    @Test
    @DisplayName("POSTGIS create is denied when the caller is not authorized for the structure")
    void postgisCreateDeniedWhenUnauthorized() {
      UUID dataSetId = UUID.randomUUID();
      DataSinkInputDTO input = postgisInput(dataSetId);

      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      doThrow(new AccessDeniedException("denied"))
          .when(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATASTRUCTURE), any());

      // The denial is masked as the same "not available" 400 that a missing structure yields, so
      // the 400-vs-403 difference cannot be used as an existence oracle over structure URNs.
      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Referenced DataStructure is not available");
      verify(dataSinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("POSTGIS create is denied when the element resolves to no known DataStructure")
    void postgisCreateDeniedWhenStructureUnresolvable() {
      UUID dataSetId = UUID.randomUUID();
      DataSinkInputDTO input = postgisInput(dataSetId);

      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(any()))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Referenced DataStructure is not available");
      verify(dataSinkRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("validateConfiguration() — FROST")
  class FrostValidation {

    @Test
    @DisplayName("Should throw InvalidInputException when FROST config carries an unknown key")
    void shouldThrowWhenFrostConfigHasUnknownKey() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("unexpected", "value"));

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should accept a FROST config referencing an existing element")
    void shouldAcceptFrostConfigWithExistingElement() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("element", ELEMENT_URN));

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataSinkRepository.save(any())).thenReturn(entity);

      assertThat(dataSinkService.create(input)).isSameAs(entity);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when the referenced element does not exist")
    void shouldThrowWhenFrostElementMissing() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("element", ELEMENT_URN));

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(modelRegistryGateway.fetchModel(ELEMENT_URN)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("validateConfiguration() — POSTGIS")
  class PostgisValidation {

    private DataSinkInputDTO basePostgisInput(UUID dataSetId) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.POSTGIS);
      return input;
    }

    private void stubDataSet(UUID dataSetId) {
      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
    }

    @Test
    @DisplayName("Should throw InvalidInputException when tableName is not a String")
    void shouldThrowWhenTableNameIsNotAString() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(Map.of("tableName", 42, "element", ELEMENT_URN));

      stubDataSet(dataSetId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when tableName is blank")
    void shouldThrowWhenTableNameBlank() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(Map.of("tableName", "  ", "element", ELEMENT_URN));

      stubDataSet(dataSetId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when element is missing")
    void shouldThrowWhenElementMissing() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(Map.of("tableName", "sensor_readings"));

      stubDataSet(dataSetId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when the referenced element is not found")
    void shouldThrowWhenElementNotFound() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(Map.of("tableName", "sensor_readings", "element", ELEMENT_URN));

      stubDataSet(dataSetId);
      when(modelRegistryGateway.fetchModel(ELEMENT_URN)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("POSTGIS tableName uniqueness within the dataset")
  class PostgisTableNameUniqueness {

    private DataSink postgisSink(UUID id, UUID dataSetId, String tableName) {
      return postgisSink(id, dataSetId, tableName, null);
    }

    /**
     * A stored POSTGIS sink in the registry model: the entity carries only its configuration URN,
     * the document (tableName, optionally dataStructureVersionId) is served by the mocked gateway.
     * Each sink gets its own URN so siblings resolve to their own documents.
     */
    private DataSink postgisSink(UUID id, UUID dataSetId, String tableName, UUID dsvId) {
      DataSink sink = new DataSink();
      sink.setId(id);
      sink.setDataSet(dataSet(dataSetId));
      sink.setDataSinkType(DataSinkType.POSTGIS);
      String urn = "urn:test:sink:" + tableName + ":" + id;
      sink.setConfigurationUrn(urn);
      Map<String, Object> document = new HashMap<>(Map.of("tableName", tableName));
      if (dsvId != null) {
        document.put("dataStructureVersionId", dsvId.toString());
      }
      lenient()
          .when(modelRegistryGateway.fetchPayload(urn))
          .thenReturn(Optional.of(new ModelRegistryGateway.RegistryDocument(document, null)));
      return sink;
    }

    private DataSinkInputDTO postgisInput(UUID dataSetId, String tableName, UUID dsvId) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          new HashMap<>(
              Map.of(
                  "tableName",
                  tableName,
                  "dataStructureVersionId",
                  dsvId.toString(),
                  "element",
                  ELEMENT_URN)));
      return input;
    }

    private void stubCreate(UUID dataSetId, UUID dsvId, DataSinkInputDTO input) {
      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.POSTGIS);
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      lenient()
          .when(dataStructureVersionRepository.findById(dsvId))
          .thenReturn(Optional.of(dataStructureVersion(dsvId)));
      // The create path stores the configuration (central storePayload stub pins
      // STORED_VERSIONED_URN) and the uniqueness check reads it back from the registry.
      lenient()
          .when(modelRegistryGateway.fetchPayload(STORED_VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new ModelRegistryGateway.RegistryDocument(
                      new HashMap<>(input.getConfiguration()), null)));
    }

    @Test
    @DisplayName("Should reject a tableName already used by a sibling sink of the same dataset")
    void shouldRejectDuplicateTableName() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = postgisInput(dataSetId, "messwerte", dsvId);
      stubCreate(dataSetId, dsvId, input);
      DataSink sibling = postgisSink(UUID.randomUUID(), dataSetId, "messwerte");
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of(sibling));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(UniqueConstraintViolationException.class)
          .hasMessageContaining("messwerte")
          .hasMessageContaining("Another POSTGIS DataSink");
    }

    @Test
    @DisplayName("Should reject a tableName differing from a sibling only in case")
    void shouldRejectCaseOnlyDifference() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = postgisInput(dataSetId, "MESSWERTE", dsvId);
      stubCreate(dataSetId, dsvId, input);
      DataSink sibling = postgisSink(UUID.randomUUID(), dataSetId, "messwerte");
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of(sibling));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(UniqueConstraintViolationException.class);
    }

    @Test
    @DisplayName("Should accept a tableName only used by a FROST sibling's unrelated configuration")
    void shouldIgnoreFrostSiblings() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSink frostSibling = new DataSink();
      frostSibling.setId(UUID.randomUUID());
      frostSibling.setDataSet(dataSet(dataSetId));
      frostSibling.setDataSinkType(DataSinkType.FROST);
      // A FROST sink stores nothing in the registry (both URN columns stay null), so it cannot
      // even carry a colliding tableName — the check must skip it by type.

      DataSinkInputDTO input = postgisInput(dataSetId, "messwerte", dsvId);
      stubCreate(dataSetId, dsvId, input);
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of(frostSibling));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      assertThat(dataSinkService.create(input)).isNotNull();
    }

    @Test
    @DisplayName("Should accept a distinct tableName alongside a sibling sink")
    void shouldAcceptDistinctTableName() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = postgisInput(dataSetId, "andere_tabelle", dsvId);
      stubCreate(dataSetId, dsvId, input);
      DataSink sibling = postgisSink(UUID.randomUUID(), dataSetId, "messwerte");
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of(sibling));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      assertThat(dataSinkService.create(input)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "  messwerte  ",
          "mess werte",
          "1messwerte",
          "mess-werte",
          "messwerte;",
          "Meßwerte",
          "mess\"werte",
          "mess.werte",
          "1"
        })
    @DisplayName("Should reject a tableName that is not a plain unquoted identifier")
    void shouldRejectNonIdentifierTableName(String tableName) {
      assertThatThrownBy(() -> createWithTableName(tableName))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("letters, digits and underscores");
    }

    @Test
    @DisplayName("Should reject a tableName longer than a PostgreSQL identifier")
    void shouldRejectOverlongTableName() {
      assertThatThrownBy(() -> createWithTableName("t".repeat(64)))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("at most 63");
    }

    @Test
    @DisplayName("Should accept a tableName at the maximum identifier length")
    void shouldAcceptTableNameAtMaximumLength() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = postgisInput(dataSetId, "t".repeat(63), dsvId);
      stubCreate(dataSetId, dsvId, input);
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of());
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      assertThat(dataSinkService.create(input)).isNotNull();
    }

    private void createWithTableName(String tableName) {
      UUID dataSetId = UUID.randomUUID();
      DataSinkInputDTO input = postgisInput(dataSetId, tableName, UUID.randomUUID());
      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      dataSinkService.create(input);
    }

    /** Without the id comparison every PATCH of a POSTGIS sink would collide with itself. */
    @Test
    @DisplayName("Should not treat the updated sink itself as a conflicting sibling")
    void shouldExcludeItselfOnUpdate() {
      UUID dataSetId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSink existing = postgisSink(sinkId, dataSetId, "messwerte", dsvId);

      DataSinkInputDTO input = postgisInput(dataSetId, "messwerte", dsvId);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      lenient()
          .when(dataStructureVersionRepository.findById(dsvId))
          .thenReturn(Optional.of(dataStructureVersion(dsvId)));
      DataSink selfInList = postgisSink(sinkId, dataSetId, "messwerte");
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of(selfInList));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      lenient()
          .when(modelRegistryGateway.fetchPayload(STORED_VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new ModelRegistryGateway.RegistryDocument(
                      new HashMap<>(input.getConfiguration()), null)));

      assertThat(dataSinkService.update(sinkId, input)).isNotNull();
    }

    /**
     * Self-exclusion and duplicate detection share one stream, so broadening the identity check
     * would disable uniqueness for every update while the self-exclusion test stays green.
     */
    @Test
    @DisplayName("Should reject renaming a sink onto a sibling's tableName")
    void shouldRejectDuplicateTableNameOnUpdate() {
      UUID dataSetId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      UUID siblingId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSink existing = postgisSink(sinkId, dataSetId, "andere_tabelle", dsvId);

      DataSinkInputDTO input = postgisInput(dataSetId, "messwerte", dsvId);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      lenient()
          .when(dataStructureVersionRepository.findById(dsvId))
          .thenReturn(Optional.of(dataStructureVersion(dsvId)));
      // The update stores the incoming configuration (central storePayload stub pins
      // STORED_VERSIONED_URN); the uniqueness check reads the renamed tableName from there.
      lenient()
          .when(modelRegistryGateway.fetchPayload(STORED_VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new ModelRegistryGateway.RegistryDocument(
                      new HashMap<>(input.getConfiguration()), null)));
      DataSink selfInList = postgisSink(sinkId, dataSetId, "andere_tabelle");
      DataSink sibling = postgisSink(siblingId, dataSetId, "messwerte");
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of(selfInList, sibling));

      assertThatThrownBy(() -> dataSinkService.update(sinkId, input))
          .isInstanceOf(UniqueConstraintViolationException.class)
          .hasMessageContaining("messwerte");
    }

    /**
     * An update carries the stored tableName forward, so a sink that already collides is rejected
     * by a request that never mentioned the name. A create-shaped message would send the caller
     * looking for the fault in their request body.
     */
    @Test
    @DisplayName("Should name the stored tableName as the conflict when an update changed nothing")
    void shouldAttributeTheConflictToTheStoredNameOnUpdate() {
      UUID dataSetId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      UUID siblingId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSink existing = postgisSink(sinkId, dataSetId, "messwerte", dsvId);

      DataSinkInputDTO input = postgisInput(dataSetId, "messwerte", dsvId);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      lenient()
          .when(dataStructureVersionRepository.findById(dsvId))
          .thenReturn(Optional.of(dataStructureVersion(dsvId)));
      lenient()
          .when(modelRegistryGateway.fetchPayload(STORED_VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new ModelRegistryGateway.RegistryDocument(
                      new HashMap<>(input.getConfiguration()), null)));
      DataSink selfInList = postgisSink(sinkId, dataSetId, "messwerte");
      DataSink sibling = postgisSink(siblingId, dataSetId, "messwerte");
      when(dataSinkRepository.findByDataSetId(dataSetId)).thenReturn(List.of(selfInList, sibling));

      assertThatThrownBy(() -> dataSinkService.update(sinkId, input))
          .isInstanceOf(UniqueConstraintViolationException.class)
          .hasMessageContaining("rename it");
    }
  }

  @Nested
  @DisplayName("data-loss confirmation on update")
  class DataLossConfirmation {

    private DataSink existingPostgisSink(boolean provisioned) {
      DataSet ds = dataSet(UUID.randomUUID());
      ds.setProvisioned(provisioned);
      DataSink sink = new DataSink();
      sink.setId(UUID.randomUUID());
      sink.setDataSet(ds);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfigurationUrn(STORED_VERSIONED_URN);
      lenient()
          .when(modelRegistryGateway.fetchPayload(STORED_VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new ModelRegistryGateway.RegistryDocument(
                      Map.of("tableName", "old_table", "element", "v1"), null)));
      return sink;
    }

    private DataSinkInputDTO updateInput(
        String tableName, String element, boolean confirmDataLoss) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(Map.of("tableName", tableName, "element", element));
      input.setConfirmDataLoss(confirmDataLoss);
      return input;
    }

    private DataSink existingFrostSink(boolean provisioned, String element) {
      DataSet ds = dataSet(UUID.randomUUID());
      ds.setProvisioned(provisioned);
      DataSink sink = new DataSink();
      sink.setId(UUID.randomUUID());
      sink.setDataSet(ds);
      sink.setDataSinkType(DataSinkType.FROST);
      sink.setConfigurationUrn(STORED_VERSIONED_URN);
      lenient()
          .when(modelRegistryGateway.fetchPayload(STORED_VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new ModelRegistryGateway.RegistryDocument(Map.of("element", element), null)));
      return sink;
    }

    @Test
    @DisplayName("rejects a FROST element change on a provisioned dataset")
    void rejectsFrostVersionChangeWithoutConfirmation() {
      DataSink sink = existingFrostSink(true, "v1");
      when(dataSinkRepository.findByIdWithRelations(sink.getId())).thenReturn(Optional.of(sink));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("element", "v2"));
      input.setConfirmDataLoss(false);

      assertThatThrownBy(() -> dataSinkService.update(sink.getId(), input))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("confirmDataLoss");
    }

    @Test
    @DisplayName("rejects a destructive change on a provisioned dataset without confirmDataLoss")
    void rejectsDestructiveChangeWithoutConfirmation() {
      DataSink sink = existingPostgisSink(true);
      when(dataSinkRepository.findByIdWithRelations(sink.getId())).thenReturn(Optional.of(sink));

      DataSinkInputDTO input = updateInput("new_table", "v1", false);

      assertThatThrownBy(() -> dataSinkService.update(sink.getId(), input))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("confirmDataLoss");
    }

    // The "allows" cases assert only that the data-loss GUARD does not reject: update() continues
    // past preProcessUpdateInput into the mapper/postConvertToEntity stage, which fails on the
    // mocked collaborators for unrelated reasons — that later failure must never be the
    // confirmDataLoss 409.

    @Test
    @DisplayName("does not raise the data-loss guard when confirmDataLoss is set")
    void allowsDestructiveChangeWhenConfirmed() {
      DataSink sink = existingPostgisSink(true);
      when(dataSinkRepository.findByIdWithRelations(sink.getId())).thenReturn(Optional.of(sink));

      DataSinkInputDTO input = updateInput("new_table", "v1", true);

      assertThatThrownBy(() -> dataSinkService.update(sink.getId(), input))
          .isNotInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("does not raise the data-loss guard on a never-provisioned dataset")
    void allowsDestructiveChangeWhenNotProvisioned() {
      DataSink sink = existingPostgisSink(false);
      when(dataSinkRepository.findByIdWithRelations(sink.getId())).thenReturn(Optional.of(sink));

      DataSinkInputDTO input = updateInput("new_table", "v1", false);

      assertThatThrownBy(() -> dataSinkService.update(sink.getId(), input))
          .isNotInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("does not raise the data-loss guard for a non-destructive change")
    void allowsNonDestructiveChange() {
      DataSink sink = existingPostgisSink(true);
      when(dataSinkRepository.findByIdWithRelations(sink.getId())).thenReturn(Optional.of(sink));

      // Same tableName and version → no data loss.
      DataSinkInputDTO input = updateInput("old_table", "v1", false);

      assertThatThrownBy(() -> dataSinkService.update(sink.getId(), input))
          .isNotInstanceOf(ResourceInUseException.class);
    }
  }
}

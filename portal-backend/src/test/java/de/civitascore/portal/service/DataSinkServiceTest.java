package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSinkServiceTest {

  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private DataSinkMapper dataSinkMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private LayerRepository layerRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;

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
  }

  private DataSet dataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("ds");
    return ds;
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

      // The element URN soft reference is preserved verbatim inside the stored payload.
      verify(modelRegistryGateway)
          .storePayload(
              eq(PayloadKind.DATA_SINK),
              eq(Optional.empty()),
              eq("sensor_readings"),
              eq(configuration),
              isNull());
      assertThat(result.getConfigurationLogicalUrn()).isEqualTo(STORED_LOGICAL_URN);
      assertThat(result.getConfigurationUrn()).isEqualTo(STORED_VERSIONED_URN);
    }

    @Test
    @DisplayName("FROST create stores nothing in the registry (empty configuration)")
    void frostCreateStoresNothing() {
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

      verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any());
      assertThat(result.getConfigurationLogicalUrn()).isNull();
      assertThat(result.getConfigurationUrn()).isNull();
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
}

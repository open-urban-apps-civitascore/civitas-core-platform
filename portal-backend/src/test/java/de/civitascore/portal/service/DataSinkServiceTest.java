package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;

  @InjectMocks private DataSinkService dataSinkService;

  private DataStructureVersion dataStructureVersion(UUID id) {
    DataStructure structure = new DataStructure();
    structure.setId(UUID.randomUUID());
    DataStructureVersion dsv = new DataStructureVersion();
    dsv.setId(id);
    dsv.setDataStructure(structure);
    return dsv;
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
    @DisplayName("Should accept a FROST config referencing an existing DataStructureVersion")
    void shouldAcceptFrostConfigWithExistingDataStructureVersion() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("dataStructureVersionId", dsvId.toString()));

      DataSink entity = new DataSink();
      DataStructureVersion dsv = dataStructureVersion(dsvId);
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(dsv));
      when(dataSinkRepository.save(any())).thenReturn(entity);

      assertThat(dataSinkService.create(input)).isSameAs(entity);

      // The guard must authorize the parent DataStructure id, never the version id — assignments
      // scope on the structure, so authorizing the version id would deny every legitimate caller.
      ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
      verify(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATASTRUCTURE), captor.capture());
      assertThat(captor.getValue())
          .containsExactly(dsv.getDataStructure().getId())
          .doesNotContain(dsvId);
    }

    @Test
    @DisplayName("Should deny an unauthorized structure indistinguishably from a missing one")
    void shouldDenyWhenNotAuthorizedForStructure() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("dataStructureVersionId", dsvId.toString()));

      when(dataSinkMapper.toEntity(any())).thenReturn(new DataSink());
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataStructureVersionRepository.findById(dsvId))
          .thenReturn(Optional.of(dataStructureVersion(dsvId)));
      doThrow(new AccessDeniedException("denied"))
          .when(scopeAccessAuthorizer)
          .authorizeReferences(eq(ScopeType.DATASTRUCTURE), any());

      // An unauthorized reference must surface as the SAME failure as a missing one (see the
      // missing-version test below): same exception type, same message, no id — so a caller cannot
      // tell an existing-but-forbidden version apart from a non-existent one (existence oracle).
      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Referenced DataStructureVersion is not available")
          .hasMessageNotContaining(dsvId.toString());
    }

    @Test
    @DisplayName("Should throw InvalidInputException when the referenced version does not exist")
    void shouldThrowWhenFrostDataStructureVersionMissing() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("dataStructureVersionId", dsvId.toString()));

      DataSink entity = new DataSink();
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.empty());

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
      input.setConfiguration(
          Map.of("tableName", 42, "dataStructureVersionId", UUID.randomUUID().toString()));

      stubDataSet(dataSetId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when tableName is blank")
    void shouldThrowWhenTableNameBlank() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(
          Map.of("tableName", "  ", "dataStructureVersionId", UUID.randomUUID().toString()));

      stubDataSet(dataSetId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when dataStructureVersionId is missing")
    void shouldThrowWhenDsvIdMissing() {
      UUID dataSetId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(Map.of("tableName", "sensor_readings"));

      stubDataSet(dataSetId);

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Should throw InvalidInputException when DataStructureVersion is not found")
    void shouldThrowWhenDsvNotFound() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(
          Map.of("tableName", "sensor_readings", "dataStructureVersionId", dsvId.toString()));

      stubDataSet(dataSetId);
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class);
    }
  }
}

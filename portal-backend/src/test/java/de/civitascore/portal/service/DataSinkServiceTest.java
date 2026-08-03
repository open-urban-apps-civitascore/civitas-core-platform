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
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
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
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    structure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
    DataStructureVersion dsv = new DataStructureVersion();
    dsv.setId(id);
    dsv.setDataStructure(structure);
    dsv.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
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

    @Test
    @DisplayName("Should reject a DataStructureVersion that is not AVAILABLE")
    void shouldRejectNonAvailableVersion() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = basePostgisInput(dataSetId);
      input.setConfiguration(
          Map.of("tableName", "sensor_readings", "dataStructureVersionId", dsvId.toString()));

      DataStructureVersion draft = dataStructureVersion(dsvId);
      draft.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

      stubDataSet(dataSetId);
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(draft));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }
  }

  @Nested
  @DisplayName("POSTGIS tableName uniqueness within the dataset")
  class PostgisTableNameUniqueness {

    private DataSink postgisSink(UUID id, UUID dataSetId, String tableName) {
      DataSink sink = new DataSink();
      sink.setId(id);
      sink.setDataSet(dataSet(dataSetId));
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfiguration(new HashMap<>(Map.of("tableName", tableName)));
      return sink;
    }

    private DataSinkInputDTO postgisInput(UUID dataSetId, String tableName, UUID dsvId) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          new HashMap<>(
              Map.of("tableName", tableName, "dataStructureVersionId", dsvId.toString())));
      return input;
    }

    private void stubCreate(UUID dataSetId, UUID dsvId, DataSinkInputDTO input) {
      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.POSTGIS);
      entity.setConfiguration(input.getConfiguration());
      when(dataSinkMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataStructureVersionRepository.findById(dsvId))
          .thenReturn(Optional.of(dataStructureVersion(dsvId)));
    }

    @Test
    @DisplayName("Should reject a tableName already used by a sibling sink of the same dataset")
    void shouldRejectDuplicateTableName() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = postgisInput(dataSetId, "messwerte", dsvId);
      stubCreate(dataSetId, dsvId, input);
      when(dataSinkRepository.findByDataSetId(dataSetId))
          .thenReturn(List.of(postgisSink(UUID.randomUUID(), dataSetId, "messwerte")));

      assertThatThrownBy(() -> dataSinkService.create(input))
          .isInstanceOf(UniqueConstraintViolationException.class)
          .hasMessageContaining("messwerte");
    }

    @Test
    @DisplayName("Should reject a tableName differing from a sibling only in case")
    void shouldRejectCaseOnlyDifference() {
      UUID dataSetId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSinkInputDTO input = postgisInput(dataSetId, "MESSWERTE", dsvId);
      stubCreate(dataSetId, dsvId, input);
      when(dataSinkRepository.findByDataSetId(dataSetId))
          .thenReturn(List.of(postgisSink(UUID.randomUUID(), dataSetId, "messwerte")));

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
      frostSibling.setConfiguration(new HashMap<>(Map.of("tableName", "messwerte")));

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
      when(dataSinkRepository.findByDataSetId(dataSetId))
          .thenReturn(List.of(postgisSink(UUID.randomUUID(), dataSetId, "messwerte")));
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
          "mess\"werte"
        })
    @DisplayName("Should reject a tableName that is not a plain unquoted identifier")
    void shouldRejectNonIdentifierTableName(String tableName) {
      assertThatThrownBy(() -> createWithTableName(tableName))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("letters, digits and underscores");
    }

    /** PostgreSQL truncates rather than rejects, which would detach the sink from its table. */
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

    /**
     * An update re-reads the dataset's sinks including the entity being updated, so without an
     * identity check every PATCH/PUT of a POSTGIS sink would collide with itself.
     */
    @Test
    @DisplayName("Should not treat the updated sink itself as a conflicting sibling")
    void shouldExcludeItselfOnUpdate() {
      UUID dataSetId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      UUID dsvId = UUID.randomUUID();

      DataSink existing = postgisSink(sinkId, dataSetId, "messwerte");
      existing.getConfiguration().put("dataStructureVersionId", dsvId.toString());

      DataSinkInputDTO input = postgisInput(dataSetId, "messwerte", dsvId);

      when(dataSinkRepository.findByIdWithRelations(sinkId)).thenReturn(Optional.of(existing));
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet(dataSetId)));
      when(dataStructureVersionRepository.findById(dsvId))
          .thenReturn(Optional.of(dataStructureVersion(dsvId)));
      // A separate instance for the same row, as a fresh query returns: sharing the entity's
      // reference would let the test pass on identity alone, without the id comparison.
      when(dataSinkRepository.findByDataSetId(dataSetId))
          .thenReturn(List.of(postgisSink(sinkId, dataSetId, "messwerte")));
      when(dataSinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      assertThat(dataSinkService.update(sinkId, input)).isNotNull();
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
      sink.setConfiguration(
          new HashMap<>(Map.of("tableName", "old_table", "dataStructureVersionId", "v1")));
      return sink;
    }

    private DataSinkInputDTO updateInput(String tableName, String dsvId, boolean confirmDataLoss) {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(Map.of("tableName", tableName, "dataStructureVersionId", dsvId));
      input.setConfirmDataLoss(confirmDataLoss);
      return input;
    }

    private DataSink existingFrostSink(boolean provisioned, String dsvId) {
      DataSet ds = dataSet(UUID.randomUUID());
      ds.setProvisioned(provisioned);
      DataSink sink = new DataSink();
      sink.setId(UUID.randomUUID());
      sink.setDataSet(ds);
      sink.setDataSinkType(DataSinkType.FROST);
      sink.setConfiguration(new HashMap<>(Map.of("dataStructureVersionId", dsvId)));
      return sink;
    }

    @Test
    @DisplayName("rejects a FROST dataStructureVersionId change on a provisioned dataset")
    void rejectsFrostVersionChangeWithoutConfirmation() {
      DataSink sink = existingFrostSink(true, "v1");
      when(dataSinkRepository.findByIdWithRelations(sink.getId())).thenReturn(Optional.of(sink));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("dataStructureVersionId", "v2"));
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

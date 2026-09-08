package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.DataSetNotEditableException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetMutationGuard Tests")
class DataSetMutationGuardTest {

  @Mock private DataSetRepository dataSetRepository;

  @InjectMocks private DataSetMutationGuard guard;

  private static DataSet dataSet(DataSetStatus status, PendingSagaType pendingSagaType) {
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setDataSetStatus(status);
    dataSet.setPendingSagaType(pendingSagaType);
    return dataSet;
  }

  @Nested
  @DisplayName("requireMutable(DataSet)")
  class RequireMutableOfResolvedDataSet {

    @Test
    @DisplayName("Should pass for a DRAFT dataset without a pending saga")
    void shouldPassWhenDraftWithoutSaga() {
      assertThatCode(() -> guard.requireMutable(dataSet(DataSetStatus.DRAFT, null)))
          .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DataSetStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should throw DataSetNotEditableException when status is not DRAFT")
    void shouldThrowWhenNotDraft(DataSetStatus status) {
      assertThatThrownBy(() -> guard.requireMutable(dataSet(status, null)))
          .isInstanceOf(DataSetNotEditableException.class);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DataSetStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should keep the rejection message identical across non-DRAFT statuses")
    void shouldNotInterpolateStatusIntoMessage(DataSetStatus status) {
      assertThatThrownBy(() -> guard.requireMutable(dataSet(status, null)))
          .hasMessageNotContaining(status.name());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PendingSagaType.class)
    @DisplayName("Should throw ResourceInUseException when a saga is in flight")
    void shouldThrowWhenSagaPending(PendingSagaType pendingSagaType) {
      assertThatThrownBy(() -> guard.requireMutable(dataSet(DataSetStatus.DRAFT, pendingSagaType)))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining(pendingSagaType.name());
    }

    @Test
    @DisplayName("Should report the status violation when a saga violation is present too")
    void shouldPreferStatusViolationOverSagaViolation() {
      assertThatThrownBy(
              () -> guard.requireMutable(dataSet(DataSetStatus.AVAILABLE, PendingSagaType.UPDATE)))
          .isInstanceOf(DataSetNotEditableException.class);
    }
  }

  @Nested
  @DisplayName("requireMutable(DataSetOwnedEntity)")
  class RequireMutableOfSubEntity {

    private static Layer subEntityOf(DataSet dataSet) {
      Layer layer = new Layer();
      layer.setId(UUID.randomUUID());
      layer.setDataSet(dataSet);
      return layer;
    }

    @Test
    @DisplayName("Should pass when the parent dataset is DRAFT without a pending saga")
    void shouldPassWhenParentIsDraftWithoutSaga() {
      assertThatCode(
              () ->
                  guard.requireMutable(
                      subEntityOf(dataSet(DataSetStatus.DRAFT, null)), "update Layer"))
          .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DataSetStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should throw DataSetNotEditableException when the parent is not DRAFT")
    void shouldThrowWhenParentIsNotDraft(DataSetStatus status) {
      assertThatThrownBy(
              () -> guard.requireMutable(subEntityOf(dataSet(status, null)), "update Layer"))
          .isInstanceOf(DataSetNotEditableException.class);
    }

    @Test
    @DisplayName("Should throw ResourceInUseException when a saga is in flight on the parent")
    void shouldThrowWhenParentHasSagaPending() {
      DataSet parent = dataSet(DataSetStatus.DRAFT, PendingSagaType.UPDATE);

      assertThatThrownBy(() -> guard.requireMutable(subEntityOf(parent), "update Layer"))
          .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    @DisplayName("Should judge the parent dataset without reloading it by id")
    void shouldNotResolveParentThroughRepository() {
      guard.requireMutable(subEntityOf(dataSet(DataSetStatus.DRAFT, null)), "update Layer");

      verifyNoInteractions(dataSetRepository);
    }
  }

  @Nested
  @DisplayName("requireMutable(UUID)")
  class RequireMutableOfDataSetId {

    @Test
    @DisplayName("Should pass when the resolved dataset is DRAFT")
    void shouldPassWhenResolvedDataSetIsDraft() {
      DataSet draft = dataSet(DataSetStatus.DRAFT, null);
      when(dataSetRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

      assertThatCode(() -> guard.requireMutable(draft.getId(), "create Pipeline"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw DataSetNotEditableException when the resolved dataset is not DRAFT")
    void shouldThrowWhenResolvedDataSetIsNotDraft() {
      DataSet available = dataSet(DataSetStatus.AVAILABLE, null);
      when(dataSetRepository.findById(available.getId())).thenReturn(Optional.of(available));

      assertThatThrownBy(() -> guard.requireMutable(available.getId(), "create Pipeline"))
          .isInstanceOf(DataSetNotEditableException.class);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when the dataset does not exist")
    void shouldThrowWhenDataSetIsUnknown() {
      UUID unknownId = UUID.randomUUID();
      when(dataSetRepository.findById(unknownId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> guard.requireMutable(unknownId, "create Pipeline"))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}

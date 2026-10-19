package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.DataSetNotEditableException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.SagaInFlightException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Decides whether a DataSet and its sub-entities may be created, changed or deleted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSetMutationGuard {

  private static final String NOT_EDITABLE_MESSAGE =
      "A dataset and its sub-entities can only be changed while the dataset is DRAFT. Unstage or "
          + "unrelease the dataset first.";

  private final DataSetRepository dataSetRepository;

  /**
   * @throws DataSetNotEditableException if the DataSet is not in DRAFT
   * @throws SagaInFlightException if a saga is in flight on the DataSet
   */
  public void requireMutable(DataSet dataSet) {
    requireMutable(dataSet, "update DataSet");
  }

  /**
   * @param target what the rejection log line calls the attempted write
   * @throws DataSetNotEditableException if the DataSet is not in DRAFT
   * @throws SagaInFlightException if a saga is in flight on the DataSet
   */
  public void requireMutable(DataSet dataSet, String target) {
    // Status first: a released dataset always has the status problem, and reporting an incidental
    // in-flight saga instead would suggest that waiting fixes it.
    if (dataSet.getDataSetStatus() != DataSetStatus.DRAFT) {
      log.warn(
          "Rejected {} on dataset {}: dataset is {}",
          target,
          dataSet.getId(),
          dataSet.getDataSetStatus());
      throw new DataSetNotEditableException(NOT_EDITABLE_MESSAGE);
    }
    if (dataSet.getPendingSagaType() != null) {
      log.warn(
          "Rejected {} on dataset {}: saga {} in flight",
          target,
          dataSet.getId(),
          dataSet.getPendingSagaType());
      throw new SagaInFlightException(
          dataSet.getId(),
          dataSet.getPendingSagaType(),
          "Cannot write while a saga is in-flight: " + dataSet.getPendingSagaType());
    }
  }

  /**
   * @throws DataSetNotEditableException if the DataSet is not in DRAFT
   * @throws SagaInFlightException if a saga is in flight on the DataSet
   */
  public void requireMutable(DataSetOwnedEntity subEntity, String target) {
    requireMutable(subEntity.getDataSet(), target);
  }

  /**
   * @throws ResourceNotFoundException if no DataSet with that id exists
   * @throws DataSetNotEditableException if the DataSet is not in DRAFT
   * @throws SagaInFlightException if a saga is in flight on the DataSet
   */
  public void requireMutable(UUID dataSetId, String target) {
    requireMutable(
        dataSetRepository
            .findById(dataSetId)
            .orElseThrow(() -> new ResourceNotFoundException("DataSet", dataSetId)),
        target);
  }
}

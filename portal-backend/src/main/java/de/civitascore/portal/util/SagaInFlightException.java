package de.civitascore.portal.util;

import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

/** Thrown when a DataSet write must wait for its pending saga to finish. */
@Getter
public class SagaInFlightException extends ProblemException {

  private final UUID dataSetId;
  private final PendingSagaType pendingSagaType;

  public SagaInFlightException(UUID dataSetId, PendingSagaType pendingSagaType, String message) {
    super(ProblemType.SAGA_IN_FLIGHT, message, message);
    this.dataSetId = dataSetId;
    this.pendingSagaType = pendingSagaType;
  }

  /** The saga type is already part of the detail, so this member discloses nothing further. */
  @Override
  public Map<String, Object> properties() {
    return Map.of("pendingSagaType", pendingSagaType);
  }

  @Override
  public String toString() {
    return super.toString() + " dataSetId=" + dataSetId;
  }
}

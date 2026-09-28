package de.civitascore.portal.util;

import de.civitascore.portal.model.embedded.PendingSagaType;
import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a DataSet write must wait for its pending saga to finish. */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class SagaInFlightException extends RuntimeException {

  private final UUID dataSetId;
  private final PendingSagaType pendingSagaType;

  public SagaInFlightException(UUID dataSetId, PendingSagaType pendingSagaType, String message) {
    super(message);
    this.dataSetId = dataSetId;
    this.pendingSagaType = pendingSagaType;
  }
}

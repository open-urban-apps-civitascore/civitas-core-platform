package de.civitascore.portal.service.removal;

import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when a request deletes stored data without {@code confirmDataLoss=true}. The request
 * changed nothing.
 */
@Getter
public class RemovalNotConfirmedException extends RuntimeException {

  private final UUID dataSetId;
  private final transient UnconfirmedRemoval removal;

  public RemovalNotConfirmedException(UUID dataSetId, UnconfirmedRemoval removal, String message) {
    super(message);
    this.dataSetId = dataSetId;
    this.removal = removal;
  }
}

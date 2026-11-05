package de.civitascore.portal.service.removal;

import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when a request removes stored data, Layers or APIs without {@code confirmDataLoss=true}.
 * The request changed nothing.
 */
@Getter
public class RemovalNotConfirmedException extends RuntimeException {

  private final UUID dataSetId;
  private final transient UnconfirmedRemoval removal;

  RemovalNotConfirmedException(UUID dataSetId, UnconfirmedRemoval removal) {
    super(
        "This request removes stored data, Layers or APIs of the Dataset; set"
            + " confirmDataLoss=true to proceed");
    this.dataSetId = dataSetId;
    this.removal = removal;
  }
}

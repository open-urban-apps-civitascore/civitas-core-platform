package de.civitascore.portal.service.removal;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when a request deletes stored data without {@code confirmDataLoss=true}. The request
 * changed nothing.
 */
@Getter
public class RemovalNotConfirmedException extends ProblemException {

  private final UUID dataSetId;
  private final transient UnconfirmedRemoval removal;

  public RemovalNotConfirmedException(UUID dataSetId, UnconfirmedRemoval removal, String message) {
    super(ProblemType.REMOVAL_NOT_CONFIRMED, message);
    this.dataSetId = dataSetId;
    this.removal = removal;
  }

  /**
   * Only a caller that may update the Dataset gets here, and the list names only that Dataset's own
   * Data sinks, Layers and APIs, which it can read anyway.
   */
  @Override
  public Map<String, Object> properties() {
    return Map.of("removal", removal);
  }
}

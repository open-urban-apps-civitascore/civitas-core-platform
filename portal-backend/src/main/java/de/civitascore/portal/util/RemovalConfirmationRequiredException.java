package de.civitascore.portal.util;

import de.civitascore.portal.model.output.DataSinkRemovalImpactDTO;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a request would delete data, Layers or APIs that it does not confirm. */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class RemovalConfirmationRequiredException extends RuntimeException {

  private final transient DataSinkRemovalImpactDTO impact;

  public RemovalConfirmationRequiredException(DataSinkRemovalImpactDTO impact) {
    super(
        "This request deletes Data sinks, Layers or APIs. Repeat it with their ids in"
            + " confirmedRemovalIds to proceed");
    this.impact = impact;
  }
}

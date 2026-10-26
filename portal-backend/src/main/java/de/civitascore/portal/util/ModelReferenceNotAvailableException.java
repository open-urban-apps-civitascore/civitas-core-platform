package de.civitascore.portal.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a data structure model references a model the caller may not use: one that does not
 * exist, belongs to no data structure, may not be read, or is embedded without a version.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ModelReferenceNotAvailableException extends RuntimeException {

  public ModelReferenceNotAvailableException() {
    super(
        "A referenced model is not available: it must exist, be readable, and be referenced with a"
            + " version");
  }
}

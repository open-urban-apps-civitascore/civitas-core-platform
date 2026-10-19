package de.civitascore.portal.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a data structure version is released while its model has no single root Element. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ModelRootMissingException extends RuntimeException {

  public ModelRootMissingException() {
    super("DataStructureVersion model must designate exactly one root Element before releasing");
  }
}

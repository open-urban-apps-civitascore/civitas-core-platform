package de.civitascore.portal.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a DataSet or its sub-entities cannot be written because it is not in DRAFT. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DataSetNotEditableException extends RuntimeException {

  public DataSetNotEditableException(String message) {
    super(message);
  }
}

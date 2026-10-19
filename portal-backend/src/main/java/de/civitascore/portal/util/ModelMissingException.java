package de.civitascore.portal.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ModelMissingException extends RuntimeException {

  public ModelMissingException() {
    super("DataStructureVersion model is missing from the model registry");
  }
}

package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

public class ModelMissingException extends ProblemException {

  public ModelMissingException() {
    super(ProblemType.MODEL_MISSING, ProblemType.MODEL_MISSING.name());
  }
}

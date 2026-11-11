package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/** Thrown when a data structure version is released while its model has no single root Element. */
public class ModelRootMissingException extends ProblemException {

  public ModelRootMissingException() {
    super(ProblemType.MODEL_ROOT_MISSING, ProblemType.MODEL_ROOT_MISSING.name(), null);
  }
}

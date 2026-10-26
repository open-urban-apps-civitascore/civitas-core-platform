package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/**
 * Thrown when a data structure model references a model the caller may not use: one that does not
 * exist, belongs to no data structure, may not be read, or is embedded without a version.
 */
public class ModelReferenceNotAvailableException extends ProblemException {

  public ModelReferenceNotAvailableException() {
    super(
        ProblemType.MODEL_REFERENCE_NOT_AVAILABLE,
        ProblemType.MODEL_REFERENCE_NOT_AVAILABLE.name());
  }
}

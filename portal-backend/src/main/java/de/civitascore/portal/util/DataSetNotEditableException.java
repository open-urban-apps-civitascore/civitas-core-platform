package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/** Thrown when a DataSet or its sub-entities cannot be written because it is not in DRAFT. */
public class DataSetNotEditableException extends ProblemException {

  public DataSetNotEditableException(String message) {
    super(ProblemType.DATASET_NOT_EDITABLE, message, message);
  }
}

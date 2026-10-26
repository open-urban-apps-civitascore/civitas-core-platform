package de.civitascore.portal.model.connector;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/**
 * A Data source request that cannot be read: an unknown key at the top level or in the connector,
 * an unknown or missing type at any level of the connector, or a connector value of the wrong form.
 * The message names only the key path or the type that the client sent, never another value,
 * because a value can carry a credential.
 */
public class MalformedDataSourceRequestException extends ProblemException {

  public MalformedDataSourceRequestException(String message) {
    super(ProblemType.MALFORMED_REQUEST, message, message);
  }
}

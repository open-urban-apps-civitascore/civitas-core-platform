package de.civitascore.portal.model.connector;

/**
 * A connector in a request that cannot be read: an unknown key, an unknown or missing {@code
 * connectorType}, or a value of the wrong form. The message names only the key path or the {@code
 * connectorType} that the client sent, never a value, because a value can carry a credential.
 */
public class InvalidConnectorException extends RuntimeException {

  public InvalidConnectorException(String message) {
    super(message);
  }
}

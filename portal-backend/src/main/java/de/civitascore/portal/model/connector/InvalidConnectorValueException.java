package de.civitascore.portal.model.connector;

/**
 * Thrown by a connector configuration constructor for a value it cannot accept. The message is the
 * key, relative to the object that rejects it, so that {@link ConnectorJson} can name the full key
 * path without the value.
 */
public class InvalidConnectorValueException extends IllegalArgumentException {

  public InvalidConnectorValueException(String key) {
    super(key);
  }

  public InvalidConnectorValueException(String key, Throwable cause) {
    super(key, cause);
  }

  public String key() {
    return getMessage();
  }
}

package de.civitascore.portal.model.connector;

/**
 * A Data source request that cannot be read: an unknown key at the top level or in the connector,
 * an unknown or missing {@code connectorType}, or a connector value of the wrong form. The message
 * names only the key path or the {@code connectorType} that the client sent, never a value, because
 * a value can carry a credential.
 */
public class MalformedDataSourceRequestException extends RuntimeException {

  public MalformedDataSourceRequestException(String message) {
    super(message);
  }
}

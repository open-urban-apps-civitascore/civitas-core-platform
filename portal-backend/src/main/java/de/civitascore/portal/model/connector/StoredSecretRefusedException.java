package de.civitascore.portal.model.connector;

/** A stored secret that may not follow the connector to the requested values. */
public final class StoredSecretRefusedException extends MalformedDataSourceRequestException {

  public StoredSecretRefusedException(String message) {
    super(message);
  }
}

package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;

/** A stored secret that a connection test may not use for the requested values. */
final class StoredSecretRefusedException extends MalformedDataSourceRequestException {

  StoredSecretRefusedException(String message) {
    super(message);
  }
}

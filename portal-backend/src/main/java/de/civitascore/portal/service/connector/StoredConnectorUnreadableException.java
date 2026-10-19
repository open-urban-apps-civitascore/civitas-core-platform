package de.civitascore.portal.service.connector;

import java.util.UUID;

/**
 * The Data source is pinned to a stored connector that the registry does not hold. Reading it as
 * absent would drop every stored secret on the next write.
 */
public class StoredConnectorUnreadableException extends RuntimeException {

  public StoredConnectorUnreadableException(UUID dataSourceId, String urn) {
    super("Stored connector of DataSource " + dataSourceId + " cannot be read: " + urn);
  }
}

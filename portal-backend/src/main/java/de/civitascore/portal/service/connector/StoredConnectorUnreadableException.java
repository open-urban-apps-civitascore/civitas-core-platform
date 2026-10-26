package de.civitascore.portal.service.connector;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.UUID;

/**
 * The Data source is pinned to a stored connector that the registry does not hold. Reading it as
 * absent would drop every stored secret on the next write.
 */
public class StoredConnectorUnreadableException extends ProblemException {

  public StoredConnectorUnreadableException(UUID dataSourceId, String urn) {
    super(
        ProblemType.PERSISTENCE_ERROR,
        "Stored connector of DataSource " + dataSourceId + " cannot be read: " + urn);
  }
}

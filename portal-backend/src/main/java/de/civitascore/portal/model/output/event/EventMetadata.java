package de.civitascore.portal.model.output.event;

import java.util.Map;

/**
 * Metadata for domain events.
 *
 * @param realm the tenant/realm identifier
 * @param correlationIds correlation IDs for request tracing (immutable, can be null)
 */
public record EventMetadata(String realm, Map<String, String> correlationIds) {
  public EventMetadata {
    correlationIds = correlationIds == null ? Map.of() : Map.copyOf(correlationIds);
  }
}

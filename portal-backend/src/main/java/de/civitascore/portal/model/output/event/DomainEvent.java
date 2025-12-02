package de.civitascore.portal.model.output.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event representing a change in an entity.
 *
 * @param eventId unique identifier for this event (for idempotency)
 * @param eventType event type in format "{aggregate}.{operation}" (e.g., "user.created")
 * @param entityId ID of the affected entity
 * @param aggregateType type of the aggregate (e.g., "User")
 * @param operation operation performed (create, update, delete)
 * @param payload the entity data
 * @param metadata additional metadata (realm, correlation IDs)
 * @param schemaVersion version of the event schema (for backwards compatibility)
 * @param timestamp when the event occurred
 */
public record DomainEvent<T>(
    UUID eventId,
    String eventType,
    UUID entityId,
    String aggregateType,
    String operation,
    T payload,
    EventMetadata metadata,
    int schemaVersion,
    Instant timestamp) {}

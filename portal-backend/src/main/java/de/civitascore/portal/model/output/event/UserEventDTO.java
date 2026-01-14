package de.civitascore.portal.model.output.event;

import java.util.UUID;

/**
 * Kafka event representation for User entity.
 *
 * <p>This DTO contains only the essential fields that should be published to Kafka, excluding
 * sensitive or unnecessary data from the full User entity.
 */
public record UserEventDTO(
    UUID id, String firstName, String lastName, String email, Boolean active, String externalId) {}

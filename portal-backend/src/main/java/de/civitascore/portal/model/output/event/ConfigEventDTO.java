package de.civitascore.portal.model.output.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Configuration event sent to config-adapter for processing.
 *
 * <p>This event instructs the config-adapter (e.g., Keycloak) to perform a configuration operation
 * such as creating, updating, or deleting resources.
 *
 * <p>Expected format:
 *
 * <pre>
 * {
 *   "metadata": {
 *     "messageId": "uuid",
 *     "timestamp": "2025-12-03T10:00:00Z",
 *     "source": "civitas.portal-backend",
 *     "correlationId": "uuid",
 *     "resultTopic": "civitas.config.result"
 *   },
 *   "payload": {
 *     "targetComponent": "keycloak",
 *     "targetResource": "realms/civitas/users/user-id",
 *     "operation": "CREATE",
 *     "config": {
 *       "value": { ... user representation ... }
 *     }
 *   }
 * }
 * </pre>
 */
public record ConfigEventDTO(
    @JsonProperty("metadata") Metadata metadata, @JsonProperty("payload") Payload payload) {

  public record Metadata(
      @JsonProperty("messageId") String messageId,
      @JsonProperty("timestamp") OffsetDateTime timestamp,
      @JsonProperty("source") String source,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("configVersion") String configVersion,
      @JsonProperty("resultTopic") String resultTopic) {}

  public record Payload(
      @JsonProperty("targetComponent") String targetComponent,
      @JsonProperty("targetResource") String targetResource,
      @JsonProperty("operation") String operation,
      @JsonProperty("config") Config config) {}

  public record Config(@JsonProperty("value") Object value) {}

  /**
   * Creates a new ConfigEvent for a domain event.
   *
   * @param domainEvent the domain event (user.created, etc.)
   * @param resultTopic the topic where config-adapter should send results
   * @param targetComponent the target component (e.g., "keycloak")
   * @return ConfigEventDTO
   */
  public static ConfigEventDTO fromDomainEvent(
      DomainEvent<?> domainEvent, String resultTopic, String targetComponent) {

    String correlationId = domainEvent.eventId().toString();
    String messageId = UUID.randomUUID().toString();

    // Build target resource path: realms/{realm}/users/{userId}
    String targetResource = buildTargetResource(domainEvent);

    // Map operation: create/update/delete → CREATE/UPDATE/DELETE
    String operation = domainEvent.operation().toUpperCase();

    Metadata metadata =
        new Metadata(
            messageId,
            OffsetDateTime.now(),
            "civitas.portal-backend",
            correlationId,
            "v1.0.0",
            resultTopic);

    Payload payload =
        new Payload(targetComponent, targetResource, operation, new Config(domainEvent.payload()));

    return new ConfigEventDTO(metadata, payload);
  }

  /**
   * Builds the target resource path from the domain event.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>User: "realms/civitas/users/{userId}"
   *   <li>Client: "realms/civitas/clients/{clientId}"
   *   <li>Role: "realms/civitas/roles/{roleId}"
   * </ul>
   */
  private static String buildTargetResource(DomainEvent<?> event) {
    String aggregateType = event.aggregateType().toLowerCase();
    String entityId = event.entityId().toString();

    // Get realm from metadata with null safety
    String realm = "civitas"; // Default
    if (event.metadata() != null && event.metadata().realm() != null) {
      realm = event.metadata().realm();
    }

    // Build resource path
    return switch (aggregateType) {
      case "user" -> String.format("realms/%s/users/%s", realm, entityId);
      case "client" -> String.format("realms/%s/clients/%s", realm, entityId);
      case "role" -> String.format("realms/%s/roles/%s", realm, entityId);
      case "group" -> String.format("realms/%s/groups/%s", realm, entityId);
      case "realm" -> String.format("realms/%s", realm);
      default -> String.format("realms/%s/%ss/%s", realm, aggregateType, entityId);
    };
  }
}

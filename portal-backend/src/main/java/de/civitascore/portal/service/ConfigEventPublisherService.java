package de.civitascore.portal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.portal.messaging.CloudEventPublisher;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for publishing configuration events to the config adapter pipeline via CloudEvents. Wraps
 * domain operations (user, group, role CRUD) as structured {@link ConfigEvent} messages and sends
 * them to Kafka topics. Returns {@link CompletableFuture} handles for tracking the asynchronous
 * config adapter result.
 */
@Service
@Slf4j
public class ConfigEventPublisherService {

  private static final String SOURCE = "portal-backend";
  private static final String CONFIG_VERSION = "1.0";
  private static final String CLOUD_EVENT_TYPE = "de.civitascore.config.event";

  private final Optional<CloudEventPublisher> cloudEventPublisher;
  private final ObjectMapper objectMapper;

  public ConfigEventPublisherService(
      @Autowired(required = false) CloudEventPublisher cloudEventPublisher,
      ObjectMapper objectMapper) {
    this.cloudEventPublisher = Optional.ofNullable(cloudEventPublisher);
    this.objectMapper = objectMapper;

    if (this.cloudEventPublisher.isPresent()) {
      log.info(
          "CloudEvent publisher initialized: {} (ready={})",
          this.cloudEventPublisher.get().getName(),
          this.cloudEventPublisher.get().isReady());
    } else {
      log.warn("No CloudEvent publisher configured - events will be logged but not published");
    }
  }

  /**
   * Publishes a config event to the given topic and returns a future for the adapter's result. If
   * no {@link CloudEventPublisher} is configured, the event is logged but not sent, and the
   * returned future completes with {@code null}.
   *
   * @param topic the Kafka topic to publish to
   * @param targetComponent the target component identifier (e.g., "user", "group")
   * @param targetResource the target resource context (e.g., Keycloak realm)
   * @param operation the CRUD operation type
   * @param configPath the configuration path (e.g., "/users")
   * @param configValue the configuration payload
   * @return a future that completes with the config adapter result, or {@code null} if no publisher
   *     is configured
   */
  public CompletableFuture<ConfigResultEvent> publishConfigEvent(
      Topics topic,
      String targetComponent,
      String targetResource,
      Operation operation,
      String configPath,
      ConfigValue configValue) {

    String messageId = UUID.randomUUID().toString();
    String correlationId = UUID.randomUUID().toString();

    // Get result topic from publisher if available
    String resultTopic = cloudEventPublisher.map(CloudEventPublisher::getResultTopic).orElse(null);

    Metadata metadata =
        new Metadata(
            messageId, OffsetDateTime.now(), SOURCE, correlationId, CONFIG_VERSION, resultTopic);

    Config config = new Config(configPath, configValue);

    Payload payload = new Payload(targetComponent, targetResource, operation, config);

    ConfigEvent configEvent = new ConfigEvent(metadata, payload);

    log.info(
        "Publishing ConfigEvent: topic={}, operation={}, targetComponent={}, targetResource={}, messageId={}, resultTopic={}",
        topic.getValue(),
        operation,
        targetComponent,
        targetResource,
        messageId,
        resultTopic);

    // Convert to CloudEvent
    CloudEvent cloudEvent;
    try {
      cloudEvent = convertToCloudEvent(messageId, topic, configEvent);
    } catch (JsonProcessingException e) {
      log.error(
          "Error converting ConfigEvent: messageId={}, topic={}", messageId, topic.getValue(), e);
      return CompletableFuture.failedFuture(e);
    } catch (RuntimeException e) {
      log.error(
          "Unexpected error creating ConfigEvent: messageId={}, topic={}",
          messageId,
          topic.getValue(),
          e);
      return CompletableFuture.failedFuture(e);
    }

    // Publish to messaging system if available
    if (cloudEventPublisher.isPresent()) {
      CompletableFuture<ConfigResultEvent> future =
          cloudEventPublisher.get().publishAsync(topic.getValue(), messageId, cloudEvent);

      // Add logging callbacks
      future
          .thenAccept(
              result ->
                  log.info(
                      "Config Adapter processed event successfully: messageId={}, operation={}, status={}",
                      messageId,
                      result.operation(),
                      result.status())) //
          .exceptionally(
              ex -> {
                log.error(
                    "Config Adapter processing failed: messageId={}, error={}",
                    messageId,
                    ex.getMessage());
                return null;
              });

      return future;
    } else {
      log.debug(
          "No publisher configured - ConfigEvent logged only: messageId={}, topic={}",
          messageId,
          topic.getValue());
      // Return a completed future with a mock success result
      return CompletableFuture.completedFuture(null);
    }
  }

  private CloudEvent convertToCloudEvent(String messageId, Topics topic, ConfigEvent configEvent)
      throws JsonProcessingException {
    String configEventJson = objectMapper.writeValueAsString(configEvent);

    return CloudEventBuilder.v1()
        .withId(messageId)
        .withSource(URI.create("urn:civitas:portal-backend"))
        .withType(CLOUD_EVENT_TYPE)
        .withDataContentType("application/json")
        .withData(configEventJson.getBytes())
        .withExtension("topic", topic.getValue())
        .withExtension("operation", configEvent.payload().operation().name())
        .withExtension("targetcomponent", configEvent.payload().targetComponent())
        .withExtension("targetresource", configEvent.payload().targetResource())
        .build();
  }

  /**
   * Publishes a USER_CREATED event.
   *
   * @param realm the Keycloak realm name
   * @param userConfig the user configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishUserCreated(
      String realm, ConfigValue userConfig) {
    return publishConfigEvent(
        Topics.USER_CREATED, "user", realm, Operation.CREATE, "/users", userConfig);
  }

  /**
   * Publishes a USER_UPDATED event.
   *
   * @param realm the Keycloak realm name
   * @param userConfig the user configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishUserUpdated(
      String realm, ConfigValue userConfig) {
    return publishConfigEvent(
        Topics.USER_UPDATED, "user", realm, Operation.UPDATE, "/users", userConfig);
  }

  /**
   * Publishes a USER_DELETED event.
   *
   * @param realm the Keycloak realm name
   * @param userConfig the user configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishUserDeleted(
      String realm, ConfigValue userConfig) {
    return publishConfigEvent(
        Topics.USER_DELETED, "user", realm, Operation.DELETE, "/users", userConfig);
  }

  /**
   * Publishes a GROUP_CREATED event.
   *
   * @param realm the Keycloak realm name
   * @param groupConfig the group configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishGroupCreated(
      String realm, ConfigValue groupConfig) {
    return publishConfigEvent(
        Topics.GROUP_CREATED, "group", realm, Operation.CREATE, "/groups", groupConfig);
  }

  /**
   * Publishes a GROUP_UPDATED event.
   *
   * @param realm the Keycloak realm name
   * @param groupConfig the group configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishGroupUpdated(
      String realm, ConfigValue groupConfig) {
    return publishConfigEvent(
        Topics.GROUP_UPDATED, "group", realm, Operation.UPDATE, "/groups", groupConfig);
  }

  /**
   * Publishes a GROUP_DELETED event.
   *
   * @param realm the Keycloak realm name
   * @param groupConfig the group configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishGroupDeleted(
      String realm, ConfigValue groupConfig) {
    return publishConfigEvent(
        Topics.GROUP_DELETED, "group", realm, Operation.DELETE, "/groups", groupConfig);
  }

  /**
   * Publishes a ROLE_CREATED event.
   *
   * @param realm the Keycloak realm name
   * @param roleConfig the role configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishRoleCreated(
      String realm, ConfigValue roleConfig) {
    return publishConfigEvent(
        Topics.ROLE_CREATED, "role", realm, Operation.CREATE, "/roles", roleConfig);
  }

  /**
   * Publishes a ROLE_UPDATED event.
   *
   * @param realm the Keycloak realm name
   * @param roleConfig the role configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishRoleUpdated(
      String realm, ConfigValue roleConfig) {
    return publishConfigEvent(
        Topics.ROLE_UPDATED, "role", realm, Operation.UPDATE, "/roles", roleConfig);
  }

  /**
   * Publishes a ROLE_DELETED event.
   *
   * @param realm the Keycloak realm name
   * @param roleConfig the role configuration payload
   * @return a future for the config adapter result
   */
  public CompletableFuture<ConfigResultEvent> publishRoleDeleted(
      String realm, ConfigValue roleConfig) {
    return publishConfigEvent(
        Topics.ROLE_DELETED, "role", realm, Operation.DELETE, "/roles", roleConfig);
  }
}

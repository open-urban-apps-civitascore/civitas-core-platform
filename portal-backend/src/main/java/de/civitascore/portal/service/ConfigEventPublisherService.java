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

  public CompletableFuture<ConfigResultEvent> publishUserCreated(
      String realm, ConfigValue userConfig) {
    return publishConfigEvent(
        Topics.USER_CREATED, "user", realm, Operation.CREATE, "/users", userConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishUserUpdated(
      String realm, ConfigValue userConfig) {
    return publishConfigEvent(
        Topics.USER_UPDATED, "user", realm, Operation.UPDATE, "/users", userConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishUserDeleted(
      String realm, ConfigValue userConfig) {
    return publishConfigEvent(
        Topics.USER_DELETED, "user", realm, Operation.DELETE, "/users", userConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishGroupCreated(
      String realm, ConfigValue groupConfig) {
    return publishConfigEvent(
        Topics.GROUP_CREATED, "group", realm, Operation.CREATE, "/groups", groupConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishGroupUpdated(
      String realm, ConfigValue groupConfig) {
    return publishConfigEvent(
        Topics.GROUP_UPDATED, "group", realm, Operation.UPDATE, "/groups", groupConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishGroupDeleted(
      String realm, ConfigValue groupConfig) {
    return publishConfigEvent(
        Topics.GROUP_DELETED, "group", realm, Operation.DELETE, "/groups", groupConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishRoleCreated(
      String realm, ConfigValue roleConfig) {
    return publishConfigEvent(
        Topics.ROLE_CREATED, "role", realm, Operation.CREATE, "/roles", roleConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishRoleUpdated(
      String realm, ConfigValue roleConfig) {
    return publishConfigEvent(
        Topics.ROLE_UPDATED, "role", realm, Operation.UPDATE, "/roles", roleConfig);
  }

  public CompletableFuture<ConfigResultEvent> publishRoleDeleted(
      String realm, ConfigValue roleConfig) {
    return publishConfigEvent(
        Topics.ROLE_DELETED, "role", realm, Operation.DELETE, "/roles", roleConfig);
  }
}

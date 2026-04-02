package de.civitascore.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.keycloak.KeycloakAdapter;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Helper class to run the Config Adapter in-process during integration tests.
 *
 * <p>This sets up: - KeycloakAdapter to process config events - Kafka consumer to listen to
 * user/group/role topics - Event publisher to send results back to the result topic
 */
@Slf4j
public class ConfigAdapterTestHelper implements AutoCloseable {

  private final KeycloakAdapter keycloakAdapter;
  private final KafkaMessageListenerContainer<String, CloudEvent> kafkaConsumer;
  private final ObjectMapper objectMapper;

  public ConfigAdapterTestHelper(
      KeycloakContainer keycloakContainer,
      String kafkaBrokers,
      KafkaTemplate<String, String> kafkaTemplate) {

    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());

    log.info("Initializing Config Adapter test helper");

    // Configure Keycloak adapter
    Map<String, Object> props = new HashMap<>();
    props.put("keycloak.url", keycloakContainer.getAuthServerUrl());
    props.put("keycloak.realm", "master");
    props.put("keycloak.username", keycloakContainer.getAdminUsername());
    props.put("keycloak.password", keycloakContainer.getAdminPassword());
    props.put("keycloak.client.id", "admin-cli");
    props.put(
        "keycloak.topics",
        String.join(
            ",",
            Topics.USER_CREATED.getValue(),
            Topics.USER_UPDATED.getValue(),
            Topics.USER_DELETED.getValue()));

    AppConfig config = new AppConfig(new MapConfiguration(props));

    // Create and initialize adapter
    keycloakAdapter = new KeycloakAdapter();
    keycloakAdapter.initialize(config);

    // Set up event publisher to send results back
    keycloakAdapter.setEventPublisher(new TestEventPublisher(kafkaTemplate, objectMapper));

    // Set up Kafka consumer to listen for events
    kafkaConsumer = createKafkaConsumer(kafkaBrokers);
    kafkaConsumer.start();

    // Wait for consumer to be ready
    ContainerTestUtils.waitForAssignment(kafkaConsumer, 3);

    log.info("Config Adapter test helper initialized and listening for events");
  }

  private KafkaMessageListenerContainer<String, CloudEvent> createKafkaConsumer(
      String kafkaBrokers) {
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(kafkaBrokers, "config-adapter-test-group", "false");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    DefaultKafkaConsumerFactory<String, CloudEvent> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    ContainerProperties containerProperties =
        new ContainerProperties(
            Topics.USER_CREATED.getValue(),
            Topics.USER_UPDATED.getValue(),
            Topics.USER_DELETED.getValue());

    KafkaMessageListenerContainer<String, CloudEvent> container =
        new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

    container.setupMessageListener(
        (MessageListener<String, CloudEvent>)
            record -> {
              try {
                log.debug("Config Adapter received event on topic: {}", record.topic());

                CloudEvent cloudEvent = record.value();

                // Extract ConfigEvent from CloudEvent data
                String configEventJson = new String(cloudEvent.getData().toBytes());
                ConfigEvent configEvent =
                    objectMapper.readValue(configEventJson, ConfigEvent.class);

                log.debug(
                    "Processing ConfigEvent: messageId={}, operation={}",
                    configEvent.metadata().messageId(),
                    configEvent.payload().operation());

                // Process event through adapter
                keycloakAdapter.processConfigEvent(record.topic(), configEvent);

              } catch (Exception e) {
                log.error("Failed to process event in Config Adapter: {}", e.getMessage(), e);
              }
            });

    return container;
  }

  @Override
  public void close() {
    log.info("Shutting down Config Adapter test helper");

    if (kafkaConsumer != null) {
      try {
        kafkaConsumer.stop();
      } catch (Exception e) {
        log.warn("Error stopping Kafka consumer: {}", e.getMessage());
      }
    }

    if (keycloakAdapter != null) {
      try {
        keycloakAdapter.close();
      } catch (Exception e) {
        log.warn("Error closing Keycloak adapter: {}", e.getMessage());
      }
    }

    log.info("Config Adapter test helper shut down");
  }

  /** Test event publisher that sends results back to Kafka */
  private static class TestEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TestEventPublisher(
        KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
      this.kafkaTemplate = kafkaTemplate;
      this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String topic, ConfigResultEvent result) {
      try {
        String resultJson = objectMapper.writeValueAsString(result);
        kafkaTemplate.send("de.civitascore.config.results", result.originalMessageId(), resultJson);
        log.debug(
            "Published result for messageId: {}, status: {}",
            result.originalMessageId(),
            result.status());
      } catch (Exception e) {
        log.error("Failed to publish result: {}", e.getMessage(), e);
      }
    }

    @Override
    public void initialize(
        de.civitascore.configadapter.configuration.ApplicationConfig config,
        de.civitascore.configadapter.adapter.ConfigAdapter adapter) {
      // No initialization needed for test
    }

    @Override
    public String getName() {
      return "test-event-publisher";
    }

    @Override
    public void close() {
      // Nothing to close
    }
  }
}

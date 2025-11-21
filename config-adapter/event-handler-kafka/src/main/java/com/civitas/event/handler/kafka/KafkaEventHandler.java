package com.civitas.event.handler.kafka;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.core.CloudEventProcessor;
import com.civitas.configadapter.messaging.EventConsumer;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigResultEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaEventHandler implements EventConsumer, EventPublisher {

  private static final Logger logger = LoggerFactory.getLogger(KafkaEventHandler.class);

  private static final String KAFKA_GROUP_ID = "kafka.group.id";
  private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

  private final KafkaConsumer<String, CloudEvent> kafkaConsumer;
  private final KafkaProducer<String, CloudEvent> kafkaProducer;
  private final ConfigAdapter adapter;
  private final CloudEventProcessor processor;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread consumerThread;

  public KafkaEventHandler(AdapterConfig config, ConfigAdapter adapter) {
    this.adapter = adapter;
    this.processor = new CloudEventProcessor(adapter);

    Properties props = new Properties();
    String kafkaServerUrl = config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092");
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServerUrl);
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG, config.getProperty(KAFKA_GROUP_ID, "config-adapter-group"));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");

    this.kafkaConsumer = new KafkaConsumer<>(props);

    List<String> topicList = new ArrayList<>(adapter.getSubscribedTopics());
    kafkaConsumer.subscribe(topicList);

    // Initialize producer for publishing events
    Properties producerProps = new Properties();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServerUrl);
    producerProps.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringSerializer.class.getName());
    producerProps.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
    producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    // Don't set encoding - let CloudEventSerializer use its default behavior
    // The serializer will choose binary or structured mode based on the CloudEvent

    this.kafkaProducer = new KafkaProducer<>(producerProps);

    // Inject this publisher into the adapter
    adapter.setEventPublisher(this);

    logger.info(
        "Kafka {} event consumer initialized for adapter {} with {} topic(s): {}",
        kafkaServerUrl,
        adapter.getClass().getSimpleName(),
        topicList.size(),
        topicList);
  }

  @Override
  public void start() {
    if (running.compareAndSet(false, true)) {
      consumerThread = Thread.ofVirtual().start(this::consume);
      logger.info("Kafka event consumer started");
    }
  }

  private void consume() {
    logger.info("Starting consumption loop");
    while (running.get()) {
      try {
        ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofMillis(1000));

        records.forEach(
            record -> {
              try {
                processor.handleEvent(record.topic(), record.value());
              } catch (Exception e) {
                logger.error("Error handling CloudEvent: {}", record.value().getId(), e);
              }
            });
      } catch (Exception e) {
        logger.error("Error consuming messages", e);
        if (!running.get()) {
          break;
        }
      }
    }
    logger.info("Consumption loop ended");
  }

  public void stop() {
    logger.info("Stopping Kafka event consumer");
    running.set(false);
    if (consumerThread != null) {
      try {
        consumerThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void publish(String topic, ConfigResultEvent resultEvent) {
    CloudEvent cloudEvent = convertToCloudEvent(resultEvent);
    try {
      ProducerRecord<String, CloudEvent> record =
          new ProducerRecord<>(topic, cloudEvent.getId(), cloudEvent);

      kafkaProducer.send(
          record,
          (metadata, exception) -> {
            if (exception != null) {
              logger.error(
                  "Failed to publish result event {} to topic {}",
                  cloudEvent.getId(),
                  topic,
                  exception);
            } else {
              logger.debug(
                  "Published result event {} to topic {} partition {} offset {}",
                  cloudEvent.getId(),
                  metadata.topic(),
                  metadata.partition(),
                  metadata.offset());
            }
          });
    } catch (Exception e) {
      logger.error("Error publishing result event {} to topic {}", cloudEvent.getId(), topic, e);
    }
  }

  /**
   * Converts a ConfigResultEvent to a CloudEvent for transmission over Kafka.
   *
   * @param resultEvent the configuration result event
   * @return a CloudEvent representation
   */
  private CloudEvent convertToCloudEvent(ConfigResultEvent resultEvent) {
    CloudEventBuilder builder =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create(resultEvent.source()))
            .withType("core.civitas.idm.processing.result")
            .withTime(resultEvent.timestamp())
            .withData("application/json", "{}".getBytes())
            .withExtension("correlationid", resultEvent.correlationId())
            .withExtension("originalmessageid", resultEvent.originalMessageId())
            .withExtension("status", resultEvent.status().name())
            .withExtension("operation", resultEvent.operation())
            .withExtension("targetresource", resultEvent.targetResource());

    if (resultEvent.message() != null) {
      builder.withExtension("message", resultEvent.message());
    }

    if (resultEvent.status() == ConfigResultEvent.Status.SUCCESS
        && resultEvent.resourceId() != null) {
      builder.withExtension("resourceid", resultEvent.resourceId());
    }

    if (resultEvent.status() == ConfigResultEvent.Status.FAILURE
        && resultEvent.errorCode() != null) {
      builder.withExtension("errorcode", resultEvent.errorCode());
    }

    if (resultEvent.status() == ConfigResultEvent.Status.FAILURE && resultEvent.message() != null) {
      builder.withExtension("errormessage", resultEvent.message());
    }

    return builder.build();
  }

  @Override
  public void close() {
    stop();
    try {
      kafkaProducer.flush();
      kafkaProducer.close();
      logger.info("Kafka producer closed");
    } catch (Exception e) {
      logger.error("Error closing Kafka producer", e);
    }
    try {
      kafkaConsumer.close();
      logger.info("Kafka consumer closed");
    } catch (Exception e) {
      logger.error("Error closing Kafka consumer", e);
    }
    try {
      adapter.close();
      logger.info("Adapter closed");
    } catch (Exception e) {
      logger.error("Error closing adapter", e);
    }
  }
}

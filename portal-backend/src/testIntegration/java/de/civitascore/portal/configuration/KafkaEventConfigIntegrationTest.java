package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.messaging.saga.InvalidSagaResultException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@EmbeddedKafka(
    partitions = 1,
    topics = {
      KafkaEventConfigIntegrationTest.RETRYABLE_TOPIC,
      KafkaEventConfigIntegrationTest.NOT_RETRYABLE_TOPIC,
      KafkaEventConfigIntegrationTest.DEAD_LETTERED_TOPIC,
      KafkaEventConfigIntegrationTest.DEAD_LETTERED_TOPIC + "-dlt"
    })
class KafkaEventConfigIntegrationTest {

  static final String RETRYABLE_TOPIC = "saga.result.retryable";
  static final String NOT_RETRYABLE_TOPIC = "saga.result.not-retryable";
  static final String DEAD_LETTERED_TOPIC = "saga.result.dead-lettered";
  private static final String GROUP_ID = "saga-error-handler-test";
  private static final String UNPROCESSABLE = "unprocessable";
  private static final String NEXT = "next";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Test
  void sagaErrorHandler_whenDeadLetterPublishFails_commitsPastTheRecord(EmbeddedKafkaBroker broker)
      throws Exception {
    List<String> deliveries =
        consume(
            broker,
            RETRYABLE_TOPIC,
            () -> new IllegalStateException("dataset service unavailable"));

    assertThat(deliveries)
        .containsExactly(UNPROCESSABLE, UNPROCESSABLE, UNPROCESSABLE, UNPROCESSABLE, NEXT);
  }

  @Test
  void sagaErrorHandler_whenResultIsInvalid_skipsRetries(EmbeddedKafkaBroker broker)
      throws Exception {
    List<String> deliveries =
        consume(
            broker, NOT_RETRYABLE_TOPIC, () -> new InvalidSagaResultException("malformed", null));

    assertThat(deliveries).containsExactly(UNPROCESSABLE, NEXT);
  }

  @Test
  void sagaErrorHandler_whenRetriesAreExhausted_publishesToDeadLetterTopic(
      EmbeddedKafkaBroker broker) throws Exception {
    List<String> deliveries =
        consume(
            broker,
            DEAD_LETTERED_TOPIC,
            new KafkaTemplate<>(producerFactory(broker)),
            () -> new IllegalStateException("dataset service unavailable"));

    assertThat(deliveries)
        .containsExactly(UNPROCESSABLE, UNPROCESSABLE, UNPROCESSABLE, UNPROCESSABLE, NEXT);
    try (Consumer<String, String> consumer = deadLetterConsumer(broker)) {
      assertThat(KafkaTestUtils.getSingleRecord(consumer, DEAD_LETTERED_TOPIC + "-dlt", TIMEOUT))
          .extracting(ConsumerRecord::value)
          .isEqualTo(UNPROCESSABLE);
    }
  }

  private List<String> consume(
      EmbeddedKafkaBroker broker, String topic, Supplier<RuntimeException> failure)
      throws Exception {
    return consume(broker, topic, deadLetterTemplateThatFails(broker), failure);
  }

  /** Sends an unprocessable record, then a processable one, and returns every delivery. */
  private List<String> consume(
      EmbeddedKafkaBroker broker,
      String topic,
      KafkaTemplate<String, String> deadLetterTemplate,
      Supplier<RuntimeException> failure)
      throws Exception {
    List<String> deliveries = new CopyOnWriteArrayList<>();
    ConcurrentMessageListenerContainer<String, String> container =
        new KafkaEventConfig(kafkaProperties(broker))
            .sagaKafkaListenerContainerFactory(deadLetterTemplate)
            .createContainer(topic);
    container.getContainerProperties().setGroupId(GROUP_ID + "-" + topic);
    container.setupMessageListener(
        (MessageListener<String, String>)
            record -> {
              deliveries.add(record.value());
              if (UNPROCESSABLE.equals(record.value())) {
                throw failure.get();
              }
            });
    container.start();
    try {
      ContainerTestUtils.waitForAssignment(container, 1);
      try (Producer<String, String> producer = producerFactory(broker).createProducer()) {
        producer.send(new ProducerRecord<>(topic, UNPROCESSABLE)).get();
        producer.send(new ProducerRecord<>(topic, NEXT)).get();
      }
      await().atMost(TIMEOUT).until(() -> committedOffset(broker, topic) == 2);
    } finally {
      container.stop();
    }
    return deliveries;
  }

  private static KafkaProperties kafkaProperties(EmbeddedKafkaBroker broker) {
    KafkaProperties properties = new KafkaProperties();
    properties.setBootstrapServers(List.of(broker.getBrokersAsString()));
    return properties;
  }

  private static ProducerFactory<String, String> producerFactory(EmbeddedKafkaBroker broker) {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
  }

  private static Consumer<String, String> deadLetterConsumer(EmbeddedKafkaBroker broker) {
    Map<String, Object> props =
        KafkaTestUtils.consumerProps(broker, GROUP_ID + "-dead-letter-reader", false);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    broker.consumeFromAnEmbeddedTopic(consumer, DEAD_LETTERED_TOPIC + "-dlt");
    return consumer;
  }

  private static KafkaTemplate<String, String> deadLetterTemplateThatFails(
      EmbeddedKafkaBroker broker) {
    return new KafkaTemplate<>(producerFactory(broker)) {
      @Override
      public CompletableFuture<SendResult<String, String>> send(
          ProducerRecord<String, String> record) {
        return CompletableFuture.failedFuture(new KafkaException("dead-letter topic unavailable"));
      }
    };
  }

  private static long committedOffset(EmbeddedKafkaBroker broker, String topic) throws Exception {
    try (Admin admin =
        Admin.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
      OffsetAndMetadata committed =
          admin
              .listConsumerGroupOffsets(GROUP_ID + "-" + topic)
              .partitionsToOffsetAndMetadata()
              .get()
              .get(new TopicPartition(topic, 0));
      return committed == null ? -1 : committed.offset();
    }
  }
}

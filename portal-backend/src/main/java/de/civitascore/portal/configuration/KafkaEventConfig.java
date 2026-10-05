package de.civitascore.portal.configuration;

import de.civitascore.portal.messaging.saga.InvalidSagaResultException;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka producer and consumer configuration for CloudEvent publishing and saga result processing.
 * Configures an idempotent producer with compression and a consumer factory with dead-letter queue
 * support.
 */
@Slf4j
@Configuration
public class KafkaEventConfig {

  private static final long RETRY_INTERVAL_MS = 1000L;
  private static final long MAX_RETRIES = 3;

  private final KafkaProperties kafkaProperties;

  private static final Set<String> VALID_COMPRESSION_TYPES =
      Set.of("none", "gzip", "snappy", "lz4", "zstd");

  public KafkaEventConfig(KafkaProperties kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
  }

  /** Validates the configured Kafka compression type on startup, failing fast if unsupported. */
  @PostConstruct
  void validateCompressionType() {
    // Mirror buildProducerProperties() precedence: the raw properties map is merged last and wins,
    // so validate the map value first and fall back to the typed field. Any other order can pass a
    // valid typed value while the producer runs the invalid map value, deferring the failure to the
    // first send() instead of failing fast at boot.
    Object nativeType = kafkaProperties.getProducer().getProperties().get("compression.type");
    String compressionType =
        nativeType != null
            ? nativeType.toString()
            : kafkaProperties.getProducer().getCompressionType();
    if (compressionType == null) {
      compressionType = "none";
    }
    if (!VALID_COMPRESSION_TYPES.contains(compressionType)) {
      throw new IllegalArgumentException(
          "Invalid kafka compression type: '%s'. Valid values: %s"
              .formatted(compressionType, VALID_COMPRESSION_TYPES));
    }
  }

  /**
   * Creates an idempotent Kafka producer template with {@code acks=all}, configurable compression,
   * and batching for CloudEvent publishing.
   */
  @Bean
  public KafkaTemplate<String, String> eventKafkaTemplate() {
    Map<String, Object> props = kafkaProperties.buildProducerProperties();

    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  /**
   * Kafka listener container factory for the saga result and pipeline status listeners. A failed
   * record is retried 3 times, 1 second apart, then published to {@code <original-topic>-dlt}. An
   * {@link InvalidSagaResultException} skips the retries. If the publish fails, the record is
   * discarded.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> sagaKafkaListenerContainerFactory(
      KafkaTemplate<String, String> eventKafkaTemplate) {

    Map<String, Object> consumerProps = new HashMap<>();
    consumerProps.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    DefaultKafkaConsumerFactory<String, String> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(
        sagaErrorHandler(new DeadLetterPublishingRecoverer(eventKafkaTemplate)));
    return factory;
  }

  static DefaultErrorHandler sagaErrorHandler(ConsumerRecordRecoverer deadLetterRecoverer) {
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            discardIfRecoveryFails(deadLetterRecoverer),
            new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    errorHandler.addNotRetryableExceptions(InvalidSagaResultException.class);
    return errorHandler;
  }

  /**
   * A recoverer that throws makes the error handler seek back and start the retries again, with no
   * limit. Catching the failure ends that cycle at the cost of the record.
   */
  private static ConsumerRecordRecoverer discardIfRecoveryFails(ConsumerRecordRecoverer recoverer) {
    return (record, exception) -> {
      try {
        recoverer.accept(record, exception);
        log.error(
            "Sent unprocessable record {} to the dead-letter topic",
            KafkaUtils.format(record),
            exception);
      } catch (RuntimeException recoveryFailure) {
        exception.addSuppressed(recoveryFailure);
        log.error(
            "Discarded unprocessable record {}, the dead-letter publish failed",
            KafkaUtils.format(record),
            exception);
      }
    };
  }
}

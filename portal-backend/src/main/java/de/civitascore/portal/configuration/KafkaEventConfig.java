package de.civitascore.portal.configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka producer and consumer configuration for CloudEvent publishing and saga result processing.
 * Configures an idempotent producer with compression and a consumer factory with dead-letter queue
 * support.
 */
@Configuration
public class KafkaEventConfig {

  private static final Set<String> VALID_COMPRESSION_TYPES =
      Set.of("none", "gzip", "snappy", "lz4", "zstd");

  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  @Value("${spring.kafka.producer.batch-size:16384}")
  private int batchSize;

  @Value("${spring.kafka.producer.properties.linger.ms:10}")
  private int lingerMs;

  @Value("${spring.kafka.producer.properties.compression.type:snappy}")
  private String compressionType;

  @Value("${spring.kafka.producer.buffer-memory:33554432}")
  private long bufferMemory;

  /** Validates the configured Kafka compression type on startup, failing fast if unsupported. */
  @PostConstruct
  void validateCompressionType() {
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
    Map<String, Object> props = new HashMap<>();

    // Connection
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    // Serialization
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    // Reliability - Ensures no duplicate messages
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

    // Performance - Compression reduces network bandwidth
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
    props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);

    // Buffer
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);

    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  /**
   * Kafka listener container factory for the saga result listener with dead-letter queue (DLQ)
   * support. Failed messages are retried up to 3 times with a 1-second interval, then published to
   * {@code <original-topic>.DLT} for manual inspection. This prevents processing failures (e.g.,
   * dataset not found, deserialization errors) from being silently dropped.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> sagaKafkaListenerContainerFactory(
      KafkaTemplate<String, String> eventKafkaTemplate) {

    Map<String, Object> consumerProps = new HashMap<>();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    DefaultKafkaConsumerFactory<String, String> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(eventKafkaTemplate);
    // 3 retries, 1 second apart; on exhaustion the message is sent to <topic>.DLT
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }
}

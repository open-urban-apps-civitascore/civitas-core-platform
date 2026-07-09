package de.civitascore.portal.configuration;

import de.civitascore.portal.messaging.CloudEventPublisher;
import de.civitascore.portal.messaging.kafka.KafkaCloudEventPublisher;
import io.cloudevents.kafka.CloudEventSerializer;
import jakarta.validation.Valid;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Slf4j
public class KafkaEventPublisherConfig {

  @Bean
  @ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
  CloudEventPublisher kafkaCloudEventPublisher(@Valid KafkaConfigProperties kafkaProperties) {

    log.info(
        "Initializing Kafka CloudEvent publisher with bootstrap servers: {}",
        kafkaProperties.getBootstrapServers());

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, kafkaProperties.getAcks());
    props.put(ProducerConfig.RETRIES_CONFIG, kafkaProperties.getRetries());
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    KafkaProducer<String, io.cloudevents.CloudEvent> kafkaProducer = new KafkaProducer<>(props);
    return new KafkaCloudEventPublisher(
        kafkaProducer, kafkaProperties.getResultTopic(), kafkaProperties.getResultTimeoutMs());
  }

  @Bean
  @ConfigurationProperties(prefix = "kafka")
  @Validated
  KafkaConfigProperties kafkaConfigProperties() {
    return new KafkaConfigProperties();
  }

  public static class KafkaConfigProperties {
    private boolean enabled = false;
    private String bootstrapServers = "localhost:9092";
    private String acks = "all";
    private int retries = 3;
    private String resultTopic = "de.civitascore.config.results";
    private long resultTimeoutMs = 30000;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBootstrapServers() {
      return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
      this.bootstrapServers = bootstrapServers;
    }

    public String getAcks() {
      return acks;
    }

    public void setAcks(String acks) {
      this.acks = acks;
    }

    public int getRetries() {
      return retries;
    }

    public void setRetries(int retries) {
      this.retries = retries;
    }

    public String getResultTopic() {
      return resultTopic;
    }

    public void setResultTopic(String resultTopic) {
      this.resultTopic = resultTopic;
    }

    public long getResultTimeoutMs() {
      return resultTimeoutMs;
    }

    public void setResultTimeoutMs(long resultTimeoutMs) {
      this.resultTimeoutMs = resultTimeoutMs;
    }
  }
}

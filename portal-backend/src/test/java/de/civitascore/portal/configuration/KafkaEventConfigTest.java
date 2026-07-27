package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

@DisplayName("KafkaEventConfig Tests")
class KafkaEventConfigTest {

  private static Map<String, Object> producerConfigOf(KafkaProperties properties) {
    return new KafkaEventConfig(properties)
        .eventKafkaTemplate()
        .getProducerFactory()
        .getConfigurationProperties();
  }

  @Test
  @DisplayName(
      "compression.type binds from spring.kafka.producer.properties (the key CI and prod set)")
  void compressionTypeBindsFromProducerPropertiesMap() {
    KafkaProperties properties = new KafkaProperties();
    properties.getProducer().getProperties().put("compression.type", "snappy");

    assertThat(producerConfigOf(properties))
        .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
  }

  @Test
  @DisplayName("producer.properties.compression.type overrides producer.compression-type")
  void producerPropertiesOverrideTypedCompressionType() {
    KafkaProperties properties = new KafkaProperties();
    properties.getProducer().setCompressionType("lz4");
    properties.getProducer().getProperties().put("compression.type", "snappy");

    // Spring's buildProducerProperties() merges the raw properties map last, so it wins.
    assertThat(producerConfigOf(properties))
        .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
  }

  @Test
  @DisplayName("eventKafkaTemplate keeps idempotence enabled")
  void eventTemplateEnablesIdempotence() {
    assertThat(producerConfigOf(new KafkaProperties()))
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
  }

  @Test
  @DisplayName("startup validation rejects an unsupported compression codec")
  void validationRejectsUnsupportedCodec() {
    KafkaProperties properties = new KafkaProperties();
    properties.getProducer().getProperties().put("compression.type", "brotli");

    assertThatThrownBy(() -> new KafkaEventConfig(properties).validateCompressionType())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("brotli");
  }

  @Test
  @DisplayName("validation checks the codec the producer actually uses (properties map wins)")
  void validationHonoursProducerPropertiesPrecedence() {
    KafkaProperties properties = new KafkaProperties();
    properties.getProducer().setCompressionType("lz4");
    properties.getProducer().getProperties().put("compression.type", "brotli");

    // The bean would be built with the invalid map value, so validation must reject it at boot
    // rather than passing on the valid typed field and failing later at the first send().
    assertThatThrownBy(() -> new KafkaEventConfig(properties).validateCompressionType())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("brotli");
  }
}

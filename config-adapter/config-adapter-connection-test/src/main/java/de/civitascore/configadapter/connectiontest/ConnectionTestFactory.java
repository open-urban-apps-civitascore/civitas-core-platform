/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestTopics;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the connection test from the {@code connectiontest.*} properties. */
public final class ConnectionTestFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionTestFactory.class);

  private static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";

  /** The value of a list property that holds nothing; an empty env var would read as unset. */
  static final String NONE = "none";

  private ConnectionTestFactory() {}

  /**
   * @return the consumer, not yet started
   * @throws IllegalArgumentException if a property has an invalid value
   */
  public static ConnectionTestConsumer create(AdapterConfig config) {
    ConnectionTestSettings settings =
        new ConnectionTestSettings(
            Duration.ofSeconds(
                Long.parseLong(config.getProperty("connectiontest.timeout-seconds", "15"))),
            Integer.parseInt(config.getProperty("connectiontest.max-concurrent", "4")));
    DestinationGuard guard =
        new DestinationGuard(
            deniedNetworks(config.getProperty("connectiontest.denied-networks", NONE)),
            InetAddress::getAllByName);

    // Stretched once: PBKDF2 with 600k iterations is too slow to repeat per test.
    byte[] stretchedKey = CryptoKeyLoader.loadAndStretchKeyFromEnv(MASTER_KEY_ENV);
    if (stretchedKey.length == 0) {
      LOG.warn("{} is not set; connection tests with a password will fail", MASTER_KEY_ENV);
    }
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(
                new SqlProber(guard, settings.clientTimeout()),
                new MqttProber(guard, settings.clientTimeout()),
                new HttpProber(guard, settings.clientTimeout())),
            connector ->
                CredentialDecryptor.decryptMapValues(
                    connector, stretchedKey, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT));

    String bootstrap = config.getProperty("kafka.bootstrap.servers", "localhost:9092");
    String groupId =
        config.getProperty("connectiontest.kafka.group.id", "config-adapter-connection-test");
    String requestTopic =
        config.getProperty("connectiontest.kafka.request-topic", ConnectionTestTopics.REQUEST);
    String resultTopic =
        config.getProperty("connectiontest.kafka.result-topic", ConnectionTestTopics.RESULT);
    LOG.info(
        "Connection test: timeout {} s, {} at a time, group {}, topics {} -> {}",
        settings.timeout().toSeconds(),
        settings.maxConcurrent(),
        groupId,
        requestTopic,
        resultTopic);
    return new ConnectionTestConsumer(
        new KafkaConsumer<>(consumerProperties(bootstrap, groupId)),
        new KafkaProducer<>(producerProperties(bootstrap)),
        requestTopic,
        resultTopic,
        service,
        settings,
        Clock.systemUTC());
  }

  static List<DestinationGuard.Network> deniedNetworks(String value) {
    return list(value).stream().map(DestinationGuard.Network::parse).toList();
  }

  /** A comma-separated list; {@value #NONE} stands for the empty one. */
  static List<String> list(String value) {
    if (NONE.equals(value.trim().toLowerCase(Locale.ROOT))) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
  }

  private static Properties consumerProperties(String bootstrap, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    // A new group starts at the end: a request from before its start has no caller left.
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    // A crashed pod cannot leave the group, so a short session timeout frees its partitions.
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000");
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000");
    return props;
  }

  private static Properties producerProperties(String bootstrap) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    return props;
  }
}

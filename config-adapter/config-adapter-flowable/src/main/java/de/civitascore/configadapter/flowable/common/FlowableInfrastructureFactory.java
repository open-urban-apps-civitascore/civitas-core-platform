/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.Properties;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Creates infrastructure dependencies (DataSource, Kafka clients) for the Flowable orchestrator.
 * Separated from {@link FlowableSagaOrchestrator} to follow SRP.
 *
 * <p>Configuration properties (all may be overridden via env vars, dots → underscores, uppercase):
 *
 * <ul>
 *   <li>{@code flowable.jdbc.url} — JDBC URL for the Flowable database (required, no default).
 *   <li>{@code flowable.jdbc.username} — DB user (required, no default).
 *   <li>{@code flowable.jdbc.password} — DB password (required, no default).
 *   <li>{@code kafka.bootstrap.servers} — Kafka bootstrap servers, default {@code localhost:9092}.
 *   <li>{@code flowable.kafka.group.id} — Consumer group id, default {@code
 *       config-adapter-flowable-group}.
 * </ul>
 */
final class FlowableInfrastructureFactory {

  private static final String PROP_JDBC_URL = "flowable.jdbc.url";
  private static final String PROP_JDBC_USERNAME = "flowable.jdbc.username";
  private static final String PROP_JDBC_PASSWORD = "flowable.jdbc.password";
  private static final String PROP_KAFKA_BOOTSTRAP = "kafka.bootstrap.servers";
  private static final String PROP_KAFKA_GROUP_ID = "flowable.kafka.group.id";
  private static final String DEFAULT_KAFKA_BOOTSTRAP = "localhost:9092";
  private static final String DEFAULT_KAFKA_GROUP_ID = "config-adapter-flowable-group";
  private static final int HIKARI_MAX_POOL_SIZE = 5;

  private FlowableInfrastructureFactory() {}

  /**
   * Creates a HikariCP DataSource from configuration. Fails if JDBC properties are not set.
   *
   * @throws IllegalStateException if required JDBC properties are missing
   */
  static HikariDataSource createDataSource(AdapterConfig config) {
    HikariConfig hikariConfig = new HikariConfig();
    String jdbcUrl = config.getProperty(PROP_JDBC_URL);
    String username = config.getProperty(PROP_JDBC_USERNAME);
    String password = config.getProperty(PROP_JDBC_PASSWORD);
    if (jdbcUrl == null || username == null || password == null) {
      throw new IllegalStateException(
          "Flowable database configuration incomplete. Required properties: "
              + "flowable.jdbc.url, flowable.jdbc.username, flowable.jdbc.password");
    }
    hikariConfig.setJdbcUrl(jdbcUrl);
    hikariConfig.setUsername(username);
    hikariConfig.setPassword(password);
    hikariConfig.setMaximumPoolSize(HIKARI_MAX_POOL_SIZE);
    hikariConfig.setPoolName("flowable-pool");
    return new HikariDataSource(hikariConfig);
  }

  /** Creates a Kafka consumer for saga trigger events. */
  static KafkaConsumer<String, byte[]> createKafkaConsumer(AdapterConfig config) {
    Properties props = new Properties();
    props.put(
        "bootstrap.servers", config.getProperty(PROP_KAFKA_BOOTSTRAP, DEFAULT_KAFKA_BOOTSTRAP));
    props.put("group.id", config.getProperty(PROP_KAFKA_GROUP_ID, DEFAULT_KAFKA_GROUP_ID));
    props.put("key.deserializer", StringDeserializer.class.getName());
    props.put("value.deserializer", ByteArrayDeserializer.class.getName());
    props.put("auto.offset.reset", "earliest");
    props.put("enable.auto.commit", "false");
    return new KafkaConsumer<>(props);
  }

  /** Creates a Kafka producer for saga result publishing. */
  static KafkaProducer<String, byte[]> createKafkaProducer(AdapterConfig config) {
    Properties props = new Properties();
    props.put(
        "bootstrap.servers", config.getProperty(PROP_KAFKA_BOOTSTRAP, DEFAULT_KAFKA_BOOTSTRAP));
    props.put("key.serializer", StringSerializer.class.getName());
    props.put("value.serializer", ByteArraySerializer.class.getName());
    props.put("acks", "all");
    return new KafkaProducer<>(props);
  }
}

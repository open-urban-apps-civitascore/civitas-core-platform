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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowableInfrastructureFactoryTest {

  @Test
  void createDataSource_missingJdbcUrl_throwsIllegalState() {
    AdapterConfig config =
        mapConfig(
            Map.of(
                "flowable.jdbc.username", "user",
                "flowable.jdbc.password", "pass"));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> FlowableInfrastructureFactory.createDataSource(config));
    assertTrue(ex.getMessage().contains("flowable.jdbc.url"));
  }

  @Test
  void createDataSource_missingPassword_throwsIllegalState() {
    AdapterConfig config =
        mapConfig(
            Map.of(
                "flowable.jdbc.url", "jdbc:h2:mem:test",
                "flowable.jdbc.username", "user"));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> FlowableInfrastructureFactory.createDataSource(config));
    assertTrue(ex.getMessage().contains("flowable.jdbc.password"));
  }

  @Test
  void createDataSource_allPropertiesSet_returnsDataSource() {
    AdapterConfig config =
        mapConfig(
            Map.of(
                "flowable.jdbc.url", "jdbc:h2:mem:test",
                "flowable.jdbc.username", "sa",
                "flowable.jdbc.password", ""));

    try (var ds = FlowableInfrastructureFactory.createDataSource(config)) {
      assertNotNull(ds);
    }
  }

  @Test
  void createKafkaConsumer_returnsConsumer() {
    AdapterConfig config = mapConfig(Map.of());

    var consumer = FlowableInfrastructureFactory.createKafkaConsumer(config);
    assertNotNull(consumer);
    consumer.close();
  }

  @Test
  void createKafkaProducer_returnsProducer() {
    AdapterConfig config = mapConfig(Map.of());

    var producer = FlowableInfrastructureFactory.createKafkaProducer(config);
    assertNotNull(producer);
    producer.close();
  }

  private AdapterConfig mapConfig(Map<String, String> props) {
    return new AdapterConfig() {
      @Override
      public String getProperty(String key) {
        return props.get(key);
      }

      @Override
      public String getProperty(String key, String defaultValue) {
        return props.getOrDefault(key, defaultValue);
      }
    };
  }
}

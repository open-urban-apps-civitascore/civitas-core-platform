/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.event.handler.kafka;

import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for Kafka integration tests. Uses the singleton container pattern so the Kafka
 * container starts only once per JVM.
 */
abstract class AbstractKafkaIntegrationTest {

  @SuppressWarnings("resource")
  protected static final ConfluentKafkaContainer KAFKA =
      new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"))
          .withReuse(false);

  static {
    KAFKA.start();

    Runtime.getRuntime().addShutdownHook(new Thread(KAFKA::stop));
  }
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the real {@link JdbcSqlSourceProbe} against an actual PostgreSQL: a reachable source
 * with valid credentials passes, while wrong credentials and an unreachable host fail loud — which
 * is how a misconfigured SQL source is stopped at deploy instead of silently producing no data. No
 * NiFi needed. Skipped when Docker is unavailable.
 */
class JdbcSqlSourceProbeIT {

  private static final String DB = "probe_db";
  private static final String DB_USER = "probe";
  private static final String DB_PASSWORD = "probe-secret";

  private static PostgreSQLContainer<?> postgres;
  private final JdbcSqlSourceProbe probe = new JdbcSqlSourceProbe();

  @BeforeAll
  static void start() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping SQL probe IT");
    postgres =
        new PostgreSQLContainer<>(
                DockerImageName.parse(TestContainerImages.POSTGIS)
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName(DB)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD);
    postgres.start();
  }

  @AfterAll
  static void stop() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void reachableSourceWithValidCredentialsPasses() {
    assertDoesNotThrow(() -> probe.probe(postgres.getJdbcUrl(), DB_USER, DB_PASSWORD));
  }

  @Test
  void wrongPasswordFailsLoud() {
    assertThrows(
        FatalAdapterException.class,
        () -> probe.probe(postgres.getJdbcUrl(), DB_USER, "definitely-wrong"));
  }

  @Test
  void unreachableHostFailsLoud() {
    assertThrows(
        FatalAdapterException.class,
        () -> probe.probe("jdbc:postgresql://127.0.0.1:1/nope", DB_USER, DB_PASSWORD));
  }
}

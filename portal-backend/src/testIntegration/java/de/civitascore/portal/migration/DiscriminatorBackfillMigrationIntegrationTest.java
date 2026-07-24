package de.civitascore.portal.migration;

import de.civitascore.portal.util.TestContainerImages;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pins the contract for the V1_1_2 connector-configuration discriminator backfill.
 *
 * <p>This test is intentionally {@link Disabled} in Phase 1 of #1391 because the migration file
 * {@code V1_1_2__connector_configuration_discriminator.sql} does not yet exist. Slice 2 of the
 * refactor adds the migration and activates this test by removing the {@link Disabled} annotation
 * and filling in the TODO sections below.
 *
 * <p>The test is its own standalone Testcontainers test (not extending {@code
 * BaseKeycloakIntegrationTest}) because the goal is to apply migrations to a partial schema
 * (target=1.1.1, insert legacy rows, then target=1.1.2). Spring Boot's auto-Flyway would
 * short-circuit this by always migrating to head on context startup.
 */
@Testcontainers
@DisplayName("V1_1_2 discriminator backfill — characterization (activated in slice 2)")
class DiscriminatorBackfillMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(TestContainerImages.POSTGRES);

  @Test
  @Disabled("activated in slice 2 once V1_1_2 migration SQL exists")
  @DisplayName("Backfills @type from connector_type for legacy rows")
  void backfillsTypeDiscriminator() throws Exception {
    // Step A: migrate to V1.1.1 only — the schema just before the discriminator migration.
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("1.1.1"))
        .load()
        .migrate();

    // Step B: insert representative legacy rows directly via JDBC. Each row exercises a
    // characterization case for the V1_1_2 backfill:
    //   - SQL row     — configuration JSONB without "@type", connector_type = SQL
    //   - MQTT row    — configuration JSONB without "@type", connector_type = MQTT
    //   - already-tagged row — configuration JSONB already contains "@type" (idempotency)
    //   - null-config row    — configuration IS NULL (pin whether migration touches it)
    //   - garbage-config row — configuration is non-object JSON (pin failure-or-skip behavior)
    // TODO[slice 2]: implement the inserts using a fresh JDBC Connection against POSTGRES.

    // Step C: apply V1.1.2 — runs the discriminator backfill migration.
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("1.1.2"))
        .load()
        .migrate();

    // Step D: assert each row's resulting JSONB matches expectations:
    //   - SQL/MQTT rows now have configuration ->> '@type' equal to the connector_type value
    //   - already-tagged row is unchanged (idempotency holds)
    //   - null-config row remains NULL (or asserts the documented behavior)
    //   - garbage-config row behaves as documented (skipped, or migration fails fast)
    // TODO[slice 2]: implement the assertions.
  }
}

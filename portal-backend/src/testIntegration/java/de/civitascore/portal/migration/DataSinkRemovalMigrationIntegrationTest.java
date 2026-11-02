package de.civitascore.portal.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Standalone instead of a Spring context, because Spring Boot would migrate straight to the newest
 * version before the rows of the old shape exist.
 */
class DataSinkRemovalMigrationIntegrationTest {

  private static final String ROADS_PIPELINE = "30000000-0000-4000-8000-000000000002";
  private static final String LINKED_SINK = "31000000-0000-4000-8000-000000000001";
  private static final String UNLINKED_UNPROVISIONED_SINK = "31000000-0000-4000-8000-000000000002";
  private static final String UNLINKED_PROVISIONED_SINK = "31000000-0000-4000-8000-000000000003";
  private static final String OWS_API = "32000000-0000-4000-8000-000000000001";
  private static final String LINKED_LAYER = "33000000-0000-4000-8000-000000000001";
  private static final String UNPROVISIONED_LAYER = "33000000-0000-4000-8000-000000000002";
  private static final String UNSERVED_LAYER = "43000000-0000-4000-8000-000000000001";

  private static PostgreSQLContainer postgres;
  private static JdbcClient jdbc;

  @BeforeAll
  static void migrateOldShape() {
    postgres = new PostgreSQLContainer(TestContainerImages.POSTGRES);
    postgres.start();
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = JdbcClient.create(dataSource);

    portalFlyway(dataSource).target(MigrationVersion.fromVersion("1.2.29")).load().migrate();
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("migration/data-sink-removal.sql"));
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
    portalFlyway(dataSource).target(MigrationVersion.fromVersion("1.2.30")).load().migrate();
  }

  @AfterAll
  static void stopPostgres() {
    postgres.stop();
  }

  @Test
  void migrate_linksEachSinkToItsPipeline() {
    assertThat(
            jdbc.sql("SELECT pipeline_id::text, data_sink_id::text FROM pipeline_data_sinks")
                .query()
                .listOfRows())
        .containsExactly(Map.of("pipeline_id", ROADS_PIPELINE, "data_sink_id", LINKED_SINK));
  }

  @Test
  void migrate_keepsEveryProvisionedSinkActive() {
    assertThat(stateOf(LINKED_SINK)).isEqualTo("ACTIVE");
    assertThat(stateOf(UNLINKED_PROVISIONED_SINK)).isEqualTo("ACTIVE");
  }

  @Test
  void migrate_deletesAnUnlinkedSinkThatWasNeverProvisionedWithItsLayers() {
    assertThat(exists("data_sinks", UNLINKED_UNPROVISIONED_SINK)).isFalse();
    assertThat(exists("layers", UNPROVISIONED_LAYER)).isFalse();
  }

  @Test
  void migrate_linksEachLayerToTheOwsApiOfItsDataset() {
    assertThat(namedApiOf(LINKED_LAYER)).isEqualTo(OWS_API);
    assertThat(namedApiOf(UNSERVED_LAYER)).isNull();
  }

  @Test
  void migrate_rejectsAPendingSinkThatWasNeverProvisioned() {
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "UPDATE data_sinks SET state = 'PENDING_UNPROVISION', provisioned = false"
                            + " WHERE id = :id::uuid")
                    .param("id", LINKED_SINK)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static FluentConfiguration portalFlyway(DriverManagerDataSource dataSource) {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
  }

  private static String stateOf(String sinkId) {
    return jdbc.sql("SELECT state FROM data_sinks WHERE id = :id::uuid")
        .param("id", sinkId)
        .query(String.class)
        .single();
  }

  private static boolean exists(String table, String id) {
    return jdbc.sql("SELECT count(*) FROM " + table + " WHERE id = :id::uuid")
            .param("id", id)
            .query(Long.class)
            .single()
        > 0;
  }

  private static String namedApiOf(String layerId) {
    return jdbc.sql("SELECT named_api_id::text FROM layers WHERE id = :id::uuid")
        .param("id", layerId)
        .query(String.class)
        .optional()
        .orElse(null);
  }
}

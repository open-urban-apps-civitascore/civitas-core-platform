package de.civitascore.portal.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  private static final String ROADS_DATASET = "30000000-0000-4000-8000-000000000001";
  private static final String ROADS_PIPELINE = "30000000-0000-4000-8000-000000000002";
  private static final String LINKED_SINK = "31000000-0000-4000-8000-000000000001";
  private static final String PROVISIONED_UNLINKED_SINK = "31000000-0000-4000-8000-000000000002";
  private static final String UNPROVISIONED_UNLINKED_SINK = "31000000-0000-4000-8000-000000000003";
  private static final String OWS_API = "32000000-0000-4000-8000-000000000001";
  private static final String LINKED_LAYER = "33000000-0000-4000-8000-000000000001";
  private static final String SENSORS_DATASET = "40000000-0000-4000-8000-000000000001";
  private static final String UNPROVISIONED_LINKED_SINK = "41000000-0000-4000-8000-000000000001";
  private static final String OLDER_STA_API = "42000000-0000-4000-8000-000000000001";
  private static final String NEWER_STA_API = "42000000-0000-4000-8000-000000000002";
  private static final String CUSTOM_API = "42000000-0000-4000-8000-000000000003";
  private static final String UNSERVED_LAYER = "43000000-0000-4000-8000-000000000001";
  private static final String DRAFT_SINK = "51000000-0000-4000-8000-000000000001";
  private static final String DRAFT_OWS_API = "52000000-0000-4000-8000-000000000001";
  private static final String DRAFT_CUSTOM_API = "52000000-0000-4000-8000-000000000002";
  private static final String RELEASED_OLDER_SINK = "61000000-0000-4000-8000-000000000001";
  private static final String RELEASED_NEWER_SINK = "61000000-0000-4000-8000-000000000002";
  private static final String RELEASED_OWS_API = "62000000-0000-4000-8000-000000000001";
  private static final String RELEASED_LAYER = "63000000-0000-4000-8000-000000000001";

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
        .contains(Map.of("pipeline_id", ROADS_PIPELINE, "data_sink_id", LINKED_SINK));
  }

  @Test
  void migrate_keepsALinkedSinkActiveWithItsLayer() {
    assertThat(stateOf(LINKED_SINK)).isEqualTo("ACTIVE");
    assertThat(layerExists(LINKED_LAYER)).isTrue();
  }

  @Test
  void migrate_marksAProvisionedSinkWithoutPipelinePendingAndDeletesItsLayers() {
    assertThat(stateOf(PROVISIONED_UNLINKED_SINK)).isEqualTo("PENDING_UNPROVISION");
    assertThat(layersOfSink(PROVISIONED_UNLINKED_SINK)).isZero();
  }

  @Test
  void migrate_deletesAnUnprovisionedSinkWithoutPipelineWithItsLayers() {
    assertThat(
            jdbc.sql("SELECT count(*) FROM data_sinks WHERE id = :id::uuid")
                .param("id", UNPROVISIONED_UNLINKED_SINK)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(layersOfSink(UNPROVISIONED_UNLINKED_SINK)).isZero();
  }

  @Test
  void migrate_dropsThePipelineColumnOfDataSinks() {
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_name = 'data_sinks' AND column_name = 'pipeline_id'")
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void migrate_linksEachLayerToTheOwsApiOfItsDataset() {
    assertThat(namedApiOf(LINKED_LAYER)).isEqualTo(OWS_API);
    assertThat(namedApiOf(UNSERVED_LAYER)).isNull();
  }

  @Test
  void migrate_givesOnlyTheOldestStaApiTheSingletonKey() {
    assertThat(singletonKeysOf(SENSORS_DATASET))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(OLDER_STA_API, "STA", NEWER_STA_API, "", CUSTOM_API, ""));
    assertThat(singletonKeysOf(ROADS_DATASET)).containsExactly(Map.entry(OWS_API, "OWS"));
  }

  @Test
  void migrate_deletesALayerWithItsApi() {
    assertThat(
            jdbc.sql(
                    "SELECT confdeltype FROM pg_constraint WHERE conname = 'fk_layers_on_named_api'")
                .query(String.class)
                .single())
        .isEqualTo("c");
  }

  @Test
  void migrate_deletesAnApiOfAnUnreleasedDatasetThatNoActiveSinkBacks() {
    assertThat(stateOf(DRAFT_SINK)).isEqualTo("PENDING_UNPROVISION");
    assertThat(apiExists(DRAFT_OWS_API)).isFalse();
    assertThat(apiExists(DRAFT_CUSTOM_API)).isTrue();
  }

  @Test
  void migrate_keepsTheNewestUnlinkedSinkActiveForTheApiOfAReleasedDataset() {
    assertThat(apiExists(RELEASED_OWS_API)).isTrue();
    assertThat(stateOf(RELEASED_NEWER_SINK)).isEqualTo("ACTIVE");
    assertThat(namedApiOf(RELEASED_LAYER)).isEqualTo(RELEASED_OWS_API);
    assertThat(stateOf(RELEASED_OLDER_SINK)).isEqualTo("PENDING_UNPROVISION");
  }

  @Test
  void migrate_rejectsAPendingSinkThatWasNeverProvisioned() {
    assertThatThrownBy(
            () ->
                jdbc.sql("UPDATE data_sinks SET state = 'PENDING_UNPROVISION' WHERE id = :id::uuid")
                    .param("id", UNPROVISIONED_LINKED_SINK)
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

  private static boolean apiExists(String apiId) {
    return jdbc.sql("SELECT count(*) FROM named_apis WHERE id = :id::uuid")
            .param("id", apiId)
            .query(Long.class)
            .single()
        > 0;
  }

  private static long layersOfSink(String sinkId) {
    return jdbc.sql("SELECT count(*) FROM layers WHERE datasink_id = :id::uuid")
        .param("id", sinkId)
        .query(Long.class)
        .single();
  }

  private static boolean layerExists(String layerId) {
    return jdbc.sql("SELECT count(*) FROM layers WHERE id = :id::uuid")
            .param("id", layerId)
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

  private static Map<String, String> singletonKeysOf(String dataSetId) {
    List<Map<String, Object>> rows =
        jdbc.sql(
                "SELECT id::text AS id, coalesce(singleton_key, '') AS singleton_key"
                    + " FROM named_apis WHERE dataset_id = :id::uuid")
            .param("id", dataSetId)
            .query()
            .listOfRows();
    return rows.stream()
        .collect(
            Collectors.toMap(
                row -> (String) row.get("id"), row -> (String) row.get("singleton_key")));
  }
}

package de.civitascore.portal.migration;

import static org.assertj.core.api.Assertions.assertThat;

import db.migration.V1_2_27__stamp_datasource_element;
import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Standalone instead of a Spring context, because Spring Boot would migrate straight to the newest
 * version before the unstamped rows exist.
 */
class DataSourceElementMigrationIntegrationTest {

  private static final String MODEL_URN =
      "urn:core:platform:civitas:element:common:10000000-0000-4000-8000-000000000001:1.0.0";
  private static final String ELEMENT_ARTIFACT = "a0000000-0000-4000-8000-000000000001";
  private static final String ELEMENT_VERSION = "b0000000-0000-4000-8000-000000000001";
  private static final List<String> TWO_VERSIONS_OF_ONE_SOURCE =
      List.of("b0000000-0000-4000-8000-000000000002", "b0000000-0000-4000-8000-000000000003");
  private static final String SOURCE_WITHOUT_STRUCTURE = "b0000000-0000-4000-8000-000000000004";
  private static final String SOURCE_ALREADY_STAMPED = "b0000000-0000-4000-8000-000000000005";
  private static final String DATASOURCE = "urn:core:platform:civitas:datasource:common:";
  private static final String WEATHER_SOURCE = DATASOURCE + "20000000-0000-4000-8000-000000000001";
  private static final String STAMPED_SOURCE = DATASOURCE + "20000000-0000-4000-8000-000000000003";
  private static final String PINNED_SINK =
      "urn:core:platform:civitas:datasink:common:30000000-0000-4000-8000-000000000001:1.0.0";
  private static final String PIPELINE_WITH_PINS = "b0000000-0000-4000-8000-000000000006";
  private static final String PIPELINE_ON_LATEST = "b0000000-0000-4000-8000-000000000007";

  private static PostgreSQLContainer postgres;
  private static DriverManagerDataSource dataSource;
  private static JdbcClient jdbc;

  @BeforeAll
  static void migrateUnstampedRows() {
    postgres = new PostgreSQLContainer(TestContainerImages.POSTGRES);
    postgres.start();
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = JdbcClient.create(dataSource);

    portalFlyway().target(MigrationVersion.fromVersion("1.2.26")).load().migrate();
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("model_forge")
        .table("model_forge_schema_history")
        .locations("classpath:db/model-forge/migration")
        .load()
        .migrate();
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("migration/datasource-without-element.sql"));
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }

    portalFlyway().load().migrate();
  }

  @AfterAll
  static void stopPostgres() {
    postgres.stop();
  }

  @Test
  void migrate_namesTheDataStructureVersionInEveryVersionOfTheConfiguration() {
    for (String version : TWO_VERSIONS_OF_ONE_SOURCE) {
      assertThat(elementOf(version)).as(version).isEqualTo(MODEL_URN);
    }
  }

  @Test
  void migrate_recordsOneResolvedElementReferencePerVersion() {
    for (String version : TWO_VERSIONS_OF_ONE_SOURCE) {
      assertThat(elementReferencesOf(version))
          .as(version)
          .containsExactly(
              Map.of(
                  "target_urn", MODEL_URN,
                  "target_artifact_id", ELEMENT_ARTIFACT,
                  "target_version_id", ELEMENT_VERSION));
    }
  }

  @Test
  void migrate_rehashesTheStampedContent() {
    assertThat(
            jdbc.sql(
                    """
                    SELECT count(*) FROM model_forge.artifact_representation
                    WHERE version_id::text IN (:versions)
                      AND content_hash
                        <> encode(sha256(convert_to(content_jsonb::text, 'UTF8')), 'hex')
                    """)
                .param("versions", TWO_VERSIONS_OF_ONE_SOURCE)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void migrate_leavesADataSourceWithoutDataStructureVersionUntouched() {
    assertThat(elementOf(SOURCE_WITHOUT_STRUCTURE)).isNull();
    assertThat(hashOf(SOURCE_WITHOUT_STRUCTURE)).isEqualTo("fixture");
    assertThat(elementReferencesOf(SOURCE_WITHOUT_STRUCTURE)).isEmpty();
  }

  @Test
  void migrate_leavesAnAlreadyStampedDocumentUntouched() {
    assertThat(hashOf(SOURCE_ALREADY_STAMPED)).isEqualTo("fixture");
    assertThat(elementReferencesOf(SOURCE_ALREADY_STAMPED)).hasSize(1);
  }

  @Test
  void migrate_pointsTheDataSourceReferencesOfAPipelineVersionAtLatest() {
    assertThat(pipelineNodeRef(PIPELINE_WITH_PINS, "src", "sourceRef"))
        .isEqualTo(WEATHER_SOURCE + ":latest");
    assertThat(pipelineNodeRef(PIPELINE_WITH_PINS, "enrich", "lookupSourceRef"))
        .isEqualTo(STAMPED_SOURCE + ":latest");
  }

  @Test
  void migrate_keepsTheSinkPinAndTheEditorLayoutOfAPipelineVersion() {
    assertThat(pipelineNodeRef(PIPELINE_WITH_PINS, "sink", "sinkRef")).isEqualTo(PINNED_SINK);
    assertThat(
            jdbc.sql(
                    """
                    SELECT content_jsonb #>> '{x-ui-styles,nodes,0,data,configurationUrn}'
                    FROM model_forge.artifact_representation WHERE version_id = :id::uuid
                    """)
                .param("id", PIPELINE_WITH_PINS)
                .query(String.class)
                .single())
        .isEqualTo(WEATHER_SOURCE + ":1.0.0");
  }

  @Test
  void migrate_recordsThePipelineEdgesToDataSourcesAsModelForgeRecordsLatest() {
    assertThat(pipelineNodeReferencesOf(PIPELINE_WITH_PINS))
        .containsExactly(
            Map.of(
                "reference_name", "source",
                "target_urn", WEATHER_SOURCE + ":latest",
                "target_artifact_id", "a0000000-0000-4000-8000-000000000002"),
            Map.of(
                "reference_name", "enrich",
                "target_urn", STAMPED_SOURCE + ":latest",
                "target_artifact_id", "a0000000-0000-4000-8000-000000000004"),
            Map.of("reference_name", "sink", "target_urn", PINNED_SINK));
  }

  @Test
  void migrate_rehashesARewrittenPipelineVersionOnly() {
    assertThat(hashOf(PIPELINE_WITH_PINS))
        .isEqualTo(
            jdbc.sql(
                    """
                    SELECT encode(sha256(convert_to(content_jsonb::text, 'UTF8')), 'hex')
                    FROM model_forge.artifact_representation WHERE version_id = :id::uuid
                    """)
                .param("id", PIPELINE_WITH_PINS)
                .query(String.class)
                .single());
    assertThat(hashOf(PIPELINE_ON_LATEST)).isEqualTo("fixture");
  }

  @Test
  void migrate_runAgain_changesNothing() throws SQLException {
    List<Map<String, Object>> representations = representations();
    List<Map<String, Object>> references = references();

    try (Connection connection = dataSource.getConnection()) {
      new V1_2_27__stamp_datasource_element().migrate(contextOf(connection));
    }

    assertThat(representations()).isEqualTo(representations);
    assertThat(references()).isEqualTo(references);
  }

  private static FluentConfiguration portalFlyway() {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
  }

  private static Context contextOf(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return portalFlyway();
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }

  private static String elementOf(String versionId) {
    return jdbc.sql(
            "SELECT content_jsonb ->> 'element' FROM model_forge.artifact_representation"
                + " WHERE version_id = :id::uuid")
        .param("id", versionId)
        .query(String.class)
        .list()
        .getFirst();
  }

  private static String hashOf(String versionId) {
    return jdbc.sql(
            "SELECT content_hash FROM model_forge.artifact_representation"
                + " WHERE version_id = :id::uuid")
        .param("id", versionId)
        .query(String.class)
        .single();
  }

  private static List<Map<String, Object>> elementReferencesOf(String versionId) {
    return jdbc.sql(
            """
            SELECT target_urn, target_artifact_id::text AS target_artifact_id,
                   target_version_id::text AS target_version_id
            FROM model_forge.artifact_reference
            WHERE from_version_id = :id::uuid AND reference_type = 'datasource-element'
            """)
        .param("id", versionId)
        .query()
        .listOfRows();
  }

  private static String pipelineNodeRef(String versionId, String nodeId, String field) {
    return jdbc.sql(
            """
            SELECT n ->> :field
            FROM model_forge.artifact_representation r,
              jsonb_array_elements(r.content_jsonb -> 'nodes') n
            WHERE r.version_id = :id::uuid AND n ->> 'id' = :node
            """)
        .param("field", field)
        .param("id", versionId)
        .param("node", nodeId)
        .query(String.class)
        .single();
  }

  /** The non-null columns of each row, so that a NULL target version shows as an absent key. */
  private static List<Map<String, Object>> pipelineNodeReferencesOf(String versionId) {
    return jdbc
        .sql(
            """
            SELECT reference_name, target_urn, target_artifact_id::text AS target_artifact_id,
                   target_version_id::text AS target_version_id
            FROM model_forge.artifact_reference
            WHERE from_version_id = :id::uuid AND reference_type = 'pipeline-node'
            ORDER BY sort_order
            """)
        .param("id", versionId)
        .query()
        .listOfRows()
        .stream()
        .map(DataSourceElementMigrationIntegrationTest::withoutNulls)
        .toList();
  }

  private static Map<String, Object> withoutNulls(Map<String, Object> row) {
    Map<String, Object> present = new LinkedHashMap<>();
    row.forEach(
        (column, value) -> {
          if (value != null) {
            present.put(column, value);
          }
        });
    return present;
  }

  private static List<Map<String, Object>> representations() {
    return jdbc.sql(
            "SELECT id, content_jsonb::text, content_hash FROM model_forge.artifact_representation"
                + " ORDER BY id")
        .query()
        .listOfRows();
  }

  private static List<Map<String, Object>> references() {
    return jdbc.sql("SELECT * FROM model_forge.artifact_reference ORDER BY id")
        .query()
        .listOfRows();
  }
}

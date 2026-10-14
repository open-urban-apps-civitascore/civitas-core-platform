package de.civitascore.portal.migration;

import static org.assertj.core.api.Assertions.assertThat;

import db.migration.V1_2_27__stamp_datasource_element;
import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.SQLException;
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

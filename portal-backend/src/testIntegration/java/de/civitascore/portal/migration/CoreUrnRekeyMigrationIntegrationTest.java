package de.civitascore.portal.migration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
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
 * version before the old-format rows exist.
 */
class CoreUrnRekeyMigrationIntegrationTest {

  private static final String PREFIX = "urn:core:platform:civitas:";
  private static final UUID STRUCTURE = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID DATASET = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID PIPELINE = UUID.fromString("20000000-0000-4000-8000-000000000002");
  private static final UUID SINK = UUID.fromString("20000000-0000-4000-8000-000000000003");
  private static final UUID SOURCE = UUID.fromString("20000000-0000-4000-8000-000000000004");
  private static final UUID THING_TREE = UrnParser.deriveId("frost-port:ThingTree");

  private static final String STRUCTURE_URN = PREFIX + "datastructure:common:" + STRUCTURE;
  private static final String BUILDING_URN =
      PREFIX + "element:common:" + UrnParser.memberId(STRUCTURE, "Building");
  private static final String ADDRESS_URN =
      PREFIX + "element:common:" + UrnParser.memberId(STRUCTURE, "Address");
  private static final String THING_TREE_URN = PREFIX + "datastructure:frost:" + THING_TREE;

  private static PostgreSQLContainer postgres;
  private static JdbcClient jdbc;

  @BeforeAll
  static void migrateOldFormatRows() {
    postgres = new PostgreSQLContainer(TestContainerImages.POSTGRES);
    postgres.start();
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = JdbcClient.create(dataSource);

    portalFlyway(dataSource).target(MigrationVersion.fromVersion("1.2.25")).load().migrate();
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("model_forge")
        .table("model_forge_schema_history")
        .locations("classpath:db/model-forge/migration")
        .load()
        .migrate();
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("migration/old-format-core-urns.sql"));
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }

    portalFlyway(dataSource).load().migrate();
  }

  @AfterAll
  static void stopPostgres() {
    postgres.stop();
  }

  @Test
  void migrate_whenOldFormatRowsExist_leavesNoOldFormatUrnInAnyColumn() {
    List<String> columns =
        jdbc.sql(
                """
                SELECT table_schema || '.' || table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema IN ('public', 'model_forge')
                  AND data_type IN ('text', 'character varying', 'jsonb', 'ARRAY')
                """)
            .query(String.class)
            .list();
    for (String column : columns) {
      String[] parts = column.split("\\.");
      Long hits =
          jdbc.sql(
                  "SELECT count(*) FROM %s.%s WHERE %s::text ~ :pattern"
                      .formatted(parts[0], parts[1], parts[2]))
              .param("pattern", "urn:core:[^:\"]+:[^:\"]+:[a-z]+:[^:\"]+:[^:\"]+:[0-9a-z]{10}")
              .query(Long.class)
              .single();
      assertThat(hits).as(column).isZero();
    }
  }

  @Test
  void migrate_givesHostEntitiesTheirOwnId() {
    assertThat(column("data_structures", "model_logical_urn")).contains(STRUCTURE_URN);
    assertThat(column("pipelines", "model_urn"))
        .containsExactly(PREFIX + "pipeline:common:" + PIPELINE + ":1.0.0");
    assertThat(column("data_sinks", "configuration_logical_urn"))
        .containsExactly(PREFIX + "datasink:common:" + SINK);
    assertThat(column("data_sources", "configuration_urn"))
        .containsExactly(PREFIX + "datasource:common:" + SOURCE + ":1.0.0");
    assertThat(column("datasets", "manifest_logical_urn"))
        .containsExactly(PREFIX + "dataset:common:" + DATASET);
  }

  @Test
  void migrate_keepsTheVersionOfEveryPin() {
    assertThat(column("data_structure_versions", "model_urn"))
        .containsExactlyInAnyOrder(STRUCTURE_URN + ":1.0.0", STRUCTURE_URN + ":2.0.0");
    assertThat(
            jdbc.sql(
                    "SELECT target_urn FROM model_forge.artifact_reference"
                        + " WHERE id = 'd0000000-0000-4000-8000-000000000005'")
                .query(String.class)
                .single())
        .isEqualTo(ADDRESS_URN + ":latest");
  }

  @Test
  void migrate_derivesMemberIdsFromTheParentAndTheDefsKey() {
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000002")).isEqualTo(BUILDING_URN);
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000003")).isEqualTo(ADDRESS_URN);
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000005"))
        .isEqualTo(PREFIX + "element:common:" + UrnParser.memberId(THING_TREE, "Thing"));
  }

  @Test
  void migrate_rewritesReferencesInsideStoredContent() {
    assertThat(contentOf("c0000000-0000-4000-8000-000000000004"))
        .contains("\"$id\": \"" + BUILDING_URN + "\"")
        .contains("\"$ref\": \"" + ADDRESS_URN + "\"");
    assertThat(contentOf("c0000000-0000-4000-8000-000000000002"))
        .contains(BUILDING_URN + ":2.0.0", ADDRESS_URN + ":2.0.0");
    assertThat(contentOf("c0000000-0000-4000-8000-000000000009"))
        .contains(
            PREFIX + "datasource:common:" + SOURCE + ":1.0.0",
            PREFIX + "datasink:common:" + SINK + ":1.0.0",
            STRUCTURE_URN + ":2.0.0");
  }

  @Test
  void migrate_rehashesRewrittenContent() {
    assertThat(
            jdbc.sql(
                    """
                    SELECT count(*) FROM model_forge.artifact_representation
                    WHERE content_jsonb IS NOT NULL
                      AND content_hash
                        <> encode(sha256(convert_to(content_jsonb::text, 'UTF8')), 'hex')
                    """)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void migrate_derivesFrostPortIdsLikeThePortRegistrar() {
    assertThat(column("published_structures", "versioned_urn"))
        .containsExactly(THING_TREE_URN + ":1.0.0");
    assertThat(
            jdbc.sql(
                    "SELECT imported_structure_urns[1] FROM data_structure_versions"
                        + " WHERE imported_structure_urns IS NOT NULL")
                .query(String.class)
                .single())
        .isEqualTo(THING_TREE_URN + ":1.0.0");
  }

  @Test
  void migrate_derivesXoevIdsLikeTheXRepositoryImport() {
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000011"))
        .isEqualTo(
            PREFIX
                + "element:xoev-lokation:"
                + UrnParser.deriveId("xrepository:xoev-lokation:Lokation.0002_Bundesland"));
  }

  @Test
  void migrate_givesAnImportedModelsGroupingAndRootElementTheHostId() {
    UUID importedStructure = UUID.fromString("10000000-0000-4000-8000-000000000004");
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000013"))
        .isEqualTo(PREFIX + "datastructure:common:" + importedStructure);
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000014"))
        .isEqualTo(PREFIX + "element:common:" + importedStructure);
  }

  @Test
  void migrate_derivesConvertedXsdTypeIdsLikeTheConverter() {
    String typePrefix = PREFIX + "element:xoev-lokation";
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000016"))
        .isEqualTo(typePrefix + ":" + UrnParser.deriveId(typePrefix + ":Bundesland"));
  }

  @Test
  void migrate_givesArtifactsWithoutDerivableIdAStableLegacyId() {
    assertThat(logicalUrnOf("a0000000-0000-4000-8000-000000000007"))
        .isEqualTo(
            PREFIX
                + "mapping:common:"
                + UrnParser.deriveId(
                    "legacy:urn:core:platform:civitas:common:Reading-to-Station:6k6cbtnnzp"));
    assertThat(
            jdbc.sql(
                    "SELECT target_urn FROM model_forge.artifact_reference"
                        + " WHERE id = 'd0000000-0000-4000-8000-000000000004'")
                .query(String.class)
                .single())
        .isEqualTo(
            PREFIX
                + "datastructure:common:"
                + UrnParser.deriveId("legacy:urn:core:platform:civitas:common:Deleted:zzzzzzzzzz"));
  }

  @Test
  void migrate_onADatabaseWithoutRegistrySchema_succeeds() {
    jdbc.sql("CREATE DATABASE fresh").update();
    String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/fresh$1");
    DriverManagerDataSource fresh =
        new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());

    portalFlyway(fresh).load().migrate();

    assertThat(
            JdbcClient.create(fresh)
                .sql("SELECT success FROM flyway_schema_history WHERE version = '1.2.26'")
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  private static FluentConfiguration portalFlyway(DriverManagerDataSource dataSource) {
    return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
  }

  private static List<String> column(String table, String column) {
    return jdbc.sql("SELECT %s FROM %s".formatted(column, table)).query(String.class).list();
  }

  private static String logicalUrnOf(String artifactId) {
    return jdbc.sql("SELECT logical_urn FROM model_forge.artifact WHERE id = :id::uuid")
        .param("id", artifactId)
        .query(String.class)
        .single();
  }

  private static String contentOf(String representationId) {
    return jdbc.sql(
            "SELECT content_jsonb::text FROM model_forge.artifact_representation"
                + " WHERE id = :id::uuid")
        .param("id", representationId)
        .query(String.class)
        .single();
  }
}

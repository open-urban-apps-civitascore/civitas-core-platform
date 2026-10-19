package de.civitascore.portal.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import db.migration.V1_2_28__strip_foreign_model_references;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Standalone instead of a Spring context, because Spring Boot would migrate straight to the newest
 * version before the references to strip exist.
 *
 * <p>Charlie lists a member of Bravo and references it from its own member, as a caller could store
 * before references were checked. Charlie also references a published port structure, GeoJSON and a
 * model that does not exist.
 */
class ForeignModelReferenceStripMigrationIntegrationTest {

  private static final String PREFIX = "urn:core:platform:civitas:";
  private static final UUID BRAVO = UUID.fromString("10000000-0000-4000-8000-0000000000b0");
  private static final UUID CHARLIE = UUID.fromString("10000000-0000-4000-8000-0000000000c0");
  private static final UUID THING_TREE = UrnParser.deriveId("frost-port:ThingTree");

  private static final String BRAVO_GROUPING = PREFIX + "datastructure:common:" + BRAVO;
  private static final String BRAVO_MEMBER =
      PREFIX + "element:common:" + UrnParser.memberId(BRAVO, "BravoInner");
  private static final String CHARLIE_GROUPING = PREFIX + "datastructure:common:" + CHARLIE;
  private static final String CHARLIE_MEMBER =
      PREFIX + "element:common:" + UrnParser.memberId(CHARLIE, "Own");
  private static final String PORT_GROUPING = PREFIX + "datastructure:frost:" + THING_TREE;
  private static final String PORT_MEMBER =
      PREFIX + "element:common:" + UrnParser.memberId(THING_TREE, "Thing");
  private static final String MISSING =
      PREFIX + "element:common:" + UUID.fromString("10000000-0000-4000-8000-0000000000ff");

  private static PostgreSQLContainer postgres;
  private static DriverManagerDataSource dataSource;
  private static JdbcClient jdbc;

  @BeforeAll
  static void migrateStoredForeignReferences() {
    postgres = new PostgreSQLContainer(TestContainerImages.POSTGRES);
    postgres.start();
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = JdbcClient.create(dataSource);

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("1.2.27"))
        .load()
        .migrate();
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("model_forge")
        .table("model_forge_schema_history")
        .locations("classpath:db/model-forge/migration")
        .load()
        .migrate();
    seed();

    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
  }

  @AfterAll
  static void stopPostgres() {
    postgres.stop();
  }

  @Test
  void migrate_dropsAMemberAnotherDataStructureMinted() {
    assertThat(content(CHARLIE_GROUPING))
        .contains("\"Own\"", CHARLIE_MEMBER)
        .doesNotContain("Stolen", BRAVO_MEMBER);
  }

  @Test
  void migrate_removesForeignReferencesAndKeepsTheRestOfTheSchema() {
    String member = content(CHARLIE_MEMBER);

    assertThat(member)
        .doesNotContain(BRAVO_MEMBER, MISSING)
        .contains("\"description\": \"kept\"", "\"y\": {\"type\": \"string\"}");
  }

  @Test
  void migrate_keepsPublishedGeoJsonAndCategoryReferences() {
    assertThat(content(CHARLIE_MEMBER))
        .contains(
            PORT_MEMBER + ":1.0.0",
            "https://geojson.org/schema/Point.json",
            "urn:core:type:Element");
  }

  @Test
  void migrate_leavesTheOtherDataStructureAlone() {
    assertThat(content(BRAVO_GROUPING)).contains(BRAVO_MEMBER);
    assertThat(edgeTargets(BRAVO_GROUPING)).containsExactly(BRAVO_MEMBER + ":1.0.0");
  }

  @Test
  void migrate_deletesTheEdgesOfRemovedReferences() {
    assertThat(edgeTargets(CHARLIE_GROUPING)).containsExactly(CHARLIE_MEMBER + ":1.0.0");
    assertThat(edgeTargets(CHARLIE_MEMBER)).containsExactly(PORT_MEMBER + ":1.0.0");
  }

  @Test
  void migrate_rehashesRewrittenContent() {
    assertThat(
            jdbc.sql(
                    """
                    SELECT count(*) FROM model_forge.artifact_representation
                    WHERE content_hash
                      <> encode(sha256(convert_to(content_jsonb::text, 'UTF8')), 'hex')
                      AND content_hash <> 'seed'
                    """)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(hash(CHARLIE_MEMBER)).isNotEqualTo("seed");
    assertThat(hash(BRAVO_GROUPING)).isEqualTo("seed");
  }

  @Test
  void migrate_runAgain_changesNothing() throws SQLException {
    List<String> before = allContent();
    try (Connection connection = dataSource.getConnection()) {
      Context context = mock(Context.class);
      when(context.getConnection()).thenReturn(connection);

      new V1_2_28__strip_foreign_model_references().migrate(context);
    }

    assertThat(allContent()).isEqualTo(before);
  }

  private static void seed() {
    jdbc.sql(
            """
            INSERT INTO data_structures (id, name, created_at, data_structure_status, model_logical_urn)
            VALUES (:bravo, 'Bravo', now(), 'AVAILABLE', :bravoUrn),
                   (:charlie, 'Charlie', now(), 'AVAILABLE', :charlieUrn)
            """)
        .param("bravo", BRAVO)
        .param("bravoUrn", BRAVO_GROUPING)
        .param("charlie", CHARLIE)
        .param("charlieUrn", CHARLIE_GROUPING)
        .update();
    jdbc.sql(
            """
            INSERT INTO data_structure_versions (id, created_at, data_structure_version_status,
                data_structure_version_source, data_structure_id, version, model_urn)
            VALUES (gen_random_uuid(), now(), 'AVAILABLE', 'OWN', :charlie, '1.0.0', :pin)
            """)
        .param("charlie", CHARLIE)
        .param("pin", CHARLIE_GROUPING + ":1.0.0")
        .update();
    jdbc.sql(
            """
            INSERT INTO published_structures (id, created_at, port, logical_urn, versioned_urn,
                content_hash)
            VALUES (gen_random_uuid(), now(), 'ThingTree', :urn, :pin, 'seed')
            """)
        .param("urn", PORT_GROUPING)
        .param("pin", PORT_GROUPING + ":1.0.0")
        .update();

    artifact(
        BRAVO_GROUPING,
        "datastructure",
        """
        {"$ref": "%1$s:1.0.0", "$defs": {"BravoInner": {"$ref": "%1$s:1.0.0"}}}
        """
            .formatted(BRAVO_MEMBER),
        edge(BRAVO_MEMBER + ":1.0.0", "datastructure-ref"));
    artifact(
        BRAVO_MEMBER,
        "element",
        "{\"type\": \"object\", \"properties\": {\"innerSecret\": {\"type\": \"string\"}}}");
    artifact(
        CHARLIE_GROUPING,
        "datastructure",
        """
        {"$ref": "%1$s:1.0.0",
         "$defs": {"Own": {"$ref": "%1$s:1.0.0"}, "Stolen": {"$ref": "%2$s:1.0.0"}}}
        """
            .formatted(CHARLIE_MEMBER, BRAVO_MEMBER),
        edge(CHARLIE_MEMBER + ":1.0.0", "datastructure-ref"),
        edge(BRAVO_MEMBER + ":1.0.0", "datastructure-ref"));
    artifact(
        CHARLIE_MEMBER,
        "element",
        """
        {"type": "object", "properties": {
          "x": {"$ref": "%1$s:1.0.0", "description": "kept"},
          "y": {"type": "string", "x-core-ref": {"type": "%1$s"}},
          "geo": {"$ref": "https://geojson.org/schema/Point.json"},
          "port": {"$ref": "%2$s:1.0.0"},
          "gone": {"$ref": "%3$s:1.0.0"},
          "category": {"type": "string", "x-core-ref": {"type": "urn:core:type:Element"}}}}
        """
            .formatted(BRAVO_MEMBER, PORT_MEMBER, MISSING),
        edge(BRAVO_MEMBER + ":1.0.0", "schema-ref"),
        edge(BRAVO_MEMBER, "association-ref"),
        edge(PORT_MEMBER + ":1.0.0", "schema-ref"),
        edge(MISSING + ":1.0.0", "schema-ref"));
    artifact(
        PORT_GROUPING,
        "datastructure",
        "{\"$defs\": {\"Thing\": {\"$ref\": \"%s:1.0.0\"}}}".formatted(PORT_MEMBER),
        edge(PORT_MEMBER + ":1.0.0", "datastructure-ref"));
    artifact(PORT_MEMBER, "element", "{\"type\": \"object\"}");
  }

  private record Edge(String target, String type) {}

  private static Edge edge(String target, String type) {
    return new Edge(target, type);
  }

  private static void artifact(String logicalUrn, String type, String content, Edge... edges) {
    UUID artifact = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO model_forge.artifact (id, logical_urn, artifact_type, name, current_version,
                created_at, updated_at)
            VALUES (:id, :urn, :type, 'name', '1.0.0', now(), now())
            """)
        .param("id", artifact)
        .param("urn", logicalUrn)
        .param("type", type)
        .update();
    jdbc.sql(
            """
            INSERT INTO model_forge.artifact_version (id, artifact_id, version, primary_format,
                created_at)
            VALUES (:id, :artifact, '1.0.0', :format, now())
            """)
        .param("id", version)
        .param("artifact", artifact)
        .param("format", type.equals("element") ? "jsonschema" : "core-json")
        .update();
    jdbc.sql(
            """
            INSERT INTO model_forge.artifact_representation (id, version_id, format, content_type,
                content_jsonb, content_hash, created_at)
            VALUES (gen_random_uuid(), :version, :format, 'application/json', :content::jsonb,
                'seed', now())
            """)
        .param("version", version)
        .param("format", type.equals("element") ? "jsonschema" : "core-json")
        .param("content", content)
        .update();
    for (Edge edge : edges) {
      jdbc.sql(
              """
              INSERT INTO model_forge.artifact_reference (id, from_version_id, target_urn,
                  reference_type, created_at)
              VALUES (gen_random_uuid(), :version, :target, :type, now())
              """)
          .param("version", version)
          .param("target", edge.target())
          .param("type", edge.type())
          .update();
    }
  }

  private static String content(String logicalUrn) {
    return jdbc.sql(
            """
            SELECT r.content_jsonb::text FROM model_forge.artifact a
              JOIN model_forge.artifact_version v ON v.artifact_id = a.id
              JOIN model_forge.artifact_representation r ON r.version_id = v.id
            WHERE a.logical_urn = :urn
            """)
        .param("urn", logicalUrn)
        .query(String.class)
        .single();
  }

  private static String hash(String logicalUrn) {
    return jdbc.sql(
            """
            SELECT r.content_hash FROM model_forge.artifact a
              JOIN model_forge.artifact_version v ON v.artifact_id = a.id
              JOIN model_forge.artifact_representation r ON r.version_id = v.id
            WHERE a.logical_urn = :urn
            """)
        .param("urn", logicalUrn)
        .query(String.class)
        .single();
  }

  private static List<String> edgeTargets(String logicalUrn) {
    return jdbc.sql(
            """
            SELECT e.target_urn FROM model_forge.artifact a
              JOIN model_forge.artifact_version v ON v.artifact_id = a.id
              JOIN model_forge.artifact_reference e ON e.from_version_id = v.id
            WHERE a.logical_urn = :urn
            ORDER BY e.target_urn
            """)
        .param("urn", logicalUrn)
        .query(String.class)
        .list();
  }

  private static List<String> allContent() {
    return jdbc.sql(
            "SELECT id::text || content_jsonb::text || content_hash"
                + " FROM model_forge.artifact_representation ORDER BY id")
        .query(String.class)
        .list();
  }
}

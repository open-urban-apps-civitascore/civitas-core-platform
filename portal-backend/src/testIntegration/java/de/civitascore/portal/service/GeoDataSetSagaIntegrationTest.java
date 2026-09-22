package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import de.civitascore.portal.config.FlowableSagaTestHelper;
import de.civitascore.portal.config.InfraTestDataFactory;
import de.civitascore.portal.config.SagaInfraVerifier;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * E2E saga test for a geo dataset: a released dataset with a POSTGIS sink runs the CREATE saga
 * through the production Flowable orchestrator with a real PostGIS handler — the sink table must
 * exist afterwards with the columns derived from the data-structure version's persisted JSON
 * Schema.
 *
 * <p>The data-structure version carries a flat (sub-package-free) JSON Schema persisted directly on
 * the entity; the pipeline-engine and GeoServer steps run as always-success stubs (see {@link
 * FlowableSagaTestHelper}).
 */
@TestPropertySource(
    properties = {"kafka.enabled=true", "spring.kafka.listener.missing-topics-fatal=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Slf4j
@Import(InfraTestDataFactory.class)
class GeoDataSetSagaIntegrationTest extends AbstractSagaIntegrationTest {

  /**
   * Flat schema the editor produces: root properties, with the geometry property referencing the
   * GeoJSON schema for its type.
   */
  private static final String FLAT_SCHEMA =
      """
      { "$id": "http://test/geoprobe/1.0.0",
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "title": "GeoProbe",
        "type": "object",
        "properties": {
          "station_id": { "type": "string" },
          "temperature": { "type": "string" },
          "location": { "$ref": "https://geojson.org/schema/Point.json", "crs": "EPSG:25832" }
        },
        "required": ["station_id", "temperature", "location"] }
      """;

  /** Sink + Flowable database — host-reachable, unlike the network-internal FROST PostGIS. */
  @SuppressWarnings("resource")
  static final PostgreSQLContainer sinkDb =
      new PostgreSQLContainer(
              DockerImageName.parse(TestContainerImages.POSTGIS)
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("geosink")
          .withUsername("geo")
          .withPassword("geo");

  private static FlowableSagaTestHelper sagaHelper;

  static {
    sinkDb.start();
  }

  @Autowired private DataSetService dataSetService;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private InfraTestDataFactory data;
  @Autowired private ObjectProvider<AllowedScopes> allowedScopesProvider;
  private SagaInfraVerifier verifier;

  @BeforeAll
  static void wireSaga() throws Exception {
    try (Connection connection = DriverManager.getConnection(sinkDb.getJdbcUrl(), "geo", "geo");
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE flowable");
    }
    String flowableJdbcUrl = sinkDb.getJdbcUrl().replace("/geosink", "/flowable");
    sagaHelper =
        new FlowableSagaTestHelper(
            kafka.getBootstrapServers(),
            frostExternalUrl,
            "http://frost-server:8080/FROST-Server/v1.1",
            flowableJdbcUrl,
            sinkDb.getJdbcUrl(),
            "geo",
            "geo");
  }

  @DynamicPropertySource
  static void configureSaga(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  /**
   * Stage and release authorize the referenced Data sources, which reads the request-scoped {@link
   * AllowedScopes}. Calling the service off a web request leaves that scope unbound.
   */
  @BeforeEach
  void initHelpers() {
    verifier = new SagaInfraVerifier(dataSetRepository, frostExternalUrl);

    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    allowedScopesProvider.getObject().setWildcard();

    PrincipalUserDetails principal =
        PrincipalUserDetails.builder()
            .userId(UUID.randomUUID())
            .username("geo-saga-test")
            .authorities(List.of())
            .build();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    SecurityContextHolder.setContext(context);
  }

  @AfterEach
  void cleanDb() {
    data.cleanAll();
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @AfterAll
  static void stopAll() {
    if (sagaHelper != null) {
      sagaHelper.close();
    }
    sinkDb.stop();
  }

  @Test
  void createSaga_withPostgisSink_provisionsSinkTableFromSchema() throws Exception {
    DataSource dataSource = data.createSqlDataSource();
    DataSet dataSet = data.createDataSet("Geo E2E Dataset");
    Map<String, Object> model = new JsonMapper().readValue(FLAT_SCHEMA, new TypeReference<>() {});
    data.createGeoPipeline(dataSet, dataSource, "sensor_observations", model);
    data.seedGroupAndAssignment(dataSet);
    UUID dataSetId = dataSet.getId();

    DataSet staged = dataSetService.stage(dataSetId);
    assertThat(staged.getDataSetStatus()).isEqualTo(DataSetStatus.READY);

    DataSet released = dataSetService.release(dataSetId);
    assertThat(released.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
    assertThat(released.getPendingSagaType()).isEqualTo(PendingSagaType.CREATE);

    DataSet completed = verifier.awaitSagaCompletion(dataSetId);
    assertThat(completed.getProjectId()).as("projectId from FROST step").isNotNull().isNotEmpty();
    assertThat(completed.getPendingSagaType()).as("pendingSagaType cleared").isNull();

    // The sink table is provisioned into the per-DataSet schema (WorkspaceNames.fromDatasetId),
    // not the shared public schema.
    String sinkSchema = WorkspaceNames.fromDatasetId(dataSetId.toString());
    assertColumn(sinkSchema, "sensor_observations", "station_id", "text", false);
    assertColumn(sinkSchema, "sensor_observations", "temperature", "text", false);
    assertGeometryColumn(sinkSchema, "sensor_observations", "location", 25832);

    verifier.verifyFrostProjectExists(completed.getProjectId());
  }

  private static void assertGeometryColumn(
      String schema, String table, String column, int expectedSrid) throws SQLException {
    try (Connection connection = DriverManager.getConnection(sinkDb.getJdbcUrl(), "geo", "geo")) {
      String typeSql =
          "SELECT udt_name, is_nullable FROM information_schema.columns"
              + " WHERE table_schema = ? AND table_name = ? AND column_name = ?";
      try (var statement = connection.prepareStatement(typeSql)) {
        statement.setString(1, schema);
        statement.setString(2, table);
        statement.setString(3, column);
        try (ResultSet rs = statement.executeQuery()) {
          assertThat(rs.next()).as("column %s.%s.%s should exist", schema, table, column).isTrue();
          assertThat(rs.getString("udt_name")).isEqualTo("geometry");
          assertThat(rs.getString("is_nullable")).isEqualTo("NO");
        }
      }
      // The geometry column's SRID must reflect the data structure's crs (EPSG:25832), not the
      // 4326 default.
      try (var statement = connection.prepareStatement("SELECT Find_SRID(?, ?, ?)")) {
        statement.setString(1, schema);
        statement.setString(2, table);
        statement.setString(3, column);
        try (ResultSet rs = statement.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1)).as("SRID of %s.%s", table, column).isEqualTo(expectedSrid);
        }
      }
    }
  }

  private static void assertColumn(
      String schema, String table, String column, String dataType, boolean nullable)
      throws SQLException {
    String sql =
        "SELECT data_type, is_nullable FROM information_schema.columns"
            + " WHERE table_schema = ? AND table_name = ? AND column_name = ?";
    try (Connection connection = DriverManager.getConnection(sinkDb.getJdbcUrl(), "geo", "geo");
        var statement = connection.prepareStatement(sql)) {
      statement.setString(1, schema);
      statement.setString(2, table);
      statement.setString(3, column);
      try (ResultSet rs = statement.executeQuery()) {
        assertThat(rs.next()).as("column %s.%s.%s should exist", schema, table, column).isTrue();
        assertThat(rs.getString("data_type")).isEqualTo(dataType);
        assertThat(rs.getString("is_nullable")).isEqualTo(nullable ? "YES" : "NO");
      }
    }
  }
}

package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import de.civitascore.portal.config.FlowableSagaTestHelper;
import de.civitascore.portal.config.InfraTestDataFactory;
import de.civitascore.portal.config.SagaInfraVerifier;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.repository.DataSetRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * E2E saga test for a geo dataset: a released dataset with a POSTGIS sink runs the CREATE saga
 * through the production Flowable orchestrator with a real PostGIS handler — the sink table must
 * exist afterwards with the columns derived from the Model-Atlas JSON Schema.
 *
 * <p>Model Atlas is stubbed with a flat (sub-package-free) schema; the pipeline-engine and
 * GeoServer steps run as always-success stubs (see {@link FlowableSagaTestHelper}).
 */
@TestPropertySource(
    properties = {"kafka.enabled=true", "spring.kafka.listener.missing-topics-fatal=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Slf4j
@Import(InfraTestDataFactory.class)
class GeoDataSetSagaIntegrationTest extends AbstractSagaIntegrationTest {

  private static final String MODEL_ATLAS_URI = "http://test/geoprobe/1.0.0";

  /**
   * Flat schema with a geometry reference — the form Model Atlas serves once it recurses
   * sub-packages and resolves the geometry type package; the geometry property references its
   * inlined type definition.
   */
  private static final String FLAT_SCHEMA =
      """
      { "$id": "http://test/geoprobe/1.0.0",
        "title": "GeoProbe",
        "definitions": {
          "Point": { "type": "object" },
          "Observation": {
            "type": "object",
            "properties": {
              "station_id": { "type": "string" },
              "temperature": { "type": "string" },
              "location": { "$ref": "#/definitions/Point" }
            },
            "required": ["station_id", "temperature", "location"]
          } } }
      """;

  /** Sink + Flowable database — host-reachable, unlike the network-internal FROST PostGIS. */
  @SuppressWarnings("resource")
  static final PostgreSQLContainer sinkDb =
      new PostgreSQLContainer(
              DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("geosink")
          .withUsername("geo")
          .withPassword("geo");

  private static HttpServer modelAtlasStub;
  private static FlowableSagaTestHelper sagaHelper;

  static {
    sinkDb.start();
    try {
      modelAtlasStub = HttpServer.create(new InetSocketAddress(0), 0);
      modelAtlasStub.createContext(
          "/",
          exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = FLAT_SCHEMA.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/schema+json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(body);
            }
          });
      modelAtlasStub.start();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start Model Atlas stub", e);
    }
  }

  @Autowired private DataSetService dataSetService;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private InfraTestDataFactory data;
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
    registry.add(
        "model-atlas.baseUrl", () -> "http://localhost:" + modelAtlasStub.getAddress().getPort());
  }

  @BeforeEach
  void initHelpers() {
    verifier = new SagaInfraVerifier(dataSetRepository, frostExternalUrl, redpandaExternalUrl);
  }

  @AfterEach
  void cleanDb() {
    data.cleanAll();
  }

  @AfterAll
  static void stopAll() {
    if (sagaHelper != null) {
      sagaHelper.close();
    }
    if (modelAtlasStub != null) {
      modelAtlasStub.stop(0);
    }
    sinkDb.stop();
  }

  @Test
  void createSaga_withPostgisSink_provisionsSinkTableFromSchema() throws Exception {
    DataSource dataSource = data.createSqlDataSource();
    DataSet dataSet = data.createDataSet("Geo E2E Dataset");
    data.createGeoPipeline(dataSet, dataSource, "sensor_observations", MODEL_ATLAS_URI);
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

    assertColumn("sensor_observations", "station_id", "text", false);
    assertColumn("sensor_observations", "temperature", "text", false);
    assertGeometryColumn("sensor_observations", "location");

    verifier.verifyFrostProjectExists(completed.getProjectId());
  }

  private static void assertColumn(String table, String column, String dataType, boolean nullable)
      throws SQLException {
    String sql =
        "SELECT data_type, is_nullable FROM information_schema.columns"
            + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?";
    try (Connection connection = DriverManager.getConnection(sinkDb.getJdbcUrl(), "geo", "geo");
        var statement = connection.prepareStatement(sql)) {
      statement.setString(1, table);
      statement.setString(2, column);
      try (ResultSet rs = statement.executeQuery()) {
        assertThat(rs.next()).as("column %s.%s should exist", table, column).isTrue();
        assertThat(rs.getString("data_type")).isEqualTo(dataType);
        assertThat(rs.getString("is_nullable")).isEqualTo(nullable ? "YES" : "NO");
      }
    }
  }
}

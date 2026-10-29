package de.civitascore.portal.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import de.civitascore.portal.config.FlowableSagaTestHelper;
import de.civitascore.portal.config.InfraTestDataFactory;
import de.civitascore.portal.config.SagaInfraVerifier;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkState;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.RemovalConfirmationRequiredException;
import de.civitascore.portal.util.TestContainerImages;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * E2E saga test for the storage of removed Data sinks: a released Dataset is unreleased, one of its
 * sinks is removed, and the next release or the Dataset delete runs through the production Flowable
 * orchestrator with real PostGIS and FROST handlers.
 */
@TestPropertySource(
    properties = {"kafka.enabled=true", "spring.kafka.listener.missing-topics-fatal=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(InfraTestDataFactory.class)
class DataSinkUnprovisionSagaIntegrationTest extends AbstractSagaIntegrationTest {

  private static final Map<String, Object> MODEL =
      Map.of(
          "$id", "http://test/unprovision/1.0.0",
          "$schema", "https://json-schema.org/draft/2020-12/schema",
          "title", "Probe",
          "type", "object",
          "properties", Map.of("station_id", Map.of("type", "string")),
          "required", List.of("station_id"));

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
  @Autowired private DataSinkService dataSinkService;
  @Autowired private PipelineService pipelineService;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private InfraTestDataFactory data;
  private SagaInfraVerifier verifier;

  @BeforeAll
  static void wireSaga() throws Exception {
    try (Connection connection = DriverManager.getConnection(sinkDb.getJdbcUrl(), "geo", "geo");
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE flowable");
    }
    sagaHelper =
        new FlowableSagaTestHelper(
            kafka.getBootstrapServers(),
            frostExternalUrl,
            "http://frost-server:8080/FROST-Server/v1.1",
            sinkDb.getJdbcUrl().replace("/geosink", "/flowable"),
            sinkDb.getJdbcUrl(),
            "geo",
            "geo");
  }

  @DynamicPropertySource
  static void configureSaga(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @BeforeEach
  void initHelpers() {
    verifier = new SagaInfraVerifier(dataSetRepository, frostExternalUrl);
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
    sinkDb.stop();
  }

  @Test
  void releaseAfterRemovingTheLastPostgisSink_dropsItsTableAndSchemaAndThePendingRow()
      throws Exception {
    DataSet dataSet = data.createDataSet("Unprovision last sink");
    Pipeline geoPipeline = geoPipeline(dataSet, "removed_observations");
    DataSink removed = sinkOf(geoPipeline);
    DataSink frost = linkFrostSinkToNewPipeline(dataSet);
    String schema = releaseAndReturnToDraft(dataSet);
    String projectId = dataSetRepository.findById(dataSet.getId()).orElseThrow().getProjectId();
    assertThat(tableExists(schema, "removed_observations")).isTrue();

    deleteConfirmed(geoPipeline);
    assertThat(dataSinkRepository.findById(removed.getId()))
        .get()
        .extracting(DataSink::getState)
        .isEqualTo(DataSinkState.PENDING_UNPROVISION);
    release(dataSet);

    assertThat(tableExists(schema, "removed_observations")).isFalse();
    assertThat(schemaExists(schema)).isFalse();
    assertThat(dataSinkRepository.findById(removed.getId())).isEmpty();
    assertThat(dataSinkRepository.findById(frost.getId()))
        .get()
        .extracting(DataSink::getState, DataSink::isProvisioned)
        .containsExactly(DataSinkState.ACTIVE, true);
    assertThat(dataSetRepository.findById(dataSet.getId()).orElseThrow().getProjectId())
        .isEqualTo(projectId);
    verifier.verifyFrostProjectExists(projectId);
  }

  @Test
  void releaseAfterRemovingOnePostgisSink_keepsTheTableOfTheActiveOne() throws Exception {
    DataSet dataSet = data.createDataSet("Unprovision one of two sinks");
    Pipeline removedPipeline = geoPipeline(dataSet, "removed_observations");
    DataSink removed = sinkOf(removedPipeline);
    DataSink kept = sinkOf(geoPipeline(dataSet, "kept_observations"));
    String schema = releaseAndReturnToDraft(dataSet);

    deleteConfirmed(removedPipeline);
    release(dataSet);

    assertThat(tableExists(schema, "removed_observations")).isFalse();
    assertThat(tableExists(schema, "kept_observations")).isTrue();
    assertThat(dataSinkRepository.findById(removed.getId())).isEmpty();
    assertThat(dataSinkRepository.findById(kept.getId()))
        .get()
        .extracting(DataSink::getState, DataSink::isProvisioned)
        .containsExactly(DataSinkState.ACTIVE, true);
  }

  @Test
  void releaseAfterRemovingTheLastFrostSink_deletesTheFrostProjectAndTheDataSetForgetsIt()
      throws Exception {
    DataSet dataSet = data.createDataSet("Unprovision FROST sink");
    geoPipeline(dataSet, "kept_observations");
    releaseAndReturnToDraft(dataSet);
    String projectId = dataSetRepository.findById(dataSet.getId()).orElseThrow().getProjectId();
    DataSink frost =
        dataSinkRepository.findByDataSetId(dataSet.getId()).stream()
            .filter(sink -> sink.getDataSinkType() == DataSinkType.FROST)
            .findFirst()
            .orElseThrow();

    RemovalConfirmationRequiredException refused =
        catchThrowableOfType(
            RemovalConfirmationRequiredException.class,
            () -> dataSinkService.deleteById(frost.getId()));
    dataSinkService.deleteById(frost.getId(), Set.copyOf(refused.getImpact().confirmationIds()));
    dataSetService.stage(dataSet.getId());
    dataSetService.release(dataSet.getId());
    awaitNoPendingSaga(dataSet);

    DataSet released = dataSetRepository.findById(dataSet.getId()).orElseThrow();
    assertThat(released.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
    assertThat(released.getProjectId()).isNull();
    assertThat(released.getFrostBaseUrl()).isNull();
    assertThat(dataSinkRepository.findById(frost.getId())).isEmpty();
    verifier.verifyFrostProjectDeleted(projectId);
  }

  @Test
  void dataSetDelete_removesTheStorageOfActiveAndPendingSinks() throws Exception {
    DataSet dataSet = data.createDataSet("Delete with pending sink");
    Pipeline removedPipeline = geoPipeline(dataSet, "removed_observations");
    geoPipeline(dataSet, "kept_observations");
    String schema = releaseAndReturnToDraft(dataSet);

    deleteConfirmed(removedPipeline);
    dataSetService.deleteById(dataSet.getId());

    await()
        .atMost(120, SECONDS)
        .pollInterval(2, SECONDS)
        .until(() -> dataSetRepository.findById(dataSet.getId()).isEmpty());
    assertThat(tableExists(schema, "removed_observations")).isFalse();
    assertThat(tableExists(schema, "kept_observations")).isFalse();
  }

  private Pipeline geoPipeline(DataSet dataSet, String tableName) {
    return data.createGeoPipeline(dataSet, data.createSqlDataSource(), tableName, MODEL);
  }

  private DataSink sinkOf(Pipeline pipeline) {
    return dataSinkRepository.findByPipelinesId(pipeline.getId()).getFirst();
  }

  private DataSink linkFrostSinkToNewPipeline(DataSet dataSet) {
    Pipeline pipeline = data.createGeneratePipeline(dataSet, data.createSqlDataSource(), "frost");
    return transactionTemplate.execute(
        _ -> {
          DataSink frost =
              dataSinkRepository.findByDataSetId(dataSet.getId()).stream()
                  .filter(sink -> sink.getDataSinkType() == DataSinkType.FROST)
                  .findFirst()
                  .orElseThrow();
          frost.getPipelines().add(pipelineRepository.getReferenceById(pipeline.getId()));
          return frost;
        });
  }

  private void deleteConfirmed(Pipeline pipeline) {
    RemovalConfirmationRequiredException refused =
        catchThrowableOfType(
            RemovalConfirmationRequiredException.class,
            () -> pipelineService.deleteById(pipeline.getId()));
    pipelineService.deleteById(pipeline.getId(), Set.copyOf(refused.getImpact().confirmationIds()));
  }

  /** Releases the Dataset, then unreleases and unstages it so that its sinks can change. */
  private String releaseAndReturnToDraft(DataSet dataSet) {
    data.seedGroupAndAssignment(dataSet);
    release(dataSet);
    dataSetService.unrelease(dataSet.getId());
    awaitNoPendingSaga(dataSet);
    dataSetService.unstage(dataSet.getId());
    assertThat(dataSetRepository.findById(dataSet.getId()).orElseThrow().getDataSetStatus())
        .isEqualTo(DataSetStatus.DRAFT);
    return WorkspaceNames.fromDatasetId(dataSet.getId().toString());
  }

  private void awaitNoPendingSaga(DataSet dataSet) {
    await()
        .atMost(120, SECONDS)
        .pollInterval(2, SECONDS)
        .until(
            () ->
                dataSetRepository.findById(dataSet.getId()).orElseThrow().getPendingSagaType()
                    == null);
  }

  private void release(DataSet dataSet) {
    dataSetService.stage(dataSet.getId());
    dataSetService.release(dataSet.getId());
    verifier.awaitSagaCompletion(dataSet.getId());
  }

  private static boolean tableExists(String schema, String table) throws SQLException {
    return exists(
        "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
        schema,
        table);
  }

  private static boolean schemaExists(String schema) throws SQLException {
    return exists("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?", schema);
  }

  private static boolean exists(String sql, String... parameters) throws SQLException {
    try (Connection connection = DriverManager.getConnection(sinkDb.getJdbcUrl(), "geo", "geo");
        var statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setString(i + 1, parameters[i]);
      }
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    }
  }
}

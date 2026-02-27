package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.SagaInfraVerifier;
import de.civitascore.portal.config.SagaOrchestratorTestHelper;
import de.civitascore.portal.config.SagaTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Extended lifecycle integration tests for the Dataset Saga workflow.
 *
 * <p>Covers scenarios beyond the basic CREATE saga tested in {@link DataSetSagaE2EIntegrationTest}:
 *
 * <ul>
 *   <li>Full round-trip: CREATE → DELETE → re-CREATE
 *   <li>DELETE saga (unrelease) infrastructure teardown
 *   <li>Publish validation guards
 *   <li>Concurrent saga guards
 *   <li>Unpublish distribution cleanup
 *   <li>Multi-pipeline dataset
 * </ul>
 *
 * <p>Uses simple {@code generate} input pipelines to keep tests fast and focused on lifecycle
 * transitions rather than datasource types.
 */
@Tag("saga")
@TestPropertySource(
    properties = {"kafka.enabled=true", "spring.kafka.listener.missing-topics-fatal=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Slf4j
class DataSetSagaLifecycleIntegrationTest extends BaseKeycloakIntegrationTest {

  static final Network sagaNetwork = Network.newNetwork();

  @SuppressWarnings("resource")
  static final GenericContainer<?> postgis =
      new GenericContainer<>("postgis/postgis:16-3.4-alpine")
          .withNetwork(sagaNetwork)
          .withNetworkAliases("database")
          .withEnv("POSTGRES_DB", "sensorthings")
          .withEnv("POSTGRES_USER", "sensorthings")
          .withEnv("POSTGRES_PASSWORD", "ChangeMe")
          .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));

  @SuppressWarnings("resource")
  static final GenericContainer<?> frost =
      new GenericContainer<>("hylkevds/frost-http-projects:latest")
          .withNetwork(sagaNetwork)
          .withNetworkAliases("frost-server")
          .withExposedPorts(8080)
          .dependsOn(postgis)
          .withEnv("serviceRootUrl", "http://localhost:8080/FROST-Server/")
          .withEnv("plugins_modelLoader_enable", "true")
          .withEnv("plugins_multiDatastream_enable", "false")
          .withEnv("plugins_actuation_enable", "false")
          .withEnv("persistence_db_driver", "org.postgresql.Driver")
          .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
          .withEnv("persistence_db_username", "sensorthings")
          .withEnv("persistence_db_password", "ChangeMe")
          .withEnv("persistence_autoUpdateDatabase", "true")
          .withEnv("plugins_modelLoader_securityPath", "")
          .withEnv("plugins_modelLoader_securityFiles", "")
          .waitingFor(
              Wait.forHttp("/FROST-Server/v1.1/Projects")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

  @SuppressWarnings("resource")
  static final GenericContainer<?> redpandaConnect =
      new GenericContainer<>("redpandadata/connect:4")
          .withNetwork(sagaNetwork)
          .withNetworkAliases("redpanda-connect")
          .withExposedPorts(4195)
          .withCommand("streams")
          .withEnv("FROST_BASE", "http://frost-server:8080/FROST-Server/v1.1")
          .waitingFor(
              Wait.forHttp("/ready").forPort(4195).withStartupTimeout(Duration.ofSeconds(60)));

  @SuppressWarnings("resource")
  static final GenericContainer<?> mosquitto =
      new GenericContainer<>("eclipse-mosquitto:2")
          .withNetwork(sagaNetwork)
          .withNetworkAliases("mqtt-broker")
          .withExposedPorts(1883)
          .withCopyFileToContainer(
              MountableFile.forHostPath(
                  Paths.get("src/testIntegration/resources/mosquitto/mosquitto.conf")
                      .toAbsolutePath()),
              "/mosquitto/config/mosquitto.conf")
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

  private static SagaOrchestratorTestHelper sagaHelper;
  private static String frostExternalUrl;
  private static String redpandaExternalUrl;

  static {
    postgis.start();
    frost.start();
    kafka.start();
    mosquitto.start();
    redpandaConnect.start();

    frostExternalUrl =
        "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + "/FROST-Server/v1.1";
    redpandaExternalUrl =
        "http://" + redpandaConnect.getHost() + ":" + redpandaConnect.getMappedPort(4195);

    log.info("Lifecycle tests: FROST at {}", frostExternalUrl);
    log.info("Lifecycle tests: Kafka at {}", kafka.getBootstrapServers());
    log.info("Lifecycle tests: Redpanda Connect at {}", redpandaExternalUrl);
  }

  @Autowired private DataSetService dataSetService;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DistributionRepository distributionRepository;
  @Autowired private PipelineRepository pipelineRepository;

  @Autowired private SagaTestDataFactory data;
  private SagaInfraVerifier verifier;

  @BeforeEach
  void initHelpers() {
    verifier = new SagaInfraVerifier(dataSetRepository, frostExternalUrl, redpandaExternalUrl);
  }

  @BeforeAll
  static void wireSaga() throws Exception {
    sagaHelper =
        new SagaOrchestratorTestHelper(
            kafka.getBootstrapServers(), frostExternalUrl, redpandaExternalUrl);
  }

  @DynamicPropertySource
  static void configureSagaKafka(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
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
    redpandaConnect.stop();
    mosquitto.stop();
    kafka.stop();
    frost.stop();
    postgis.stop();
    sagaNetwork.close();
  }

  @Test
  @DisplayName("Full lifecycle: create → unrelease (DELETE saga) → re-release (new CREATE saga)")
  void fullRoundTrip_createThenDeleteThenReCreate() throws Exception {
    DataSource dataSource = data.createMqttDataSource();
    DataSet dataSet = data.createDataSet("RoundTrip Dataset");
    Pipeline pipeline = data.createGeneratePipeline(dataSet, dataSource);
    data.seedGroupAndAssignment(dataSet);
    UUID dataSetId = dataSet.getId();
    UUID pipelineId = pipeline.getId();

    // Phase 1: DRAFT → READY → AVAILABLE (CREATE saga)
    dataSetService.publish(dataSetId);
    dataSetService.release(dataSetId);

    DataSet created = verifier.awaitSagaCompletion(dataSetId);
    String firstProjectId = created.getProjectId();
    assertThat(firstProjectId).as("First CREATE: projectId").isNotNull();
    assertThat(created.getRouteId()).as("First CREATE: routeId").isNotNull();
    assertThat(created.getPipelineIds()).as("First CREATE: pipelineIds").isNotNull();

    // Phase 2: AVAILABLE → unrelease → DELETE saga → READY
    dataSetService.unrelease(dataSetId);
    DataSet afterUnrelease = dataSetRepository.findById(dataSetId).orElseThrow();
    assertThat(afterUnrelease.getPendingSagaType()).isEqualTo(PendingSagaType.DELETE);

    DataSet deleted = verifier.awaitSagaDeletion(dataSetId);
    assertThat(deleted.getDataSetStatus())
        .as("After DELETE saga: status should be READY")
        .isEqualTo(DataSetStatus.READY);
    assertThat(deleted.getProjectId()).as("After DELETE: projectId cleared").isNull();
    assertThat(deleted.getFrostBaseUrl()).as("After DELETE: frostBaseUrl cleared").isNull();
    assertThat(deleted.getRouteId()).as("After DELETE: routeId cleared").isNull();
    assertThat(deleted.getServiceId()).as("After DELETE: serviceId cleared").isNull();
    assertThat(deleted.getPublicUrl()).as("After DELETE: publicUrl cleared").isNull();
    assertThat(deleted.getPipelineIds()).as("After DELETE: pipelineIds cleared").isNull();
    assertThat(deleted.getPendingSagaType()).as("After DELETE: pendingSagaType cleared").isNull();

    verifier.verifyFrostProjectDeleted(firstProjectId);
    verifier.verifyRedpandaPipelineDeleted(pipelineId);

    // Phase 3: READY → AVAILABLE (new CREATE saga)
    dataSetService.release(dataSetId);
    DataSet reCreated = verifier.awaitSagaCompletion(dataSetId);

    assertThat(reCreated.getProjectId())
        .as("Re-create: new projectId should differ from first")
        .isNotNull()
        .isNotEqualTo(firstProjectId);
    assertThat(reCreated.getRouteId()).as("Re-create: routeId").isNotNull();
    assertThat(reCreated.getPipelineIds()).as("Re-create: pipelineIds").isNotNull();
    assertThat(reCreated.getDataSetStatus())
        .as("Re-create: status AVAILABLE")
        .isEqualTo(DataSetStatus.AVAILABLE);

    log.info(
        "Full round-trip completed: firstProjectId={}, secondProjectId={}",
        firstProjectId,
        reCreated.getProjectId());
  }

  @Test
  @DisplayName("Unrelease DELETE saga tears down FROST project, APISIX route, and pipeline")
  void unrelease_deleteSagaCleansInfrastructure() throws Exception {
    DataSource dataSource = data.createMqttDataSource();
    DataSet dataSet = data.createDataSet("Delete Saga Dataset");
    Pipeline pipeline = data.createGeneratePipeline(dataSet, dataSource);
    data.seedGroupAndAssignment(dataSet);
    UUID dataSetId = dataSet.getId();
    UUID pipelineId = pipeline.getId();

    dataSetService.publish(dataSetId);
    dataSetService.release(dataSetId);
    DataSet created = verifier.awaitSagaCompletion(dataSetId);

    verifier.verifyFrostProjectExists(created.getProjectId());
    verifier.verifyRedpandaPipelineExists(pipelineId);

    DataSet withDist = dataSetRepository.findByIdWithRelations(dataSetId).orElseThrow();
    long autoDistCount =
        withDist.getDistributions().stream()
            .filter(d -> Boolean.TRUE.equals(d.getAutoGenerated()))
            .count();
    assertThat(autoDistCount).as("Should have auto-generated distributions").isGreaterThan(0);

    dataSetService.unrelease(dataSetId);
    DataSet deleted = verifier.awaitSagaDeletion(dataSetId);

    assertThat(deleted.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    assertThat(deleted.getProjectId()).isNull();
    assertThat(deleted.getFrostBaseUrl()).isNull();
    assertThat(deleted.getRouteId()).isNull();
    assertThat(deleted.getServiceId()).isNull();
    assertThat(deleted.getPublicUrl()).isNull();
    assertThat(deleted.getPipelineIds()).isNull();

    verifier.verifyFrostProjectDeleted(created.getProjectId());
    verifier.verifyRedpandaPipelineDeleted(pipelineId);

    DataSet afterDelete = dataSetRepository.findByIdWithRelations(dataSetId).orElseThrow();
    long remainingAutoDist =
        afterDelete.getDistributions().stream()
            .filter(d -> Boolean.TRUE.equals(d.getAutoGenerated()))
            .count();
    assertThat(remainingAutoDist)
        .as("Auto-generated distributions should be removed after DELETE saga")
        .isZero();

    verifier.verifyApisixReceivedRequests(sagaHelper, dataSetId, "DELETE");

    log.info(
        "DELETE saga completed: projectId={} deleted, pipelineId={} deleted",
        created.getProjectId(),
        pipelineId);
  }

  @Nested
  @DisplayName("Publish validation")
  class PublishValidation {

    @Test
    @DisplayName("publish() fails without pipelines")
    void publishFailsWithoutPipeline() {
      DataSet dataSet = data.createDataSet("No Pipeline Dataset");
      data.seedGroupAndAssignment(dataSet);

      assertThatThrownBy(() -> dataSetService.publish(dataSet.getId()))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Pipeline");
    }

    @Test
    @DisplayName("publish() fails without datasource on pipeline")
    void publishFailsWithoutDataSource() {
      DataSet dataSet = data.createDataSet("No DataSource Dataset");
      data.seedGroupAndAssignment(dataSet);

      // Create pipeline without datasources
      Pipeline pipeline = new Pipeline();
      pipeline.setName("empty-pipeline-" + System.nanoTime());
      pipeline.setDescription("Pipeline without datasources");
      pipeline.setDataSet(dataSet);
      pipeline.setApis(List.of("/v1.1/Things"));
      pipeline.setModel(Map.of("input", Map.of("generate", Map.of("count", 1))));
      Pipeline saved = pipelineRepository.save(pipeline);
      dataSet.getPipelines().add(saved);
      dataSetRepository.save(dataSet);

      assertThatThrownBy(() -> dataSetService.publish(dataSet.getId()))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSource");
    }

    @Test
    @DisplayName("publish() fails on non-DRAFT dataset")
    void publishFailsOnNonDraftStatus() {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Already Published Dataset");
      data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);

      dataSetService.publish(dataSet.getId());

      assertThatThrownBy(() -> dataSetService.publish(dataSet.getId()))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSet is already published");
    }
  }

  @Test
  @DisplayName("unrelease() rejects when CREATE saga is still in-flight")
  void concurrentSagaGuard_unreleaseWhileSagaInFlight() throws Exception {
    DataSource dataSource = data.createMqttDataSource();
    DataSet dataSet = data.createDataSet("Concurrent Guard Dataset");
    data.createGeneratePipeline(dataSet, dataSource);
    data.seedGroupAndAssignment(dataSet);
    UUID dataSetId = dataSet.getId();

    dataSetService.publish(dataSetId);
    dataSetService.release(dataSetId);

    // pendingSagaType is CREATE — try to unrelease immediately
    assertThatThrownBy(() -> dataSetService.unrelease(dataSetId))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("saga is in-flight");

    DataSet completed = verifier.awaitSagaCompletion(dataSetId);
    assertThat(completed.getProjectId()).isNotNull();
    assertThat(completed.getPendingSagaType()).isNull();

    log.info("Concurrent saga guard verified: unrelease rejected during in-flight CREATE saga");
  }

  @Test
  @DisplayName("unpublish() removes auto-generated distributions but preserves manual ones")
  void unpublish_removesAutoGeneratedDistributionsOnly() {
    DataSource dataSource = data.createMqttDataSource();
    DataSet dataSet = data.createDataSet("Unpublish Distribution Dataset");
    data.createGeneratePipelineWithMultipleApis(dataSet, dataSource);
    data.seedGroupAndAssignment(dataSet);
    UUID dataSetId = dataSet.getId();

    DataSet published = dataSetService.publish(dataSetId);
    assertThat(published.getDistributions())
        .as("Publish should create auto-generated distributions")
        .isNotEmpty();

    int autoGenCount =
        (int)
            published.getDistributions().stream()
                .filter(d -> Boolean.TRUE.equals(d.getAutoGenerated()))
                .count();
    assertThat(autoGenCount)
        .as("Should have at least 2 auto-generated distributions")
        .isGreaterThanOrEqualTo(2);

    // Manually add a non-auto-generated distribution
    Distribution manual = new Distribution();
    manual.setAccessUrl("https://manual.example.com/data");
    manual.setApiType("REST");
    manual.setFormat("text/csv");
    manual.setAutoGenerated(false);
    manual.setDataSet(dataSet);
    distributionRepository.save(manual);

    DataSet unpublished = dataSetService.unpublish(dataSetId);
    assertThat(unpublished.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);

    DataSet reloaded = dataSetRepository.findByIdWithRelations(dataSetId).orElseThrow();
    long remainingAutoGen =
        reloaded.getDistributions().stream()
            .filter(d -> Boolean.TRUE.equals(d.getAutoGenerated()))
            .count();
    long remainingManual =
        reloaded.getDistributions().stream()
            .filter(d -> !Boolean.TRUE.equals(d.getAutoGenerated()))
            .count();

    assertThat(remainingAutoGen).as("Auto-generated distributions should be removed").isZero();
    assertThat(remainingManual).as("Manual distribution should be preserved").isEqualTo(1);

    log.info(
        "Unpublish verified: {} auto-generated removed, {} manual preserved",
        autoGenCount,
        remainingManual);
  }

  @Test
  @DisplayName("CREATE saga deploys all pipelines when dataset has multiple pipelines")
  void createSaga_withMultiplePipelines_allDeployed() throws Exception {
    DataSource dataSource = data.createMqttDataSource();
    DataSet dataSet = data.createDataSet("Multi-Pipeline Dataset");
    Pipeline pipeline1 = data.createGeneratePipeline(dataSet, dataSource, "/v1.1/Things", "p1");
    Pipeline pipeline2 =
        data.createGeneratePipeline(dataSet, dataSource, "/v1.1/Datastreams", "p2");
    data.seedGroupAndAssignment(dataSet);
    UUID dataSetId = dataSet.getId();

    dataSetService.publish(dataSetId);
    dataSetService.release(dataSetId);
    DataSet completed = verifier.awaitSagaCompletion(dataSetId);

    assertThat(completed.getPipelineIds())
        .as("pipelineIds should contain both deployed pipelines")
        .hasSize(2)
        .contains(pipeline1.getId().toString(), pipeline2.getId().toString());

    verifier.verifyRedpandaPipelineExists(pipeline1.getId());
    verifier.verifyRedpandaPipelineExists(pipeline2.getId());

    DataSet withDist = dataSetRepository.findByIdWithRelations(dataSetId).orElseThrow();
    List<String> distUrls =
        withDist.getDistributions().stream()
            .filter(d -> Boolean.TRUE.equals(d.getAutoGenerated()))
            .map(Distribution::getAccessUrl)
            .toList();
    assertThat(distUrls)
        .as("Should have distributions for both pipeline APIs")
        .anyMatch(url -> url.contains("/v1.1/Things"))
        .anyMatch(url -> url.contains("/v1.1/Datastreams"));

    log.info(
        "Multi-pipeline saga completed: pipelineIds={}, distributionCount={}",
        completed.getPipelineIds(),
        distUrls.size());
  }
}

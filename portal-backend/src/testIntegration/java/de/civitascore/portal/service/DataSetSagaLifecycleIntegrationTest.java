package de.civitascore.portal.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.SagaInfraVerifier;
import de.civitascore.portal.config.SagaOrchestratorTestHelper;
import de.civitascore.portal.config.SagaTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.InvalidInputException;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;

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
class DataSetSagaLifecycleIntegrationTest extends AbstractSagaIntegrationTest {

  static final Network sagaNetwork = Network.newNetwork();

  static final GenericContainer<?> postgis = createPostgis(sagaNetwork);
  static final GenericContainer<?> frost = createFrost(sagaNetwork, postgis);
  static final KafkaContainer kafka = createKafka();
  static final GenericContainer<?> redpandaConnect = createRedpandaConnect(sagaNetwork);
  static final GenericContainer<?> mosquitto = createMosquitto(sagaNetwork);

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
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired private SagaTestDataFactory data;
  private SagaInfraVerifier verifier;

  @BeforeEach
  void initHelpers() {
    verifier = new SagaInfraVerifier(dataSetRepository, frostExternalUrl, redpandaExternalUrl);
  }

  @BeforeAll
  static void wireSaga() throws Exception {
    String frostPublicUrl = "http://frost-server:8080/FROST-Server/v1.1";
    sagaHelper =
        new SagaOrchestratorTestHelper(
            kafka.getBootstrapServers(), frostExternalUrl, frostPublicUrl, redpandaExternalUrl);
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

  @Nested
  @DisplayName("UPDATE saga (updatePublishedMeta)")
  class UpdateSaga {

    @Test
    @DisplayName(
        "updatePublishedMeta on AVAILABLE dataset triggers UPDATE saga and preserves infra")
    void updatePublishedMeta_triggersUpdateSaga() throws Exception {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Update Saga Dataset");
      Pipeline pipeline = data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);
      UUID dataSetId = dataSet.getId();

      // DRAFT → READY → AVAILABLE (CREATE saga completes first)
      dataSetService.publish(dataSetId);
      dataSetService.release(dataSetId);
      DataSet created = verifier.awaitSagaCompletion(dataSetId);
      String originalProjectId = created.getProjectId();
      String originalRouteId = created.getRouteId();

      assertThat(originalProjectId).isNotNull();
      assertThat(originalRouteId).isNotNull();

      // Update metadata on AVAILABLE dataset → triggers UPDATE saga
      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Name " + System.nanoTime());
      updateInput.setDescription("Updated description");
      DataSet updated = dataSetService.updatePublishedMeta(dataSetId, updateInput);

      assertThat(updated.getPendingSagaType())
          .as("UPDATE saga should be pending")
          .isEqualTo(PendingSagaType.UPDATE);
      assertThat(updated.getDataSetStatus())
          .as("Status should remain AVAILABLE during UPDATE")
          .isEqualTo(DataSetStatus.AVAILABLE);

      DataSet completed = verifier.awaitSagaUpdate(dataSetId);

      assertThat(completed.getProjectId())
          .as("projectId should be preserved after UPDATE")
          .isEqualTo(originalProjectId);
      assertThat(completed.getRouteId())
          .as("routeId should be preserved after UPDATE")
          .isEqualTo(originalRouteId);
      assertThat(completed.getPendingSagaType()).as("pendingSagaType should be cleared").isNull();
      assertThat(completed.getPipelineIds())
          .as("pipelineIds should still contain the pipeline")
          .contains(pipeline.getId().toString());

      log.info(
          "UPDATE saga completed: projectId={}, name={}",
          completed.getProjectId(),
          completed.getName());
    }

    @Test
    @DisplayName("updatePublishedMeta skips saga for READY dataset (no infra yet)")
    void updatePublishedMeta_readyDataset_noSagaTriggered() {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Ready Update Dataset");
      data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);
      UUID dataSetId = dataSet.getId();

      // DRAFT → READY only (no release, no infra)
      dataSetService.publish(dataSetId);

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Ready Name " + System.nanoTime());
      updateInput.setDescription("Updated ready description");
      DataSet updated = dataSetService.updatePublishedMeta(dataSetId, updateInput);

      assertThat(updated.getPendingSagaType())
          .as("No saga should be triggered for READY dataset")
          .isNull();
      assertThat(updated.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(updated.getName()).contains("Updated Ready Name");

      log.info("READY dataset updated without saga trigger");
    }

    @Test
    @DisplayName("updatePublishedMeta rejects DRAFT dataset")
    void updatePublishedMeta_draftDataset_rejected() {
      DataSet dataSet = data.createDataSet("Draft Meta Update Dataset");

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Attempted update " + System.nanoTime());

      assertThatThrownBy(() -> dataSetService.updatePublishedMeta(dataSet.getId(), updateInput))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName(
        "Full cycle: CREATE → UPDATE (metadata change) → DELETE preserves correct transitions")
    void createThenUpdateThenDelete_fullCycle() throws Exception {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Full Cycle Update Dataset");
      Pipeline pipeline = data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);
      UUID dataSetId = dataSet.getId();

      // Phase 1: CREATE
      dataSetService.publish(dataSetId);
      dataSetService.release(dataSetId);
      DataSet created = verifier.awaitSagaCompletion(dataSetId);
      assertThat(created.getProjectId()).isNotNull();

      // Phase 2: UPDATE metadata
      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Renamed Dataset " + System.nanoTime());
      dataSetService.updatePublishedMeta(dataSetId, updateInput);
      DataSet afterUpdate = verifier.awaitSagaUpdate(dataSetId);
      assertThat(afterUpdate.getProjectId())
          .as("projectId preserved through UPDATE")
          .isEqualTo(created.getProjectId());

      // Phase 3: DELETE (unrelease)
      dataSetService.unrelease(dataSetId);
      DataSet afterDelete = verifier.awaitSagaDeletion(dataSetId);
      assertThat(afterDelete.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(afterDelete.getProjectId()).isNull();

      log.info("Full CREATE → UPDATE → DELETE cycle completed successfully");
    }
  }

  @Nested
  @DisplayName("DataSetSagaResultListener direct message handling")
  class ResultListenerTests {

    private static final String SAGA_RESULT_TOPIC = "de.civitascore.saga.result";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends a SAGA_COMPLETED message directly to Kafka and verifies the listener persists
     * infrastructure fields on the DataSet.
     */
    @Test
    @DisplayName("SAGA_COMPLETED message persists infrastructure fields on DataSet")
    void completedMessage_persistsInfrastructureFields() throws Exception {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Listener Completed Dataset");
      data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);
      UUID dataSetId = dataSet.getId();

      // Put dataset in AVAILABLE + pending CREATE state (as if release() was called)
      dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
      dataSet.setPendingSagaType(PendingSagaType.CREATE);
      dataSetRepository.save(dataSet);

      // Send SAGA_COMPLETED directly to the result topic
      String projectId = "proj-" + UUID.randomUUID();
      String message =
          objectMapper.writeValueAsString(
              Map.of(
                  "type",
                  "SAGA_COMPLETED",
                  "result",
                  Map.of(
                      "datasetId", dataSetId.toString(),
                      "projectId", projectId,
                      "baseUrl", "http://frost:8080/FROST-Server/v1.1/Projects(" + projectId + ")",
                      "routeId", dataSetId.toString(),
                      "serviceId", dataSetId.toString(),
                      "publicUrl", "http://gateway/datasets/" + dataSetId,
                      "pipelineIds", List.of("pipe-1"))));
      kafkaTemplate.send(SAGA_RESULT_TOPIC, dataSetId.toString(), message).get();

      await()
          .atMost(30, SECONDS)
          .pollInterval(1, SECONDS)
          .untilAsserted(
              () -> {
                DataSet ds = dataSetRepository.findById(dataSetId).orElseThrow();
                assertThat(ds.getPendingSagaType()).as("pendingSagaType cleared").isNull();
                assertThat(ds.getProjectId()).as("projectId persisted").isEqualTo(projectId);
              });

      DataSet persisted = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(persisted.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(persisted.getFrostBaseUrl()).contains(projectId);
      assertThat(persisted.getRouteId()).isEqualTo(dataSetId.toString());
      assertThat(persisted.getServiceId()).isEqualTo(dataSetId.toString());
      assertThat(persisted.getPublicUrl()).contains("/datasets/" + dataSetId);
      assertThat(persisted.getPipelineIds()).containsExactly("pipe-1");

      log.info("ResultListener SAGA_COMPLETED test passed for dataset {}", dataSetId);
    }

    /**
     * Sends a SAGA_FAILED message directly to Kafka for a CREATE saga and verifies the listener
     * reverts the dataset status to READY.
     */
    @Test
    @DisplayName("SAGA_FAILED for CREATE reverts dataset to READY")
    void failedMessage_createSaga_revertsToReady() throws Exception {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Listener Failed Dataset");
      data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);
      UUID dataSetId = dataSet.getId();

      // Put dataset in AVAILABLE + pending CREATE
      dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
      dataSet.setPendingSagaType(PendingSagaType.CREATE);
      dataSetRepository.save(dataSet);

      String message =
          objectMapper.writeValueAsString(
              Map.of(
                  "type", "SAGA_FAILED",
                  "datasetId", dataSetId.toString(),
                  "failedStep", "FROST",
                  "error", "Connection refused",
                  "compensated", true));
      kafkaTemplate.send(SAGA_RESULT_TOPIC, dataSetId.toString(), message).get();

      await()
          .atMost(30, SECONDS)
          .pollInterval(1, SECONDS)
          .untilAsserted(
              () -> {
                DataSet ds = dataSetRepository.findById(dataSetId).orElseThrow();
                assertThat(ds.getPendingSagaType()).as("pendingSagaType cleared").isNull();
              });

      DataSet persisted = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(persisted.getDataSetStatus())
          .as("CREATE failure should revert to READY")
          .isEqualTo(DataSetStatus.READY);
      assertThat(persisted.getProjectId()).as("No infra fields should be set").isNull();

      log.info("ResultListener SAGA_FAILED test passed for dataset {}", dataSetId);
    }

    /**
     * Sends a SAGA_FAILED message for a DELETE saga and verifies the dataset stays AVAILABLE (since
     * stale infrastructure may still exist).
     */
    @Test
    @DisplayName("SAGA_FAILED for DELETE keeps dataset AVAILABLE")
    void failedMessage_deleteSaga_staysAvailable() throws Exception {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Listener Delete Failed Dataset");
      data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);
      UUID dataSetId = dataSet.getId();

      // Simulate an AVAILABLE dataset with infra, pending DELETE
      dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
      dataSet.setProjectId("proj-existing");
      dataSet.setRouteId("route-existing");
      dataSet.setPendingSagaType(PendingSagaType.DELETE);
      dataSetRepository.save(dataSet);

      String message =
          objectMapper.writeValueAsString(
              Map.of(
                  "type", "SAGA_FAILED",
                  "datasetId", dataSetId.toString(),
                  "failedStep", "APISIX",
                  "error", "Route not found",
                  "compensated", false));
      kafkaTemplate.send(SAGA_RESULT_TOPIC, dataSetId.toString(), message).get();

      await()
          .atMost(30, SECONDS)
          .pollInterval(1, SECONDS)
          .untilAsserted(
              () -> {
                DataSet ds = dataSetRepository.findById(dataSetId).orElseThrow();
                assertThat(ds.getPendingSagaType()).as("pendingSagaType cleared").isNull();
              });

      DataSet persisted = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(persisted.getDataSetStatus())
          .as("DELETE failure should keep AVAILABLE (stale infra may exist)")
          .isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(persisted.getProjectId())
          .as("Infra fields should be preserved on DELETE failure")
          .isEqualTo("proj-existing");

      log.info("ResultListener DELETE SAGA_FAILED test passed for dataset {}", dataSetId);
    }

    /** Messages with unknown type should be silently ignored — no dataset changes. */
    @Test
    @DisplayName("Unknown message type is silently ignored")
    void unknownType_silentlyIgnored() throws Exception {
      DataSource dataSource = data.createMqttDataSource();
      DataSet dataSet = data.createDataSet("Listener Unknown Type Dataset");
      data.createGeneratePipeline(dataSet, dataSource);
      data.seedGroupAndAssignment(dataSet);
      UUID dataSetId = dataSet.getId();

      dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
      dataSet.setPendingSagaType(PendingSagaType.CREATE);
      dataSetRepository.save(dataSet);

      String message =
          objectMapper.writeValueAsString(
              Map.of("type", "SAGA_UNKNOWN", "datasetId", dataSetId.toString()));
      kafkaTemplate.send(SAGA_RESULT_TOPIC, dataSetId.toString(), message).get();

      // Send a valid COMPLETED after to prove the listener is consuming
      String validMessage =
          objectMapper.writeValueAsString(
              Map.of(
                  "type",
                  "SAGA_COMPLETED",
                  "result",
                  Map.of("datasetId", dataSetId.toString(), "projectId", "proj-after-unknown")));
      kafkaTemplate.send(SAGA_RESULT_TOPIC, dataSetId.toString(), validMessage).get();

      await()
          .atMost(30, SECONDS)
          .pollInterval(1, SECONDS)
          .untilAsserted(
              () -> {
                DataSet ds = dataSetRepository.findById(dataSetId).orElseThrow();
                assertThat(ds.getPendingSagaType()).isNull();
                assertThat(ds.getProjectId()).isEqualTo("proj-after-unknown");
              });

      log.info("ResultListener unknown type test passed — ignored and continued processing");
    }
  }
}

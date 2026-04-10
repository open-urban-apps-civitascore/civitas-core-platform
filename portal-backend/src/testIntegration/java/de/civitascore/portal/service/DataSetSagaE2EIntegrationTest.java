package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.InfraTestDataFactory;
import de.civitascore.portal.config.SagaInfraVerifier;
import de.civitascore.portal.config.SagaOrchestratorTestHelper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.DataSetRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

/**
 * End-to-end integration test for the full Dataset Saga workflow.
 *
 * <p>Validates the complete loop: portal-backend creates entities, release() triggers saga,
 * orchestrator processes steps against real FROST + mock APISIX + real Redpanda Connect, result
 * flows back, and infrastructure fields are persisted on the DataSet entity.
 *
 * <p>Two test scenarios cover both datasource types:
 *
 * <ul>
 *   <li>SQL datasource: PostgreSQL → Redpanda Connect sql_select → FROST
 *   <li>MQTT datasource: Mosquitto → Redpanda Connect mqtt → FROST
 * </ul>
 *
 * <p>Uses real Kafka (not EmbeddedKafka) because the orchestrator uses raw KafkaConsumer/Producer
 * which are incompatible with Spring's embedded broker.
 */
@Tag("saga")
@TestPropertySource(
    properties = {"kafka.enabled=true", "spring.kafka.listener.missing-topics-fatal=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Slf4j
@Import(InfraTestDataFactory.class)
class DataSetSagaE2EIntegrationTest extends AbstractSagaIntegrationTest {

  static final Network sagaNetwork = Network.newNetwork();

  static final GenericContainer<?> postgis = createPostgis(sagaNetwork);
  static final GenericContainer<?> frost = createFrost(sagaNetwork, postgis);
  static final KafkaContainer kafka = createKafka();
  static final GenericContainer<?> redpandaConnect = createRedpandaConnect(sagaNetwork);
  static final GenericContainer<?> mosquitto = createMosquitto(sagaNetwork);

  @SuppressWarnings("resource")
  static final GenericContainer<?> datasourcePg =
      new GenericContainer<>("postgres:15-alpine")
          .withNetwork(sagaNetwork)
          .withNetworkAliases("datasource-db")
          .withEnv("POSTGRES_DB", "testdb")
          .withEnv("POSTGRES_USER", "testuser")
          .withEnv("POSTGRES_PASSWORD", "testpass")
          .withCopyToContainer(
              MountableFile.forClasspathResource("datasource-db/init.sql"),
              "/docker-entrypoint-initdb.d/init.sql")
          .waitingFor(
              org.testcontainers.containers.wait.strategy.Wait.forLogMessage(
                  ".*database system is ready to accept connections.*", 2));

  private static SagaOrchestratorTestHelper sagaHelper;
  private static String frostExternalUrl;
  private static String redpandaExternalUrl;

  static {
    postgis.start();
    frost.start();
    kafka.start();
    datasourcePg.start();
    mosquitto.start();
    redpandaConnect.start();

    frostExternalUrl =
        "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + "/FROST-Server/v1.1";
    redpandaExternalUrl =
        "http://" + redpandaConnect.getHost() + ":" + redpandaConnect.getMappedPort(4195);

    log.info("FROST available at {}", frostExternalUrl);
    log.info("Kafka available at {}", kafka.getBootstrapServers());
    log.info("Redpanda Connect available at {}", redpandaExternalUrl);
    log.info("Datasource PG available at datasource-db:5432 (internal)");
    log.info("Mosquitto available at mqtt-broker:1883 (internal)");
  }

  @Autowired private DataSetService dataSetService;
  @Autowired private DataSetRepository dataSetRepository;

  @Autowired private InfraTestDataFactory data;
  private SagaInfraVerifier verifier;

  @BeforeEach
  void initHelpers() {
    verifier = new SagaInfraVerifier(dataSetRepository, frostExternalUrl, redpandaExternalUrl);
  }

  @BeforeAll
  static void seedAndWireSaga() throws Exception {
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
    datasourcePg.stop();
    kafka.stop();
    frost.stop();
    postgis.stop();
    sagaNetwork.close();
  }

  @Test
  void createSaga_withSqlPipeline_resultPersistedAndDataFlowsToFrost() throws Exception {
    DataSource sqlDataSource = data.createSqlDataSource();
    DataSet dataSet = data.createDataSet("SQL E2E Dataset");
    Pipeline sqlPipeline = data.createSqlPipeline(dataSet, sqlDataSource);
    data.seedGroupAndAssignment(dataSet);

    UUID dataSetId = dataSet.getId();
    UUID pipelineId = sqlPipeline.getId();

    // Publish: DRAFT → READY (generates distributions from pipeline APIs)
    DataSet published = dataSetService.markReady(dataSetId);
    assertThat(published.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    assertThat(published.getDistributions())
        .as("Publish should create auto-generated distributions from pipeline APIs")
        .isNotEmpty();

    // Release: READY → AVAILABLE (triggers CREATE saga)
    DataSet released = dataSetService.release(dataSetId);
    assertThat(released.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
    assertThat(released.getPendingSagaType()).isEqualTo(PendingSagaType.CREATE);

    DataSet completed = verifier.awaitSagaCompletion(dataSetId);

    assertThat(completed.getProjectId()).as("projectId from FROST step").isNotNull().isNotEmpty();
    assertThat(completed.getFrostBaseUrl())
        .as("frostBaseUrl should contain the projectId")
        .isNotNull()
        .contains("Projects(" + completed.getProjectId() + ")");
    assertThat(completed.getRouteId())
        .as("routeId from APISIX step (deterministic = datasetId)")
        .isEqualTo(dataSetId.toString());
    assertThat(completed.getServiceId())
        .as("serviceId from APISIX step (deterministic = datasetId)")
        .isEqualTo(dataSetId.toString());
    assertThat(completed.getPublicUrl())
        .as("publicUrl from APISIX step")
        .isNotNull()
        .contains("/datasets/" + dataSetId);
    assertThat(completed.getPipelineIds())
        .as("pipelineIds from Redpanda step")
        .isNotNull()
        .containsExactly(pipelineId.toString());
    assertThat(completed.getPendingSagaType()).as("pendingSagaType cleared").isNull();

    DataSet withDistributions = dataSetRepository.findById(dataSetId).orElseThrow();
    for (Distribution dist : withDistributions.getDistributions()) {
      if (Boolean.TRUE.equals(dist.getAutoGenerated())) {
        assertThat(dist.getAccessUrl())
            .as("Auto-generated distribution accessUrl should start with publicUrl")
            .startsWith(completed.getPublicUrl());
      }
    }

    verifier.verifyFrostProjectExists(completed.getProjectId());
    verifier.verifyApisixReceivedRequests(sagaHelper, dataSetId, "PUT");
    verifier.verifyRedpandaPipelineExists(pipelineId);
    verifier.verifyFrostHasThings("SQL Sensor");

    log.info(
        "SQL E2E saga completed: projectId={}, routeId={}, pipelineIds={}",
        completed.getProjectId(),
        completed.getRouteId(),
        completed.getPipelineIds());
  }

  @Test
  void createSaga_withMqttPipeline_resultPersistedAndDataFlowsToFrost() throws Exception {
    DataSource mqttDataSource = data.createMqttDataSource();
    DataSet dataSet = data.createDataSet("MQTT E2E Dataset");
    Pipeline mqttPipeline = data.createMqttPipeline(dataSet, mqttDataSource);
    data.seedGroupAndAssignment(dataSet);

    UUID dataSetId = dataSet.getId();
    UUID pipelineId = mqttPipeline.getId();

    // Publish: DRAFT → READY
    DataSet published = dataSetService.markReady(dataSetId);
    assertThat(published.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    assertThat(published.getDistributions()).isNotEmpty();

    // Release: READY → AVAILABLE (triggers CREATE saga)
    DataSet released = dataSetService.release(dataSetId);
    assertThat(released.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
    assertThat(released.getPendingSagaType()).isEqualTo(PendingSagaType.CREATE);

    DataSet completed = verifier.awaitSagaCompletion(dataSetId);

    assertThat(completed.getProjectId()).as("projectId from FROST step").isNotNull().isNotEmpty();
    assertThat(completed.getFrostBaseUrl())
        .isNotNull()
        .contains("Projects(" + completed.getProjectId() + ")");
    assertThat(completed.getRouteId()).isEqualTo(dataSetId.toString());
    assertThat(completed.getServiceId()).isEqualTo(dataSetId.toString());
    assertThat(completed.getPublicUrl()).isNotNull().contains("/datasets/" + dataSetId);
    assertThat(completed.getPipelineIds()).isNotNull().containsExactly(pipelineId.toString());
    assertThat(completed.getPendingSagaType()).isNull();

    DataSet withDistributions = dataSetRepository.findById(dataSetId).orElseThrow();
    for (Distribution dist : withDistributions.getDistributions()) {
      if (Boolean.TRUE.equals(dist.getAutoGenerated())) {
        assertThat(dist.getAccessUrl()).startsWith(completed.getPublicUrl());
      }
    }

    verifier.verifyFrostProjectExists(completed.getProjectId());
    verifier.verifyApisixReceivedRequests(sagaHelper, dataSetId, "PUT");
    verifier.verifyRedpandaPipelineExists(pipelineId);

    publishMqttMessage(
        "sensors/e2e", "{\"name\":\"MQTT Sensor\",\"description\":\"Created from MQTT\"}");
    verifier.verifyFrostHasThings("MQTT Sensor");

    log.info(
        "MQTT E2E saga completed: projectId={}, routeId={}, pipelineIds={}",
        completed.getProjectId(),
        completed.getRouteId(),
        completed.getPipelineIds());
  }

  private void publishMqttMessage(String topic, String payload) throws Exception {
    mosquitto.execInContainer("mosquitto_pub", "-h", "localhost", "-t", topic, "-r", "-m", payload);
    log.info("Published MQTT message to topic '{}': {}", topic, payload);
  }
}

package de.civitascore.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.RoleRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

/**
 * Test helper for creating and cleaning up saga integration test entities.
 *
 * <p>Registered as a Spring {@code @TestComponent} so it can be injected via {@code @Autowired} in
 * any integration test that extends {@link BaseKeycloakIntegrationTest}.
 */
@TestComponent
public class SagaTestDataFactory {

  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AssignmentRepository assignmentRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // ─── DataSet ─────────────────────────────────────────────────────────────────

  public DataSet createDataSet(String name) {
    DataSet ds = new DataSet();
    ds.setName(name + " " + System.nanoTime());
    ds.setDescription("Integration test dataset");
    ds.setOpenDataAccess(true);
    ds.setDataSetStatus(DataSetStatus.DRAFT);
    return dataSetRepository.save(ds);
  }

  // ─── DataSource ──────────────────────────────────────────────────────────────

  public DataSource createMqttDataSource() {
    DataSource ds = new DataSource();
    ds.setName("mqtt-datasource-" + System.nanoTime());
    ds.setDescription("MQTT datasource for saga test");
    ds.setConnectorType(ConnectorType.MQTT);
    ds.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    ds.setConfiguration(
        Map.of(
            "host",
            "mqtt-broker",
            "port",
            1883,
            "topics",
            List.of("sensors/e2e"),
            "client_id",
            "civitas-e2e-" + System.nanoTime()));
    return dataSourceRepository.save(ds);
  }

  public DataSource createSqlDataSource() {
    DataSource ds = new DataSource();
    ds.setName("sql-datasource-" + System.nanoTime());
    ds.setDescription("PostgreSQL datasource for saga test");
    ds.setConnectorType(ConnectorType.SQL);
    ds.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    ds.setConfiguration(
        Map.of(
            "host", "datasource-db",
            "port", 5432,
            "database", "testdb",
            "username", "testuser",
            "password", "testpass",
            "query", "SELECT sensor_name, sensor_description FROM sensors"));
    return dataSourceRepository.save(ds);
  }

  // ─── Pipeline ────────────────────────────────────────────────────────────────

  /**
   * Creates a pipeline with a synthetic {@code generate} input — no real datasource container
   * needed. The {@code dataSource} parameter is linked as an entity relation only; the pipeline
   * model contains no placeholder because {@code generate} produces its own data.
   */
  public Pipeline createGeneratePipeline(DataSet dataSet, DataSource dataSource) {
    return createGeneratePipeline(dataSet, dataSource, "/v1.1/Things", "default");
  }

  public Pipeline createGeneratePipeline(
      DataSet dataSet, DataSource dataSource, String apiPath, String nameSuffix) {
    Pipeline pipeline = new Pipeline();
    pipeline.setName("pipeline-" + nameSuffix + "-" + System.nanoTime());
    pipeline.setDescription("Generate → FROST pipeline");
    pipeline.setDataSet(dataSet);
    pipeline.setDataSources(Set.of(dataSource));
    pipeline.setApis(List.of(apiPath));
    pipeline.setModel(loadPipelineConfig("pipelines/generate-pipeline-config.json"));

    Pipeline saved = pipelineRepository.save(pipeline);
    dataSet.getPipelines().add(saved);
    dataSetRepository.save(dataSet);
    return saved;
  }

  public Pipeline createGeneratePipelineWithMultipleApis(DataSet dataSet, DataSource dataSource) {
    Pipeline pipeline = new Pipeline();
    pipeline.setName("pipeline-multi-api-" + System.nanoTime());
    pipeline.setDescription("Pipeline with multiple APIs");
    pipeline.setDataSet(dataSet);
    pipeline.setDataSources(Set.of(dataSource));
    pipeline.setApis(List.of("/v1.1/Things", "/v1.1/Datastreams"));
    pipeline.setModel(loadPipelineConfig("pipelines/generate-pipeline-config.json"));

    Pipeline saved = pipelineRepository.save(pipeline);
    dataSet.getPipelines().add(saved);
    dataSetRepository.save(dataSet);
    return saved;
  }

  /**
   * Creates a pipeline that reads from PostgreSQL using Redpanda Connect's sql_raw input. The
   * pipeline model uses a datasource placeholder that the config-adapter's DatasourceInjector
   * resolves at deploy time using the datasource's UUID.
   */
  public Pipeline createSqlPipeline(DataSet dataSet, DataSource dataSource) {
    Pipeline pipeline = new Pipeline();
    pipeline.setName("sql-pipeline-" + System.nanoTime());
    pipeline.setDescription("SQL → FROST pipeline");
    pipeline.setDataSet(dataSet);
    pipeline.setDataSources(Set.of(dataSource));
    pipeline.setApis(List.of("/v1.1/Things"));
    pipeline.setModel(
        injectDatasourceId(
            loadPipelineConfig("pipelines/sql-pipeline-config.json"),
            dataSource.getId().toString()));

    Pipeline saved = pipelineRepository.save(pipeline);
    dataSet.getPipelines().add(saved);
    dataSetRepository.save(dataSet);
    return saved;
  }

  /**
   * Creates a pipeline that subscribes to an MQTT topic and posts incoming messages to FROST. The
   * pipeline model uses a datasource placeholder that the config-adapter's DatasourceInjector
   * resolves at deploy time using the datasource's UUID.
   */
  public Pipeline createMqttPipeline(DataSet dataSet, DataSource dataSource) {
    Pipeline pipeline = new Pipeline();
    pipeline.setName("mqtt-pipeline-" + System.nanoTime());
    pipeline.setDescription("MQTT → FROST pipeline");
    pipeline.setDataSet(dataSet);
    pipeline.setDataSources(Set.of(dataSource));
    pipeline.setApis(List.of("/v1.1/Things"));
    pipeline.setModel(
        injectDatasourceId(
            loadPipelineConfig("pipelines/mqtt-pipeline-config.json"),
            dataSource.getId().toString()));

    Pipeline saved = pipelineRepository.save(pipeline);
    dataSet.getPipelines().add(saved);
    dataSetRepository.save(dataSet);
    return saved;
  }

  // ─── Group + Role + Assignment ───────────────────────────────────────────────

  /** Creates a Group, Role (DATA type), and Assignment scoped to the given DataSet. */
  public void seedGroupAndAssignment(DataSet dataSet) {
    Group group = new Group();
    group.setName("test-group-" + System.nanoTime());
    group.setDescription("Test group for saga");
    group = groupRepository.save(group);

    Role role = new Role();
    role.setName("test-role-" + System.nanoTime());
    role.setDescription("Test role for saga");
    role.setRoleType(RoleType.DATA);
    role = roleRepository.save(role);

    Assignment assignment = new Assignment();
    assignment.setGroup(group);
    assignment.setRole(role);
    assignment.setScopeType(ScopeType.DATASET);
    assignment.setDataset(dataSet);
    assignmentRepository.save(assignment);
  }

  // ─── Cleanup ─────────────────────────────────────────────────────────────────

  /** Deletes all test entities in the correct order (respecting FK constraints). */
  public void cleanAll() {
    assignmentRepository.deleteAll();
    pipelineRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataSourceRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
  }

  // ─── Private Helpers ─────────────────────────────────────────────────────────

  /**
   * Loads a pipeline configuration from a JSON file in the classpath.
   *
   * @param resourcePath the path to the JSON file relative to src/testIntegration/resources
   * @return the parsed pipeline model as a mutable Map
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> loadPipelineConfig(String resourcePath) {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IllegalArgumentException("Pipeline config not found: " + resourcePath);
      }
      return objectMapper.readValue(is, Map.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load pipeline config from " + resourcePath, e);
    }
  }

  /**
   * Replaces the {@code PLACEHOLDER} token inside a pipeline model's input label with the real
   * datasource UUID. This mirrors exactly what the frontend does when it embeds {@code label:
   * "${datasource_<uuid>}"} into the pipeline model.
   *
   * <p>The config-adapter's {@code DatasourceInjector} then resolves the label at deploy time by
   * matching the UUID against the {@code datasources[].id} in the Kafka trigger.
   *
   * @param model the pipeline model loaded from a JSON template
   * @param datasourceId the UUID of the datasource to inject
   * @return the same model map with the placeholder replaced
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> injectDatasourceId(
      Map<String, Object> model, String datasourceId) {
    Object inputObj = model.get("input");
    if (!(inputObj instanceof Map<?, ?> inputMap)) return model;

    Object label = inputMap.get("label");
    if (label instanceof String labelStr && labelStr.contains("PLACEHOLDER")) {
      ((Map<String, Object>) inputMap).put("label", labelStr.replace("PLACEHOLDER", datasourceId));
    }
    return model;
  }
}

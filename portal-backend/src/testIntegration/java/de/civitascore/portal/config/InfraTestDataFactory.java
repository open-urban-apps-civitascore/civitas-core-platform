package de.civitascore.portal.config;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test helper for creating and cleaning up saga / infrastructure integration test entities.
 *
 * <p>Delegates common entity creation (DataSet, DataSource, Group, Role, Assignment, Pipeline) to
 * {@link PortalTestDataFactory} and keeps saga-specific concerns: pipeline JSON loading, encrypted
 * credentials, and datasource-ID injection.
 *
 * <p>Registered as a Spring {@code @TestComponent} so it can be injected via {@code @Autowired} in
 * any integration test that extends {@link BaseKeycloakIntegrationTest}.
 */
@TestComponent
public class InfraTestDataFactory {

  /**
   * Must match application-test-integration.yml ({@code civitas.master-key}). Package-visible so
   * {@link SagaOrchestratorTestHelper} can pass it to the config-adapter via adapter config.
   */
  static final String TEST_MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private static final byte[] TEST_STRETCHED_KEY;

  static {
    try {
      TEST_STRETCHED_KEY =
          CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(TEST_MASTER_KEY_HEX));
    } catch (GeneralSecurityException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private PortalTestDataFactory portalData;

  private final ObjectMapper objectMapper = new JsonMapper();

  public DataSet createDataSet(String name) {
    return portalData.dataSet(
        b ->
            b.name(name + " " + System.nanoTime())
                .description("Integration test dataset")
                .openDataAccess(true));
  }

  public DataSource createMqttDataSource() {
    return portalData.dataSource(
        b ->
            b.name("mqtt-datasource-" + System.nanoTime())
                .description("MQTT datasource for saga test")
                .connectorType(ConnectorType.MQTT)
                .dataSourceStatus(DataSourceStatus.AVAILABLE)
                .configuration(
                    Map.of(
                        "host",
                        "mqtt-broker",
                        "port",
                        1883,
                        "topics",
                        List.of("sensors/e2e"),
                        "client_id",
                        "civitas-e2e-" + System.nanoTime())));
  }

  public DataSource createSqlDataSource() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("host", "datasource-db");
    config.put("port", 5432);
    config.put("database", "testdb");
    config.put("username", "testuser");
    config.put("password", encryptCredential("testpass"));
    config.put("query", "SELECT sensor_name, sensor_description FROM sensors");

    return portalData.dataSource(
        b ->
            b.name("sql-datasource-" + System.nanoTime())
                .description("PostgreSQL datasource for saga test")
                .connectorType(ConnectorType.SQL)
                .dataSourceStatus(DataSourceStatus.AVAILABLE)
                .configuration(config));
  }

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
    return portalData.pipeline(
        dataSet,
        b ->
            b.name("pipeline-" + nameSuffix + "-" + System.nanoTime())
                .description("Generate → FROST pipeline")
                .dataSources(Set.of(dataSource))
                .apis(List.of(apiPath))
                .model(loadPipelineConfig("pipelines/generate-pipeline-config.json")));
  }

  public Pipeline createGeneratePipelineWithMultipleApis(DataSet dataSet, DataSource dataSource) {
    return portalData.pipeline(
        dataSet,
        b ->
            b.name("pipeline-multi-api-" + System.nanoTime())
                .description("Pipeline with multiple APIs")
                .dataSources(Set.of(dataSource))
                .apis(List.of("/v1.1/Things", "/v1.1/Datastreams"))
                .model(loadPipelineConfig("pipelines/generate-pipeline-config.json")));
  }

  /**
   * Creates a pipeline that reads from PostgreSQL using Redpanda Connect's sql_raw input. The
   * pipeline model uses a datasource placeholder that the config-adapter's DatasourceInjector
   * resolves at deploy time using the datasource's UUID.
   */
  public Pipeline createSqlPipeline(DataSet dataSet, DataSource dataSource) {
    return portalData.pipeline(
        dataSet,
        b ->
            b.name("sql-pipeline-" + System.nanoTime())
                .description("SQL → FROST pipeline")
                .dataSources(Set.of(dataSource))
                .apis(List.of("/v1.1/Things"))
                .model(
                    injectDatasourceId(
                        loadPipelineConfig("pipelines/sql-pipeline-config.json"),
                        dataSource.getId().toString())));
  }

  /**
   * Creates a pipeline that subscribes to an MQTT topic and posts incoming messages to FROST. The
   * pipeline model uses a datasource placeholder that the config-adapter's DatasourceInjector
   * resolves at deploy time using the datasource's UUID.
   */
  public Pipeline createMqttPipeline(DataSet dataSet, DataSource dataSource) {
    return portalData.pipeline(
        dataSet,
        b ->
            b.name("mqtt-pipeline-" + System.nanoTime())
                .description("MQTT → FROST pipeline")
                .dataSources(Set.of(dataSource))
                .apis(List.of("/v1.1/Things"))
                .model(
                    injectDatasourceId(
                        loadPipelineConfig("pipelines/mqtt-pipeline-config.json"),
                        dataSource.getId().toString())));
  }

  /** Creates a Group, Role (DATA type), and Assignment scoped to the given DataSet. */
  public void seedGroupAndAssignment(DataSet dataSet) {
    var group = portalData.group(b -> b.description("Test group for saga"));
    var role = portalData.role(b -> b.description("Test role for saga"));
    portalData.assignment(group, role, dataSet);
  }

  /** Deletes all test entities in the correct order (respecting FK constraints). */
  public void cleanAll() {
    portalData.cleanAll();
  }

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
    } catch (JacksonException e) {
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

  /**
   * Encrypts a credential value using the test master key, matching what portal-backend's {@code
   * EncryptionConfig} does in production. This ensures saga tests exercise the real
   * encryption/decryption path through the config-adapter.
   */
  private static String encryptCredential(String plaintext) {
    try {
      return "ENC("
          + CredentialEncryptor.encrypt(
              plaintext, TEST_STRETCHED_KEY, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
          + ")";
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Test credential encryption failed", e);
    }
  }
}

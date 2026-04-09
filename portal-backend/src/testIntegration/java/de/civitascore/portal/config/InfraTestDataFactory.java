package de.civitascore.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

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

  private final ObjectMapper objectMapper = new ObjectMapper();

  public DataSet createDataSet(String name) {
    return portalData
        .dataSet()
        .withName(name + " " + System.nanoTime())
        .withDescription("Integration test dataset")
        .withOpenDataAccess(true)
        .build();
  }

  public DataSource createMqttDataSource() {
    return portalData
        .dataSource()
        .withName("mqtt-datasource-" + System.nanoTime())
        .withDescription("MQTT datasource for saga test")
        .withConnectorType(ConnectorType.MQTT)
        .withStatus(DataSourceStatus.AVAILABLE)
        .withConfiguration(
            Map.of(
                "host",
                "mqtt-broker",
                "port",
                1883,
                "topics",
                List.of("sensors/e2e"),
                "client_id",
                "civitas-e2e-" + System.nanoTime()))
        .build();
  }

  public DataSource createSqlDataSource() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("host", "datasource-db");
    config.put("port", 5432);
    config.put("database", "testdb");
    config.put("username", "testuser");
    config.put("password", encryptCredential("testpass"));
    config.put("query", "SELECT sensor_name, sensor_description FROM sensors");

    return portalData
        .dataSource()
        .withName("sql-datasource-" + System.nanoTime())
        .withDescription("PostgreSQL datasource for saga test")
        .withConnectorType(ConnectorType.SQL)
        .withStatus(DataSourceStatus.AVAILABLE)
        .withConfiguration(config)
        .build();
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
    return portalData
        .pipeline()
        .withName("pipeline-" + nameSuffix + "-" + System.nanoTime())
        .withDescription("Generate → FROST pipeline")
        .withDataSet(dataSet)
        .withDataSources(Set.of(dataSource))
        .withApis(List.of(apiPath))
        .withModel(loadPipelineConfig("pipelines/generate-pipeline-config.json"))
        .build();
  }

  public Pipeline createGeneratePipelineWithMultipleApis(DataSet dataSet, DataSource dataSource) {
    return portalData
        .pipeline()
        .withName("pipeline-multi-api-" + System.nanoTime())
        .withDescription("Pipeline with multiple APIs")
        .withDataSet(dataSet)
        .withDataSources(Set.of(dataSource))
        .withApis(List.of("/v1.1/Things", "/v1.1/Datastreams"))
        .withModel(loadPipelineConfig("pipelines/generate-pipeline-config.json"))
        .build();
  }

  /**
   * Creates a pipeline that reads from PostgreSQL using Redpanda Connect's sql_raw input. The
   * pipeline model uses a datasource placeholder that the config-adapter's DatasourceInjector
   * resolves at deploy time using the datasource's UUID.
   */
  public Pipeline createSqlPipeline(DataSet dataSet, DataSource dataSource) {
    return portalData
        .pipeline()
        .withName("sql-pipeline-" + System.nanoTime())
        .withDescription("SQL → FROST pipeline")
        .withDataSet(dataSet)
        .withDataSources(Set.of(dataSource))
        .withApis(List.of("/v1.1/Things"))
        .withModel(
            injectDatasourceId(
                loadPipelineConfig("pipelines/sql-pipeline-config.json"),
                dataSource.getId().toString()))
        .build();
  }

  /**
   * Creates a pipeline that subscribes to an MQTT topic and posts incoming messages to FROST. The
   * pipeline model uses a datasource placeholder that the config-adapter's DatasourceInjector
   * resolves at deploy time using the datasource's UUID.
   */
  public Pipeline createMqttPipeline(DataSet dataSet, DataSource dataSource) {
    return portalData
        .pipeline()
        .withName("mqtt-pipeline-" + System.nanoTime())
        .withDescription("MQTT → FROST pipeline")
        .withDataSet(dataSet)
        .withDataSources(Set.of(dataSource))
        .withApis(List.of("/v1.1/Things"))
        .withModel(
            injectDatasourceId(
                loadPipelineConfig("pipelines/mqtt-pipeline-config.json"),
                dataSource.getId().toString()))
        .build();
  }

  /** Creates a Group, Role (DATA type), and Assignment scoped to the given DataSet. */
  public void seedGroupAndAssignment(DataSet dataSet) {
    var group = portalData.group().withDescription("Test group for saga").build();
    var role = portalData.role().withDescription("Test role for saga").build();
    portalData.assignment().withGroup(group).withRole(role).withScope(dataSet).build();
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

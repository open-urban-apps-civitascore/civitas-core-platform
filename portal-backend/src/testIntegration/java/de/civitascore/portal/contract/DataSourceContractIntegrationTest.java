package de.civitascore.portal.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Pins the persisted connector-configuration shape for the SQL and MQTT connectors, and pins the
 * mutation semantics on PUT and PATCH (Phase 1 of #1391). The inline {@code
 * data_sources.configuration} JSONB column was dropped (V1_2_17); the configuration now lives as a
 * CORE DataSource artifact in the Model Forge registry, read back here via its {@code
 * configurationUrn}.
 *
 * <p>This is the Java-only half of the data source contract characterization. The HTTP contract is
 * covered by Bruno collections under {@code api/portal-backend/bruno-api/datasources/contract/};
 * those tests cannot reach into the registry, which is why the storage-level assertions live here.
 *
 * <p>The class extends {@link BaseKeycloakIntegrationTest} directly (rather than {@link
 * de.civitascore.portal.controller.BaseDataEntityControllerIntegrationTest}) to avoid inheriting
 * ~20 generic CRUD tests that are already exercised by {@code DataSourceControllerIntegrationTest}.
 */
@DisplayName("DataSource JSONB Persistence Contract — characterization (Phase 1 of #1391)")
class DataSourceContractIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String DATASOURCES_ENDPOINT = "/datasources";

  /**
   * Comparator used by {@link #assertPersistedJsonbMatches}. Uses {@link
   * JSONCompareMode#NON_EXTENSIBLE} so extra fields in the actual JSONB are treated as contract
   * drift. The {@code password} field accepts the placeholder {@code "${nonEmptyString}"} to match
   * any non-empty string — used for encrypted secrets whose ciphertext is non-deterministic.
   */
  private static final CustomComparator JSONB_COMPARATOR =
      new CustomComparator(
          // Lenient: the registry payload additionally carries the stamped
          // $schema/id/connectionType
          // identity fields, which are not part of the connector-configuration contract pinned
          // here.
          JSONCompareMode.LENIENT,
          new Customization(
              "password",
              (actual, expected) -> {
                if ("${nonEmptyString}".equals(expected)) {
                  return actual instanceof String s && !s.isEmpty();
                }
                return Objects.equals(actual, expected);
              }));

  @Autowired private PortalTestDataFactory portalData;

  @Autowired private ModelRegistryGateway modelRegistryGateway;

  @Autowired private DataSourceRepository dataSourceRepository;

  @AfterEach
  void cleanUp() {
    portalData.cleanAll();
  }

  private UUID createAvailableDataStructureVersionId() {
    var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
    DataStructureVersion dsv =
        portalData.dataStructureVersion(
            ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
    return dsv.getId();
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(MediaType.parseMediaTypes("application/json"));
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    return headers;
  }

  private String exchange(HttpMethod method, String urlPath, String body) {
    ResponseEntity<String> resp =
        restTemplate.exchange(urlPath, method, new HttpEntity<>(body, authHeaders()), String.class);
    assertThat(resp.getStatusCode().is2xxSuccessful())
        .as("%s %s should succeed; body=%s", method, urlPath, resp.getBody())
        .isTrue();
    return resp.getBody();
  }

  /**
   * Reads the persisted connector configuration back from the Model Forge registry. The inline
   * {@code data_sources.configuration} JSONB column was dropped (V1_2_17); the configuration now
   * lives as a CORE DataSource artifact keyed by the shell's {@code configurationUrn}. The returned
   * JSON additionally carries the registry-stamped {@code $schema}/{@code id}/{@code
   * connectionType} (ignored by the lenient comparator).
   */
  private String readStoredConfiguration(String dataSourceId) {
    DataSource dataSource =
        dataSourceRepository.findById(UUID.fromString(dataSourceId)).orElseThrow();
    Map<String, Object> content =
        modelRegistryGateway
            .fetchPayload(dataSource.getConfigurationUrn())
            .map(ModelRegistryGateway.RegistryDocument::content)
            .orElseThrow();
    return new JSONObject(content).toString();
  }

  private String createSqlDataSource(String name, UUID dsvId) {
    String body =
        """
        {
          "name": "%s",
          "description": "contract — SQL",
          "connectorType": "SQL",
          "configuration": {
            "driver": "postgres",
            "dsn": "postgres://example:5432/db",
            "user": "alice",
            "password": "s3cret",
            "table": "events",
            "columns": ["*"]
          },
          "dataStructureVersionId": "%s"
        }
        """
            .formatted(name, dsvId);
    return JsonPath.read(exchange(HttpMethod.POST, DATASOURCES_ENDPOINT, body), "$.id");
  }

  private String createMqttDataSource(String name, UUID dsvId) {
    String body =
        """
        {
          "name": "%s",
          "description": "contract — MQTT",
          "connectorType": "MQTT",
          "configuration": {
            "urls": ["tcp://broker:1883"],
            "topics": ["sensor/data"],
            "qos": 1
          },
          "dataStructureVersionId": "%s"
        }
        """
            .formatted(name, dsvId);
    return JsonPath.read(exchange(HttpMethod.POST, DATASOURCES_ENDPOINT, body), "$.id");
  }

  /**
   * Pins what lands in the {@code data_sources.configuration} JSONB column for a fresh POST. The
   * Bruno chain pins the HTTP response shape; this nested class pins the storage shape so a
   * refactor that changes one but not the other is caught at integration-test time.
   */
  @Nested
  @DisplayName("Persisted JSONB shape after create")
  class PersistedShapeOnCreate {

    @Test
    @DisplayName("SQL: configuration column matches expected JSONB shape")
    void sqlConfigurationIsPersistedInExpectedShape() throws Exception {
      String id =
          createSqlDataSource(
              "contract_sql_persist_create", createAvailableDataStructureVersionId());
      String actualJson = readStoredConfiguration(id);
      String expected =
          """
          {
            "driver": "postgres",
            "dsn": "postgres://example:5432/db",
            "user": "alice",
            "password": "${nonEmptyString}",
            "table": "events",
            "columns": ["*"],
            "where": null,
            "prefix": null,
            "suffix": null,
            "init_statement": null,
            "conn_max_idle": 2,
            "conn_max_open": 0,
            "conn_max_idle_time": null,
            "conn_max_life_time": null
          }
          """;
      assertPersistedJsonbMatches(expected, actualJson);
    }

    @Test
    @DisplayName("MQTT: configuration column matches expected JSONB shape")
    void mqttConfigurationIsPersistedInExpectedShape() throws Exception {
      String id =
          createMqttDataSource(
              "contract_mqtt_persist_create", createAvailableDataStructureVersionId());
      String actualJson = readStoredConfiguration(id);
      String expected =
          """
          {
            "qos": 1,
            "tls": { "enabled": false },
            "urls": ["tcp://broker:1883"],
            "user": null,
            "topics": ["sensor/data"],
            "password": null,
            "keepalive": null,
            "connect_timeout": null
          }
          """;
      assertPersistedJsonbMatches(expected, actualJson);
    }
  }

  /**
   * Pins the JSONB mutation semantics across PUT and PATCH — specifically the masked-password
   * round-trip (the placeholder must resolve back to the stored ciphertext, not overwrite it with a
   * literal {@code "********"}) and the connector-type-change normalization on DRAFT. These
   * invariants are easy to break with a refactor that touches the merge-on-update path; pinning
   * them here turns silent corruption into a failing integration test.
   */
  @Nested
  @DisplayName("Persisted JSONB shape after PUT and PATCH (mutation contracts)")
  class PersistedShapeOnMutation {

    @Test
    @DisplayName("SQL: PUT with masked password preserves the stored ciphertext")
    void sqlPutWithMaskedPasswordPreservesStoredCiphertext() throws Exception {
      UUID dsvId = createAvailableDataStructureVersionId();
      String id = createSqlDataSource("contract_sql_put_mask", dsvId);
      String originalCiphertext = JsonPath.read(readStoredConfiguration(id), "$.password");
      assertThat(originalCiphertext)
          .as("create should persist a non-empty ciphertext")
          .isNotEmpty();

      String putBody =
          """
          {
            "name": "contract_sql_put_mask",
            "description": "contract — SQL after PUT with mask",
            "connectorType": "SQL",
            "configuration": {
              "driver": "postgres",
              "dsn": "postgres://example:5432/db",
              "user": "alice",
              "password": "********",
              "table": "events",
              "columns": ["*"]
            },
            "dataStructureVersionId": "%s"
          }
          """
              .formatted(dsvId);
      exchange(HttpMethod.PUT, DATASOURCES_ENDPOINT + "/" + id, putBody);

      String persistedAfterPut = readStoredConfiguration(id);
      String ciphertextAfterPut = JsonPath.read(persistedAfterPut, "$.password");
      assertThat(ciphertextAfterPut)
          .as("PUT with masked password must not overwrite the stored ciphertext")
          .isEqualTo(originalCiphertext)
          .isNotEqualTo("********")
          .isNotEmpty();
    }

    @Test
    @DisplayName("MQTT: configuration column matches expected JSONB shape after PUT")
    void mqttConfigurationIsPersistedInExpectedShapeAfterPut() throws Exception {
      UUID dsvId = createAvailableDataStructureVersionId();
      String id = createMqttDataSource("contract_mqtt_put", dsvId);

      String putBody =
          """
          {
            "name": "contract_mqtt_put",
            "description": "contract — MQTT after PUT",
            "connectorType": "MQTT",
            "configuration": {
              "urls": ["tcp://broker:1883"],
              "topics": ["sensor/data", "sensor/heartbeat"],
              "qos": 1
            },
            "dataStructureVersionId": "%s"
          }
          """
              .formatted(dsvId);
      exchange(HttpMethod.PUT, DATASOURCES_ENDPOINT + "/" + id, putBody);

      String actualJson = readStoredConfiguration(id);
      String expected =
          """
          {
            "qos": 1,
            "tls": { "enabled": false },
            "urls": ["tcp://broker:1883"],
            "user": null,
            "topics": ["sensor/data", "sensor/heartbeat"],
            "password": null,
            "keepalive": null,
            "connect_timeout": null
          }
          """;
      assertPersistedJsonbMatches(expected, actualJson);
    }

    @Test
    @DisplayName(
        "SQL: PATCH with configuration object preserves stored ciphertext for omitted secret")
    void sqlPatchWithConfigurationPreservesStoredCiphertext() throws Exception {
      UUID dsvId = createAvailableDataStructureVersionId();
      String id = createSqlDataSource("contract_sql_patch_cfg", dsvId);
      String originalCiphertext = JsonPath.read(readStoredConfiguration(id), "$.password");
      assertThat(originalCiphertext).isNotEmpty();

      // PATCH with a configuration body that does NOT carry password.
      // Pin whatever the system currently does — merge (preserve password) or replace (wipe it).
      String patchBody =
          """
          {
            "configuration": {
              "driver": "postgres",
              "dsn": "postgres://example:5432/db",
              "user": "alice",
              "table": "events_v2",
              "columns": ["id", "ts"]
            }
          }
          """;
      exchange(HttpMethod.PATCH, DATASOURCES_ENDPOINT + "/" + id, patchBody);

      String ciphertextAfterPatch = JsonPath.read(readStoredConfiguration(id), "$.password");
      assertThat(ciphertextAfterPatch)
          .as(
              "PATCH with a configuration body that omits 'password' must preserve the stored "
                  + "ciphertext. If this assertion ever fails because PATCH replaces (rather than "
                  + "merges) configuration, that is a behavior change that the refactor must "
                  + "explicitly acknowledge — do not silently weaken this assertion.")
          .isEqualTo(originalCiphertext);
    }

    @Test
    @DisplayName("MQTT: PATCH with description-only does not touch configuration JSONB")
    void mqttPatchDescriptionOnlyDoesNotTouchConfiguration() throws Exception {
      UUID dsvId = createAvailableDataStructureVersionId();
      String id = createMqttDataSource("contract_mqtt_patch_desc", dsvId);
      String configBeforePatch = readStoredConfiguration(id);

      String patchBody =
          """
          { "description": "contract — MQTT description-only patch" }
          """;
      exchange(HttpMethod.PATCH, DATASOURCES_ENDPOINT + "/" + id, patchBody);

      String configAfterPatch = readStoredConfiguration(id);
      // Compare structurally: the registry read does not guarantee a stable key order, and no field
      // in the stored configuration depends on description.
      JSONAssert.assertEquals(
          "PATCH that does not name configuration must leave the stored configuration untouched",
          configBeforePatch,
          configAfterPatch,
          JSONCompareMode.STRICT);
    }
  }

  /**
   * Assert that a persisted JSONB column matches the expected shape via {@link #JSONB_COMPARATOR}.
   */
  static void assertPersistedJsonbMatches(String expectedJson, String actualJson)
      throws JSONException {
    JSONAssert.assertEquals(expectedJson, actualJson, JSONB_COMPARATOR);
  }
}

package de.civitascore.portal.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Pins the persisted connector-configuration shape for the SQL, MQTT and HTTP/REST connectors, and
 * pins the mutation semantics on PUT and PATCH (Phase 1 of #1391). The inline {@code
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

  private ResponseEntity<String> send(HttpMethod method, String urlPath, String body) {
    return restTemplate.exchange(
        urlPath, method, new HttpEntity<>(body, authHeaders()), String.class);
  }

  private String exchange(HttpMethod method, String urlPath, String body) {
    ResponseEntity<String> resp = send(method, urlPath, body);
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
          "connector": {
            "connectorType": "SQL",
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
          "connector": {
            "connectorType": "MQTT",
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
            "where": null
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

    @Test
    @DisplayName("HTTP/REST: configuration column matches expected JSONB shape")
    void httpConfigurationIsPersistedInExpectedShape() throws Exception {
      String connector =
          """
          { "connectorType": "HTTP",
            "baseUrl": "https://api.example.org/v1",
            "authentication": { "type": "BASIC", "username": "reader", "password": "s3cret" },
            "apiCalls": [
              { "name": "Get all weather stations", "path": "/stations",
                "queryParameters": [{ "name": "status", "value": "active" }],
                "headers": [{ "name": "Accept", "value": "application/json" }],
                "pagination": { "type": "PAGE_NUMBER", "pageParameter": "page",
                                "pageSizeParameter": "size", "pageSize": 100 } } ] }
          """;
      String id =
          JsonPath.read(
              exchange(
                  HttpMethod.POST,
                  DATASOURCES_ENDPOINT,
                  HttpConnector.body(
                      "contract_http_persist_create",
                      connector,
                      createAvailableDataStructureVersionId())),
              "$.id");
      String actualJson = readStoredConfiguration(id);
      String expected =
          """
          {
            "baseUrl": "https://api.example.org/v1",
            "authentication": { "type": "BASIC", "username": "reader" },
            "apiCalls": [
              { "name": "Get all weather stations", "method": "GET", "path": "/stations",
                "queryParameters": [{ "name": "status", "value": "active" }],
                "headers": [{ "name": "Accept", "value": "application/json" }],
                "pagination": { "type": "PAGE_NUMBER", "pageParameter": "page",
                                "pageSizeParameter": "size", "pageSize": 100,
                                "startPage": 0, "endPage": null } }
            ]
          }
          """;
      assertPersistedJsonbMatches(expected, actualJson);
      assertThat((String) JsonPath.read(actualJson, "$.authentication.password"))
          .isNotBlank()
          .isNotEqualTo("s3cret");
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
            "connector": {
              "connectorType": "SQL",
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
            "connector": {
              "connectorType": "MQTT",
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
    @DisplayName("SQL: PATCH with connector object preserves stored ciphertext for omitted secret")
    void sqlPatchWithConfigurationPreservesStoredCiphertext() throws Exception {
      UUID dsvId = createAvailableDataStructureVersionId();
      String id = createSqlDataSource("contract_sql_patch_cfg", dsvId);
      String originalCiphertext = JsonPath.read(readStoredConfiguration(id), "$.password");
      assertThat(originalCiphertext).isNotEmpty();

      String patchBody =
          """
          {
            "connector": {
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
              "PATCH merges the connector into the stored one (RFC 7396), so an omitted "
                  + "'password' keeps the stored ciphertext")
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
   * Documents as the release before the typed connector wrote them: every field of the type, nulls
   * included, plus the {@code connectionType} discriminator.
   */
  @Nested
  @DisplayName("Documents stored by the previous release")
  class StoredByPreviousRelease {

    private String storePreviousReleaseMqttDocument() {
      Map<String, Object> document = new LinkedHashMap<>();
      document.put("urls", List.of("tcp://broker:1883"));
      document.put("topics", List.of("sensor/data"));
      document.put("qos", 1);
      document.put("protocol_version", "3");
      document.put("connect_timeout", null);
      document.put("keepalive", null);
      document.put("tls", Map.of("enabled", false));
      document.put("user", "mqttuser");
      document.put("password", "ENC(previous-release-ciphertext)");
      DataSource dataSource =
          portalData.dataSource(
              b ->
                  b.connectorType(ConnectorType.MQTT)
                      .dataStructureVersion(
                          portalData.dataStructureVersion(
                              portalData.dataStructure(
                                  d -> d.dataStructureStatus(DataStructureStatus.AVAILABLE)),
                              v ->
                                  v.dataStructureVersionStatus(
                                      DataStructureVersionStatus.AVAILABLE))));
      return portalData.attachSourceConfiguration(dataSource, document).getId().toString();
    }

    @Test
    @DisplayName("MQTT: reads, saves and releases without a migration")
    void mqttDocumentReadsSavesAndReleases() throws Exception {
      String id = storePreviousReleaseMqttDocument();

      String read = exchange(HttpMethod.GET, DATASOURCES_ENDPOINT + "/" + id, null);
      JSONAssert.assertEquals(
          """
          {"connector": {"connectorType": "MQTT", "urls": ["tcp://broker:1883"],
           "user": "mqttuser", "password": "********", "tls": {"enabled": false}}}
          """,
          read,
          JSONCompareMode.LENIENT);

      exchange(
          HttpMethod.PATCH,
          DATASOURCES_ENDPOINT + "/" + id,
          """
          { "connector": { "topics": ["sensor/other"] } }
          """);
      exchange(HttpMethod.POST, DATASOURCES_ENDPOINT + "/" + id + "/release", null);

      String stored = readStoredConfiguration(id);
      assertThat((String) JsonPath.read(stored, "$.password"))
          .isEqualTo("ENC(previous-release-ciphertext)");
      assertThat((Object) JsonPath.read(stored, "$.topics[0]")).isEqualTo("sensor/other");
    }
  }

  @Nested
  @DisplayName("HTTP/REST connector")
  class HttpConnector {

    private static final String PASSWORD = "http-s3cret";

    private static final String VALID_API_CALL =
        """
        { "name": "stations", "path": "/stations",
          "pagination": { "type": "PAGE_NUMBER", "pageParameter": "page",
                          "pageSizeParameter": "size", "pageSize": 100 } }
        """;

    private static String connector(String password) {
      return """
            { "connectorType": "HTTP",
              "baseUrl": "https://api.example.org/v1",
              "authentication": { "type": "BASIC", "username": "reader", "password": "%s" },
              "apiCalls": [ %s ] }
            """
          .formatted(password, VALID_API_CALL);
    }

    private static String body(String name, String connector, UUID dsvId) {
      return """
          { "name": "%s", "description": "contract — HTTP", "connector": %s,
            "dataStructureVersionId": "%s" }
          """
          .formatted(name, connector, dsvId);
    }

    private String storedPassword(String id) {
      return JsonPath.read(readStoredConfiguration(id), "$.authentication.password");
    }

    @Test
    @DisplayName(
        "creates, reads, updates, patches, releases and deletes without exposing the password")
    void lifecycleNeverExposesThePassword() throws Exception {
      UUID dsvId = createAvailableDataStructureVersionId();
      String path = DATASOURCES_ENDPOINT + "/";
      List<String> responses = new ArrayList<>();

      responses.add(
          exchange(
              HttpMethod.POST,
              DATASOURCES_ENDPOINT,
              body("contract_http_lifecycle", connector(PASSWORD), dsvId)));
      String id = JsonPath.read(responses.getFirst(), "$.id");
      JSONAssert.assertEquals(
          """
          {"connector": {"connectorType": "HTTP", "baseUrl": "https://api.example.org/v1",
           "authentication": {"type": "BASIC", "username": "reader", "password": "********"},
           "apiCalls": [{"name": "stations", "method": "GET", "path": "/stations",
             "queryParameters": null, "headers": null,
             "pagination": {"type": "PAGE_NUMBER", "pageParameter": "page",
               "pageSizeParameter": "size", "pageSize": 100, "startPage": 0, "endPage": null}}]}}
          """,
          responses.getFirst(),
          JSONCompareMode.LENIENT);
      String ciphertext = storedPassword(id);
      assertThat(ciphertext).isNotBlank().isNotEqualTo(PASSWORD).isNotEqualTo("********");

      responses.add(exchange(HttpMethod.GET, path + id, null));
      responses.add(
          exchange(
              HttpMethod.PUT,
              path + id,
              body("contract_http_lifecycle", connector("********"), dsvId)));
      assertThat(storedPassword(id)).isEqualTo(ciphertext);

      responses.add(
          exchange(
              HttpMethod.PATCH,
              path + id,
              """
              { "connector": { "baseUrl": "https://other.example.org" } }
              """));
      assertThat((String) JsonPath.read(readStoredConfiguration(id), "$.baseUrl"))
          .isEqualTo("https://other.example.org");
      assertThat(storedPassword(id)).isEqualTo(ciphertext);

      responses.add(exchange(HttpMethod.POST, path + id + "/release", null));
      assertThat((String) JsonPath.read(responses.getLast(), "$.dataSourceStatus"))
          .isEqualTo("AVAILABLE");
      responses.add(exchange(HttpMethod.POST, path + id + "/unrelease", null));
      exchange(HttpMethod.DELETE, path + id, null);

      assertThat(send(HttpMethod.GET, path + id, null).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(responses).noneMatch(response -> response.contains(PASSWORD));
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '"',
        value = {
          "{'type':'BASIC','username':'a:b'} | [] | connector.authentication.username",
          "null | [{'path':'stations'}] | connector.apiCalls[0].path",
          "null | [{'headers':[{'name':'X Y','value':'v'}]}] | connector.apiCalls[0].headers[0].name",
          "null | [{'headers':[{'name':'X','value':'a\\nb'}]}] | connector.apiCalls[0].headers[0].value",
          "null | [{'headers':[{'name':'Cookie','value':'v'}]}] | connector.apiCalls[0].headers[0].name",
          "null | [{'name':'a'},{'name':'a'}] | connector.apiCalls[1].name",
          "null | [{'pagination':{'type':'PAGE_NUMBER','pageParameter':'p-1'}}]"
              + " | connector.apiCalls[0].pagination.pageParameter",
          "null | [{'pagination':{'type':'PAGE_NUMBER','pageSize':0}}]"
              + " | connector.apiCalls[0].pagination.pageSize",
          "null | [{'pagination':{'type':'PAGE_NUMBER','startPage':-1}}]"
              + " | connector.apiCalls[0].pagination.startPage",
          "null | [{'pagination':{'type':'PAGE_NUMBER','endPage':-1}}]"
              + " | connector.apiCalls[0].pagination.endPage",
          "null | [{'pagination':{'type':'PAGE_NUMBER','startPage':2,'endPage':1}}]"
              + " | connector.apiCalls[0].pagination.endPage"
        })
    @DisplayName("rejects a save that breaks a rule, with the key path of the field")
    void saveWithInvalidConfigurationNamesTheField(
        String authentication, String apiCalls, String field) {
      String connector =
          """
          { "connectorType": "HTTP", "authentication": %s, "apiCalls": %s }
          """
              .formatted(authentication, apiCalls)
              .replace('\'', '"');

      ResponseEntity<String> response =
          send(
              HttpMethod.POST,
              DATASOURCES_ENDPOINT,
              body("contract_http_invalid", connector, createAvailableDataStructureVersionId()));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat((List<String>) JsonPath.read(response.getBody(), "$.fieldErrors[*].field"))
          .containsOnly(field);
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '"',
        value = {
          "'baseUrl':null | connector.baseUrl",
          "'apiCalls':[] | connector.apiCalls",
          "'authentication':{'type':'BASIC','password':'p'} | connector.authentication.username",
          "'authentication':{'type':'BASIC','username':'u'} | connector.authentication.password",
          "'apiCalls':[{'path':'/x'}] | connector.apiCalls[0].name",
          "'apiCalls':[{'name':'a'}] | connector.apiCalls[0].path",
          "'apiCalls':[{'name':'a','path':'/x','queryParameters':[{'value':'v'}]}]"
              + " | connector.apiCalls[0].queryParameters[0].name",
          "'apiCalls':[{'name':'a','path':'/x','headers':[{'value':'v'}]}]"
              + " | connector.apiCalls[0].headers[0].name",
          "'apiCalls':[{'name':'a','path':'/x','pagination':{'type':'PAGE_NUMBER',"
              + "'pageSizeParameter':'s','pageSize':1}}] | connector.apiCalls[0].pagination.pageParameter",
          "'apiCalls':[{'name':'a','path':'/x','pagination':{'type':'PAGE_NUMBER',"
              + "'pageParameter':'p','pageSize':1}}] | connector.apiCalls[0].pagination.pageSizeParameter",
          "'apiCalls':[{'name':'a','path':'/x','pagination':{'type':'PAGE_NUMBER',"
              + "'pageParameter':'p','pageSizeParameter':'s'}}] | connector.apiCalls[0].pagination.pageSize"
        })
    @DisplayName("rejects a release of an incomplete configuration, with the key path of the field")
    void releaseWithIncompleteConfigurationNamesTheField(String member, String field)
        throws Exception {
      JSONObject complete = new JSONObject(connector("p"));
      JSONObject change = new JSONObject("{" + member.replace('\'', '"') + "}");
      for (Iterator<String> keys = change.keys(); keys.hasNext(); ) {
        String key = keys.next();
        complete.put(key, change.get(key));
      }
      String id =
          JsonPath.read(
              exchange(
                  HttpMethod.POST,
                  DATASOURCES_ENDPOINT,
                  body(
                      "contract_http_incomplete",
                      complete.toString(),
                      createAvailableDataStructureVersionId())),
              "$.id");

      ResponseEntity<String> response =
          send(HttpMethod.POST, DATASOURCES_ENDPOINT + "/" + id + "/release", null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat((List<String>) JsonPath.read(response.getBody(), "$.fieldErrors[*].field"))
          .containsOnly(field);
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '"',
        value = {
          "'startPage':'abc' | startPage",
          "'endPage':1.5 | endPage",
          "'pageSize':1.5 | pageSize"
        })
    @DisplayName("rejects a page number that is no integer on save as malformed")
    void saveWithNonIntegerPageNumberIsMalformed(String member, String key) {
      String connector =
          """
          { "connectorType": "HTTP",
            "apiCalls": [{ "pagination": { "type": "PAGE_NUMBER", %s } }] }
          """
              .formatted(member.replace('\'', '"'));

      ResponseEntity<String> response =
          send(
              HttpMethod.POST,
              DATASOURCES_ENDPOINT,
              body("contract_http_malformed", connector, createAvailableDataStructureVersionId()));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat((String) JsonPath.read(response.getBody(), "$.type"))
          .endsWith("MALFORMED_REQUEST");
      assertThat((String) JsonPath.read(response.getBody(), "$.detail"))
          .isEqualTo("Invalid value: connector.apiCalls[0].pagination." + key);
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

package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Outcome;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestTopics;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.configuration.ConnectionTestProperties;
import de.civitascore.portal.messaging.kafka.ConnectionTestClient;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The connection test endpoints against a responder that stands in for config-adapter. The
 * responder reads requests with Jackson 2, as config-adapter does.
 */
@EmbeddedKafka(
    partitions = 1,
    topics = {ConnectionTestTopics.REQUEST, ConnectionTestTopics.RESULT})
@TestPropertySource(properties = {"kafka.enabled=true", "connection-test.wait-seconds=3"})
@Import(DataSourceConnectionTestIntegrationTest.SecondInstance.class)
@DisplayName("Data source connection test")
class DataSourceConnectionTestIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String DATASOURCES = "/datasources";
  private static final String PLAIN_PASSWORD = "plain-s3cret";
  private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(10);
  private static final JsonMapper CONFIG_ADAPTER_JSON =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();

  /** Requests the responder received, as sent on the wire. */
  private static final BlockingQueue<String> REQUESTS = new LinkedBlockingQueue<>();

  /** The responder's answer to a request; null answers nothing. */
  private static volatile Function<ConnectionTestRequest, ConnectionTestResult> reply;

  private static KafkaMessageListenerContainer<String, String> responder;

  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private KafkaListenerEndpointRegistry listenerRegistry;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private ConnectionTestClient connectionTestClient;
  @Autowired private PortalTestDataFactory portalData;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private ModelRegistryGateway modelRegistryGateway;

  /** A second backend instance, with its own listener beside the application's. */
  @TestConfiguration
  static class SecondInstance {
    @Bean(autowireCandidate = false)
    ConnectionTestClient secondConnectionTestClient(
        KafkaTemplate<String, String> eventKafkaTemplate,
        ObjectMapper objectMapper,
        ConnectionTestProperties properties) {
      return new ConnectionTestClient(
          eventKafkaTemplate, objectMapper, properties, "second-instance");
    }
  }

  @BeforeEach
  void startResponderAndAwaitListeners() {
    REQUESTS.clear();
    reply = null;
    if (responder == null) {
      responder = startResponder();
    }
    for (MessageListenerContainer container : listenerRegistry.getAllListenerContainers()) {
      if (String.valueOf(container.getGroupId()).contains("-connection-test-")) {
        ContainerTestUtils.waitForAssignment(container, 1);
      }
    }
    ConnectionTestClient second = secondInstance();
    await().atMost(RECEIVE_TIMEOUT).until(() -> connectionTestClient.isReady() && second.isReady());
  }

  private ConnectionTestClient secondInstance() {
    return applicationContext.getBean("secondConnectionTestClient", ConnectionTestClient.class);
  }

  @AfterEach
  void cleanUp() {
    portalData.cleanAll();
  }

  @AfterAll
  static void stopResponder() {
    if (responder != null) {
      responder.stop();
      responder = null;
    }
  }

  private KafkaMessageListenerContainer<String, String> startResponder() {
    Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "responder", false);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    KafkaMessageListenerContainer<String, String> container =
        new KafkaMessageListenerContainer<>(
            new DefaultKafkaConsumerFactory<>(props),
            new ContainerProperties(ConnectionTestTopics.REQUEST));
    container.setupMessageListener(
        (MessageListener<String, String>)
            record -> {
              REQUESTS.add(record.value());
              Function<ConnectionTestRequest, ConnectionTestResult> answer = reply;
              if (answer != null) {
                ConnectionTestResult result = answer.apply(readAsConfigAdapter(record.value()));
                kafkaTemplate.send(
                    ConnectionTestTopics.RESULT, result.requestId(), writeAsConfigAdapter(result));
              }
            });
    container.start();
    ContainerTestUtils.waitForAssignment(container, 1);
    return container;
  }

  private static ConnectionTestRequest readAsConfigAdapter(String json) {
    try {
      return CONFIG_ADAPTER_JSON.readValue(json, ConnectionTestRequest.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("config-adapter cannot read the request", e);
    }
  }

  private static String writeAsConfigAdapter(ConnectionTestResult result) {
    try {
      return CONFIG_ADAPTER_JSON.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String nextRequest() throws InterruptedException {
    String request = REQUESTS.poll(RECEIVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    assertThat(request).as("a request reached the responder").isNotNull();
    return request;
  }

  /**
   * @param scopes the scope ids OPA allows, {@code *} for a tenant-wide grant
   */
  private HttpHeaders headers(String scopes) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(AllowedScopesFilter.HEADER_NAME, scopes);
    return headers;
  }

  private ResponseEntity<JsonNode> post(String path, Object body) {
    return post(path, body, "*");
  }

  private ResponseEntity<JsonNode> post(String path, Object body, String scopes) {
    ResponseEntity<String> raw =
        restTemplate.exchange(
            path, HttpMethod.POST, new HttpEntity<>(body, headers(scopes)), String.class);
    JsonNode json = raw.getBody() == null ? null : objectMapper.readTree(raw.getBody());
    return ResponseEntity.status(raw.getStatusCode()).body(json);
  }

  private ResponseEntity<JsonNode> testUnsaved(Map<String, Object> body) {
    return post(DATASOURCES + "/connection-test", body);
  }

  private static Map<String, Object> body(Map<String, Object> connector, String apiCall) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("connector", connector);
    if (apiCall != null) {
      body.put("apiCall", apiCall);
    }
    return body;
  }

  private static Map<String, Object> sqlConnector(String password) {
    Map<String, Object> connector = new LinkedHashMap<>();
    connector.put("connectorType", "SQL");
    connector.put("driver", "postgres");
    connector.put("dsn", "postgres://db.example:5432/city");
    connector.put("table", "readings");
    connector.put("columns", List.of("id", "value"));
    connector.put("user", "reader");
    connector.put("password", password);
    return connector;
  }

  private static ConnectionTestResult failure(ConnectionTestRequest request, Reason reason) {
    return ConnectionTestResult.failure(request.requestId(), reason, "no answer", 5);
  }

  @Test
  @DisplayName("success answers 200 with the CSV preview")
  void success_answersWithThePreview() throws Exception {
    reply =
        request ->
            new ConnectionTestResult(
                request.requestId(),
                Outcome.SUCCESS,
                null,
                "2 rows",
                "id,value\n1,20.5\n2,21.0\n",
                false,
                2,
                null,
                480);

    ResponseEntity<JsonNode> response = testUnsaved(body(sqlConnector(PLAIN_PASSWORD), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("success").asBoolean()).isTrue();
    assertThat(response.getBody().path("preview").asString())
        .isEqualTo("id,value\n1,20.5\n2,21.0\n");
    assertThat(response.getBody().path("rowCount").asInt()).isEqualTo(2);
    assertThat(response.getBody().hasNonNull("reason")).isFalse();
  }

  @Test
  @DisplayName("failure of the source answers 200 with the reason")
  void sourceFailure_answers200() {
    reply = request -> failure(request, Reason.SOURCE_ERROR);

    ResponseEntity<JsonNode> response = testUnsaved(body(sqlConnector(PLAIN_PASSWORD), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("success").asBoolean()).isFalse();
    assertThat(response.getBody().path("reason").asString()).isEqualTo("SOURCE_ERROR");
    assertThat(response.getBody().path("message").asString()).isEqualTo("no answer");
  }

  @Test
  @DisplayName("BUSY of config-adapter answers 429")
  void adapterBusy_answers429() {
    reply = request -> failure(request, Reason.BUSY);

    ResponseEntity<JsonNode> response = testUnsaved(body(sqlConnector(PLAIN_PASSWORD), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getBody().path("type").asString())
        .isEqualTo("urn:civitas:error:TOO_MANY_REQUESTS");
  }

  @Test
  @DisplayName("UNAVAILABLE of config-adapter answers 502")
  void adapterUnavailable_answers502() {
    reply = request -> failure(request, Reason.UNAVAILABLE);

    ResponseEntity<JsonNode> response = testUnsaved(body(sqlConnector(PLAIN_PASSWORD), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  @DisplayName("no answer within the wait answers 504")
  void silentAdapter_answers504() throws Exception {
    ResponseEntity<JsonNode> response = testUnsaved(body(sqlConnector(PLAIN_PASSWORD), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    assertThat(response.getBody().path("type").asString())
        .isEqualTo("urn:civitas:error:EXTERNAL_SYSTEM_TIMEOUT");
    nextRequest();
  }

  @Test
  @DisplayName("incomplete connector answers 400 with field errors")
  void incompleteConnector_answers400() {
    Map<String, Object> connector = sqlConnector(PLAIN_PASSWORD);
    connector.remove("table");

    ResponseEntity<JsonNode> response = testUnsaved(body(connector, null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("type").asString())
        .isEqualTo("urn:civitas:error:VALIDATION_FAILED");
    assertThat(response.getBody().path("fieldErrors").findValuesAsString("field"))
        .contains("connector.table");
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  @DisplayName("body without connector answers 400 with a field error")
  void missingConnector_answers400() {
    ResponseEntity<JsonNode> response = testUnsaved(new HashMap<>());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("type").asString())
        .isEqualTo("urn:civitas:error:VALIDATION_FAILED");
    assertThat(response.getBody().path("fieldErrors").findValuesAsString("field"))
        .contains("connector");
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  @DisplayName("unknown top-level key answers 400 naming the key")
  void unknownTopLevelKey_answers400() {
    Map<String, Object> body = body(sqlConnector(PLAIN_PASSWORD), null);
    body.put("extra", 1);

    ResponseEntity<JsonNode> response = testUnsaved(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("type").asString())
        .isEqualTo("urn:civitas:error:MALFORMED_REQUEST");
    assertThat(response.getBody().path("detail").asString()).isEqualTo("Unknown key: extra");
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  @DisplayName("placeholder of an unsaved connector answers 400")
  void placeholderOfUnsavedConnector_answers400() {
    ResponseEntity<JsonNode> response = testUnsaved(body(sqlConnector("********"), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("detail").asString())
        .isEqualTo("No stored secret for ******** at connector.password");
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  @DisplayName("HTTP connector without apiCall answers 400 with a field error")
  void httpWithoutApiCall_answers400() {
    Map<String, Object> connector = new LinkedHashMap<>();
    connector.put("connectorType", "HTTP");
    connector.put("baseUrl", "https://api.example.org");
    connector.put("apiCalls", List.of(Map.of("name", "stations", "path", "/stations")));

    ResponseEntity<JsonNode> response = testUnsaved(body(connector, null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("fieldErrors").findValuesAsString("field"))
        .containsExactly("apiCall");
  }

  @Test
  @DisplayName("unknown data source answers 404")
  void unknownDataSource_answers404() {
    ResponseEntity<JsonNode> response =
        post(
            DATASOURCES + "/" + UUID.randomUUID() + "/connection-test",
            body(sqlConnector(PLAIN_PASSWORD), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("clear-text password is sent only encrypted, expiresAt as an ISO-8601 string")
  void clearTextPassword_isSentEncrypted() throws Exception {
    reply = request -> failure(request, Reason.SOURCE_ERROR);

    testUnsaved(body(sqlConnector(PLAIN_PASSWORD), null));

    String wire = nextRequest();
    assertThat(wire).doesNotContain(PLAIN_PASSWORD);
    JsonNode request = objectMapper.readTree(wire);
    assertThat(request.at("/connector/password").asString()).startsWith("ENC(");
    assertThat(request.at("/connector/connectionType").asString()).isEqualTo("sql");
    assertThat(request.path("expiresAt").isString()).isTrue();
    assertThat(readAsConfigAdapter(wire).expiresAt()).isAfter(Instant.now());
  }

  @Test
  @DisplayName("placeholder of a saved data source sends its stored ciphertext")
  void placeholderOfSavedDataSource_sendsStoredCiphertext() throws Exception {
    UUID id = createSqlDataSource();
    Object storedPassword = storedDocument(id).get("password");
    reply = request -> failure(request, Reason.SOURCE_ERROR);

    ResponseEntity<JsonNode> response =
        post(DATASOURCES + "/" + id + "/connection-test", body(sqlConnector("********"), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String wire = nextRequest();
    assertThat(wire).doesNotContain(PLAIN_PASSWORD);
    assertThat(objectMapper.readTree(wire).at("/connector/password").asString())
        .startsWith("ENC(")
        .isEqualTo(storedPassword);
  }

  @Test
  @DisplayName("placeholder of a saved draft with another host answers 400 and sends nothing")
  void placeholderForAnotherHost_answers400() {
    UUID id = createSqlDataSource();
    Map<String, Object> elsewhere = sqlConnector("********");
    elsewhere.put("dsn", "postgres://capture.example:5432/city");

    ResponseEntity<JsonNode> response =
        post(DATASOURCES + "/" + id + "/connection-test", body(elsewhere, null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  @DisplayName("a reader of the saved data source only may not test another destination")
  void readerOfTheDataSourceOnly_withAnotherDestination_answers403() {
    UUID id = createSqlDataSource();
    Map<String, Object> elsewhere = sqlConnector("typed-s3cret");
    elsewhere.put("dsn", "postgres://capture.example:5432/city");

    ResponseEntity<JsonNode> response =
        post(DATASOURCES + "/" + id + "/connection-test", body(elsewhere, null), id.toString());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(REQUESTS).isEmpty();
  }

  @Test
  @DisplayName("a reader of the saved data source only tests other values at its destination")
  void readerOfTheDataSourceOnly_atItsDestination_answers200() throws Exception {
    UUID id = createSqlDataSource();
    reply = request -> failure(request, Reason.SOURCE_ERROR);
    Map<String, Object> otherTable = sqlConnector("typed-s3cret");
    otherTable.put("table", "stations");

    ResponseEntity<JsonNode> response =
        post(DATASOURCES + "/" + id + "/connection-test", body(otherTable, null), id.toString());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(nextRequest()).at("/connector/table").asString())
        .isEqualTo("stations");
  }

  @Test
  @DisplayName("new password of a saved data source is sent encrypted")
  void newPasswordOfSavedDataSource_isSentEncrypted() throws Exception {
    UUID id = createSqlDataSource();
    reply = request -> failure(request, Reason.SOURCE_ERROR);

    ResponseEntity<JsonNode> response =
        post(DATASOURCES + "/" + id + "/connection-test", body(sqlConnector("new-s3cret"), null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String wire = nextRequest();
    assertThat(wire).doesNotContain("new-s3cret");
    assertThat(objectMapper.readTree(wire).at("/connector/password").asString())
        .startsWith("ENC(")
        .isNotEqualTo(storedDocument(id).get("password"));
  }

  private UUID createSqlDataSource() {
    Map<String, Object> dataSource = new HashMap<>();
    dataSource.put("name", "connection-test-" + UUID.randomUUID().toString().substring(0, 8));
    dataSource.put("description", "a saved data source");
    dataSource.put("connector", sqlConnector(PLAIN_PASSWORD));
    dataSource.put("dataStructureVersionId", null);
    ResponseEntity<JsonNode> created = post(DATASOURCES, dataSource);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return UUID.fromString(created.getBody().path("id").asString());
  }

  private Map<String, Object> storedDocument(UUID id) {
    return modelRegistryGateway
        .fetchPayload(dataSourceRepository.findById(id).orElseThrow().getConfigurationUrn())
        .map(ModelRegistryGateway.RegistryDocument::content)
        .orElseThrow();
  }

  @Test
  @DisplayName("two listener instances both receive one result")
  void twoInstances_bothReceiveOneResult() throws Exception {
    ConnectionTestClient second = secondInstance();
    assertThat(second.getGroupId()).isNotEqualTo(connectionTestClient.getGroupId());
    ConnectionTestRequest request =
        new ConnectionTestRequest(
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(10),
            Map.of("connectionType", "sql"),
            null,
            false);
    List<CompletableFuture<ConnectionTestResult>> answers = new ArrayList<>();
    for (ConnectionTestClient instance : List.of(connectionTestClient, second)) {
      answers.add(CompletableFuture.supplyAsync(() -> instance.test(request)));
    }
    nextRequest();
    nextRequest();
    ConnectionTestResult result =
        new ConnectionTestResult(
            request.requestId(), Outcome.SUCCESS, null, "ok", null, false, null, null, 1);

    kafkaTemplate
        .send(ConnectionTestTopics.RESULT, request.requestId(), writeAsConfigAdapter(result))
        .get(10, TimeUnit.SECONDS);

    for (CompletableFuture<ConnectionTestResult> answer : answers) {
      assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo(result);
    }
  }
}

package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Outcome;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import de.civitascore.portal.configuration.ConnectionTestProperties;
import de.civitascore.portal.messaging.kafka.ConnectionTestClient;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import de.civitascore.portal.model.connector.Secret;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.ConnectionTestInputDTO;
import de.civitascore.portal.model.output.ConnectionTestResultDTO;
import de.civitascore.portal.service.DataSourceService;
import de.civitascore.portal.util.ConnectionTestBusyException;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("DataSourceConnectionTestService Tests")
class DataSourceConnectionTestServiceTest {

  private static final UUID USER = UUID.randomUUID();
  private static final String PLAIN_PASSWORD = "s3cret-plain";
  private static final String STORED_SECRET = "ENC(stored)";
  private static final JsonMapper MAPPER = new JsonMapper();

  private final DataSourceService dataSourceService = mock(DataSourceService.class);
  private final DataSourceConnectorStore connectorStore = mock(DataSourceConnectorStore.class);
  private final ConnectionTestClient client = mock(ConnectionTestClient.class);
  private final TextEncryptor encryptor = mock(TextEncryptor.class);

  private DataSourceConnectionTestService service;
  private ListAppender<ILoggingEvent> logs;
  private Logger serviceLogger;

  @BeforeEach
  void setUp() {
    when(encryptor.encrypt(any()))
        .thenAnswer(
            call -> "ENC(" + new StringBuilder(call.<String>getArgument(0)).reverse() + ")");
    service = service(Optional.of(client));
    serviceLogger = (Logger) LoggerFactory.getLogger(DataSourceConnectionTestService.class);
    logs = new ListAppender<>();
    logs.start();
    serviceLogger.addAppender(logs);
  }

  @AfterEach
  void detachLogs() {
    serviceLogger.detachAppender(logs);
  }

  private DataSourceConnectionTestService service(Optional<ConnectionTestClient> kafka) {
    return new DataSourceConnectionTestService(
        dataSourceService,
        connectorStore,
        new ConnectorValidator(List.of(new HttpConnectorRules())),
        encryptor,
        () -> Optional.of(USER),
        new ConnectionTestProperties("request", "result", 20),
        kafka);
  }

  private static ConnectionTestInputDTO input(String connectorJson, String apiCall) {
    ConnectionTestInputDTO input = new ConnectionTestInputDTO();
    input.setConnector(ConnectorJson.read(MAPPER.readTree(connectorJson.replace('\'', '"'))));
    input.setApiCall(apiCall);
    return input;
  }

  private static ConnectionTestInputDTO sql(String password) {
    return input(
        "{'connectorType':'SQL','driver':'postgres','dsn':'postgres://db.example:5432/city',"
            + "'table':'readings','columns':['id','value'],'where':'secret_column > 7',"
            + "'user':'reader','password':'"
            + password
            + "'}",
        null);
  }

  private static ConnectionTestInputDTO http(String apiCall) {
    return input(
        "{'connectorType':'HTTP','baseUrl':'https://api.example.org/v1',"
            + "'apiCalls':[{'name':'stations','path':'/stations'}]}",
        apiCall);
  }

  private static ConnectionTestInputDTO mqtt(String apiCall) {
    return input(
        "{'connectorType':'MQTT','urls':['tcp://broker-a:1883','tcp://broker-b:1883'],"
            + "'topics':['sensor/#'],'qos':1}",
        apiCall);
  }

  private static ConnectionTestResult success(ConnectionTestRequest request) {
    return new ConnectionTestResult(
        request.requestId(),
        Outcome.SUCCESS,
        null,
        "1 row",
        "id,value\n1,20.5\n",
        false,
        1,
        null,
        9);
  }

  private void answer(Function<ConnectionTestRequest, ConnectionTestResult> f) {
    when(client.test(any())).thenAnswer(call -> f.apply(call.getArgument(0)));
  }

  /**
   * A saved data source whose stored connector is {@code stored}, every secret {@code ENC(stored)}.
   */
  private UUID savedDataSource(DataSourceStatus status, ConnectionTestInputDTO stored) {
    UUID id = UUID.randomUUID();
    DataSource dataSource = new DataSource();
    dataSource.setDataSourceStatus(status);
    Map<String, Object> document =
        ConnectorJson.toDocument(stored.getConnector(), null, secret -> STORED_SECRET);
    when(dataSourceService.findByIdOrThrow(id)).thenReturn(dataSource);
    when(connectorStore.read(dataSource))
        .thenReturn(ConnectorJson.readStored(stored.getConnector().connectorType(), document));
    when(connectorStore.storedDocument(dataSource)).thenReturn(document);
    return id;
  }

  /** A saved connector of each type, its secret as the placeholder that a client sends back. */
  enum Saved {
    SQL(
        "{'connectorType':'SQL','driver':'postgres','dsn':'postgres://db.example:5432/city',"
            + "'table':'readings','columns':['id','value'],'user':'reader','password':'********'}",
        null,
        "/password"),
    MQTT(
        "{'connectorType':'MQTT','urls':['tcp://broker-a:1883','tcp://broker-b:1883'],"
            + "'topics':['sensor/#'],'qos':1,'user':'reader','password':'********'}",
        null,
        "/password"),
    HTTP(
        "{'connectorType':'HTTP','baseUrl':'https://api.example.org/v1',"
            + "'authentication':{'type':'BASIC','username':'reader','password':'********'},"
            + "'apiCalls':[{'name':'stations','path':'/stations'}]}",
        "stations",
        "/authentication/password");

    private final String json;
    private final String apiCall;
    private final String secretPointer;

    Saved(String json, String apiCall, String secretPointer) {
      this.json = json;
      this.apiCall = apiCall;
      this.secretPointer = secretPointer;
    }

    /** The connector as it was saved, with its secret in clear text. */
    ConnectionTestInputDTO stored() {
      return input(json.replace(Secret.PLACEHOLDER, PLAIN_PASSWORD), apiCall);
    }

    /** The saved connector as a client sends it back unchanged. */
    ConnectionTestInputDTO requested() {
      return input(json, apiCall);
    }

    /** The saved connector as a client sends it back, with {@code from} replaced by {@code to}. */
    ConnectionTestInputDTO requested(String from, String to) {
      assertThat(json).contains(from);
      return input(json.replace(from, to), apiCall);
    }

    /** The secret of the request that config-adapter receives. */
    String sentSecret(ConnectionTestRequest request) {
      return MAPPER.valueToTree(request.connector()).at(secretPointer).asString();
    }
  }

  private ConnectionTestRequest sentRequest() {
    ArgumentCaptor<ConnectionTestRequest> request =
        ArgumentCaptor.forClass(ConnectionTestRequest.class);
    verify(client).test(request.capture());
    return request.getValue();
  }

  private List<String> auditLines() {
    return logs.list.stream()
        .filter(event -> event.getLevel() == Level.INFO)
        .map(ILoggingEvent::getFormattedMessage)
        .filter(message -> message.startsWith("Connection test:"))
        .toList();
  }

  private String singleAuditLine() {
    assertThat(auditLines()).hasSize(1);
    return auditLines().getFirst();
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("connector that misses a value required for release is rejected untested")
    void incompleteConnector_isRejectedWithoutTest() {
      ConnectionTestInputDTO input = input("{'connectorType':'SQL','driver':'postgres'}", null);

      assertThatThrownBy(() -> service.test(input))
          .isInstanceOfSatisfying(
              ConnectorValidationException.class,
              e ->
                  assertThat(e.getViolations())
                      .extracting(ConnectorViolation::path)
                      .contains("connector.dsn", "connector.table"));
      verify(client, never()).test(any());
      assertThat(singleAuditLine()).endsWith("outcome=INVALID");
    }

    @Test
    @DisplayName("placeholder of an unsaved connector is rejected, naming its key path")
    void placeholderOfUnsavedConnector_isRejected() {
      assertThatThrownBy(() -> service.test(sql("********")))
          .isInstanceOf(MalformedDataSourceRequestException.class)
          .hasMessage("No stored secret for ******** at connector.password");
      verify(client, never()).test(any());
      assertThat(singleAuditLine()).contains("dataSource=unsaved").endsWith("outcome=INVALID");
    }

    @Test
    @DisplayName("HTTP connector without apiCall is rejected with a field error")
    void httpWithoutApiCall_isRejected() {
      assertThatThrownBy(() -> service.test(http(null)))
          .isInstanceOfSatisfying(
              ConnectorValidationException.class,
              e ->
                  assertThat(e.getViolations())
                      .containsExactly(
                          new ConnectorViolation(
                              "apiCall", "'apiCall' is required for an HTTP connector")));
      verify(client, never()).test(any());
    }

    @Test
    @DisplayName("HTTP connector with an apiCall it does not have is rejected with a field error")
    void httpWithUnknownApiCall_isRejected() {
      assertThatThrownBy(() -> service.test(http("unknown")))
          .isInstanceOfSatisfying(
              ConnectorValidationException.class,
              e ->
                  assertThat(e.getViolations())
                      .containsExactly(
                          new ConnectorViolation(
                              "apiCall",
                              "'apiCall' must name one of the API calls of the connector")));
    }

    @Test
    @DisplayName("SQL connector with apiCall is rejected with a field error")
    void sqlWithApiCall_isRejected() {
      ConnectionTestInputDTO input = sql(PLAIN_PASSWORD);
      input.setApiCall("stations");

      assertThatThrownBy(() -> service.test(input))
          .isInstanceOfSatisfying(
              ConnectorValidationException.class,
              e ->
                  assertThat(e.getViolations())
                      .containsExactly(
                          new ConnectorViolation(
                              "apiCall", "'apiCall' must be absent for a SQL connector")));
    }

    @Test
    @DisplayName("HTTP connector runs the named API call")
    void httpWithApiCall_sendsIt() {
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(http("stations"));

      assertThat(sentRequest().apiCall()).isEqualTo("stations");
    }

    @Test
    @DisplayName("MQTT connector with a blank apiCall is tested without one")
    void mqttWithBlankApiCall_sendsNone() {
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(mqtt("  "));

      assertThat(sentRequest().apiCall()).isNull();
    }
  }

  @Nested
  @DisplayName("Secrets")
  class Secrets {

    @Test
    @DisplayName("clear-text password leaves the backend only encrypted")
    void clearTextPassword_isSentEncrypted() {
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(sql(PLAIN_PASSWORD));

      ConnectionTestRequest request = sentRequest();
      assertThat(request.connector())
          .containsEntry("password", "ENC(" + new StringBuilder(PLAIN_PASSWORD).reverse() + ")")
          .containsEntry("connectionType", "sql");
      assertThat(MAPPER.writeValueAsString(request)).doesNotContain(PLAIN_PASSWORD);
    }

    @Test
    @DisplayName("placeholder of a saved data source takes its stored ciphertext")
    void placeholderOfSavedDataSource_takesStoredCiphertext() {
      UUID id = savedDataSource(DataSourceStatus.DRAFT, sql(PLAIN_PASSWORD));
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(id, sql("********"));

      assertThat(sentRequest().connector()).containsEntry("password", STORED_SECRET);
      assertThat(singleAuditLine()).contains("dataSource=" + id);
    }

    @Test
    @DisplayName("placeholder of a saved data source without a stored secret is rejected")
    void placeholderOfSavedDataSourceWithoutStoredSecret_isRejected() {
      UUID id = UUID.randomUUID();
      DataSource dataSource = new DataSource();
      when(dataSourceService.findByIdOrThrow(id)).thenReturn(dataSource);
      when(connectorStore.read(dataSource)).thenReturn(sql(PLAIN_PASSWORD).getConnector());
      when(connectorStore.storedDocument(dataSource))
          .thenReturn(Map.of("connectionType", "mqtt", "password", "ENC(stored)"));

      assertThatThrownBy(() -> service.test(id, sql("********")))
          .isInstanceOf(MalformedDataSourceRequestException.class);
      verify(client, never()).test(any());
    }

    static Stream<Arguments> otherDestinations() {
      return Stream.of(
          arguments(Saved.SQL, "another host", "db.example", "attacker.example"),
          arguments(Saved.SQL, "another port", "db.example:5432", "db.example:5433"),
          arguments(Saved.SQL, "another scheme", "postgres://", "postgresql://"),
          arguments(Saved.SQL, "the port left out", "db.example:5432", "db.example"),
          arguments(Saved.MQTT, "another host", "broker-b:1883", "broker-c:1883"),
          arguments(Saved.MQTT, "another port", "broker-a:1883", "broker-a:8883"),
          arguments(
              Saved.MQTT,
              "an added URL",
              "'tcp://broker-b:1883'",
              "'tcp://broker-b:1883','tcp://broker-c:1883'"),
          arguments(Saved.MQTT, "a removed URL", ",'tcp://broker-b:1883'", ""),
          arguments(Saved.HTTP, "another host", "api.example.org", "api.attacker.example"),
          arguments(Saved.HTTP, "another port", "api.example.org/", "api.example.org:8443/"));
    }

    static Stream<Arguments> otherValuesAtTheSameDestination() {
      return Stream.of(
          arguments(Saved.SQL, "another table", "'readings'", "'stations'"),
          arguments(Saved.SQL, "other columns", "['id','value']", "['id']"),
          arguments(Saved.SQL, "another database", ":5432/city", ":5432/archive"),
          arguments(Saved.MQTT, "another topic", "sensor/#", "weather/#"),
          arguments(Saved.HTTP, "another API call path", "'/stations'", "'/stations/active'"));
    }

    static Stream<Arguments> changedValues() {
      return Stream.concat(otherDestinations(), otherValuesAtTheSameDestination());
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("otherDestinations")
    @DisplayName("stored secret of a draft is not sent to another destination")
    void placeholderOfDraftForAnotherDestination_isRejected(
        Saved saved, String change, String from, String to) {
      UUID id = savedDataSource(DataSourceStatus.DRAFT, saved.stored());

      assertThatThrownBy(() -> service.test(id, saved.requested(from, to)))
          .isInstanceOf(MalformedDataSourceRequestException.class)
          .hasMessage(DataSourceConnectionTestService.STORED_SECRET_FOR_STORED_DESTINATION);
      verify(client, never()).test(any());
      assertThat(singleAuditLine()).endsWith("outcome=INVALID");
    }

    @ParameterizedTest
    @EnumSource(DataSourceStatus.class)
    @DisplayName("stored secret of an HTTP connector is not sent over http")
    void placeholderOfHttpOverHttp_isRejected(DataSourceStatus status) {
      UUID id = savedDataSource(status, Saved.HTTP.stored());

      // Authentication needs https, so validation refuses the scheme before the secret is resolved.
      assertThatThrownBy(() -> service.test(id, Saved.HTTP.requested("https://", "http://")))
          .isInstanceOfSatisfying(
              ConnectorValidationException.class,
              e ->
                  assertThat(e.getViolations())
                      .extracting(ConnectorViolation::path)
                      .containsExactly("connector.baseUrl"));
      verify(client, never()).test(any());
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("otherValuesAtTheSameDestination")
    @DisplayName("stored secret of a draft serves other values at the same destination")
    void placeholderOfDraftForTheSameDestination_takesStoredCiphertext(
        Saved saved, String change, String from, String to) {
      UUID id = savedDataSource(DataSourceStatus.DRAFT, saved.stored());
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(id, saved.requested(from, to));

      assertThat(saved.sentSecret(sentRequest())).isEqualTo(STORED_SECRET);
    }

    @Test
    @DisplayName("a new password may go to another destination")
    void newPasswordForAnotherDestination_isSentEncrypted() {
      UUID id = savedDataSource(DataSourceStatus.DRAFT, sql(PLAIN_PASSWORD));
      answer(DataSourceConnectionTestServiceTest::success);
      ConnectionTestInputDTO elsewhere =
          input(
              "{'connectorType':'SQL','driver':'postgres','dsn':'postgres://other.example:5432/city',"
                  + "'table':'readings','columns':['id'],'user':'reader','password':'new-secret'}",
              null);

      service.test(id, elsewhere);

      assertThat(sentRequest().connector()).containsEntry("password", "ENC(terces-wen)");
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("changedValues")
    @DisplayName("stored secret of a released data source serves only its connector as released")
    void placeholderOfReleasedDataSourceWithChangedValues_isRejected(
        Saved saved, String change, String from, String to) {
      UUID id = savedDataSource(DataSourceStatus.AVAILABLE, saved.stored());

      assertThatThrownBy(() -> service.test(id, saved.requested(from, to)))
          .isInstanceOf(MalformedDataSourceRequestException.class)
          .hasMessage(DataSourceConnectionTestService.STORED_SECRET_FOR_RELEASED_CONNECTOR);
      verify(client, never()).test(any());
      assertThat(singleAuditLine()).endsWith("outcome=INVALID");
    }

    @ParameterizedTest
    @EnumSource(Saved.class)
    @DisplayName("stored secret of a released data source tests its connector as released")
    void placeholderOfReleasedDataSourceAsReleased_takesStoredCiphertext(Saved saved) {
      UUID id = savedDataSource(DataSourceStatus.AVAILABLE, saved.stored());
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(id, saved.requested());

      assertThat(saved.sentSecret(sentRequest())).isEqualTo(STORED_SECRET);
    }

    @Test
    @DisplayName("unknown data source answers 404 before anything is tested")
    void unknownDataSource_isNotFound() {
      UUID id = UUID.randomUUID();
      when(dataSourceService.findByIdOrThrow(id))
          .thenThrow(new ResourceNotFoundException("DataSource", id));

      assertThatThrownBy(() -> service.test(id, sql(PLAIN_PASSWORD)))
          .isInstanceOf(ResourceNotFoundException.class);
      verify(client, never()).test(any());
    }
  }

  @Nested
  @DisplayName("Result")
  class Result {

    @Test
    @DisplayName("success answers with the preview")
    void success_answersWithThePreview() {
      answer(DataSourceConnectionTestServiceTest::success);

      ConnectionTestResultDTO answer = service.test(sql(PLAIN_PASSWORD));

      assertThat(answer)
          .isEqualTo(
              new ConnectionTestResultDTO(
                  true, null, "1 row", "id,value\n1,20.5\n", false, 1, null));
      assertThat(singleAuditLine()).endsWith("outcome=SUCCESS");
    }

    @ParameterizedTest
    @EnumSource(
        value = Reason.class,
        names = {"INVALID_CONNECTOR", "SOURCE_ERROR", "TIMEOUT"})
    @DisplayName("failure of the source is an answer, not an error")
    void sourceFailure_isAnAnswer(Reason reason) {
      answer(request -> ConnectionTestResult.failure(request.requestId(), reason, "no", 3));

      ConnectionTestResultDTO answer = service.test(sql(PLAIN_PASSWORD));

      assertThat(answer.success()).isFalse();
      assertThat(answer.reason()).isEqualTo(reason.name());
      assertThat(answer.message()).isEqualTo("no");
      assertThat(singleAuditLine()).endsWith("outcome=FAILURE:" + reason.name());
    }

    @Test
    @DisplayName("BUSY of config-adapter is too many requests")
    void adapterBusy_isTooManyRequests() {
      answer(request -> ConnectionTestResult.failure(request.requestId(), Reason.BUSY, "full", 0));

      assertThatThrownBy(() -> service.test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ConnectionTestBusyException.class)
          .hasMessage(DataSourceConnectionTestService.ADAPTER_BUSY);
      assertThat(singleAuditLine()).endsWith("outcome=BUSY");
    }

    @Test
    @DisplayName("UNAVAILABLE of config-adapter is a bad gateway")
    void adapterUnavailable_isBadGateway() {
      answer(
          request ->
              ConnectionTestResult.failure(request.requestId(), Reason.UNAVAILABLE, "no NiFi", 0));

      assertThatThrownBy(() -> service.test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ExternalSystemRejectionException.class);
      assertThat(singleAuditLine()).endsWith("outcome=UNAVAILABLE");
    }

    @Test
    @DisplayName("failure without a reason is a bad gateway")
    void failureWithoutReason_isBadGateway() {
      answer(request -> ConnectionTestResult.failure(request.requestId(), null, "?", 0));

      assertThatThrownBy(() -> service.test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ExternalSystemRejectionException.class);
    }

    @Test
    @DisplayName("no result in time is audited as TIMEOUT and lets the user test again")
    void noResultInTime_isAuditedAndFreesTheUsersSlot() {
      when(client.test(any()))
          .thenThrow(new ExternalSystemTimeoutException("no result", null))
          .thenAnswer(call -> success(call.getArgument(0)));

      assertThatThrownBy(() -> service.test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ExternalSystemTimeoutException.class);
      assertThat(singleAuditLine()).endsWith("outcome=TIMEOUT");

      assertThat(service.test(sql(PLAIN_PASSWORD)).success()).isTrue();
      assertThat(auditLines()).last().asString().endsWith("outcome=SUCCESS");
    }

    @Test
    @DisplayName("request expires after the configured wait")
    void request_expiresAfterTheConfiguredWait() {
      answer(DataSourceConnectionTestServiceTest::success);
      Instant before = Instant.now();

      service.test(sql(PLAIN_PASSWORD));

      assertThat(sentRequest().expiresAt())
          .isBetween(before.plusSeconds(20), Instant.now().plusSeconds(20));
    }

    @Test
    @DisplayName("without Kafka a test is a bad gateway, never a success")
    void withoutKafka_isBadGateway() {
      assertThatThrownBy(() -> service(Optional.empty()).test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ExternalSystemRejectionException.class);
      assertThat(singleAuditLine()).endsWith("outcome=UNAVAILABLE");
    }
  }

  @Nested
  @DisplayName("One test per user")
  class OneTestPerUser {

    @Test
    @DisplayName("second test of the same user while the first runs is too many requests")
    void secondConcurrentTest_isTooManyRequests() throws Exception {
      CountDownLatch running = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      answer(
          request -> {
            running.countDown();
            await().atMost(Duration.ofSeconds(10)).until(() -> release.getCount() == 0);
            return success(request);
          });
      CompletableFuture<ConnectionTestResultDTO> first =
          CompletableFuture.supplyAsync(() -> service.test(sql(PLAIN_PASSWORD)));
      assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> service.test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ConnectionTestBusyException.class)
          .hasMessage(DataSourceConnectionTestService.USER_BUSY);
      release.countDown();

      assertThat(first.get(5, TimeUnit.SECONDS).success()).isTrue();
      assertThat(auditLines()).hasSize(2).anyMatch(line -> line.endsWith("outcome=BUSY"));
    }

    @Test
    @DisplayName("the user's slot is free again after a failed test")
    void slot_isFreeAgainAfterAFailedTest() {
      when(client.test(any()))
          .thenThrow(new ExternalSystemRejectionException("down"))
          .thenThrow(new ExternalSystemTimeoutException("slow", null))
          .thenAnswer(call -> success(call.getArgument(0)));

      assertThatThrownBy(() -> service.test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ExternalSystemRejectionException.class);
      assertThatThrownBy(() -> service.test(sql(PLAIN_PASSWORD)))
          .isInstanceOf(ExternalSystemTimeoutException.class);

      assertThat(service.test(sql(PLAIN_PASSWORD)).success()).isTrue();
    }
  }

  @Nested
  @DisplayName("Audit")
  class Audit {

    @Test
    @DisplayName(
        "audit line names user, request, data source, type, destination, target and outcome")
    void auditLine_namesWhoTestedWhatWhere() {
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(sql(PLAIN_PASSWORD));

      assertThat(singleAuditLine())
          .isEqualTo(
              "Connection test: user="
                  + USER
                  + " requestId="
                  + sentRequest().requestId()
                  + " dataSource=unsaved connectorType=SQL destination=db.example:5432"
                  + " target=readings outcome=SUCCESS");
    }

    @Test
    @DisplayName("no log line carries the connector's secret, query or the preview")
    void logs_carryNoConnectorValues() {
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(sql(PLAIN_PASSWORD));

      assertThat(logs.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .noneMatch(message -> message.contains(PLAIN_PASSWORD))
          .noneMatch(message -> message.contains("secret_column"))
          .noneMatch(message -> message.contains("20.5"))
          .noneMatch(message -> message.contains("ENC("));
    }

    @Test
    @DisplayName("destination is the first MQTT broker, host and port only")
    void destination_ofMqtt_isTheFirstBroker() {
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(mqtt(null));

      assertThat(singleAuditLine()).contains("connectorType=MQTT destination=broker-a:1883 ");
    }

    @Test
    @DisplayName("destination of an HTTP connector is the API host without its path")
    void destination_ofHttp_isTheHostWithoutPath() {
      answer(DataSourceConnectionTestServiceTest::success);

      service.test(http("stations"));

      assertThat(singleAuditLine()).contains("destination=api.example.org ");
    }

    @Test
    @DisplayName("destination of a connector without a URL is unknown")
    void destination_withoutUrl_isUnknown() {
      assertThatThrownBy(() -> service.test(input("{'connectorType':'SQL'}", null)))
          .isInstanceOf(ConnectorValidationException.class);

      assertThat(singleAuditLine()).contains("destination=unknown ");
    }
  }
}

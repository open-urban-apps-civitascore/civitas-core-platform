package de.civitascore.portal.service.connector;

import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.portal.configuration.ConnectionTestProperties;
import de.civitascore.portal.messaging.kafka.ConnectionTestClient;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.connector.ConnectorUris;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.connector.StoredSecretRefusedException;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.ConnectionTestInputDTO;
import de.civitascore.portal.model.output.ConnectionTestResultDTO;
import de.civitascore.portal.model.output.ConnectionTestResultDTO.Reason;
import de.civitascore.portal.service.DataSourceService;
import de.civitascore.portal.util.ConnectionTestBusyException;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

/**
 * Tests connector values against their source through a request to the connection test service.
 * Every test writes one audit log line.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceConnectionTestService {

  static final String API_CALL = "apiCall";
  static final String USER_BUSY = "A connection test of this user is still running";
  static final String ADAPTER_BUSY = "Too many connection tests are running; try again later";
  private static final String UNSAVED = "unsaved";
  private static final String UNKNOWN = "unknown";
  static final String STORED_SECRET_FOR_RELEASED_CONNECTOR =
      "The stored secret of a released data source is kept only to test its connector as released;"
          + " enter it again to test other values";

  private final DataSourceService dataSourceService;
  private final DataSourceConnectorStore connectorStore;
  private final ConnectorValidator connectorValidator;
  private final TextEncryptor textEncryptor;
  private final AuditorAware<UUID> currentUser;
  private final ConnectionTestProperties properties;
  private final Optional<ConnectionTestClient> client;

  private final Set<String> usersTesting = ConcurrentHashMap.newKeySet();
  private final AtomicInteger running = new AtomicInteger();

  /**
   * Tests connector values that no data source holds yet.
   *
   * @throws ConnectorValidationException if the values do not meet the release rules
   * @throws MalformedDataSourceRequestException for a placeholder
   * @throws ConnectionTestBusyException if the user runs a test already, or too many tests run
   * @throws ExternalSystemRejectionException if the test cannot be run
   * @throws ExternalSystemTimeoutException if no answer arrives in time
   */
  public ConnectionTestResultDTO test(ConnectionTestInputDTO input) {
    return bounded(() -> audited(null, null, null, true, input));
  }

  /**
   * Tests connector values for a saved data source in any status. A {@code "********"} placeholder
   * keeps the stored secret only for the stored destinations of a draft, as every write does, or
   * for the unchanged connector of a released source, so the secret cannot reach another host or
   * other data. The where clause runs only as saved, or with secrets the caller typed.
   *
   * @throws ResourceNotFoundException if no such data source exists
   * @throws MalformedDataSourceRequestException for a placeholder the stored secret may not fill
   * @see #test(ConnectionTestInputDTO)
   */
  public ConnectionTestResultDTO test(UUID dataSourceId, ConnectionTestInputDTO input) {
    return bounded(
        () -> {
          DataSource dataSource = dataSourceService.findByIdOrThrow(dataSourceId);
          boolean unchanged = unchanged(input.getConnector(), connectorStore.read(dataSource));
          // A stored secret runs only a where clause that someone who may edit the source saved.
          boolean applyWhere = unchanged || !ConnectorJson.hasPlaceholder(input.getConnector());
          if (dataSource.getDataSourceStatus() == DataSourceStatus.DRAFT || unchanged) {
            return audited(
                dataSourceId, connectorStore.storedDocument(dataSource), null, applyWhere, input);
          }
          return audited(
              dataSourceId, null, STORED_SECRET_FOR_RELEASED_CONNECTOR, applyWhere, input);
        });
  }

  /**
   * Runs {@code test} unless {@code max-concurrent} tests wait on this instance already. Checked
   * before the data source is read, because a waiting test keeps its database connection.
   */
  private ConnectionTestResultDTO bounded(Supplier<ConnectionTestResultDTO> test) {
    try {
      if (running.incrementAndGet() > properties.maxConcurrent()) {
        log.info(
            "Connection test: user={} outcome=BUSY",
            Encode.forJava(currentUser.getCurrentAuditor().map(UUID::toString).orElse(UNKNOWN)));
        throw new ConnectionTestBusyException(ADAPTER_BUSY);
      }
      return test.get();
    } finally {
      running.decrementAndGet();
    }
  }

  private static boolean unchanged(
      ConnectorConfiguration requested, ConnectorConfiguration stored) {
    return stored != null
        && ConnectorJson.toMaskedTree(requested).equals(ConnectorJson.toMaskedTree(stored));
  }

  /**
   * @param dataSourceId the saved data source, or null for unsaved values
   * @param storedDocument the document whose secrets a placeholder may keep, or null
   * @param placeholderRefusal why a placeholder may not keep the stored secret, or null
   * @param applyWhere whether an SQL test runs the where clause
   * @param input the values to test
   */
  private ConnectionTestResultDTO audited(
      UUID dataSourceId,
      Map<String, Object> storedDocument,
      String placeholderRefusal,
      boolean applyWhere,
      ConnectionTestInputDTO input) {
    String user = currentUser.getCurrentAuditor().map(UUID::toString).orElse(UNKNOWN);
    String requestId = UUID.randomUUID().toString();
    ConnectorConfiguration connector = input.getConnector();
    // Read before the test runs, so that an unknown connector type fails before anything is sent.
    String destination = destinationHost(connector);
    String target = target(connector, input.getApiCall());
    String outcome = "ERROR";
    String data = "none";
    try {
      ConnectionTestResultDTO answer =
          run(user, requestId, storedDocument, placeholderRefusal, applyWhere, input);
      outcome = answer.success() ? "SUCCESS" : "FAILURE:" + answer.reason();
      data = answer.preview() == null ? "none" : "shown";
      return answer;
    } catch (StoredSecretRefusedException e) {
      outcome = "REFUSED";
      throw e;
    } catch (ConnectorValidationException | MalformedDataSourceRequestException e) {
      outcome = "INVALID";
      throw e;
    } catch (ConnectionTestBusyException e) {
      outcome = "BUSY";
      throw e;
    } catch (ExternalSystemTimeoutException e) {
      outcome = "TIMEOUT";
      throw e;
    } catch (ExternalSystemRejectionException e) {
      outcome = "UNAVAILABLE";
      throw e;
    } finally {
      // TR-03187 L-1/L-6: who tested which destination, whether data of the source was shown and
      // how it ended, without secrets, query or preview. The request id finds the matching line in
      // the connection test service's log.
      log.info(
          "Connection test: user={} requestId={} dataSource={} connectorType={}"
              + " destination=\"{}\" target=\"{}\" data={} outcome={}",
          Encode.forJava(user),
          requestId,
          dataSourceId == null ? UNSAVED : dataSourceId,
          connector.connectorType(),
          Encode.forJava(destination),
          Encode.forJava(target),
          data,
          outcome);
    }
  }

  private ConnectionTestResultDTO run(
      String user,
      String requestId,
      Map<String, Object> storedDocument,
      String placeholderRefusal,
      boolean applyWhere,
      ConnectionTestInputDTO input) {
    ConnectorConfiguration connector = input.getConnector();
    String apiCall = blankToNull(input.getApiCall());
    connectorValidator.requireValid(connector, ConnectorValidator.Stage.RELEASE);
    requireFittingApiCall(connector, apiCall);
    if (placeholderRefusal != null && ConnectorJson.hasPlaceholder(connector)) {
      throw new StoredSecretRefusedException(placeholderRefusal);
    }
    ConnectorJson.requireStoredSecrets(connector, storedDocument);
    Map<String, Object> document =
        ConnectorJson.toDocument(connector, storedDocument, textEncryptor::encrypt);
    ConnectionTestClient kafka =
        client.orElseThrow(
            () ->
                new ExternalSystemRejectionException("Connection tests need Kafka to be enabled"));

    if (!usersTesting.add(user)) {
      throw new ConnectionTestBusyException(USER_BUSY);
    }
    try {
      ConnectionTestRequest request =
          new ConnectionTestRequest(
              requestId, Instant.now().plus(properties.waitTime()), document, apiCall, applyWhere);
      return toAnswer(kafka.test(request));
    } finally {
      usersTesting.remove(user);
    }
  }

  /** Only an HTTP connector takes an {@code apiCall}, which must name one of its API calls. */
  private static void requireFittingApiCall(ConnectorConfiguration connector, String apiCall) {
    String violation = null;
    if (connector instanceof HttpConnectorConfiguration http) {
      if (apiCall == null) {
        violation = "'apiCall' is required for an HTTP connector";
      } else if (http.apiCalls().stream().noneMatch(call -> apiCall.equals(call.name()))) {
        violation = "'apiCall' must name one of the API calls of the connector";
      }
    } else if (apiCall != null) {
      violation = "'apiCall' must be absent for a " + connector.connectorType() + " connector";
    }
    if (violation != null) {
      throw new ConnectorValidationException(List.of(new ConnectorViolation(API_CALL, violation)));
    }
  }

  private static ConnectionTestResultDTO toAnswer(ConnectionTestResult result) {
    if (result.outcome() == ConnectionTestResult.Outcome.SUCCESS) {
      return answer(true, null, result);
    }
    if (result.reason() == null) {
      throw new ExternalSystemRejectionException(
          "The connection test service answered connection test "
              + result.requestId()
              + " without a reason");
    }
    return switch (result.reason()) {
      case INVALID_CONNECTOR -> answer(false, Reason.INVALID_CONNECTOR, result);
      case SOURCE_ERROR -> answer(false, Reason.SOURCE_ERROR, result);
      case TIMEOUT -> answer(false, Reason.TIMEOUT, result);
      case BUSY -> throw new ConnectionTestBusyException(ADAPTER_BUSY);
      case UNAVAILABLE ->
          throw new ExternalSystemRejectionException(
              "The connection test service could not run connection test "
                  + result.requestId()
                  + ": "
                  + result.message());
    };
  }

  private static ConnectionTestResultDTO answer(
      boolean success, Reason reason, ConnectionTestResult result) {
    return new ConnectionTestResultDTO(
        success,
        reason,
        result.message(),
        result.preview(),
        result.previewTruncated(),
        result.rowCount(),
        result.httpStatus());
  }

  /** What the test reads: the SQL table, the first MQTT topic, or the HTTP API call. */
  static String target(ConnectorConfiguration connector, String apiCall) {
    String target =
        switch (connector) {
          case SqlConnectorConfiguration sql -> sql.table();
          case MqttConnectorConfiguration mqtt ->
              mqtt.topics() == null || mqtt.topics().isEmpty() ? null : mqtt.topics().getFirst();
          case HttpConnectorConfiguration http -> apiCall;
          default -> null;
        };
    return target == null || target.isBlank() ? UNKNOWN : target;
  }

  /** The host and port, if any, of the first address of the connector, or {@code unknown}. */
  static String destinationHost(ConnectorConfiguration connector) {
    return ConnectorUris.addresses(connector).stream()
        .findFirst()
        .map(DataSourceConnectionTestService::hostAndPort)
        .orElse(UNKNOWN);
  }

  private static String hostAndPort(String url) {
    try {
      URI uri = new URI(url);
      if (uri.getHost() == null) {
        return UNKNOWN;
      }
      return uri.getPort() < 0 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
    } catch (URISyntaxException e) {
      return UNKNOWN;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}

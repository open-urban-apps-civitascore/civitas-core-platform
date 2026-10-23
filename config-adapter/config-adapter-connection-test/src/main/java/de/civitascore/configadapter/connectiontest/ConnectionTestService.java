/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static de.civitascore.configadapter.connector.ConnectorValues.containsNifiReference;
import static de.civitascore.configadapter.connector.ConnectorValues.isEncrypted;
import static de.civitascore.configadapter.connector.ConnectorValues.trimmedString;

import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Outcome;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one connection test: picks the prober of the connector's type, decrypts the connector and
 * makes the result safe to return. Knows nothing about Kafka or time limits.
 */
final class ConnectionTestService {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionTestService.class);

  private final Map<String, ConnectionProber> probers;
  private final Decryptor decryptor;

  ConnectionTestService(List<ConnectionProber> probers, Decryptor decryptor) {
    this.probers =
        probers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ConnectionProber::connectionType, Function.identity()));
    this.decryptor = decryptor;
  }

  /** Never throws: every problem becomes a failure result. */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // A test must always produce a result
  ConnectionTestResult test(ConnectionTestRequest request, Cancellation cancellation) {
    String requestId = request.requestId();
    long begin = System.nanoTime();
    ResultText text = new ResultText(List.of());
    try {
      Map<String, Object> connector = request.connector();
      ConnectionProber prober = proberFor(connector);
      rejectPlaintextPasswords(connector);
      Map<String, Object> decrypted = decrypt(connector);
      text = new ResultText(ResultText.secretsOf(connector, decrypted));
      rejectReferences("", decrypted);
      ProbeResult probe = prober.probe(decrypted, request.apiCall(), cancellation);
      return new ConnectionTestResult(
          requestId,
          probe.success() ? Outcome.SUCCESS : Outcome.FAILURE,
          probe.success() ? null : Reason.SOURCE_ERROR,
          text.message(probe.message()),
          probe.rowCount(),
          probe.httpStatus(),
          millisSince(begin));
    } catch (ConnectionTestFailure e) {
      if (e.destinationRefused()) {
        LOG.warn(
            "Connection test {} refused: {}",
            Encode.forJava(requestId),
            Encode.forJava(text.message(e.getMessage())));
      } else if (LOG.isDebugEnabled()) {
        LOG.debug(
            "Connection test {} failed: {}",
            Encode.forJava(requestId),
            Encode.forJava(text.message(e.getMessage())));
      }
      return ConnectionTestResult.failure(
          requestId, e.reason(), text.message(e.getMessage()), millisSince(begin));
    } catch (RuntimeException e) {
      LOG.error(
          "Connection test {} failed unexpectedly",
          Encode.forJava(requestId),
          ConnectionTestFailure.withoutText(e));
      return ConnectionTestResult.failure(
          requestId, Reason.UNAVAILABLE, "The test failed unexpectedly.", millisSince(begin));
    }
  }

  static long millisSince(long nanoTime) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoTime);
  }

  private ConnectionProber proberFor(Map<String, Object> connector) throws ConnectionTestFailure {
    if (connector == null || connector.isEmpty()) {
      throw ConnectionTestFailure.invalid("The request has no connector.");
    }
    String type = trimmedString(connector.get("connectionType")).toLowerCase(Locale.ROOT);
    ConnectionProber prober = probers.get(type);
    if (prober == null) {
      throw ConnectionTestFailure.invalid("Connection type '" + type + "' cannot be tested.");
    }
    return prober;
  }

  /** Refuses a plain-text password, which must never travel on Kafka. */
  private static void rejectPlaintextPasswords(Map<?, ?> document) throws ConnectionTestFailure {
    for (Map.Entry<?, ?> entry : document.entrySet()) {
      if (entry.getValue() instanceof Map<?, ?> nested) {
        rejectPlaintextPasswords(nested);
      } else if ("password".equals(entry.getKey())
          && !trimmedString(entry.getValue()).isEmpty()
          && !isEncrypted(entry.getValue())) {
        throw ConnectionTestFailure.invalid(
            "The password must be encrypted (plaintext secrets are not accepted).");
      }
    }
  }

  /**
   * Refuses a value holding <code>${</code> or <code>#{</code>, which NiFi would evaluate in a
   * property. The message names the field, never the value, which may be a password.
   */
  private static void rejectReferences(String path, Object value) throws ConnectionTestFailure {
    if (value instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        rejectReferences(
            path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey(),
            entry.getValue());
      }
    } else if (value instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        rejectReferences(path + "[" + i + "]", list.get(i));
      }
    } else if (value instanceof String string && containsNifiReference(string)) {
      throw ConnectionTestFailure.invalid(
          "'" + path + "' contains '${' or '#{', which the pipeline does not accept in a value.");
    }
  }

  private Map<String, Object> decrypt(Map<String, Object> connector) throws ConnectionTestFailure {
    try {
      return decryptor.decrypt(connector);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new ConnectionTestFailure(
          Reason.UNAVAILABLE, "The stored credentials cannot be decrypted.", e);
    }
  }

  /** Decrypts every {@code ENC(...)} value of a connector document. */
  @FunctionalInterface
  interface Decryptor {
    Map<String, Object> decrypt(Map<String, Object> connector) throws GeneralSecurityException;
  }
}

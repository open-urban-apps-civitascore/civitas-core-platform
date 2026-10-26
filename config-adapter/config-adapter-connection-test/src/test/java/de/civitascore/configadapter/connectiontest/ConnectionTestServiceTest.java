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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.connectiontest.ConnectionTestService.Decryptor;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Outcome;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectionTestServiceTest {

  private static final String PASSWORD = "p4ssw0rd-plain";

  private final AtomicInteger probes = new AtomicInteger();
  private final AtomicReference<Map<String, Object>> probed = new AtomicReference<>();
  private final AtomicReference<String> probedApiCall = new AtomicReference<>();
  private final AtomicReference<Boolean> probedApplyWhere = new AtomicReference<>();

  private static final Decryptor IDENTITY = connector -> connector;

  /** Replaces every {@code ENC(x)} value by {@code PASSWORD}, at any depth. */
  private static Decryptor decrypting() {
    return connector -> decryptDocument(connector);
  }

  private static Map<String, Object> decryptDocument(Map<String, Object> document) {
    Map<String, Object> result = new HashMap<>();
    document.forEach(
        (key, value) -> {
          if (value instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) nested;
            result.put(key, decryptDocument(typed));
          } else if (value instanceof String text && text.startsWith("ENC(")) {
            result.put(key, PASSWORD);
          } else {
            result.put(key, value);
          }
        });
    return result;
  }

  private ConnectionProber prober(String type, ProbeBehaviour behaviour) {
    return new ConnectionProber() {
      @Override
      public String connectionType() {
        return type;
      }

      @Override
      public ProbeResult probe(
          Map<String, Object> connector,
          String apiCall,
          boolean applyWhere,
          Cancellation cancellation)
          throws ConnectionTestFailure {
        probes.incrementAndGet();
        probed.set(connector);
        probedApiCall.set(apiCall);
        probedApplyWhere.set(applyWhere);
        return behaviour.probe(connector);
      }
    };
  }

  @FunctionalInterface
  private interface ProbeBehaviour {
    ProbeResult probe(Map<String, Object> connector) throws ConnectionTestFailure;
  }

  private ConnectionTestService service(Decryptor decryptor) {
    return new ConnectionTestService(
        List.of(prober("sql", connector -> ProbeResult.success("Connected.", "a,b\n1,2"))),
        decryptor);
  }

  private static ConnectionTestRequest request(Map<String, Object> connector) {
    return new ConnectionTestRequest("req-1", Instant.EPOCH, connector, "call", false);
  }

  private ConnectionTestResult run(ConnectionTestService service, Map<String, Object> connector) {
    return service.test(request(connector), new Cancellation());
  }

  @Test
  void nullConnectorFails() {
    ConnectionTestResult result = run(service(IDENTITY), null);

    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals("req-1", result.requestId());
    assertEquals(Reason.INVALID_CONNECTOR, result.reason());
    assertTrue(result.message().contains("no connector"));
  }

  @Test
  void emptyConnectorFails() {
    ConnectionTestResult result = run(service(IDENTITY), Map.of());

    assertEquals(Outcome.FAILURE, result.outcome());
    assertTrue(result.message().contains("no connector"));
  }

  @Test
  void unknownConnectionTypeFailsNamingTheType() {
    ConnectionTestResult result = run(service(IDENTITY), Map.of("connectionType", "ftp"));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertTrue(result.message().contains("'ftp'"), result.message());
    assertEquals(Reason.INVALID_CONNECTOR, result.reason());
  }

  @Test
  void missingConnectionTypeFails() {
    ConnectionTestResult result = run(service(IDENTITY), Map.of("dsn", "x"));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals(0, probes.get());
  }

  @Test
  void connectionTypeIsMatchedCaseInsensitively() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(prober("sql", connector -> ProbeResult.success("ok", null))), IDENTITY);

    ConnectionTestResult result = run(service, Map.of("connectionType", " SQL "));

    assertEquals(Outcome.SUCCESS, result.outcome());
    assertEquals(1, probes.get());
  }

  @Test
  void plaintextPasswordAtTopLevelFailsWithoutProbing() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(prober("sql", connector -> ProbeResult.success("ok", null))), IDENTITY);

    ConnectionTestResult result =
        run(service, Map.of("connectionType", "sql", "password", PASSWORD));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertTrue(result.message().contains("must be encrypted"), result.message());
    assertFalse(result.message().contains(PASSWORD));
    assertEquals(Reason.INVALID_CONNECTOR, result.reason());
    assertEquals(0, probes.get());
  }

  @Test
  void plaintextPasswordNestedFailsWithoutProbing() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(prober("sql", connector -> ProbeResult.success("ok", null))), IDENTITY);

    ConnectionTestResult result =
        run(
            service,
            Map.of("connectionType", "sql", "authentication", Map.of("password", PASSWORD)));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertTrue(result.message().contains("must be encrypted"), result.message());
    assertEquals(0, probes.get());
  }

  @Test
  void encryptedPasswordPassesAndProberGetsDecryptedConnector() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(prober("sql", connector -> ProbeResult.success("ok", null))), decrypting());

    ConnectionTestResult result =
        run(
            service,
            Map.of("connectionType", "sql", "authentication", Map.of("password", "ENC(abc)")));

    assertEquals(Outcome.SUCCESS, result.outcome());
    assertEquals(Map.of("password", PASSWORD), probed.get().get("authentication"));
  }

  @Test
  void emptyPasswordIsNotTreatedAsPlaintext() {
    ConnectionTestResult result =
        run(service(IDENTITY), Map.of("connectionType", "sql", "password", " "));

    assertEquals(Outcome.SUCCESS, result.outcome());
  }

  @Test
  void decryptorFailureIsReportedWithoutProbing() {
    Decryptor failing =
        connector -> {
          throw new GeneralSecurityException("bad tag for ENC(abc)");
        };
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(prober("sql", connector -> ProbeResult.success("ok", null))), failing);

    ConnectionTestResult result =
        run(service, Map.of("connectionType", "sql", "password", "ENC(abc)"));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertTrue(result.message().contains("cannot be decrypted"), result.message());
    assertFalse(result.message().contains("bad tag"));
    assertEquals(Reason.UNAVAILABLE, result.reason());
    assertEquals(0, probes.get());
  }

  @Test
  void probeSuccessMapsOutcomeMessagePreviewAndRequestId() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(
                prober(
                    "http",
                    connector ->
                        new ProbeResult(true, "Got a response.", "{\"a\":1}", false, null, 200))),
            IDENTITY);

    ConnectionTestResult result = run(service, Map.of("connectionType", "http"));

    assertEquals("req-1", result.requestId());
    assertEquals(Outcome.SUCCESS, result.outcome());
    assertNull(result.reason());
    assertEquals("Got a response.", result.message());
    assertEquals("{\"a\":1}", result.preview());
    assertEquals(200, result.httpStatus());
    assertEquals("call", probedApiCall.get());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void theRequestDecidesWhetherTheWhereClauseApplies(boolean applyWhere) {
    ConnectionTestService service = service(IDENTITY);

    service.test(
        new ConnectionTestRequest(
            "req-1", Instant.EPOCH, Map.of("connectionType", "sql"), null, applyWhere),
        new Cancellation());

    assertEquals(applyWhere, probedApplyWhere.get());
  }

  @Test
  void unsuccessfulProbeResultIsFailureWithPreviewAndStatusKept() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(
                prober(
                    "http",
                    connector ->
                        new ProbeResult(false, "Unauthorized.", "denied", false, null, 401))),
            IDENTITY);

    ConnectionTestResult result = run(service, Map.of("connectionType", "http"));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals(Reason.SOURCE_ERROR, result.reason());
    assertEquals("Unauthorized.", result.message());
    assertEquals("denied", result.preview());
    assertEquals(401, result.httpStatus());
  }

  @Test
  void secretsAreMaskedInProbeMessageAndPreview() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(
                prober(
                    "http",
                    connector ->
                        new ProbeResult(
                            true,
                            "echo " + PASSWORD,
                            "{\"seen\":\"" + PASSWORD + "\"}",
                            false,
                            null,
                            200))),
            decrypting());

    ConnectionTestResult result =
        run(service, Map.of("connectionType", "http", "password", "ENC(abc)"));

    assertFalse(result.message().contains(PASSWORD));
    assertFalse(result.preview().contains(PASSWORD));
    assertTrue(result.message().contains("********"));
  }

  @Test
  void failureMessageContainingTheDecryptedPasswordIsMasked() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(
                prober(
                    "sql",
                    connector -> {
                      throw new ConnectionTestFailure("login failed for " + PASSWORD);
                    })),
            decrypting());

    ConnectionTestResult result =
        run(service, Map.of("connectionType", "sql", "password", "ENC(abc)"));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals("login failed for ********", result.message());
    assertEquals(Reason.SOURCE_ERROR, result.reason());
    assertNull(result.preview());
  }

  @Test
  void aReferenceInADecryptedPasswordIsRefusedLikeTheReleaseWithoutQuotingIt() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(prober("mqtt", connector -> ProbeResult.success("ok", null))),
            connector -> {
              Map<String, Object> plain = new HashMap<>(connector);
              plain.put("password", "pw-#{x}-tail");
              return plain;
            });

    ConnectionTestResult result =
        run(service, Map.of("connectionType", "mqtt", "password", "ENC(abc)"));

    assertEquals(Reason.INVALID_CONNECTOR, result.reason());
    assertTrue(result.message().contains("'password'"), result.message());
    assertFalse(result.message().contains("pw-"), result.message());
    assertEquals(0, probes.get());
  }

  @Test
  void aReferenceInAListValueIsRefusedNamingItsPlace() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(prober("mqtt", connector -> ProbeResult.success("ok", null))), IDENTITY);

    ConnectionTestResult result =
        run(service, Map.of("connectionType", "mqtt", "topics", List.of("sensors/${x}")));

    assertEquals(Reason.INVALID_CONNECTOR, result.reason());
    assertTrue(result.message().contains("'topics[0]'"), result.message());
  }

  @Test
  void runtimeExceptionFromProberIsGenericFailureWithoutExceptionText() {
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(
                prober(
                    "sql",
                    connector -> {
                      throw new IllegalStateException("leaks " + PASSWORD);
                    })),
            decrypting());

    ConnectionTestResult result =
        run(service, Map.of("connectionType", "sql", "password", "ENC(abc)"));

    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals("The test failed unexpectedly.", result.message());
    assertEquals(Reason.UNAVAILABLE, result.reason());
    assertEquals("req-1", result.requestId());
  }
}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The HTTP prober against an in-process server on the loopback interface. */
class HttpProberTest {

  private static HttpServer server;
  private static final List<HttpExchange> REQUESTS = new CopyOnWriteArrayList<>();

  /** Accepts every host: the server runs on the loopback interface the guard refuses. */
  private static final DestinationCheck ANY_HOST = host -> {};

  private final HttpProber prober = new HttpProber(ANY_HOST, Duration.ofSeconds(2));

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/stations", exchange -> respond(exchange, 200, "application/json", "[{\"id\":1}]"));
    server.createContext(
        "/secured", exchange -> respond(exchange, 401, "text/plain", "login first"));
    server.createContext(
        "/moved",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/stations");
          respond(exchange, 302, "text/plain", "");
        });
    server.createContext(
        "/large", exchange -> respond(exchange, 200, "text/plain", "ä".repeat(10_000)));
    server.createContext(
        "/archive",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/zip");
          byte[] bytes = new byte[] {0x50, 0x4b, 0x03, 0x04, 0, 0, 0};
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
          }
        });
    server.createContext("/empty", exchange -> respond(exchange, 204, "", null));
    server.createContext(
        "/scalars", exchange -> respond(exchange, 200, "application/json", "[1, 2, 3]"));
    server.createContext(
        "/gzipped",
        exchange -> {
          ByteArrayOutputStream compressed = new ByteArrayOutputStream();
          try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
          }
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.getResponseHeaders().add("Content-Encoding", "gzip");
          exchange.sendResponseHeaders(200, compressed.size());
          try (OutputStream body = exchange.getResponseBody()) {
            body.write(compressed.toByteArray());
          }
        });
    server.createContext(
        "/brotli",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.getResponseHeaders().add("Content-Encoding", "br");
          byte[] bytes = {0x1b, 0x0a, 0x00, 0x00};
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
          }
        });
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(10_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          respond(exchange, 200, "text/plain", "late");
        });
    server.createContext(
        "/latin1",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=ISO-8859-1");
          byte[] bytes = "Grüße".getBytes(StandardCharsets.ISO_8859_1);
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
          }
        });
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @BeforeEach
  void clearRequests() {
    REQUESTS.clear();
  }

  @Test
  void returnsTheStatusAndBodyOfTheNamedApiCall() throws Exception {
    ProbeResult result = probe(call("stations", "/stations"), "stations");

    assertTrue(result.success());
    assertEquals(200, result.httpStatus());
    assertEquals("[{\"id\":1}]", result.preview());
    assertEquals("The API answered with HTTP 200.", result.message());
  }

  @Test
  void buildsTheRequestAsThePipelineDoes() throws Exception {
    Map<String, Object> call = call("stations", "/stations");
    call.put(
        "queryParameters",
        List.of(parameter("q", "a b&c=d+e/ä"), parameter("limit", "10"), parameter("empty", null)));
    call.put("headers", List.of(parameter("Accept", "application/json"), parameter("X-Key", "k")));
    Map<String, Object> connector = connector(call);
    connector.put("baseUrl", baseUrl() + "/");

    probe(connector, "stations");

    HttpExchange request = REQUESTS.get(0);
    assertEquals("GET", request.getRequestMethod());
    assertEquals(
        "/stations?q=a%20b%26c%3Dd%2Be%2F%C3%A4&limit=10&empty=",
        request.getRequestURI().getRawPath() + "?" + request.getRequestURI().getRawQuery());
    assertEquals("application/json", request.getRequestHeaders().getFirst("Accept"));
    assertEquals("k", request.getRequestHeaders().getFirst("X-Key"));
  }

  @Test
  void aStatusOutside2xxIsAFailureThatKeepsStatusAndBody() throws Exception {
    ProbeResult result = probe(call("secured", "/secured"), "secured");

    assertFalse(result.success());
    assertEquals(401, result.httpStatus());
    assertEquals("login first", result.preview());
  }

  @Test
  void doesNotFollowARedirect() throws Exception {
    ProbeResult result = probe(call("moved", "/moved"), "moved");

    assertFalse(result.success());
    assertEquals(302, result.httpStatus());
    assertTrue(result.message().contains("does not follow redirects"));
    assertEquals(1, REQUESTS.size(), "only the redirecting request was sent");
  }

  @Test
  void keepsTheStartOfALargeBodyAndSaysItIsTruncated() throws Exception {
    ProbeResult result = probe(call("large", "/large"), "large");

    assertTrue(result.truncated());
    assertTrue(result.preview().length() <= ResultText.MAX_PREVIEW_BYTES);
    assertTrue(result.preview().startsWith("ää"));
  }

  @Test
  void describesABinaryBodyInsteadOfShowingItAndFailsItAsThePipelineReadsJsonOnly()
      throws Exception {
    ProbeResult result = probe(call("archive", "/archive"), "archive");

    assertFalse(result.success());
    assertEquals(200, result.httpStatus());
    assertTrue(result.message().contains("not JSON"), result.message());
    assertEquals("(7 bytes of application/zip, not shown)", result.preview());
  }

  @Test
  void jsonWithoutRecordsSucceedsAndSaysThePipelineReadsNothingFromIt() throws Exception {
    ProbeResult result = probe(call("scalars", "/scalars"), "scalars");

    assertTrue(result.success());
    assertTrue(result.message().contains("reads no record"), result.message());
  }

  @Test
  void anEmptyBodyHasNoPreview() throws Exception {
    ProbeResult result = probe(call("empty", "/empty"), "empty");

    assertTrue(result.success());
    assertEquals(204, result.httpStatus());
    assertNull(result.preview());
  }

  @Test
  void showsAGzipEncodedBodyButFailsItAsThePipelineDoesNotUnpackIt() throws Exception {
    ProbeResult result = probe(call("gzipped", "/gzipped"), "gzipped");

    assertEquals("{\"ok\":true}", result.preview());
    assertFalse(result.success());
    assertTrue(result.message().contains("compressed (gzip)"), result.message());
  }

  @Test
  void describesABodyInAnEncodingItCannotDecode() throws Exception {
    ProbeResult result = probe(call("brotli", "/brotli"), "brotli");

    assertEquals("(4 bytes encoded as br, not shown)", result.preview());
    assertFalse(result.success());
  }

  @Test
  void masksTheBasicCredentialAnEchoingApiReturns(@TempDir Path dir) throws Exception {
    ProbeResult result = probeEchoingApi(dir, "");

    assertTrue(result.success(), result.message());
    assertEquals(
        "Basic dXNlcjpzM2NyZXQ=", REQUESTS.get(0).getRequestHeaders().getFirst("Authorization"));
    assertEquals("{\"pad\": \"\", \"Authorization\": \"Basic ********\"}", result.preview());
  }

  /**
   * The credential starts 10 bytes before the preview limit, so a cut there leaves "Basic dXNl".
   */
  @Test
  void masksAnEchoedCredentialThatCrossesThePreviewLimit(@TempDir Path dir) throws Exception {
    int before = "{\"pad\": \"".length() + "\", \"Authorization\": \"".length();
    String pad = "x".repeat(ResultText.MAX_PREVIEW_BYTES - 10 - before);

    ProbeResult result = probeEchoingApi(dir, pad);

    assertTrue(
        result.preview().contains("\"Authorization\": \"Basic ********\""), result.message());
    assertFalse(result.preview().contains("dXNl"));
  }

  @Test
  void basicAuthenticationAcceptsAnUpperCaseHttpsScheme() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    Map<String, Object> connector = connector(call("root", "/"));
    connector.put(
        "baseUrl",
        "HTTPS://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + closedPort);
    connector.put("authentication", Map.of("type", "BASIC", "username", "u", "password", "p"));

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () -> prober.probe(connector, "root", false, new Cancellation()));

    assertEquals(Reason.SOURCE_ERROR, failure.reason(), failure.getMessage());
  }

  /** Probes an HTTPS API that answers with the Authorization header it received. */
  private ProbeResult probeEchoingApi(Path dir, String pad) throws Exception {
    SSLContext tls = selfSignedTls(dir);
    HttpsServer echo =
        HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    echo.setHttpsConfigurator(new HttpsConfigurator(tls));
    echo.createContext(
        "/headers",
        exchange ->
            respond(
                exchange,
                200,
                "application/json",
                "{\"pad\": \""
                    + pad
                    + "\", \"Authorization\": \""
                    + exchange.getRequestHeaders().getFirst("Authorization")
                    + "\"}"));
    echo.start();
    Map<String, Object> connector = connector(call("headers", "/headers"));
    connector.put(
        "baseUrl",
        "https://"
            + InetAddress.getLoopbackAddress().getHostAddress()
            + ":"
            + echo.getAddress().getPort());
    connector.put(
        "authentication", Map.of("type", "BASIC", "username", "user", "password", "s3cret"));
    SSLContext jvmDefault = SSLContext.getDefault();
    // The prober trusts the JVM's default context, so the test certificate must be in it.
    SSLContext.setDefault(tls);
    try {
      return prober.probe(connector, "headers", false, new Cancellation());
    } finally {
      SSLContext.setDefault(jvmDefault);
      echo.stop(0);
    }
  }

  @Test
  void decodesTheBodyInItsDeclaredCharset() throws Exception {
    ProbeResult result = probe(call("latin1", "/latin1"), "latin1");

    assertEquals("Grüße", result.preview());
    assertTrue(result.message().contains("not JSON"), result.message());
  }

  @Test
  void aSlowApiEndsAtTheClientTimeout() {
    long begin = System.nanoTime();

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(call("slow", "/slow"), "slow"));

    assertEquals("The API did not answer in time.", failure.getMessage());
    assertEquals(Reason.TIMEOUT, failure.reason());
    assertTrue(Duration.ofNanos(System.nanoTime() - begin).toSeconds() < 5);
  }

  @Test
  void refusesAuthenticationOverHttp() {
    Map<String, Object> connector = connector(call("stations", "/stations"));
    connector.put("authentication", Map.of("type", "BASIC", "username", "u", "password", "secret"));

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(connector, "stations"));

    assertEquals("Authentication needs an https base URL.", failure.getMessage());
    assertEquals(Reason.INVALID_CONNECTOR, failure.reason());
    assertTrue(REQUESTS.isEmpty());
  }

  @Test
  void refusesAHeaderTheClientCannotSendWithoutQuotingIt() {
    Map<String, Object> call = call("stations", "/stations");
    call.put("headers", List.of(parameter("Bad Header", "secret-value")));

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(connector(call), "stations"));

    assertEquals("The API call has a header that cannot be sent.", failure.getMessage());
  }

  @Test
  void refusesAnUnknownOrMissingApiCall() {
    ConnectionTestFailure unknown =
        assertThrows(
            ConnectionTestFailure.class, () -> probe(call("stations", "/stations"), "other"));
    ConnectionTestFailure missing =
        assertThrows(ConnectionTestFailure.class, () -> probe(call("stations", "/stations"), null));

    assertEquals("The connector has no API call named 'other'.", unknown.getMessage());
    assertEquals("The test names no API call.", missing.getMessage());
  }

  @Test
  void refusesABaseUrlThatIsNotHttp() {
    Map<String, Object> connector = connector(call("stations", "/stations"));
    connector.put("baseUrl", "ftp://example.org");

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(connector, "stations"));

    assertEquals("The base URL must be an http or https URL with a host.", failure.getMessage());
  }

  @Test
  void asksTheDestinationCheckBeforeConnecting() {
    HttpProber guarded =
        new HttpProber(
            host -> {
              throw new ConnectionTestFailure("refused " + host);
            },
            Duration.ofSeconds(2));

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () ->
                guarded.probe(
                    connector(call("stations", "/stations")),
                    "stations",
                    false,
                    new Cancellation()));

    assertEquals(
        "refused " + InetAddress.getLoopbackAddress().getHostAddress(), failure.getMessage());
    assertTrue(REQUESTS.isEmpty());
  }

  @Test
  void namesARefusedConnectionWithoutTheClientText() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    Map<String, Object> connector = connector(call("root", "/"));
    connector.put(
        "baseUrl",
        "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + closedPort);

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(connector, "root"));

    assertEquals("The API did not answer: The connection was refused.", failure.getMessage());
  }

  /** The chain the JDK's HTTP client builds for a connect timeout. */
  @Test
  void aConnectTimeoutOfTheClientIsATimeout() {
    HttpConnectTimeoutException timeout = new HttpConnectTimeoutException("HTTP connect timed out");
    timeout.initCause(new ConnectException("HTTP connect timed out"));

    ConnectionTestFailure failure = HttpProber.failure("The API", new ExecutionException(timeout));

    assertEquals(Reason.TIMEOUT, failure.reason());
    assertEquals("The API did not answer: The connection timed out.", failure.getMessage());
  }

  @Test
  void namesAnUntrustedCertificateWithoutTheClientText(@TempDir Path dir) throws Exception {
    HttpsServer untrusted =
        HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    untrusted.setHttpsConfigurator(new HttpsConfigurator(selfSignedTls(dir)));
    untrusted.start();
    try {
      Map<String, Object> connector = connector(call("root", "/"));
      connector.put(
          "baseUrl",
          "https://"
              + InetAddress.getLoopbackAddress().getHostAddress()
              + ":"
              + untrusted.getAddress().getPort());

      ConnectionTestFailure failure =
          assertThrows(ConnectionTestFailure.class, () -> probe(connector, "root"));

      assertEquals(
          "The API did not answer: The server's certificate is not issued by a trusted"
              + " certificate authority.",
          failure.getMessage());
    } finally {
      untrusted.stop(0);
    }
  }

  private ProbeResult probe(Map<String, Object> callOrConnector, String apiCall)
      throws ConnectionTestFailure {
    Map<String, Object> connector =
        callOrConnector.containsKey("apiCalls") ? callOrConnector : connector(callOrConnector);
    return prober.probe(connector, apiCall, false, new Cancellation());
  }

  private static Map<String, Object> connector(Map<String, Object> call) {
    Map<String, Object> connector = new HashMap<>();
    connector.put("connectionType", "http");
    connector.put("baseUrl", baseUrl());
    connector.put("authentication", Map.of("type", "NONE"));
    connector.put("apiCalls", new ArrayList<>(List.of(call)));
    return connector;
  }

  private static Map<String, Object> call(String name, String path) {
    Map<String, Object> call = new HashMap<>();
    call.put("name", name);
    call.put("method", "GET");
    call.put("path", path);
    return call;
  }

  private static Map<String, Object> parameter(String name, String value) {
    Map<String, Object> parameter = new HashMap<>();
    parameter.put("name", name);
    parameter.put("value", value);
    return parameter;
  }

  /** A context that serves and trusts one self-signed certificate for the loopback address. */
  private static SSLContext selfSignedTls(Path dir) throws Exception {
    Path keystore = dir.resolve("echo.p12");
    char[] password = "changeit".toCharArray();
    Process keytool =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair",
                "-alias",
                "echo",
                "-keyalg",
                "EC",
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=ip:127.0.0.1,ip:::1",
                "-validity",
                "1",
                "-storetype",
                "PKCS12",
                "-keystore",
                keystore.toString(),
                "-storepass",
                new String(password))
            .redirectErrorStream(true)
            .start();
    String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, keytool.waitFor(), output);
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(keystore)) {
      store.load(in, password);
    }
    KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(store, password);
    TrustManagerFactory trust =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(store);
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
    return tls;
  }

  private static String baseUrl() {
    return "http://"
        + InetAddress.getLoopbackAddress().getHostAddress()
        + ":"
        + server.getAddress().getPort();
  }

  private static void respond(HttpExchange exchange, int status, String type, String body)
      throws IOException {
    REQUESTS.add(exchange);
    if (!type.isEmpty()) {
      exchange.getResponseHeaders().add("Content-Type", type);
    }
    byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}

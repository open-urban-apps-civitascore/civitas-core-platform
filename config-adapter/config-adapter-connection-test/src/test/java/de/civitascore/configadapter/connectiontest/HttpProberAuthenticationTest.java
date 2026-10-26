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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The authentications of the HTTP prober against an in-process https server on the loopback
 * interface, which serves the API and a token endpoint and asks for a client certificate.
 */
class HttpProberAuthenticationTest {

  private static final char[] STORE_PASSWORD = "changeit".toCharArray();
  private static final String ACCESS_TOKEN = "access-t0ken-from-the-endpoint";
  private static final String TRUST_STORE = "javax.net.ssl.trustStore";
  private static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
  private static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";

  private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();

  /** Accepts every host: the server runs on the loopback interface the guard refuses. */
  private static final DestinationCheck ANY_HOST = host -> {};

  @TempDir static Path dir;

  private static HttpsServer server;
  private static SSLContext jvmDefault;
  private static Map<String, String> jvmTrustStore;
  private static String clientCertificatePem;
  private static String clientKeyPem;

  private final HttpProber prober = new HttpProber(ANY_HOST, Duration.ofSeconds(2));

  @BeforeAll
  static void startServer() throws Exception {
    Path serverStore = dir.resolve("server.p12");
    keytool(
        serverStore,
        "-genkeypair",
        "-alias",
        "server",
        "-keyalg",
        "EC",
        "-dname",
        "CN=localhost",
        "-ext",
        "SAN=ip:127.0.0.1,ip:::1");
    Path clientStore = dir.resolve("client.p12");
    keytool(
        clientStore,
        "-genkeypair",
        "-alias",
        "ca",
        "-keyalg",
        "RSA",
        "-dname",
        "CN=Test CA",
        "-ext",
        "bc:c");
    keytool(
        clientStore, "-genkeypair", "-alias", "client", "-keyalg", "RSA", "-dname", "CN=client");
    Path csr = dir.resolve("client.csr");
    Path signed = dir.resolve("client.pem");
    keytool(clientStore, "-certreq", "-alias", "client", "-file", csr.toString());
    keytool(
        clientStore,
        "-gencert",
        "-alias",
        "ca",
        "-rfc",
        "-infile",
        csr.toString(),
        "-outfile",
        signed.toString());

    KeyStore serverKeys = load(serverStore);
    KeyStore clientKeys = load(clientStore);
    clientCertificatePem =
        Files.readString(signed, StandardCharsets.US_ASCII)
            + pem("CERTIFICATE", clientKeys.getCertificate("ca").getEncoded());
    clientKeyPem = pem("PRIVATE KEY", clientKeys.getKey("client", STORE_PASSWORD).getEncoded());

    server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setHttpsConfigurator(
        new HttpsConfigurator(tls(serverKeys, clientKeys)) {
          @Override
          public void configure(HttpsParameters params) {
            SSLParameters parameters = getSSLContext().getDefaultSSLParameters();
            parameters.setWantClientAuth(true);
            params.setSSLParameters(parameters);
          }
        });
    server.createContext("/api", exchange -> respond(exchange, 200, "{}"));
    server.createContext(
        "/token",
        exchange ->
            respond(
                exchange,
                200,
                "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\"}"));
    server.createContext(
        "/token-refused",
        exchange ->
            respond(
                exchange,
                401,
                "{\"error\":\"invalid_client\",\"error_description\":\"wrong secret s3cret\"}"));
    server.createContext(
        "/token-without-token", exchange -> respond(exchange, 200, "{\"token_type\":\"Bearer\"}"));
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();

    // The prober trusts the JVM's default context, and a client certificate context the default
    // trust store, so the test's server certificate must be in both.
    jvmDefault = SSLContext.getDefault();
    SSLContext.setDefault(tls(null, serverKeys));
    jvmTrustStore = new HashMap<>();
    for (String property : List.of(TRUST_STORE, TRUST_STORE_PASSWORD, TRUST_STORE_TYPE)) {
      jvmTrustStore.put(property, System.getProperty(property));
    }
    System.setProperty(TRUST_STORE, serverStore.toString());
    System.setProperty(TRUST_STORE_PASSWORD, new String(STORE_PASSWORD));
    System.setProperty(TRUST_STORE_TYPE, "PKCS12");
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
    SSLContext.setDefault(jvmDefault);
    jvmTrustStore.forEach(
        (property, value) -> {
          if (value == null) {
            System.clearProperty(property);
          } else {
            System.setProperty(property, value);
          }
        });
  }

  @BeforeEach
  void clearRequests() {
    REQUESTS.clear();
  }

  @Test
  void sendsABearerToken() throws Exception {
    ProbeResult result = probe(Map.of("type", "BEARER_TOKEN", "token", "b3arer"));

    assertTrue(result.success(), result.message());
    assertEquals("Bearer b3arer", api().authorization());
  }

  @Test
  void sendsAnApiTokenAsAHeader() throws Exception {
    ProbeResult result =
        probe(Map.of("type", "API_TOKEN", "name", "X-API-Key", "value", "k3y", "in", "HEADER"));

    assertTrue(result.success(), result.message());
    assertEquals("k3y", api().headers().get("X-api-key"));
    assertNull(api().query());
  }

  @Test
  void appendsAnApiTokenToTheQueryPercentEncoded() throws Exception {
    Map<String, Object> call = call();
    call.put("queryParameters", List.of(Map.of("name", "limit", "value", "10")));

    ProbeResult result =
        prober.probe(
            connector(
                call,
                Map.of("type", "API_TOKEN", "name", "api key", "value", "a+b/c", "in", "QUERY")),
            "api",
            false,
            new Cancellation());

    assertTrue(result.success(), result.message());
    assertEquals("limit=10&api%20key=a%2Bb%2Fc", api().query());
  }

  @Test
  void refusesAnApiTokenWithoutALocation() {
    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(Map.of("type", "API_TOKEN", "name", "key", "value", "k3y")));

    assertEquals(Reason.INVALID_CONNECTOR, failure.reason());
    assertTrue(REQUESTS.isEmpty());
  }

  @Test
  void fetchesAnOAuth2TokenWithTheClientCredentialsGrantAndSendsIt() throws Exception {
    ProbeResult result = probe(oauth2("/token", "read write"));

    assertTrue(result.success(), result.message());
    Request token = REQUESTS.get(0);
    assertEquals("POST /token", token.method() + " " + token.path());
    assertEquals(
        "Basic " + base64("my%20client:s3cr%3At"), token.authorization(), "client_secret_basic");
    assertEquals("application/x-www-form-urlencoded", token.headers().get("Content-type"));
    assertEquals("grant_type=client_credentials&scope=read%20write", token.body());
    assertEquals("Bearer " + ACCESS_TOKEN, api().authorization());
  }

  @Test
  void requestsNoScopeWhenNoneIsSet() throws Exception {
    probe(oauth2("/token", null));

    assertEquals("grant_type=client_credentials", REQUESTS.get(0).body());
  }

  /** The token endpoint's description and the client secret must stay out of the message. */
  @Test
  void aRefusedTokenRequestIsASourceErrorWithTheOAuthErrorCodeOnly() {
    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(oauth2("/token-refused", null)));

    assertEquals(Reason.SOURCE_ERROR, failure.reason());
    assertEquals(
        "The token endpoint answered with HTTP 401 (invalid_client), so the API was not called.",
        failure.getMessage());
    assertEquals(1, REQUESTS.size(), "the API was not called");
  }

  @Test
  void aTokenResponseWithoutAnAccessTokenIsASourceError() {
    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class, () -> probe(oauth2("/token-without-token", null)));

    assertEquals(Reason.SOURCE_ERROR, failure.reason());
    assertEquals(
        "The token endpoint answered with HTTP 200, but without an access token in a JSON object.",
        failure.getMessage());
  }

  @Test
  void anUnreachableTokenEndpointIsNamedLikeAnUnreachableApi() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    Map<String, Object> oauth2 = new HashMap<>(oauth2("/token", null));
    oauth2.put("tokenUrl", "https://" + loopback() + ":" + closedPort + "/token");

    ConnectionTestFailure failure = assertThrows(ConnectionTestFailure.class, () -> probe(oauth2));

    assertEquals(Reason.SOURCE_ERROR, failure.reason());
    assertEquals(
        "The token endpoint did not answer: The connection was refused.", failure.getMessage());
  }

  @Test
  void refusesATokenUrlOverHttp() {
    Map<String, Object> oauth2 = new HashMap<>(oauth2("/token", null));
    oauth2.put("tokenUrl", "http://" + loopback() + ":" + server.getAddress().getPort() + "/token");

    ConnectionTestFailure failure = assertThrows(ConnectionTestFailure.class, () -> probe(oauth2));

    assertEquals("The token URL must be an https URL with a host.", failure.getMessage());
    assertTrue(REQUESTS.isEmpty());
  }

  @Test
  void asksTheDestinationCheckForTheTokenHost() {
    HttpProber guarded =
        new HttpProber(
            host -> {
              if ("auth.example.org".equals(host)) {
                throw ConnectionTestFailure.refused("refused " + host);
              }
            },
            Duration.ofSeconds(2));
    Map<String, Object> oauth2 = new HashMap<>(oauth2("/token", null));
    oauth2.put("tokenUrl", "https://auth.example.org/token");

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () -> guarded.probe(connector(call(), oauth2), "api", false, new Cancellation()));

    assertEquals("refused auth.example.org", failure.getMessage());
    assertTrue(failure.destinationRefused());
  }

  @Test
  void refusesEveryAuthenticationOverHttp() {
    for (Map<String, Object> authentication :
        List.of(
            Map.<String, Object>of("type", "BEARER_TOKEN", "token", "t"),
            Map.<String, Object>of("type", "API_TOKEN", "name", "k", "value", "v", "in", "QUERY"),
            oauth2("/token", null),
            Map.<String, Object>of(
                "type", "CLIENT_CERTIFICATE", "certificate", "c", "privateKey", "k"))) {
      Map<String, Object> connector = connector(call(), authentication);
      connector.put("baseUrl", "http://" + loopback() + ":" + server.getAddress().getPort());

      ConnectionTestFailure failure =
          assertThrows(
              ConnectionTestFailure.class,
              () -> prober.probe(connector, "api", false, new Cancellation()));

      assertEquals("Authentication needs an https base URL.", failure.getMessage());
    }
    assertTrue(REQUESTS.isEmpty());
  }

  @Test
  void presentsTheClientCertificateWithItsChain() throws Exception {
    ProbeResult result =
        probe(
            Map.of(
                "type",
                "CLIENT_CERTIFICATE",
                "certificate",
                clientCertificatePem,
                "privateKey",
                clientKeyPem));

    assertTrue(result.success(), result.message());
    assertEquals(List.of("CN=client", "CN=Test CA"), api().clientCertificates());
  }

  @Test
  void presentsNoClientCertificateWithoutThatAuthentication() throws Exception {
    probe(Map.of("type", "NONE"));

    assertEquals(List.of(), api().clientCertificates());
  }

  @Test
  void readsAnEcPrivateKey() throws Exception {
    KeyStore server = load(dir.resolve("server.p12"));
    String certificate = pem("CERTIFICATE", server.getCertificate("server").getEncoded());
    String key = pem("PRIVATE KEY", server.getKey("server", STORE_PASSWORD).getEncoded());

    assertNotNull(ClientCertificate.tls(certificate, key));
  }

  @Test
  void refusesAPrivateKeyThatIsNotPkcs8WithoutQuotingIt() {
    String pkcs1 = clientKeyPem.replace("PRIVATE KEY", "RSA PRIVATE KEY");

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () ->
                probe(
                    Map.of(
                        "type",
                        "CLIENT_CERTIFICATE",
                        "certificate",
                        clientCertificatePem,
                        "privateKey",
                        pkcs1)));

    assertEquals(Reason.INVALID_CONNECTOR, failure.reason());
    assertTrue(failure.getMessage().startsWith("The private key must be"), failure.getMessage());
  }

  @Test
  void refusesACertificateThatIsNotPem() {
    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () ->
                probe(
                    Map.of(
                        "type",
                        "CLIENT_CERTIFICATE",
                        "certificate",
                        "not a certificate",
                        "privateKey",
                        clientKeyPem)));

    assertEquals(
        "The client certificate must be one or more PEM certificates ('BEGIN CERTIFICATE').",
        failure.getMessage());
  }

  /** The JDK client quotes a rejected header value in its exception text. */
  @Test
  void refusesATokenThatCannotBeSentWithoutQuotingIt() {
    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(Map.of("type", "BEARER_TOKEN", "token", "s3cret\r\nX: y")));

    assertEquals("The authentication cannot be sent as a header.", failure.getMessage());
  }

  @Test
  void refusesAnUnknownAuthenticationType() {
    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(Map.of("type", "DIGEST")));

    assertEquals("Authentication type 'DIGEST' cannot be tested.", failure.getMessage());
  }

  private ProbeResult probe(Map<String, Object> authentication) throws ConnectionTestFailure {
    return prober.probe(connector(call(), authentication), "api", false, new Cancellation());
  }

  private static Request api() {
    return REQUESTS.stream()
        .filter(request -> "/api".equals(request.path()))
        .findFirst()
        .orElseThrow();
  }

  private static Map<String, Object> oauth2(String path, String scope) {
    Map<String, Object> oauth2 = new HashMap<>();
    oauth2.put("type", "OAUTH2");
    oauth2.put("tokenUrl", "https://" + loopback() + ":" + server.getAddress().getPort() + path);
    oauth2.put("clientId", "my client");
    oauth2.put("clientSecret", "s3cr:t");
    oauth2.put("scope", scope);
    return oauth2;
  }

  private static Map<String, Object> connector(
      Map<String, Object> call, Map<String, Object> authentication) {
    Map<String, Object> connector = new HashMap<>();
    connector.put("connectionType", "http");
    connector.put("baseUrl", "https://" + loopback() + ":" + server.getAddress().getPort());
    connector.put("authentication", authentication);
    connector.put("apiCalls", new ArrayList<>(List.of(call)));
    return connector;
  }

  private static Map<String, Object> call() {
    Map<String, Object> call = new HashMap<>();
    call.put("name", "api");
    call.put("method", "GET");
    call.put("path", "/api");
    return call;
  }

  private static String loopback() {
    return InetAddress.getLoopbackAddress().getHostAddress();
  }

  private static String base64(String text) {
    return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  private static String pem(String label, byte[] der) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
        + "\n-----END "
        + label
        + "-----\n";
  }

  /** Server keys from {@code keys}, if any, and every certificate of {@code trusted} as anchor. */
  private static SSLContext tls(KeyStore keys, KeyStore trusted) throws Exception {
    KeyManagerFactory keyManagers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    if (keys != null) {
      keyManagers.init(keys, STORE_PASSWORD);
    }
    TrustManagerFactory trust =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(trusted);
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(keys == null ? null : keyManagers.getKeyManagers(), trust.getTrustManagers(), null);
    return tls;
  }

  private static KeyStore load(Path file) throws Exception {
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(file)) {
      store.load(in, STORE_PASSWORD);
    }
    return store;
  }

  private static void keytool(Path store, String... arguments) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
    command.addAll(List.of(arguments));
    command.addAll(
        List.of(
            "-validity",
            "1",
            "-storetype",
            "PKCS12",
            "-keystore",
            store.toString(),
            "-storepass",
            new String(STORE_PASSWORD)));
    Process keytool = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, keytool.waitFor(), output);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    REQUESTS.add(Request.of(exchange));
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  /** What the server received, read while the exchange is open. */
  private record Request(
      String method,
      String path,
      String query,
      Map<String, String> headers,
      String body,
      List<String> clientCertificates) {

    String authorization() {
      return headers.get("Authorization");
    }

    static Request of(HttpExchange exchange) throws IOException {
      Map<String, String> headers = new HashMap<>();
      exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.get(0)));
      List<String> certificates = new ArrayList<>();
      try {
        for (Certificate certificate :
            ((HttpsExchange) exchange).getSSLSession().getPeerCertificates()) {
          certificates.add(((X509Certificate) certificate).getSubjectX500Principal().getName());
        }
      } catch (SSLPeerUnverifiedException noClientCertificate) {
        certificates.clear();
      }
      return new Request(
          exchange.getRequestMethod(),
          exchange.getRequestURI().getRawPath(),
          exchange.getRequestURI().getRawQuery(),
          headers,
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
          certificates);
    }
  }
}

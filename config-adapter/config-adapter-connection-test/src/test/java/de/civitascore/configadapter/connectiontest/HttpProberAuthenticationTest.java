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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
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
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import org.apache.nifi.security.ssl.PemPrivateKeyCertificateKeyStoreBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
  private static final String KEY_FORMAT =
      "The private key must be an unencrypted PEM key that starts on the first line: PKCS#8 ('BEGIN"
          + " PRIVATE KEY') of type RSA, EC or Ed25519, or PKCS#1 ('BEGIN RSA PRIVATE KEY').";

  private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();

  /** Accepts every host: the server runs on the loopback interface the guard refuses. */
  private static final DestinationCheck ANY_HOST = host -> {};

  @TempDir static Path dir;

  /** What {@code /scripted} answers; each test starts with an empty 200. */
  private static volatile Answer scripted;

  private static HttpsServer server;
  private static KeyStore serverKeys;
  private static SSLContext jvmDefault;
  private static Map<String, String> jvmTrustStore;
  private static String clientCertificatePem;
  private static String clientKeyPem;
  private static Map<String, Key> keys;

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
    Path ed25519Store = dir.resolve("ed25519.p12");
    keytool(ed25519Store, "-genkeypair", "-alias", "ed", "-keyalg", "Ed25519", "-dname", "CN=ed");
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

    serverKeys = load(serverStore);
    KeyStore clientKeys = load(clientStore);
    clientCertificatePem =
        Files.readString(signed, StandardCharsets.US_ASCII)
            + pem("CERTIFICATE", clientKeys.getCertificate("ca").getEncoded());
    byte[] clientKey = clientKeys.getKey("client", STORE_PASSWORD).getEncoded();
    clientKeyPem = pem("PRIVATE KEY", clientKey);
    keys = keys(clientKey, serverKeys, load(ed25519Store));

    server = https(tls(serverKeys, clientKeys), parameters -> parameters.setWantClientAuth(true));
    server.createContext("/api", exchange -> respond(exchange, 200, "{}"));
    server.createContext(
        "/echo",
        exchange ->
            respond(
                exchange,
                200,
                "{\"Authorization\": \""
                    + exchange.getRequestHeaders().getFirst("Authorization")
                    + "\"}"));
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
        "/echo-query",
        exchange -> {
          String query = exchange.getRequestURI().getRawQuery();
          respond(
              exchange,
              200,
              "{\"raw\": \""
                  + query
                  + "\", \"decoded\": \""
                  + URLDecoder.decode(query, StandardCharsets.UTF_8)
                  + "\"}");
        });
    server.createContext(
        "/scripted",
        exchange -> {
          Answer answer = scripted;
          if (answer.location() != null) {
            exchange.getResponseHeaders().add("Location", answer.location());
          }
          respond(exchange, answer.status(), answer.body());
        });
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
    scripted = new Answer(200, "{}", null);
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
  void postsTheClientCredentialsGrantAndSendsTheAccessToken() throws Exception {
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
  void masksTheAccessTokenThatAnApiEchoes() throws Exception {
    Map<String, Object> call = call();
    call.put("path", "/echo");

    ProbeResult result =
        prober.probe(connector(call, oauth2("/token", null)), "api", new Cancellation());

    assertTrue(result.success(), result.message());
    assertEquals("{\"Authorization\": \"Bearer ********\"}", result.preview());
  }

  @Test
  void masksAnApiTokenInTheQueryThatAnApiEchoes() throws Exception {
    Map<String, Object> call = call();
    call.put("path", "/echo-query");

    ProbeResult result =
        prober.probe(
            connector(
                call, Map.of("type", "API_TOKEN", "name", "key", "value", "a+b/c", "in", "QUERY")),
            "api",
            new Cancellation());

    assertTrue(result.success(), result.message());
    assertEquals("{\"raw\": \"key=********\", \"decoded\": \"key=********\"}", result.preview());
  }

  @ParameterizedTest
  @MethodSource("refusals")
  void aRefusedCredentialFailsWithTheStatus(String method, int status) throws Exception {
    scripted = new Answer(status, "{}", null);
    Map<String, Object> call = call();
    call.put("path", "/scripted");

    ProbeResult result =
        prober.probe(connector(call, authentication(method)), "api", new Cancellation());

    assertFalse(result.success());
    assertEquals(status, result.httpStatus());
    assertEquals("The API answered with HTTP " + status + ".", result.message());
  }

  @Test
  void requestsNoScopeWhenNoneIsSet() throws Exception {
    probe(oauth2("/token", null));

    assertEquals("grant_type=client_credentials", REQUESTS.get(0).body());
  }

  @Test
  void sendsTheScopeUntrimmedAsThePipelineDoes() throws Exception {
    probe(oauth2("/token", " read "));

    assertEquals("grant_type=client_credentials&scope=%20read%20", REQUESTS.get(0).body());
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

  @ParameterizedTest
  @ValueSource(ints = {500, 408, 429})
  void aTemporaryErrorOfTheTokenEndpointCallsNoApi(int status) {
    scripted = new Answer(status, "{}", null);

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(oauth2("/scripted", null)));

    assertEquals(Reason.SOURCE_ERROR, failure.reason());
    assertEquals(
        "The token endpoint answered with HTTP " + status + ", so the API was not called.",
        failure.getMessage());
    assertEquals(1, REQUESTS.size(), "the API was not called");
  }

  @Test
  void aRedirectOfTheTokenEndpointIsNotFollowed() {
    scripted = new Answer(302, "", oauth2("/token", null).get("tokenUrl").toString());

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(oauth2("/scripted", null)));

    assertEquals(
        "The token endpoint answered with HTTP 302, so the API was not called.",
        failure.getMessage());
    assertEquals(List.of("/scripted"), REQUESTS.stream().map(Request::path).toList());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"token_type\":\"Bearer\"}",
        "{}",
        "[]",
        "not JSON",
        "",
        "{\"access_token\":\" \"}",
        "{\"access_token\":42}"
      })
  void aTokenResponseWithoutAnAccessTokenIsASourceError(String body) {
    scripted = new Answer(200, body, null);

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(oauth2("/scripted", null)));

    assertEquals(Reason.SOURCE_ERROR, failure.reason());
    assertEquals(
        "The token endpoint answered with HTTP 200, but without an access token in a JSON object.",
        failure.getMessage());
  }

  @Test
  void readsAtMostTheFirst64KiBOfTheTokenResponse() {
    scripted =
        new Answer(
            200,
            "{\"padding\":\""
                + "x".repeat(ClientCredentialsGrant.MAX_RESPONSE_BYTES)
                + "\",\"access_token\":\"t\"}",
            null);

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(oauth2("/scripted", null)));

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

  @ParameterizedTest
  @ValueSource(strings = {"?realm=x", "#x"})
  void refusesATokenUrlWithAQueryOrAFragment(String suffix) {
    Map<String, Object> oauth2 = new HashMap<>(oauth2("/token", null));
    oauth2.put("tokenUrl", oauth2.get("tokenUrl") + suffix);

    ConnectionTestFailure failure = assertThrows(ConnectionTestFailure.class, () -> probe(oauth2));

    assertEquals(Reason.INVALID_CONNECTOR, failure.reason());
    assertEquals("The token URL must not contain a query or a fragment.", failure.getMessage());
    assertTrue(REQUESTS.isEmpty());
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
            () -> guarded.probe(connector(call(), oauth2), "api", new Cancellation()));

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
              () -> prober.probe(connector, "api", new Cancellation()));

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
  void aRefusedClientCertificateInTheHandshakeIsFatal() throws Exception {
    HttpsServer refusing =
        https(
            tls(serverKeys, serverKeys),
            parameters -> {
              parameters.setProtocols(new String[] {"TLSv1.2"});
              parameters.setNeedClientAuth(true);
            });
    refusing.createContext("/api", exchange -> respond(exchange, 200, "{}"));
    refusing.start();
    try {
      Map<String, Object> connector =
          connector(
              call(),
              Map.of(
                  "type",
                  "CLIENT_CERTIFICATE",
                  "certificate",
                  clientCertificatePem,
                  "privateKey",
                  clientKeyPem));
      connector.put("baseUrl", "https://" + loopback() + ":" + refusing.getAddress().getPort());

      ConnectionTestFailure failure =
          assertThrows(
              ConnectionTestFailure.class,
              () -> prober.probe(connector, "api", new Cancellation()));

      assertEquals(Reason.SOURCE_ERROR, failure.reason());
      assertEquals(
          "The API did not answer: The TLS handshake with the server failed.",
          failure.getMessage());
      assertTrue(hasCause(failure, SSLHandshakeException.class));
      assertTrue(REQUESTS.isEmpty());
    } finally {
      refusing.stop(0);
    }
  }

  @Test
  void readsAPkcs8RsaKey() throws Exception {
    assertNotNull(tls(keys.get("PKCS#8 RSA")));
  }

  @Test
  void readsAPkcs8EcKey() throws Exception {
    assertNotNull(tls(keys.get("PKCS#8 EC")));
  }

  @Test
  void readsAPkcs8Ed25519Key() throws Exception {
    assertNotNull(tls(keys.get("PKCS#8 Ed25519")));
  }

  @Test
  void readsAPkcs1RsaKey() throws Exception {
    assertNotNull(tls(keys.get("PKCS#1 RSA")));
  }

  @Test
  void refusesAnEncryptedPkcs8Key() {
    assertRefused(keys.get("encrypted PKCS#8 RSA"));
  }

  @Test
  void refusesASec1EcKey() {
    assertRefused(keys.get("SEC1 EC"));
  }

  @Test
  void refusesAKeyThatDoesNotStartOnTheFirstLine() {
    assertRefused(keys.get("PKCS#8 RSA after an empty line"));
  }

  /** The connection test promises what the pipeline does, so it must not accept more or less. */
  @ParameterizedTest
  @MethodSource("keyNames")
  void acceptsTheSameKeysAsNiFi(String name) {
    Key key = keys.get(name);
    boolean nifiAccepts;
    try {
      new PemPrivateKeyCertificateKeyStoreBuilder()
          .certificateInputStream(ascii(key.certificate()))
          .privateKeyInputStream(ascii(key.privateKey()))
          .build();
      nifiAccepts = true;
    } catch (RuntimeException e) {
      nifiAccepts = false;
    }
    boolean testAccepts;
    try {
      tls(key);
      testAccepts = true;
    } catch (ConnectionTestFailure e) {
      testAccepts = false;
    }

    assertEquals(nifiAccepts, testAccepts);
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

  private static Stream<Arguments> refusals() {
    return Stream.of(
            "BASIC",
            "BEARER_TOKEN",
            "API_TOKEN in HEADER",
            "API_TOKEN in QUERY",
            "OAUTH2",
            "CLIENT_CERTIFICATE")
        .flatMap(method -> Stream.of(Arguments.of(method, 401), Arguments.of(method, 403)));
  }

  private static Map<String, Object> authentication(String method) {
    return switch (method) {
      case "BASIC" -> Map.of("type", "BASIC", "username", "user", "password", "pa55");
      case "BEARER_TOKEN" -> Map.of("type", "BEARER_TOKEN", "token", "b3arer");
      case "API_TOKEN in HEADER" ->
          Map.of("type", "API_TOKEN", "name", "X-API-Key", "value", "k3y", "in", "HEADER");
      case "API_TOKEN in QUERY" ->
          Map.of("type", "API_TOKEN", "name", "key", "value", "k3y", "in", "QUERY");
      case "OAUTH2" -> oauth2("/token", null);
      case "CLIENT_CERTIFICATE" ->
          Map.of(
              "type",
              "CLIENT_CERTIFICATE",
              "certificate",
              clientCertificatePem,
              "privateKey",
              clientKeyPem);
      default -> throw new IllegalArgumentException(method);
    };
  }

  private static Stream<String> keyNames() {
    return keys.keySet().stream().sorted();
  }

  /** Each key with a certificate of its public key, named by its format. */
  private static Map<String, Key> keys(byte[] rsa, KeyStore ec, KeyStore ed25519) throws Exception {
    byte[] ecKey = ec.getKey("server", STORE_PASSWORD).getEncoded();
    String ecCertificate = pem("CERTIFICATE", ec.getCertificate("server").getEncoded());
    Map<String, Key> keys = new HashMap<>();
    keys.put("PKCS#8 RSA", new Key(clientCertificatePem, pem("PRIVATE KEY", rsa)));
    keys.put(
        "PKCS#8 RSA with CRLF line ends",
        new Key(clientCertificatePem, pem("PRIVATE KEY", rsa).replace("\n", "\r\n")));
    keys.put("PKCS#8 RSA after an empty line", new Key(clientCertificatePem, "\n" + clientKeyPem));
    keys.put("PKCS#1 RSA", new Key(clientCertificatePem, pem("RSA PRIVATE KEY", innerKey(rsa))));
    keys.put(
        "encrypted PKCS#8 RSA",
        new Key(clientCertificatePem, pem("ENCRYPTED PRIVATE KEY", encrypted(rsa))));
    keys.put("PKCS#8 EC", new Key(ecCertificate, pem("PRIVATE KEY", ecKey)));
    keys.put("SEC1 EC", new Key(ecCertificate, pem("EC PRIVATE KEY", innerKey(ecKey))));
    keys.put(
        "PKCS#8 Ed25519",
        new Key(
            pem("CERTIFICATE", ed25519.getCertificate("ed").getEncoded()),
            pem("PRIVATE KEY", ed25519.getKey("ed", STORE_PASSWORD).getEncoded())));
    return keys;
  }

  /** The key inside a PKCS#8 PrivateKeyInfo: PKCS#1 for RSA, SEC1 for EC. */
  private static byte[] innerKey(byte[] pkcs8) {
    ByteBuffer der = ByteBuffer.wrap(pkcs8);
    derLength(der, 0x30);
    skip(der, 0x02);
    skip(der, 0x30);
    byte[] key = new byte[derLength(der, 0x04)];
    der.get(key);
    return key;
  }

  private static void skip(ByteBuffer der, int tag) {
    int length = derLength(der, tag);
    der.position(der.position() + length);
  }

  private static int derLength(ByteBuffer der, int tag) {
    assertEquals(tag, der.get() & 0xFF);
    int first = der.get() & 0xFF;
    if (first < 0x80) {
      return first;
    }
    int length = 0;
    for (int i = 0; i < (first & 0x7F); i++) {
      length = length << 8 | der.get() & 0xFF;
    }
    return length;
  }

  /** The algorithm is a JDK one that {@code EncryptedPrivateKeyInfo} can encode. */
  private static byte[] encrypted(byte[] pkcs8) throws Exception {
    String algorithm = "PBEWithSHA1AndDESede";
    Cipher cipher = Cipher.getInstance(algorithm);
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeyFactory.getInstance(algorithm).generateSecret(new PBEKeySpec(STORE_PASSWORD)),
        new PBEParameterSpec(new byte[16], 10_000));
    return new EncryptedPrivateKeyInfo(cipher.getParameters(), cipher.doFinal(pkcs8)).getEncoded();
  }

  private static SSLContext tls(Key key) throws ConnectionTestFailure {
    return ClientCertificate.tls(key.certificate(), key.privateKey());
  }

  private static void assertRefused(Key key) {
    ConnectionTestFailure failure = assertThrows(ConnectionTestFailure.class, () -> tls(key));

    assertEquals(Reason.INVALID_CONNECTOR, failure.reason());
    assertEquals(KEY_FORMAT, failure.getMessage());
  }

  private static InputStream ascii(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));
  }

  private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (type.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }

  private ProbeResult probe(Map<String, Object> authentication) throws ConnectionTestFailure {
    return prober.probe(connector(call(), authentication), "api", new Cancellation());
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

  private static HttpsServer https(SSLContext tls, Consumer<SSLParameters> clientAuthentication)
      throws IOException {
    HttpsServer https =
        HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    https.setHttpsConfigurator(
        new HttpsConfigurator(tls) {
          @Override
          public void configure(HttpsParameters params) {
            SSLParameters parameters = getSSLContext().getDefaultSSLParameters();
            clientAuthentication.accept(parameters);
            params.setSSLParameters(parameters);
          }
        });
    https.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    return https;
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
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  /** A status, a body and an optional {@code Location}. */
  private record Answer(int status, String body, String location) {}

  /** A PEM private key with the certificate of its public key. */
  private record Key(String certificate, String privateKey) {}

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

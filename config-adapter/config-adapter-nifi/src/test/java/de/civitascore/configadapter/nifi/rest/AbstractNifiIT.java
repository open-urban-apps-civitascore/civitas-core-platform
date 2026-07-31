/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.rest;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.nifi.auth.OidcClientCredentialsTokenProvider;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.awaitility.Awaitility;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Shared Testcontainers scaffolding for the real-NiFi integration tests: brings up an Apache NiFi
 * 2.9.0 container secured with OpenID Connect, a trust-all JAX-RS {@link Client} and a {@link
 * NifiRestClient} that authenticates via the OIDC client-credentials grant. A single Keycloak
 * container (started once per JVM) issues the tokens; NiFi validates them against that same
 * provider. Subclasses provide their own {@code @BeforeAll}/{@code @AfterAll} — they start any
 * IT-specific containers first, then call {@link #startNifi(int, Network)} (so NiFi can join a
 * shared network the sibling containers already sit on), and mirror the teardown with {@link
 * #stopNifi()}.
 *
 * <p>Keycloak is reached through {@code host.docker.internal} from BOTH the test JVM (via the fixed
 * published port) and the NiFi container (via the host gateway), so the token issuer string is
 * identical on both sides — a mismatch there would make NiFi reject every token.
 *
 * <p>Deliberately a plain JUnit 5 + Testcontainers base with no Spring annotations — these ITs run
 * a real NiFi via failsafe, not a Spring context.
 */
abstract class AbstractNifiIT {

  private static final String REALM = "nifi-test";
  protected static final String OIDC_CLIENT_ID = "nifi";
  protected static final String OIDC_CLIENT_SECRET = "nifi-test-secret";
  // NiFi maps a bearer (client-credentials) token to its 'sub' claim — the service account's
  // Keycloak
  // user id, pinned in nifi-test-realm.json so it is a known value here. This is NiFi's initial
  // admin; the config-adapter then self-provisions its own root-canvas policies on first deploy.
  protected static final String OIDC_ADMIN_IDENTITY = "a11ce55a-0000-4000-8000-000000000001";
  // Fixed so the issuer URL is known before NiFi starts and is byte-for-byte identical on host and
  // container side (see class javadoc). host.docker.internal resolves on the host and, with the
  // host-gateway extra host, inside the NiFi container.
  private static final int KEYCLOAK_PORT = 8098;
  // The token issuer, pinned via Keycloak's KC_HOSTNAME so it is identical no matter which host the
  // token is fetched from. NiFi (in a container) reaches Keycloak here via the host gateway.
  private static final String ISSUER_HOST = "host.docker.internal:" + KEYCLOAK_PORT;
  private static final String DISCOVERY_URL =
      "http://" + ISSUER_HOST + "/realms/" + REALM + "/.well-known/openid-configuration";

  // secure.sh (AUTH=oidc) requires an explicit TLS keystore/truststore — unlike single-user mode it
  // does not auto-generate one. A throwaway self-signed pair is generated once per JVM and copied
  // into each NiFi container. The client trusts all certs, so the cert's identity is irrelevant.
  private static final String KEYSTORE_PASSWORD = "changeit";
  private static Path certsDir;

  @SuppressWarnings("resource")
  private static final GenericContainer<?> KEYCLOAK;

  /** Whether the shared Keycloak + keystores came up; ITs {@code assumeTrue} on this to skip. */
  private static boolean infraReady;

  static {
    // Awaitility's poll delay defaults to the poll interval, delaying the first condition check.
    // Zeroing it lets conditions that already hold return immediately.
    Awaitility.setDefaultPollDelay(Duration.ZERO);

    KEYCLOAK =
        new FixedHostPortGenericContainer<>(TestContainerImages.KEYCLOAK)
            .withFixedExposedPort(KEYCLOAK_PORT, 8080)
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HOSTNAME_STRICT", "false")
            // Pin the issuer to the exact host:port both sides use, regardless of request host.
            .withEnv("KC_HOSTNAME", "http://" + ISSUER_HOST)
            .withCopyToContainer(
                MountableFile.forClasspathResource("nifi-test-realm.json"),
                "/opt/keycloak/data/import/nifi-test-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(
                Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));
    if (DockerClientFactory.instance().isDockerAvailable()) {
      try {
        KEYCLOAK.start();
        Runtime.getRuntime().addShutdownHook(new Thread(KEYCLOAK::stop));
        certsDir = generateKeystores();
        infraReady = true;
      } catch (RuntimeException e) {
        // e.g. the fixed Keycloak port (8098) is already in use. Fail soft so these ITs skip
        // cleanly via assumeTrue(dockerAvailable()) instead of aborting class initialization with
        // an ExceptionInInitializerError that fails every test in the class. But do NOT swallow it
        // silently — log loudly so a skipped run is diagnosable (not mistaken for "Docker absent").
        infraReady = false;
        System.err.println(
            "NiFi IT infrastructure (Keycloak/keystore) failed to start — all NiFi ITs in this JVM"
                + " will be SKIPPED. Cause:");
        e.printStackTrace();
      }
    }
  }

  /**
   * Generates a self-signed PKCS12 keystore + matching truststore for NiFi's HTTPS connector. The
   * cert's SAN must include the host the client connects to, or NiFi 2.x rejects the TLS handshake
   * with HTTP 400 "Invalid SNI". The client uses {@link #dockerHost} (localhost locally, {@code
   * docker} in GitLab DinD, or an IP), so that host is resolved here and added to the SAN.
   */
  private static Path generateKeystores() {
    try {
      dockerHost = DockerClientFactory.instance().dockerHostIpAddress();
      String san = "SAN=dns:localhost,dns:host.docker.internal,ip:127.0.0.1";
      if (!dockerHost.equals("localhost") && !dockerHost.equals("127.0.0.1")) {
        san +=
            dockerHost.matches("\\d{1,3}(\\.\\d{1,3}){3}")
                ? ",ip:" + dockerHost
                : ",dns:" + dockerHost;
      }
      Path dir = Files.createTempDirectory("nifi-oidc-certs");
      Path keystore = dir.resolve("keystore.p12");
      Path truststore = dir.resolve("truststore.p12");
      Path cert = dir.resolve("nifi.crt");
      String keytool = System.getProperty("java.home") + "/bin/keytool";
      runKeytool(
          keytool,
          "-genkeypair",
          "-alias",
          "nifi",
          "-keyalg",
          "RSA",
          "-keysize",
          "2048",
          "-validity",
          "3650",
          "-dname",
          "CN=nifi",
          "-storetype",
          "PKCS12",
          "-keystore",
          keystore.toString(),
          "-storepass",
          KEYSTORE_PASSWORD,
          "-keypass",
          KEYSTORE_PASSWORD,
          "-ext",
          san);
      runKeytool(
          keytool,
          "-exportcert",
          "-alias",
          "nifi",
          "-keystore",
          keystore.toString(),
          "-storepass",
          KEYSTORE_PASSWORD,
          "-rfc",
          "-file",
          cert.toString());
      runKeytool(
          keytool,
          "-importcert",
          "-alias",
          "nifi",
          "-keystore",
          truststore.toString(),
          "-storetype",
          "PKCS12",
          "-storepass",
          KEYSTORE_PASSWORD,
          "-noprompt",
          "-file",
          cert.toString());
      return dir;
    } catch (Exception e) {
      throw new IllegalStateException("failed to generate NiFi OIDC keystores", e);
    }
  }

  protected static void runKeytool(String... cmd) throws Exception {
    Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("keytool timed out");
    }
    if (process.exitValue() != 0) {
      throw new IllegalStateException(
          "keytool failed: " + new String(process.getInputStream().readAllBytes()));
    }
  }

  protected static FixedHostPortGenericContainer<?> nifi;
  protected static Client httpClient;
  protected static NifiRestClient client;

  /**
   * Host on which published container ports are reachable. In CI the Docker daemon is remote
   * (DinD), so this resolves to the Docker host rather than localhost.
   */
  protected static String dockerHost;

  protected final ObjectMapper mapper = new ObjectMapper();

  /**
   * Whether these ITs can run: a Docker daemon is available AND the shared Keycloak + keystores
   * started. ITs {@code assumeTrue} on this so a missing daemon or an occupied fixed port skips
   * them cleanly rather than erroring.
   */
  protected static boolean dockerAvailable() {
    return DockerClientFactory.instance().isDockerAvailable() && infraReady;
  }

  /** Starts a standalone NiFi (no shared network, no extra container customization). */
  protected static void startNifi(int hostPort) {
    startNifi(hostPort, null, container -> {});
  }

  /** Starts NiFi joined to the given shared Docker network. */
  protected static void startNifi(int hostPort, Network network) {
    startNifi(hostPort, network, container -> {});
  }

  /**
   * Starts NiFi on the given fixed host port, wires up the trust-all HTTP client and a {@link
   * NifiRestClient}, then polls until it authenticates. Pass a {@code network} to place NiFi on a
   * shared Docker network (so sibling containers can reach it under {@code
   * https://<dockerHost>:<hostPort>} and NiFi can reach them by alias); pass {@code null} for a
   * standalone NiFi. {@code customizer} runs after network/env wiring and before start, for
   * IT-specific tweaks such as mounting a JDBC driver.
   */
  protected static void startNifi(
      int hostPort, Network network, Consumer<FixedHostPortGenericContainer<?>> customizer) {
    // In CI the Docker daemon is remote (DinD), so published ports are reachable on the resolved
    // Docker host, not localhost. Use that host both for the client URL and for NiFi's
    // proxy-host whitelist (Host-header check), so authenticate() does not get a connection
    // refused.
    dockerHost = DockerClientFactory.instance().dockerHostIpAddress();

    nifi =
        new FixedHostPortGenericContainer<>(TestContainerImages.NIFI)
            .withFixedExposedPort(hostPort, 8443)
            // Secure NiFi with OpenID Connect against the shared Keycloak; the config-adapter
            // authenticates as the 'nifi' service account (client-credentials grant).
            .withEnv("AUTH", "oidc")
            .withEnv("NIFI_WEB_HTTPS_PORT", "8443")
            .withEnv("NIFI_WEB_PROXY_HOST", dockerHost + ":" + hostPort + ",localhost:" + hostPort)
            .withEnv("NIFI_SECURITY_USER_OIDC_DISCOVERY_URL", DISCOVERY_URL)
            .withEnv("NIFI_SECURITY_USER_OIDC_CLIENT_ID", OIDC_CLIENT_ID)
            .withEnv("NIFI_SECURITY_USER_OIDC_CLIENT_SECRET", OIDC_CLIENT_SECRET)
            // NiFi identifies a bearer token by its 'sub' claim, so the initial admin must be the
            // service account's Keycloak user id, not its username (claim.identifying.user only
            // affects the unused browser-login flow).
            .withEnv("INITIAL_ADMIN_IDENTITY", OIDC_ADMIN_IDENTITY)
            // Let NiFi resolve host.docker.internal (Keycloak's issuer host) via the host gateway.
            .withExtraHost("host.docker.internal", "host-gateway")
            // AUTH=oidc requires an explicit keystore/truststore (no auto-generation).
            .withCopyFileToContainer(
                MountableFile.forHostPath(certsDir.resolve("keystore.p12")),
                "/opt/certs/keystore.p12")
            .withCopyFileToContainer(
                MountableFile.forHostPath(certsDir.resolve("truststore.p12")),
                "/opt/certs/truststore.p12")
            .withEnv("KEYSTORE_PATH", "/opt/certs/keystore.p12")
            .withEnv("KEYSTORE_TYPE", "PKCS12")
            .withEnv("KEYSTORE_PASSWORD", KEYSTORE_PASSWORD)
            .withEnv("KEY_PASSWORD", KEYSTORE_PASSWORD)
            .withEnv("TRUSTSTORE_PATH", "/opt/certs/truststore.p12")
            .withEnv("TRUSTSTORE_TYPE", "PKCS12")
            .withEnv("TRUSTSTORE_PASSWORD", KEYSTORE_PASSWORD)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
    if (network != null) {
      nifi.withNetwork(network);
    }
    customizer.accept(nifi);
    nifi.start();

    httpClient =
        ClientBuilder.newBuilder()
            .sslContext(trustAll())
            .hostnameVerifier((host, session) -> true)
            .build();
    // The config-adapter runs on the host, so it fetches tokens from Keycloak's published port on
    // the Docker host — NOT via host.docker.internal (that gateway address is for containers and is
    // not reachable back from the host). Keycloak's pinned KC_HOSTNAME still stamps the issuer as
    // host.docker.internal:PORT, so NiFi accepts the token regardless.
    String tokenUri =
        "http://"
            + dockerHost
            + ":"
            + KEYCLOAK_PORT
            + "/realms/"
            + REALM
            + "/protocol/openid-connect/token";
    client =
        new NifiRestClient(
            "https://" + dockerHost + ":" + hostPort,
            new OidcClientCredentialsTokenProvider(
                tokenUri, OIDC_CLIENT_ID, OIDC_CLIENT_SECRET, null, httpClient),
            httpClient);

    // NiFi keeps initialising after the port opens; poll the REST API (with a real, token-backed
    // request) until it both accepts the OIDC token and has materialised the root process group.
    await()
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(5))
        .ignoreExceptions()
        .until(
            () -> {
              client.authenticate();
              client.getRootProcessGroupId();
              return true;
            });
  }

  /** Closes the HTTP client and stops NiFi. Subclasses stop their own containers separately. */
  protected static void stopNifi() {
    if (httpClient != null) {
      httpClient.close();
    }
    if (nifi != null) {
      nifi.stop();
    }
  }

  protected Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  protected static SSLContext trustAll() {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType) {}

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType) {}

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          },
          new SecureRandom());
      return ctx;
    } catch (Exception e) {
      throw new IllegalStateException("cannot build trust-all SSL context", e);
    }
  }
}

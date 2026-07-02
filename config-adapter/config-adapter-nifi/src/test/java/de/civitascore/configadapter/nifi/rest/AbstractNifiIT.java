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
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Shared Testcontainers scaffolding for the real-NiFi integration tests: brings up an Apache NiFi
 * 2.9.0 single-user container, a trust-all JAX-RS {@link Client} and a {@link NifiRestClient}
 * authenticated against it. Subclasses provide their own {@code @BeforeAll}/{@code @AfterAll} —
 * they start any IT-specific containers first, then call {@link #startNifi(int, Network)} (so NiFi
 * can join a shared network the sibling containers already sit on), and mirror the teardown with
 * {@link #stopNifi()}.
 *
 * <p>Deliberately a plain JUnit 5 + Testcontainers base with no Spring annotations — these ITs run
 * a real NiFi via failsafe, not a Spring context.
 */
abstract class AbstractNifiIT {

  protected static final String USER = "admin";
  protected static final String PASSWORD = "ctsNiFiTestPassword123";

  protected static FixedHostPortGenericContainer<?> nifi;
  protected static Client httpClient;
  protected static NifiRestClient client;

  /**
   * Host on which published container ports are reachable. In CI the Docker daemon is remote
   * (DinD), so this resolves to the Docker host rather than localhost.
   */
  protected static String dockerHost;

  protected final ObjectMapper mapper = new ObjectMapper();

  /** Whether a Docker daemon is available; ITs {@code assumeTrue} on this to skip without it. */
  protected static boolean dockerAvailable() {
    return DockerClientFactory.instance().isDockerAvailable();
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
        new FixedHostPortGenericContainer<>("apache/nifi:2.9.0")
            .withFixedExposedPort(hostPort, 8443)
            .withEnv("SINGLE_USER_CREDENTIALS_USERNAME", USER)
            .withEnv("SINGLE_USER_CREDENTIALS_PASSWORD", PASSWORD)
            .withEnv("NIFI_WEB_HTTPS_PORT", "8443")
            .withEnv("NIFI_WEB_PROXY_HOST", dockerHost + ":" + hostPort + ",localhost:" + hostPort)
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
    client =
        new NifiRestClient("https://" + dockerHost + ":" + hostPort, USER, PASSWORD, httpClient);

    // NiFi keeps initialising after the port opens; poll the REST API until it authenticates.
    await()
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(5))
        .ignoreExceptions()
        .until(
            () -> {
              client.authenticate();
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

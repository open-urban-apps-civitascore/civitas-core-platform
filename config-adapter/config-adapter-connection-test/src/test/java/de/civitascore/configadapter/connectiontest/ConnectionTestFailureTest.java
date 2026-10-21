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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class ConnectionTestFailureTest {

  /** The chain the JDK builds for an expired server certificate: validation fails on expiry. */
  @Test
  void anExpiredCertificateIsNamedAsExpiredNotAsUntrusted() {
    CertPathValidatorException validation =
        new CertPathValidatorException("validity check failed", new CertificateExpiredException());
    SSLHandshakeException handshake = new SSLHandshakeException("certificate_expired");
    handshake.initCause(new CertificateException(validation));

    assertEquals(
        "The server's certificate has expired.", ConnectionTestFailure.describe(handshake));
  }

  @Test
  void aCertificateOfAnUnknownAuthorityIsNamedAsUntrusted() {
    SSLHandshakeException handshake = new SSLHandshakeException("certificate_unknown");
    handshake.initCause(new CertificateException(new CertPathBuilderException("no path")));

    assertEquals(
        "The server's certificate is not issued by a trusted certificate authority.",
        ConnectionTestFailure.describe(handshake));
  }

  /** The chain the JDK builds for a certificate issued for another host name. */
  @Test
  void anyOtherFailedHandshakeIsNamedWithoutTheClientText() {
    SSLHandshakeException handshake = new SSLHandshakeException("No subject alternative names");
    handshake.initCause(new CertificateException("No subject alternative DNS name matching x"));

    assertEquals(
        "The TLS handshake with the server failed.", ConnectionTestFailure.describe(handshake));
  }

  /** The JDK's HTTP client reports a refusal as a ConnectException without any text. */
  @Test
  void aRefusedHttpConnectionNamesTheRefusal() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }

    Throwable failure = httpFailure("http://127.0.0.1:" + closedPort + "/");

    assertEquals("The connection was refused.", ConnectionTestFailure.describe(failure));
  }

  /** The same text-less ConnectException, with an unresolved address below it. */
  @Test
  void anUnresolvableHttpHostIsNotNamedAsARefusal() {
    Throwable failure = httpFailure("http://connection-test.invalid/");

    assertEquals("The host name could not be resolved.", ConnectionTestFailure.describe(failure));
  }

  /** The chain the JDK's HTTP client builds for a connect timeout. */
  @Test
  void aConnectTimeoutIsNotNamedAsARefusal() {
    HttpConnectTimeoutException timeout = new HttpConnectTimeoutException("HTTP connect timed out");
    timeout.initCause(new ConnectException("HTTP connect timed out"));

    assertEquals("The connection timed out.", ConnectionTestFailure.describe(timeout));
  }

  @Test
  void anUnknownFailureShowsNeitherItsTypeNorItsText() {
    assertEquals(
        "The connection failed.",
        ConnectionTestFailure.describe(
            new IllegalStateException("com.example.InternalClient failed on db-1.internal")));
  }

  private static Throwable httpFailure(String url) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
    try (HttpClient client = HttpClient.newHttpClient()) {
      return assertThrows(
              ExecutionException.class,
              () -> client.sendAsync(request, BodyHandlers.discarding()).get())
          .getCause();
    }
  }

  @Test
  void aSocketTimeoutBelowIsATimeout() {
    assertTrue(ConnectionTestFailure.timedOut(new IOException(new SocketTimeoutException("read"))));
    assertTrue(ConnectionTestFailure.timedOut(new HttpConnectTimeoutException("connect")));
    assertFalse(ConnectionTestFailure.timedOut(new IOException("refused")));
  }
}

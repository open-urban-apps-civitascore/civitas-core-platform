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
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
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

    String text = ConnectionTestFailure.describe(handshake);

    assertTrue(text.startsWith("The server's certificate has expired. "), text);
  }

  @Test
  void aCertificateOfAnUnknownAuthorityIsNamedAsUntrusted() {
    SSLHandshakeException handshake = new SSLHandshakeException("certificate_unknown");
    handshake.initCause(new CertificateException(new CertPathBuilderException("no path")));

    String text = ConnectionTestFailure.describe(handshake);

    assertTrue(
        text.startsWith("The server's certificate is not issued by a trusted certificate"), text);
  }

  @Test
  void anOtherFailureKeepsItsTypeAndTextOnly() {
    assertEquals(
        "IOException: connection reset",
        ConnectionTestFailure.describe(new IOException("connection reset")));
  }

  /** The JDK's HTTP client reports a refusal as a ConnectException whose text is in its cause. */
  @Test
  void aRefusedHttpConnectionNamesTheRefusal() throws IOException, InterruptedException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + closedPort + "/")).build();

    ExecutionException failure;
    try (HttpClient client = HttpClient.newHttpClient()) {
      failure =
          assertThrows(
              ExecutionException.class,
              () -> client.sendAsync(request, BodyHandlers.discarding()).get());
    }

    assertEquals(
        "ConnectException: Connection refused", ConnectionTestFailure.describe(failure.getCause()));
  }

  @Test
  void aSocketTimeoutBelowIsATimeout() {
    assertTrue(ConnectionTestFailure.timedOut(new IOException(new SocketTimeoutException("read"))));
    assertFalse(ConnectionTestFailure.timedOut(new IOException("refused")));
  }
}

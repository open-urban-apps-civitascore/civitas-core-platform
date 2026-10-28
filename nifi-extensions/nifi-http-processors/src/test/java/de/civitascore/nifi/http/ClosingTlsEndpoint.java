/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/**
 * An https API that never answers a request. After a completed TLS handshake it reads the first
 * byte of the request and closes the connection: a client sees what a server shows that refuses a
 * client certificate after a TLS 1.3 handshake, or that fails in the middle of a request.
 *
 * <p>Unlike the JDK HttpsServer, it sends the alert of a failed handshake to the client.
 */
final class ClosingTlsEndpoint implements AutoCloseable {

  private static final int DRAIN_TIMEOUT_MS = 5000;

  private final ServerSocket socket;
  private final SSLContext tls;
  private final boolean refusesClientCertificates;

  private ClosingTlsEndpoint(SSLContext tls, boolean refusesClientCertificates) {
    try {
      this.socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    this.tls = tls;
    this.refusesClientCertificates = refusesClientCertificates;
  }

  static ClosingTlsEndpoint start(SSLContext tls) {
    return start(new ClosingTlsEndpoint(tls, false));
  }

  /** Needs a client certificate in a TLS 1.2 handshake, which {@code tls} must not trust. */
  static ClosingTlsEndpoint refusingClientCertificates(SSLContext tls) {
    return start(new ClosingTlsEndpoint(tls, true));
  }

  private static ClosingTlsEndpoint start(ClosingTlsEndpoint endpoint) {
    Thread.ofVirtual().start(endpoint::answer);
    return endpoint;
  }

  private void answer() {
    try (Socket connection = socket.accept()) {
      connection.setSoTimeout(DRAIN_TIMEOUT_MS);
      SSLSocket tlsConnection =
          (SSLSocket)
              tls.getSocketFactory().createSocket(connection, null, connection.getPort(), false);
      tlsConnection.setUseClientMode(false);
      if (refusesClientCertificates) {
        tlsConnection.setNeedClientAuth(true);
        tlsConnection.setEnabledProtocols(new String[] {"TLSv1.2"});
      }
      try {
        tlsConnection.startHandshake();
        tlsConnection.getInputStream().read();
      } catch (SSLException e) {
        // A close with unread client data sends a TCP reset, which can drop the alert at the
        // client.
        connection.shutdownOutput();
        connection.getInputStream().transferTo(OutputStream.nullOutputStream());
      }
    } catch (IOException e) {
      // A refused handshake or the end of the test; there is nobody left to answer.
    }
  }

  String baseUrl() {
    return "https://127.0.0.1:" + socket.getLocalPort() + "/v1";
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}

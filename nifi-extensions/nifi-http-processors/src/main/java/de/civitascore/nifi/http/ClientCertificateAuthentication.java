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

import java.util.Optional;
import java.util.Set;
import javax.net.ssl.SSLHandshakeException;
import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;

/**
 * Adds nothing to the request: the web client service presents the certificate in the TLS
 * handshake. A handshake that the server ends with a certificate alert is fatal, because the same
 * certificate fails again. Every other failed handshake, for example an untrusted server
 * certificate, stays transient as for the other methods.
 *
 * <p>With TLS 1.3 the server can refuse the certificate after the handshake. When the client then
 * sees only a closed connection, the error stays transient, as the processor cannot tell it from a
 * network error.
 */
final class ClientCertificateAuthentication implements Authentication {

  private static final String METHOD = "Client certificate";

  /** The JDK names an alert of the server in the message only, after this text. */
  private static final String RECEIVED_ALERT = "Received fatal alert: ";

  /**
   * The alerts of RFC 8446 that refuse a certificate, and {@code handshake_failure}, which servers
   * send in TLS 1.2 when the client presents no acceptable certificate.
   */
  private static final Set<String> CERTIFICATE_ALERTS =
      Set.of(
          "handshake_failure",
          "bad_certificate",
          "unsupported_certificate",
          "certificate_revoked",
          "certificate_expired",
          "certificate_unknown",
          "unknown_ca",
          "access_denied",
          "certificate_required");

  @Override
  public String method() {
    return METHOD;
  }

  @Override
  public HttpRequestHeadersSpec headers(HttpRequestHeadersSpec request) {
    return request;
  }

  @Override
  public Optional<ApiCallOutcome.Failure> transportFailure(String request, Throwable cause) {
    for (Throwable current = cause; current != null; current = current.getCause()) {
      if (current instanceof SSLHandshakeException handshake && refusedCertificate(handshake)) {
        return Optional.of(ApiCallOutcome.Failure.ofRefusedHandshake(request, METHOD));
      }
    }
    return Optional.empty();
  }

  private static boolean refusedCertificate(SSLHandshakeException handshake) {
    String message = handshake.getMessage();
    int alert = message == null ? -1 : message.indexOf(RECEIVED_ALERT);
    return alert >= 0
        && CERTIFICATE_ALERTS.contains(message.substring(alert + RECEIVED_ALERT.length()));
  }
}

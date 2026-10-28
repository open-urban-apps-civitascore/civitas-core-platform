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
import java.io.InputStream;
import java.util.Optional;
import org.apache.nifi.web.client.api.HttpResponseEntity;

/** What one request of an API call gave. */
sealed interface ApiCallOutcome {

  /** The status of the response, 0 when no response came. */
  int status();

  /**
   * A 2xx response with a body. The caller reads the content and closes the response.
   *
   * @param content the body as the server sent it, from its first byte
   */
  record Body(int status, InputStream content, HttpResponseEntity response)
      implements ApiCallOutcome, AutoCloseable {

    @Override
    public void close() {
      ApiCallClient.discard(response);
    }
  }

  /** A 2xx response without a body, for example a 204. */
  record NoBody(int status) implements ApiCallOutcome {}

  /**
   * A request that failed.
   *
   * @param message with each control character and line separator escaped, so that no part of it
   *     can start a log line of its own
   */
  record Failure(ErrorKind kind, int status, String message) implements ApiCallOutcome {

    public Failure {
      message = withEscapedControlCharacters(message);
    }

    boolean fatal() {
      return kind == ErrorKind.FATAL;
    }

    /**
     * The runtime monitor of the NiFi config-adapter counts a WARN bulletin only when its text
     * holds a word such as {@code failed}, so a transient status sets the Pipeline to ERROR only
     * with this wording.
     */
    static Failure ofStatus(String request, int status, ErrorKind kind) {
      return new Failure(kind, status, request + " failed with status " + status);
    }

    /** A 401 or a 403 of the API, which names the authentication method that it refused. */
    static Failure ofRefusedCredential(String request, int status, String method) {
      return new Failure(
          ErrorKind.FATAL, status, request + " failed with status " + status + " (" + method + ")");
    }

    /**
     * A request that got no complete response. The message names the exception class alone, because
     * an exception message can hold the URI with its query.
     */
    static Failure ofTransport(String request, Throwable cause) {
      return new Failure(
          ErrorKind.TRANSIENT, 0, request + " failed: " + transportCause(cause).getSimpleName());
    }

    /** A client certificate that the server refused in the TLS handshake. */
    static Failure ofRefusedHandshake(String request, String method) {
      return new Failure(
          ErrorKind.FATAL,
          0,
          request + " failed: TLS handshake with " + method + " failed (SSLHandshakeException)");
    }

    /**
     * An error status of the OAuth2 token endpoint.
     *
     * @param tokenEndpoint the host, the port and the path of the token URL, never its query
     * @param errorCode an error code of RFC 6749, section 5.2, the only text of the response that
     *     the message repeats
     */
    static Failure ofTokenStatus(
        String apiCall,
        String tokenEndpoint,
        int status,
        ErrorKind kind,
        Optional<String> errorCode) {
      return new Failure(
          kind,
          status,
          tokenRequest(apiCall, tokenEndpoint)
              + " failed with status "
              + status
              + errorCode.map(code -> " (" + code + ")").orElse(""));
    }

    /** A token request that got no complete response. */
    static Failure ofTokenTransport(String apiCall, String tokenEndpoint, Throwable cause) {
      return ofTransport(tokenRequest(apiCall, tokenEndpoint), cause);
    }

    /** A 2xx of the token endpoint that holds no access token. */
    static Failure ofMissingAccessToken(String apiCall, String tokenEndpoint, int status) {
      return new Failure(
          ErrorKind.FATAL,
          status,
          tokenRequest(apiCall, tokenEndpoint)
              + " failed: status "
              + status
              + " without an access token in a JSON object");
    }

    private static String tokenRequest(String apiCall, String tokenEndpoint) {
      return apiCall + ": OAuth2 token request to " + tokenEndpoint;
    }

    /**
     * A 2xx response whose body is not the JSON of records that agree with the record schema, or
     * whose values the pagination cannot use.
     *
     * @param reason names no value of the response
     */
    static Failure ofRejectedBody(String apiCall, int status, String reason) {
      return new Failure(ErrorKind.FATAL, status, apiCall + ": " + reason);
    }

    /** A paginated run that read {@code maxPages} pages and met no stop condition. */
    static Failure ofPageLimit(String apiCall, int status, int maxPages) {
      return new Failure(
          ErrorKind.FATAL,
          status,
          apiCall + ": no stop condition after " + maxPages + " pages (Max Pages)");
    }

    /** A page whose body is the same as the body of an earlier page of the run. */
    static Failure ofRepeatedPage(String apiCall, int status, String page, String earlierPage) {
      return new Failure(
          ErrorKind.FATAL, status, apiCall + ": " + page + " repeats " + earlierPage);
    }

    /**
     * A page that would send the same request as an earlier page of the run, so that the API would
     * repeat its pages without end.
     */
    static Failure ofRepeatedRequest(String apiCall, int status, String page, String earlierPage) {
      return new Failure(
          ErrorKind.FATAL,
          status,
          apiCall + ": " + page + " would send the same request as " + earlierPage);
    }

    /** A run that NiFi stopped before this request. */
    static Failure ofStopped(String request) {
      return new Failure(
          ErrorKind.TRANSIENT, 0, request + " not sent, because the processor stopped");
    }

    /** A request the web client refused to build. The same configuration fails again. */
    static Failure ofRejectedRequest(String request, IllegalArgumentException cause) {
      return new Failure(
          ErrorKind.FATAL, 0, request + " failed: " + cause.getClass().getSimpleName());
    }

    private static String withEscapedControlCharacters(String text) {
      StringBuilder escaped = new StringBuilder(text.length());
      for (char c : text.toCharArray()) {
        switch (c) {
          case '\r' -> escaped.append("\\r");
          case '\n' -> escaped.append("\\n");
          default ->
              escaped.append(
                  isControlOrLineSeparator(c)
                      ? String.format("\\u%04x", (int) c)
                      : String.valueOf(c));
        }
      }
      return escaped.toString();
    }

    private static boolean isControlOrLineSeparator(char c) {
      int type = Character.getType(c);
      return type == Character.CONTROL
          || type == Character.LINE_SEPARATOR
          || type == Character.PARAGRAPH_SEPARATOR;
    }

    /** The first I/O exception of the chain; the wrappers around it say nothing more. */
    private static Class<?> transportCause(Throwable cause) {
      for (Throwable current = cause; current != null; current = current.getCause()) {
        if (current instanceof IOException) {
          return current.getClass();
        }
      }
      return cause.getClass();
    }
  }
}

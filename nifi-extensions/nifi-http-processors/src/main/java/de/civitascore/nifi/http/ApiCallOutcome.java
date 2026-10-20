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
import org.apache.nifi.web.client.api.HttpResponseEntity;

/** What one request of an API call gave. */
sealed interface ApiCallOutcome {

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
   * @param status the status of the response, 0 when no response came
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

    /**
     * A request that got no complete response. The message names the exception class alone, because
     * an exception message can hold the URI with its query.
     */
    static Failure ofTransport(String request, Throwable cause) {
      return new Failure(
          ErrorKind.TRANSIENT, 0, request + " failed: " + transportCause(cause).getSimpleName());
    }

    /**
     * A 2xx response whose body is not the JSON of records that agree with the record schema.
     *
     * @param reason names no value of the response
     */
    static Failure ofRejectedBody(String apiCall, int status, String reason) {
      return new Failure(ErrorKind.FATAL, status, apiCall + ": " + reason);
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

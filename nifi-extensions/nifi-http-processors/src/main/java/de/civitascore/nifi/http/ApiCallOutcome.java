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
   * @param message names the method, the base URL and the path, never the query, a header or a
   *     credential
   */
  record Failure(ErrorKind kind, int status, String message) implements ApiCallOutcome {

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

    /** A body that gives no records. The same API call returns a body of the same shape again. */
    static Failure ofBodyShape(String request, int status) {
      return new Failure(
          ErrorKind.FATAL,
          status,
          request + " failed: the body is not a JSON object or an array of objects");
    }

    /** A request the web client refused to build. The same configuration fails again. */
    static Failure ofRejectedRequest(String request, IllegalArgumentException cause) {
      return new Failure(
          ErrorKind.FATAL, 0, request + " failed: " + cause.getClass().getSimpleName());
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

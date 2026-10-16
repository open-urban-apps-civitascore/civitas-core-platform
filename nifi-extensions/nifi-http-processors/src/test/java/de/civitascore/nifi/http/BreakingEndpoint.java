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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * An HTTP API that announces a body of 100 bytes, sends only the first part of it and closes the
 * connection, as a server does that fails in the middle of a response.
 */
final class BreakingEndpoint implements AutoCloseable {

  private static final int ANNOUNCED_LENGTH = 100;

  private final ServerSocket socket;

  private BreakingEndpoint(ServerSocket socket) {
    this.socket = socket;
  }

  static BreakingEndpoint sending(String part) {
    try {
      ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      BreakingEndpoint endpoint = new BreakingEndpoint(socket);
      Thread.ofVirtual().start(() -> endpoint.answer(part));
      return endpoint;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void answer(String part) {
    try (Socket connection = socket.accept()) {
      BufferedReader request =
          new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.ISO_8859_1));
      String line = request.readLine();
      while (line != null && !line.isEmpty()) {
        line = request.readLine();
      }
      OutputStream response = connection.getOutputStream();
      response.write(
          ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                  + ANNOUNCED_LENGTH
                  + "\r\n\r\n"
                  + part)
              .getBytes(StandardCharsets.UTF_8));
      response.flush();
    } catch (IOException e) {
      // The socket closes when the test ends; there is nobody left to answer.
    }
  }

  String baseUrl() {
    return "http://127.0.0.1:" + socket.getLocalPort() + "/v1";
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}

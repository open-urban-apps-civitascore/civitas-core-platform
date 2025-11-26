/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.application;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckServer implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(HealthCheckServer.class);

  private final HttpServer server;
  private final HealthStatus healthStatus;

  public HealthCheckServer(int port, List<?> consumers) throws IOException {
    this.healthStatus = new HealthStatus(consumers);
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    this.server.createContext("/health", new HealthHandler());
    this.server.createContext("/health/ready", new ReadinessHandler());
    this.server.createContext("/health/live", new LivenessHandler());
  }

  public void start() {
    server.start();
    logger.info("Health check server started on port {}", server.getAddress().getPort());
  }

  @Override
  public void close() {
    logger.info("Stopping health check server");
    server.stop(0);
  }

  public void markReady() {
    this.healthStatus.markReady();
  }

  private class HealthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"GET".equals(exchange.getRequestMethod())) {
        sendResponse(exchange, 405, "Method Not Allowed");
        return;
      }

      boolean isHealthy = healthStatus.isHealthy();
      int statusCode = isHealthy ? 200 : 503;
      String status = isHealthy ? "UP" : "DOWN";

      String response =
          String.format(
              "{\"status\":\"%s\",\"consumers\":%d,\"details\":%s}",
              status, healthStatus.getConsumerCount(), healthStatus.getDetails());

      sendJsonResponse(exchange, statusCode, response);
    }
  }

  private class ReadinessHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"GET".equals(exchange.getRequestMethod())) {
        sendResponse(exchange, 405, "Method Not Allowed");
        return;
      }

      boolean isReady = healthStatus.isReady();
      int statusCode = isReady ? 200 : 503;
      String status = isReady ? "UP" : "DOWN";

      String response = String.format("{\"status\":\"%s\"}", status);

      sendJsonResponse(exchange, statusCode, response);
    }
  }

  private class LivenessHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"GET".equals(exchange.getRequestMethod())) {
        sendResponse(exchange, 405, "Method Not Allowed");
        return;
      }
      sendJsonResponse(exchange, 200, "{\"status\":\"UP\"}");
    }
  }

  private void sendJsonResponse(HttpExchange exchange, int statusCode, String response)
      throws IOException {
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    sendResponse(exchange, statusCode, response);
  }

  private void sendResponse(HttpExchange exchange, int statusCode, String response)
      throws IOException {
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  static class HealthStatus {
    private final List<?> consumers;
    private volatile boolean ready;

    public HealthStatus(List<?> consumers) {
      this.consumers = consumers;
      this.ready = false;
    }

    public void markReady() {
      this.ready = true;
    }

    public boolean isHealthy() {
      return ready && !consumers.isEmpty();
    }

    public boolean isReady() {
      return ready;
    }

    public int getConsumerCount() {
      return consumers.size();
    }

    public String getDetails() {
      return String.format("{\"ready\":%s}", ready);
    }
  }
}

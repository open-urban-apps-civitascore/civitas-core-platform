/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.model.dataset.Datasource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared test fixture factories for Redpanda adapter tests. */
final class RedpandaTestFixtures {

  private RedpandaTestFixtures() {}

  // ─── Datasource factories ───────────────────────────────────────────────────

  static Datasource createMqttDatasource(String id, List<String> urls, List<String> topics) {
    Datasource ds = new Datasource();
    ds.setId(id);
    ds.setType("mqtt");
    ds.handleUnknownProperty(
        "configuration",
        Map.of(
            "urls", urls,
            "topics", topics));
    return ds;
  }

  static Datasource createMqttDatasourceWithDetails(
      String id,
      List<String> urls,
      List<String> topics,
      String clientId,
      int qos,
      String user,
      String password) {
    Datasource ds = new Datasource();
    ds.setId(id);
    ds.setType("mqtt");
    ds.handleUnknownProperty(
        "configuration",
        Map.of(
            "urls", urls,
            "topics", topics,
            "client_id", clientId,
            "qos", qos,
            "user", user,
            "password", password));
    return ds;
  }

  static Datasource createSqlDatasource(String id, String dsn, String query) {
    Datasource ds = new Datasource();
    ds.setId(id);
    ds.setType("postgresql");
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("dsn", dsn);
    if (query != null) {
      config.put("query", query);
    }
    ds.handleUnknownProperty("configuration", config);
    return ds;
  }

  static Datasource createPostgresDatasource(
      String id, String host, int port, String database, String username, String password) {
    Datasource ds = new Datasource();
    ds.setId(id);
    ds.setType("postgresql");
    ds.setName("Test DB");
    ds.setHost(host);
    ds.setPort(port);
    ds.handleUnknownProperty("database", database);
    ds.handleUnknownProperty("username", username);
    if (password != null) ds.handleUnknownProperty("password", password);
    ds.handleUnknownProperty("ssl_mode", "require");
    return ds;
  }

  // ─── Command factory ────────────────────────────────────────────────────────

  static SagaCommandMessage createCommand(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "deploy-pipelines", "redpanda", operation, payload);
  }

  static SagaCommandMessage createIntegrationCommand(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type, "msg-it-001", "saga-it-001", "deploy-pipelines", "redpanda", operation, payload);
  }
}

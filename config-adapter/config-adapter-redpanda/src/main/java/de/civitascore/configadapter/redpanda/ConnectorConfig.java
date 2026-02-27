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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed, immutable connector configuration for a Redpanda Connect input block.
 *
 * <p>Created by {@link DatasourceParser}, consumed by {@link DatasourceInjector}. Each record
 * serializes itself via {@link #toInputMap()} into the structure expected by {@link
 * PipelineSerializer}.
 */
sealed interface ConnectorConfig permits ConnectorConfig.Mqtt, ConnectorConfig.Sql {

  /**
   * Returns the Redpanda Connect {@code input} map with the datasource ID as label:
   *
   * <pre>{@code { "label": "<datasource-id>", "mqtt": { ... } }}</pre>
   */
  Map<String, Object> toInputMap(String label);

  // ─── MQTT ──────────────────────────────────────────────────────────────────

  /** MQTT input connector. {@code password} may be {@code ENC(...)} encrypted. */
  record Mqtt(
      List<String> urls,
      List<String> topics,
      String clientId,
      Integer qos,
      Integer keepalive,
      String connectTimeout,
      String user,
      Object password,
      boolean tlsEnabled)
      implements ConnectorConfig {

    @Override
    public Map<String, Object> toInputMap(String label) {
      Map<String, Object> mqtt = new LinkedHashMap<>();
      if (urls != null && !urls.isEmpty()) mqtt.put("urls", List.copyOf(urls));
      if (topics != null && !topics.isEmpty()) mqtt.put("topics", List.copyOf(topics));
      if (clientId != null) mqtt.put("client_id", clientId);
      if (qos != null) mqtt.put("qos", qos);
      if (keepalive != null) mqtt.put("keepalive", keepalive);
      if (connectTimeout != null) mqtt.put("connect_timeout", connectTimeout);
      if (user != null) mqtt.put("user", user);
      if (password != null) mqtt.put("password", password);
      if (tlsEnabled) mqtt.put("tls", Map.of("enabled", true));

      Map<String, Object> input = new LinkedHashMap<>();
      input.put("label", label);
      input.put(ConnectorType.MQTT.redpandaKey, Map.copyOf(mqtt));
      return Map.copyOf(input);
    }
  }

  // ─── SQL ───────────────────────────────────────────────────────────────────

  /** SQL (sql_raw) input connector. {@code dsn} may be {@code ENC(...)} encrypted. */
  record Sql(String driver, String dsn, String query) implements ConnectorConfig {

    @Override
    public Map<String, Object> toInputMap(String label) {
      Map<String, Object> sql = new LinkedHashMap<>();
      sql.put("driver", driver != null ? driver : "postgres");
      if (dsn != null) sql.put("dsn", dsn);
      if (query != null) sql.put("queries", List.of(Map.of("query", query)));

      Map<String, Object> input = new LinkedHashMap<>();
      input.put("label", label);
      input.put(ConnectorType.SQL.redpandaKey, Map.copyOf(sql));
      return Map.copyOf(input);
    }
  }
}

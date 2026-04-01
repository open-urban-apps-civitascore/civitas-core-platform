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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed, immutable connector configuration for a Redpanda Connect input block.
 *
 * <p>Created by {@link DatasourceParser}, consumed by {@link DatasourceInjector}. Each record
 * serializes itself via {@link #toInputMap(String)} into the structure expected by {@link
 * PipelineSerializer}.
 */
sealed interface ConnectorConfig permits ConnectorConfig.Mqtt, ConnectorConfig.Sql {

  /** Shared key for the datasource label in the Redpanda Connect input map. */
  String KEY_LABEL = "label";

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

    private static final String KEY_URLS = "urls";
    private static final String KEY_TOPICS = "topics";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_QOS = "qos";
    private static final String KEY_KEEPALIVE = "keepalive";
    private static final String KEY_CONNECT_TIMEOUT = "connect_timeout";
    private static final String KEY_USER = "user";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_TLS = "tls";
    private static final String KEY_ENABLED = "enabled";

    @Override
    public Map<String, Object> toInputMap(String label) {
      Map<String, Object> mqtt = new LinkedHashMap<>();
      if (urls != null && !urls.isEmpty()) mqtt.put(KEY_URLS, Collections.unmodifiableList(urls));
      if (topics != null && !topics.isEmpty())
        mqtt.put(KEY_TOPICS, Collections.unmodifiableList(topics));
      if (clientId != null) mqtt.put(KEY_CLIENT_ID, clientId);
      if (qos != null) mqtt.put(KEY_QOS, qos);
      if (keepalive != null) mqtt.put(KEY_KEEPALIVE, keepalive);
      if (connectTimeout != null) mqtt.put(KEY_CONNECT_TIMEOUT, connectTimeout);
      if (user != null) mqtt.put(KEY_USER, user);
      if (password != null) mqtt.put(KEY_PASSWORD, password);
      if (tlsEnabled) mqtt.put(KEY_TLS, Map.of(KEY_ENABLED, true));

      Map<String, Object> input = new LinkedHashMap<>();
      input.put(KEY_LABEL, label);
      input.put(RedpandaInputType.MQTT.key, Collections.unmodifiableMap(mqtt));
      return Collections.unmodifiableMap(input);
    }
  }

  // ─── SQL ───────────────────────────────────────────────────────────────────

  /** SQL input connector. {@code dsn} may be {@code ENC(...)} encrypted. */
  record Sql(
      String driver,
      String dsn,
      String user,
      Object password,
      String query,
      String table,
      List<String> columns,
      String where)
      implements ConnectorConfig {

    private static final String KEY_DRIVER = "driver";
    private static final String KEY_DSN = "dsn";
    private static final String KEY_USER = "user";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_QUERY = "query";
    private static final String KEY_TABLE = "table";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_WHERE = "where";
    private static final String DEFAULT_DRIVER = "postgres";

    public Sql {
      boolean hasQuery = query != null;
      boolean hasTable = table != null;
      boolean hasColumns = columns != null && !columns.isEmpty();

      if (!hasQuery && hasTable != hasColumns) {
        throw new IllegalArgumentException(
            "SQL select inputs require both 'table' and non-empty 'columns'");
      }
    }

    @Override
    public Map<String, Object> toInputMap(String label) {
      Map<String, Object> sql = new LinkedHashMap<>();
      sql.put(KEY_DRIVER, driver != null ? driver : DEFAULT_DRIVER);
      if (dsn != null) sql.put(KEY_DSN, dsn);
      if (user != null) sql.put(KEY_USER, user);
      if (password != null) sql.put(KEY_PASSWORD, password);
      RedpandaInputType inputType = RedpandaInputType.SQL_RAW;
      if (query != null) {
        sql.put(KEY_QUERY, query);
      } else {
        inputType = RedpandaInputType.SQL_SELECT;
        if (table != null) sql.put(KEY_TABLE, table);
        if (columns != null && !columns.isEmpty())
          sql.put(KEY_COLUMNS, Collections.unmodifiableList(columns));
        if (where != null) sql.put(KEY_WHERE, where);
      }

      Map<String, Object> input = new LinkedHashMap<>();
      input.put(KEY_LABEL, label);
      input.put(inputType.key, Collections.unmodifiableMap(sql));
      return Collections.unmodifiableMap(input);
    }
  }
}

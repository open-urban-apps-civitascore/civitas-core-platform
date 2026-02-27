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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses a raw datasource payload map into a typed {@link ConnectorConfig}.
 *
 * <p>This is the single place where raw Map keys are accessed — exclusively via {@link
 * DatasourceField} constants. All other classes work with typed records.
 *
 * <p>Expected payload shape:
 *
 * <pre>{@code
 * { "id": "uuid", "connectorType": "MQTT",
 *   "configuration": { "urls": [...], "topics": [...], ... } }
 * }</pre>
 */
final class DatasourceParser {

  private DatasourceParser() {}

  /** Parses the raw datasource map. Returns empty if the type is unsupported or missing. */
  static Optional<ConnectorConfig> parse(Map<String, Object> datasource) {
    String rawType = ConnectorTypeField.asString(datasource);

    return ConnectorType.fromRaw(rawType)
        .map(
            type ->
                switch (type) {
                  case MQTT -> parseMqtt(datasource);
                  case SQL -> parseSql(datasource);
                });
  }

  // ─── MQTT ──────────────────────────────────────────────────────────────────

  private static ConnectorConfig.Mqtt parseMqtt(Map<String, Object> datasource) {
    Map<String, Object> cfg = configuration(datasource);

    List<String> urls = DatasourceField.URLS.asList(cfg);
    if (urls.isEmpty()) {
      String host = DatasourceField.HOST.asString(datasource, cfg).orElse(null);
      int port = DatasourceField.PORT.asInt(datasource, cfg).orElse(1883);
      if (host != null) urls = List.of("tcp://" + host + ":" + port);
    }

    return new ConnectorConfig.Mqtt(
        urls,
        DatasourceField.TOPICS.asList(cfg),
        DatasourceField.CLIENT_ID.asString(cfg).orElse(null),
        DatasourceField.QOS.asInt(cfg).orElse(null),
        DatasourceField.KEEPALIVE.asInt(cfg).orElse(null),
        DatasourceField.CONNECT_TIMEOUT.asString(cfg).orElse(null),
        DatasourceField.USER.asString(cfg).orElse(null),
        DatasourceField.PASSWORD.asObject(cfg),
        DatasourceField.TLS_ENABLED.asBoolean(cfg));
  }

  // ─── SQL ───────────────────────────────────────────────────────────────────

  private static ConnectorConfig.Sql parseSql(Map<String, Object> datasource) {
    Map<String, Object> cfg = configuration(datasource);

    String dsn = DatasourceField.DSN.asString(cfg).orElseGet(() -> buildDsn(datasource, cfg));
    String driver = DatasourceField.DRIVER.asString(cfg).orElse("postgres");
    String query = DatasourceField.QUERY.asString(cfg).orElse(null);

    return new ConnectorConfig.Sql(driver, dsn, query);
  }

  private static String buildDsn(Map<String, Object> datasource, Map<String, Object> cfg) {
    String host = DatasourceField.HOST.asString(datasource, cfg).orElse(null);
    int port = DatasourceField.PORT.asInt(datasource, cfg).orElse(5432);
    String database = DatasourceField.DATABASE.asString(cfg).orElse(null);
    String username = DatasourceField.USERNAME.asString(cfg).orElse(null);
    Object password = DatasourceField.PASSWORD.asObject(cfg);
    String sslMode = DatasourceField.SSL_MODE.asString(cfg).orElse("disable");

    if (host == null || database == null || username == null) return null;
    return "postgres://"
        + username
        + ":"
        + password
        + "@"
        + host
        + ":"
        + port
        + "/"
        + database
        + "?sslmode="
        + sslMode;
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  /** Returns the nested {@code configuration} map, or the datasource map itself if flat. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> configuration(Map<String, Object> datasource) {
    Object cfg = datasource.get("configuration");
    return cfg instanceof Map<?, ?> m ? (Map<String, Object>) m : datasource;
  }

  /**
   * Reads {@code connectorType} or {@code type} from the top-level datasource map. Kept as a small
   * inner helper because these are envelope fields, not configuration fields.
   */
  private static final class ConnectorTypeField {
    private ConnectorTypeField() {}

    static String asString(Map<String, Object> datasource) {
      Object v = datasource.get("connectorType");
      if (v instanceof String s) return s;
      v = datasource.get("type");
      return v instanceof String s ? s : null;
    }
  }
}

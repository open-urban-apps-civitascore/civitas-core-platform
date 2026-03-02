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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses a typed {@link Datasource} into a {@link ConnectorConfig} for Redpanda Connect input
 * injection.
 *
 * <p>Configuration-specific fields (topics, urls, database, etc.) are read from the datasource's
 * {@code additionalProperties}, optionally nested under a {@code "configuration"} key for
 * portal-backend compatibility.
 */
final class DatasourceParser {

  private static final Logger log = LoggerFactory.getLogger(DatasourceParser.class);

  private static final Set<String> VALID_SSL_MODES =
      Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

  private DatasourceParser() {}

  /**
   * Parses the typed datasource. Returns empty if the type is unsupported or missing.
   *
   * @throws FatalAdapterException if the datasource configuration is invalid
   */
  static Optional<ConnectorConfig> parse(Datasource datasource) throws FatalAdapterException {
    String rawType = datasource.getType();
    Optional<ConnectorType> typeOpt = ConnectorType.fromRaw(rawType);
    if (typeOpt.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        switch (typeOpt.get()) {
          case MQTT -> parseMqtt(datasource);
          case SQL -> parseSql(datasource);
        });
  }

  // ─── MQTT ──────────────────────────────────────────────────────────────────

  private static ConnectorConfig.Mqtt parseMqtt(Datasource datasource) {
    Map<String, Object> cfg = configuration(datasource);

    List<String> urls = DatasourceField.URLS.asList(cfg);
    if (urls.isEmpty()) {
      String host = datasource.getHost();
      int port = datasource.getPort() != null ? datasource.getPort() : 1883;
      if (host == null) {
        host = DatasourceField.HOST.asString(cfg).orElse(null);
      }
      if (host != null) urls = List.of("tcp://" + host + ":" + port);
    }

    if (urls.isEmpty()) {
      log.warn(
          "MQTT datasource '{}' has no URLs configured and no host fallback", datasource.getId());
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
        DatasourceField.TLS_ENABLED.asBooleanNested(cfg));
  }

  // ─── SQL ───────────────────────────────────────────────────────────────────

  private static ConnectorConfig.Sql parseSql(Datasource datasource) throws FatalAdapterException {
    Map<String, Object> cfg = configuration(datasource);

    String dsn = DatasourceField.DSN.asString(cfg).orElse(null);
    if (dsn == null) {
      dsn = buildDsn(datasource, cfg);
    }
    if (dsn == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "SQL datasource '"
              + datasource.getId()
              + "' has no DSN and cannot build one (missing host, database, or username)");
    }
    String driver = DatasourceField.DRIVER.asString(cfg).orElse("postgres");
    String query = DatasourceField.QUERY.asString(cfg).orElse(null);

    return new ConnectorConfig.Sql(driver, dsn, query);
  }

  /**
   * Builds a DSN string from individual components. Handles null password correctly — if password
   * is null, it is omitted from the credentials part.
   *
   * <p>The {@code ssl_mode} value is validated against PostgreSQL's allowed modes: {@code disable},
   * {@code allow}, {@code prefer}, {@code require}, {@code verify-ca}, {@code verify-full}.
   * Defaults to {@code disable} if not specified.
   *
   * @return the DSN string, or {@code null} if host, database, or username is missing
   * @throws FatalAdapterException if {@code ssl_mode} is not in the whitelist
   */
  static String buildDsn(Datasource datasource, Map<String, Object> cfg)
      throws FatalAdapterException {
    String host = datasource.getHost();
    if (host == null) host = DatasourceField.HOST.asString(cfg).orElse(null);
    int port =
        datasource.getPort() != null
            ? datasource.getPort()
            : DatasourceField.PORT.asInt(cfg).orElse(5432);
    String database = DatasourceField.DATABASE.asString(cfg).orElse(null);
    String username = DatasourceField.USERNAME.asString(cfg).orElse(null);
    Object password = DatasourceField.PASSWORD.asObject(cfg);
    String sslMode = DatasourceField.SSL_MODE.asString(cfg).orElse("disable");
    if (!VALID_SSL_MODES.contains(sslMode)) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Invalid ssl_mode: '"
              + sslMode
              + "'. Must be one of: disable, allow, prefer, require, verify-ca, verify-full");
    }

    if (host == null || database == null || username == null) {
      log.warn(
          "Cannot build DSN: missing host={}, database={}, username={}",
          host != null,
          database != null,
          username != null);
      return null;
    }
    String encUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
    String credentials =
        password != null
            ? encUser + ":" + URLEncoder.encode(String.valueOf(password), StandardCharsets.UTF_8)
            : encUser;
    String encDatabase = URLEncoder.encode(database, StandardCharsets.UTF_8);
    // Host is not URL-encoded: RFC 3986 hostnames are restricted to unreserved
    // characters. IPv6 literals must be bracketed per RFC 3986 §3.2.2.
    return "postgres://"
        + credentials
        + "@"
        + host
        + ":"
        + port
        + "/"
        + encDatabase
        + "?sslmode="
        + sslMode;
  }

  /**
   * Builds a DSN string from a typed {@link Datasource} for use by {@link PlaceholderResolver}.
   * Falls back to configuration sub-map for fields not available on the core Datasource object.
   */
  static String buildDsnFromDatasource(Datasource datasource) throws FatalAdapterException {
    Map<String, Object> cfg = configuration(datasource);
    return buildDsn(datasource, cfg);
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Returns the nested {@code configuration} map from the datasource's additional properties, or
   * the additional properties map itself if no {@code configuration} sub-key exists.
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> configuration(Datasource datasource) {
    Map<String, Object> props = datasource.getAdditionalProperties();
    Object cfg = props.get("configuration");
    return cfg instanceof Map<?, ?> m ? (Map<String, Object>) m : props;
  }
}

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
 * Typed field descriptors for datasource configuration maps.
 *
 * <p>Each constant declares all payload key aliases it accepts (first alias is primary). Reading is
 * done via the typed {@code from(Map)} methods — no raw string keys outside this enum.
 */
enum DatasourceField {

  // ─── Common ────────────────────────────────────────────────────────────────
  HOST("host"),
  PORT("port"),
  PASSWORD("password"),

  // ─── MQTT ──────────────────────────────────────────────────────────────────
  URLS("urls"),
  TOPICS("topics"),
  CLIENT_ID("client_id"),
  QOS("qos"),
  KEEPALIVE("keepalive"),
  CONNECT_TIMEOUT("connect_timeout"),
  USER("user", "username"),
  TLS_ENABLED("tls.enabled"),

  // ─── SQL ───────────────────────────────────────────────────────────────────
  DSN("dsn"),
  DRIVER("driver"),
  QUERY("query"),
  TABLE("table"),
  COLUMNS("columns"),
  WHERE("where"),
  DATABASE("database"),
  USERNAME("username", "user"),
  SSL_MODE("ssl_mode");

  private final String[] keys;

  DatasourceField(String... keys) {
    this.keys = keys;
  }

  /** Reads a {@code String} value, trying each alias in order. */
  Optional<String> asString(Map<String, Object> map) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v instanceof String s) return Optional.of(s);
    }
    return Optional.empty();
  }

  /**
   * Reads a {@code String} value from {@code cfg}, falling back to {@code datasource}. Useful for
   * fields that may appear at the top level or inside {@code configuration}.
   */
  Optional<String> asString(Map<String, Object> datasource, Map<String, Object> cfg) {
    return asString(cfg).or(() -> asString(datasource));
  }

  /** Reads an {@code Integer} value, accepting any {@link Number}. */
  Optional<Integer> asInt(Map<String, Object> map) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v instanceof Integer i) return Optional.of(i);
      if (v instanceof Number n) return Optional.of(n.intValue());
    }
    return Optional.empty();
  }

  /** Reads an {@code Integer} from {@code cfg}, falling back to {@code datasource}. */
  Optional<Integer> asInt(Map<String, Object> datasource, Map<String, Object> cfg) {
    return asInt(cfg).or(() -> asInt(datasource));
  }

  /** Reads a {@code List<String>}, returning empty list if absent or wrong type. */
  List<String> asList(Map<String, Object> map) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v instanceof List<?> l) return l.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  /** Reads a raw {@code Object} value (e.g. for password which may be ENC(...)). */
  Object asObject(Map<String, Object> map) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v != null) return v;
    }
    return null;
  }

  /** Reads a {@code boolean} flag. */
  boolean asBoolean(Map<String, Object> map) {
    for (String key : keys) {
      Object v = map.get(key);
      if (Boolean.TRUE.equals(v)) return true;
    }
    return false;
  }

  /**
   * Reads a {@code boolean} flag supporting both flat-key and nested-map formats. For example,
   * {@code TLS_ENABLED} (key {@code "tls.enabled"}) matches both:
   *
   * <ul>
   *   <li>Flat: {@code {"tls.enabled": true}}
   *   <li>Nested: {@code {"tls": {"enabled": true}}}
   * </ul>
   */
  @SuppressWarnings("unchecked")
  boolean asBooleanNested(Map<String, Object> map) {
    if (asBoolean(map)) return true;
    for (String key : keys) {
      int dot = key.indexOf('.');
      if (dot > 0) {
        String parent = key.substring(0, dot);
        String child = key.substring(dot + 1);
        Object nested = map.get(parent);
        if (nested instanceof Map<?, ?> m) {
          Object v = ((Map<String, Object>) m).get(child);
          if (Boolean.TRUE.equals(v)) return true;
        }
      }
    }
    return false;
  }
}

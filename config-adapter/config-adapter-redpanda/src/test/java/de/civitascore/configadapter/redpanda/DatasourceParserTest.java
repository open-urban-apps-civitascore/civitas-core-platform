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

import static org.junit.jupiter.api.Assertions.*;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DatasourceParserTest {

  // ─── parse() ──────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("parse()")
  class Parse {

    @Test
    @DisplayName("returns empty for null type")
    void parse_nullType_returnsEmpty() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-1");
      // type is null by default

      assertEquals(Optional.empty(), DatasourceParser.parse(ds));
    }

    @Test
    @DisplayName("returns empty for unsupported type")
    void parse_unsupportedType_returnsEmpty() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-1");
      ds.setType("redis");

      assertEquals(Optional.empty(), DatasourceParser.parse(ds));
    }

    @Test
    @DisplayName("returns Mqtt for type 'mqtt'")
    void parse_mqttType_returnsMqttConfig() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-mqtt");
      ds.setType("mqtt");
      ds.setHost("broker.local");
      ds.handleUnknownProperty("configuration", Map.of("topics", List.of("t/#")));

      Optional<ConnectorConfig> result = DatasourceParser.parse(ds);

      assertTrue(result.isPresent());
      assertInstanceOf(ConnectorConfig.Mqtt.class, result.get());
    }

    @Test
    @DisplayName("returns Sql for type 'postgresql'")
    void parse_postgresqlType_returnsSqlConfig() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-pg");
      ds.setType("postgresql");
      ds.handleUnknownProperty("configuration", Map.of("dsn", "postgres://user:pass@db:5432/mydb"));

      Optional<ConnectorConfig> result = DatasourceParser.parse(ds);

      assertTrue(result.isPresent());
      assertInstanceOf(ConnectorConfig.Sql.class, result.get());
    }

    @Test
    @DisplayName("returns Sql for type 'sql_raw'")
    void parse_sqlRawType_returnsSqlConfig() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-sql");
      ds.setType("sql_raw");
      ds.handleUnknownProperty("configuration", Map.of("dsn", "postgres://user:pass@db:5432/mydb"));

      Optional<ConnectorConfig> result = DatasourceParser.parse(ds);

      assertTrue(result.isPresent());
      assertInstanceOf(ConnectorConfig.Sql.class, result.get());
    }

    @Test
    @DisplayName("returns Sql for type 'sql'")
    void parse_sqlType_returnsSqlConfig() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-sql-alias");
      ds.setType("sql");
      ds.handleUnknownProperty("configuration", Map.of("dsn", "postgres://user:pass@db:5432/mydb"));

      Optional<ConnectorConfig> result = DatasourceParser.parse(ds);

      assertTrue(result.isPresent());
      assertInstanceOf(ConnectorConfig.Sql.class, result.get());
    }
  }

  // ─── parseMqtt (via parse()) ──────────────────────────────────────────────

  @Nested
  @DisplayName("MQTT parsing")
  class MqttParsing {

    @Test
    @DisplayName("uses urls from configuration when present")
    void parseMqtt_urlsInConfig_usesConfigUrls() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-1");
      ds.setType("mqtt");
      ds.handleUnknownProperty(
          "configuration",
          Map.of("urls", List.of("tcp://a:1883", "tcp://b:1883"), "topics", List.of("t/#")));

      ConnectorConfig.Mqtt mqtt = (ConnectorConfig.Mqtt) DatasourceParser.parse(ds).orElseThrow();

      assertEquals(List.of("tcp://a:1883", "tcp://b:1883"), mqtt.urls());
    }

    @Test
    @DisplayName("falls back to host+port when urls absent")
    void parseMqtt_noUrls_fallsBackToHostPort() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-1");
      ds.setType("mqtt");
      ds.setHost("broker.local");
      ds.setPort(1884);
      ds.handleUnknownProperty("configuration", Map.of("topics", List.of("t/#")));

      ConnectorConfig.Mqtt mqtt = (ConnectorConfig.Mqtt) DatasourceParser.parse(ds).orElseThrow();

      assertEquals(List.of("tcp://broker.local:1884"), mqtt.urls());
    }

    @Test
    @DisplayName("falls back to host from config when datasource host is null")
    void parseMqtt_hostInConfig_usesConfigHost() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-1");
      ds.setType("mqtt");
      ds.handleUnknownProperty(
          "configuration", Map.of("host", "config-broker.local", "topics", List.of("t/#")));

      ConnectorConfig.Mqtt mqtt = (ConnectorConfig.Mqtt) DatasourceParser.parse(ds).orElseThrow();

      assertEquals(List.of("tcp://config-broker.local:1883"), mqtt.urls());
    }

    @Test
    @DisplayName("produces empty urls when no urls and no host")
    void parseMqtt_noUrlsNoHost_emptyUrls() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-no-urls");
      ds.setType("mqtt");
      ds.handleUnknownProperty("configuration", Map.of("topics", List.of("t/#")));

      ConnectorConfig.Mqtt mqtt = (ConnectorConfig.Mqtt) DatasourceParser.parse(ds).orElseThrow();

      assertTrue(mqtt.urls().isEmpty());
    }

    @Test
    @DisplayName("parses all MQTT fields correctly")
    void parseMqtt_allFields_parsedCorrectly() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-full");
      ds.setType("mqtt");
      Map<String, Object> cfg = new HashMap<>();
      cfg.put("urls", List.of("tcp://broker:1883"));
      cfg.put("topics", List.of("sensor/#", "data/#"));
      cfg.put("client_id", "my-client");
      cfg.put("qos", 2);
      cfg.put("keepalive", 60);
      cfg.put("connect_timeout", "30s");
      cfg.put("user", "mqttuser");
      cfg.put("password", "ENC(secret)");
      cfg.put("tls", Map.of("enabled", true));
      ds.handleUnknownProperty("configuration", cfg);

      ConnectorConfig.Mqtt mqtt = (ConnectorConfig.Mqtt) DatasourceParser.parse(ds).orElseThrow();

      assertEquals(List.of("tcp://broker:1883"), mqtt.urls());
      assertEquals(List.of("sensor/#", "data/#"), mqtt.topics());
      assertEquals("my-client", mqtt.clientId());
      assertEquals(2, mqtt.qos());
      assertEquals(60, mqtt.keepalive());
      assertEquals("30s", mqtt.connectTimeout());
      assertEquals("mqttuser", mqtt.user());
      assertEquals("ENC(secret)", mqtt.password());
      assertTrue(mqtt.tlsEnabled());
    }
  }

  // ─── parseSql (via parse()) ───────────────────────────────────────────────

  @Nested
  @DisplayName("SQL parsing")
  class SqlParsing {

    @Test
    @DisplayName("uses DSN from configuration when present")
    void parseSql_dsnInConfig_usesConfigDsn() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-sql");
      ds.setType("postgresql");
      ds.handleUnknownProperty(
          "configuration", Map.of("dsn", "postgres://user:pass@db:5432/mydb?sslmode=disable"));

      ConnectorConfig.Sql sql = (ConnectorConfig.Sql) DatasourceParser.parse(ds).orElseThrow();

      assertEquals("postgres://user:pass@db:5432/mydb?sslmode=disable", sql.dsn());
      assertEquals("postgres", sql.driver());
      assertNull(sql.user());
      assertNull(sql.password());
    }

    @Test
    @DisplayName("builds DSN from host/database/username when dsn absent")
    void parseSql_noDsn_buildsDsnFromComponents() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-sql");
      ds.setType("postgresql");
      ds.setHost("db.local");
      ds.setPort(5432);
      ds.handleUnknownProperty(
          "configuration", Map.of("database", "sensordb", "username", "reader", "password", "pw"));

      ConnectorConfig.Sql sql = (ConnectorConfig.Sql) DatasourceParser.parse(ds).orElseThrow();

      assertEquals("postgres://reader:pw@db.local:5432/sensordb?sslmode=disable", sql.dsn());
      assertEquals("reader", sql.user());
      assertEquals("pw", sql.password());
    }

    @Test
    @DisplayName("throws FatalAdapterException when DSN cannot be built (missing host)")
    void parseSql_noDsnMissingHost_throwsException() {
      Datasource ds = new Datasource();
      ds.setId("ds-broken");
      ds.setType("postgresql");
      ds.handleUnknownProperty("configuration", Map.of("database", "mydb", "username", "user"));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> DatasourceParser.parse(ds));
      assertTrue(ex.getMessage().contains("ds-broken"));
      assertTrue(ex.getMessage().contains("missing host, database, or username"));
    }

    @Test
    @DisplayName("throws FatalAdapterException when DSN cannot be built (missing database)")
    void parseSql_noDsnMissingDatabase_throwsException() {
      Datasource ds = new Datasource();
      ds.setId("ds-no-db");
      ds.setType("sql");
      ds.setHost("db.local");
      ds.handleUnknownProperty("configuration", Map.of("username", "user"));

      assertThrows(FatalAdapterException.class, () -> DatasourceParser.parse(ds));
    }

    @Test
    @DisplayName("throws FatalAdapterException when DSN cannot be built (missing username)")
    void parseSql_noDsnMissingUsername_throwsException() {
      Datasource ds = new Datasource();
      ds.setId("ds-no-user");
      ds.setType("sql");
      ds.setHost("db.local");
      ds.handleUnknownProperty("configuration", Map.of("database", "mydb"));

      assertThrows(FatalAdapterException.class, () -> DatasourceParser.parse(ds));
    }

    @Test
    @DisplayName("uses custom driver when specified")
    void parseSql_customDriver_usesSpecifiedDriver() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-mysql");
      ds.setType("sql");
      ds.handleUnknownProperty(
          "configuration", Map.of("dsn", "mysql://user:pass@db:3306/mydb", "driver", "mysql"));

      ConnectorConfig.Sql sql = (ConnectorConfig.Sql) DatasourceParser.parse(ds).orElseThrow();

      assertEquals("mysql", sql.driver());
    }

    @Test
    @DisplayName("includes query when present")
    void parseSql_withQuery_includesQuery() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-q");
      ds.setType("sql");
      ds.handleUnknownProperty(
          "configuration", Map.of("dsn", "postgres://u:p@h:5432/d", "query", "SELECT * FROM t"));

      ConnectorConfig.Sql sql = (ConnectorConfig.Sql) DatasourceParser.parse(ds).orElseThrow();

      assertEquals("SELECT * FROM t", sql.query());
    }

    @Test
    @DisplayName("includes sql user and encrypted password when present")
    void parseSql_withCredentials_includesUserAndPassword() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-credentials");
      ds.setType("sql");
      ds.handleUnknownProperty(
          "configuration",
          Map.of(
              "dsn", "postgres://u:p@h:5432/d",
              "username", "sql-user",
              "password", "ENC(secret)",
              "query", "SELECT 1"));

      ConnectorConfig.Sql sql = (ConnectorConfig.Sql) DatasourceParser.parse(ds).orElseThrow();

      assertEquals("sql-user", sql.user());
      assertEquals("ENC(secret)", sql.password());
    }

    @Test
    @DisplayName("keeps sql user and password optional when absent")
    void parseSql_withoutCredentials_keepsUserAndPasswordNull() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-no-credentials");
      ds.setType("sql");
      ds.handleUnknownProperty(
          "configuration",
          Map.of(
              "dsn", "postgres://u:p@h:5432/d",
              "table", "public.device_definitions",
              "columns", List.of("*")));

      ConnectorConfig.Sql sql = (ConnectorConfig.Sql) DatasourceParser.parse(ds).orElseThrow();

      assertNull(sql.user());
      assertNull(sql.password());
    }

    @Test
    @DisplayName("includes optional where for sql_select inputs")
    void parseSql_withTableColumnsAndWhere_includesWhere() throws Exception {
      Datasource ds = new Datasource();
      ds.setId("ds-select");
      ds.setType("sql");
      ds.handleUnknownProperty(
          "configuration",
          Map.of(
              "dsn", "postgres://u:p@h:5432/d",
              "table", "public.device_definitions",
              "columns", List.of("*"),
              "where", "device_id = 1"));

      ConnectorConfig.Sql sql = (ConnectorConfig.Sql) DatasourceParser.parse(ds).orElseThrow();

      assertNull(sql.query());
      assertEquals("public.device_definitions", sql.table());
      assertEquals(List.of("*"), sql.columns());
      assertEquals("device_id = 1", sql.where());
    }

    @Test
    @DisplayName("rejects table without columns")
    void parseSql_withTableButNoColumns_rejectsInvalidSelectInput() {
      Datasource ds = new Datasource();
      ds.setId("ds-select-invalid-table");
      ds.setType("sql");
      ds.handleUnknownProperty(
          "configuration",
          Map.of("dsn", "postgres://u:p@h:5432/d", "table", "public.device_definitions"));

      assertThrows(IllegalArgumentException.class, () -> DatasourceParser.parse(ds));
    }

    @Test
    @DisplayName("rejects columns without table")
    void parseSql_withColumnsButNoTable_rejectsInvalidSelectInput() {
      Datasource ds = new Datasource();
      ds.setId("ds-select-invalid-columns");
      ds.setType("sql");
      ds.handleUnknownProperty(
          "configuration", Map.of("dsn", "postgres://u:p@h:5432/d", "columns", List.of("*")));

      assertThrows(IllegalArgumentException.class, () -> DatasourceParser.parse(ds));
    }
  }

  // ─── buildDsn() ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("buildDsn()")
  class BuildDsn {

    @Test
    @DisplayName("builds full DSN with password")
    void buildDsn_withPassword_includesPassword() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");
      ds.setPort(5433);

      String dsn =
          DatasourceParser.buildDsn(
              ds,
              Map.of(
                  "database", "testdb",
                  "username", "admin",
                  "password", "secret",
                  "ssl_mode", "require"));

      assertEquals("postgres://admin:secret@db.local:5433/testdb?sslmode=require", dsn);
    }

    @Test
    @DisplayName("builds DSN without password when password is null")
    void buildDsn_withoutPassword_omitsPassword() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");
      ds.setPort(5432);

      String dsn = DatasourceParser.buildDsn(ds, Map.of("database", "testdb", "username", "admin"));

      assertEquals("postgres://admin@db.local:5432/testdb?sslmode=disable", dsn);
    }

    @Test
    @DisplayName("returns null when host is missing")
    void buildDsn_missingHost_returnsNull() throws Exception {
      Datasource ds = new Datasource();

      String dsn = DatasourceParser.buildDsn(ds, Map.of("database", "testdb", "username", "admin"));

      assertNull(dsn);
    }

    @Test
    @DisplayName("returns null when database is missing")
    void buildDsn_missingDatabase_returnsNull() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");

      String dsn = DatasourceParser.buildDsn(ds, Map.of("username", "admin"));

      assertNull(dsn);
    }

    @Test
    @DisplayName("returns null when username is missing")
    void buildDsn_missingUsername_returnsNull() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");

      String dsn = DatasourceParser.buildDsn(ds, Map.of("database", "testdb"));

      assertNull(dsn);
    }

    @Test
    @DisplayName("uses default port 5432 when port is null")
    void buildDsn_defaultPort_uses5432() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");

      String dsn = DatasourceParser.buildDsn(ds, Map.of("database", "testdb", "username", "admin"));

      assertNotNull(dsn);
      assertTrue(dsn.contains(":5432/"));
    }

    @Test
    @DisplayName("falls back to host from config map")
    void buildDsn_hostFromConfig_usesConfigHost() throws Exception {
      Datasource ds = new Datasource();

      String dsn =
          DatasourceParser.buildDsn(
              ds, Map.of("host", "cfg-host", "database", "testdb", "username", "admin"));

      assertNotNull(dsn);
      assertTrue(dsn.contains("cfg-host"));
    }

    @Test
    @DisplayName("falls back to port from config map")
    void buildDsn_portFromConfig_usesConfigPort() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");

      String dsn =
          DatasourceParser.buildDsn(
              ds, Map.of("port", 5555, "database", "testdb", "username", "admin"));

      assertNotNull(dsn);
      assertTrue(dsn.contains(":5555/"));
    }

    @Test
    @DisplayName("uses default ssl_mode=disable when not specified")
    void buildDsn_defaultSslMode_usesDisable() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");
      ds.setPort(5432);

      String dsn =
          DatasourceParser.buildDsn(
              ds, Map.of("database", "testdb", "username", "admin", "password", "pw"));

      assertNotNull(dsn);
      assertTrue(dsn.endsWith("?sslmode=disable"));
    }

    @Test
    @DisplayName("rejects invalid ssl_mode with FatalAdapterException")
    void buildDsn_invalidSslMode_throwsFatalAdapterException() {
      Datasource ds = new Datasource();
      ds.setHost("db.local");
      ds.setPort(5432);

      assertThrows(
          FatalAdapterException.class,
          () ->
              DatasourceParser.buildDsn(
                  ds,
                  Map.of(
                      "database", "testdb",
                      "username", "admin",
                      "ssl_mode", "require'; DROP TABLE")));
    }

    @Test
    @DisplayName("accepts all valid PostgreSQL ssl_mode values")
    void buildDsn_validSslModes_accepted() throws Exception {
      List<String> validModes =
          List.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

      for (String mode : validModes) {
        Datasource ds = new Datasource();
        ds.setHost("db.local");
        ds.setPort(5432);

        String dsn =
            DatasourceParser.buildDsn(
                ds, Map.of("database", "testdb", "username", "admin", "ssl_mode", mode));

        assertNotNull(dsn, "DSN should not be null for ssl_mode=" + mode);
        assertTrue(dsn.endsWith("?sslmode=" + mode), "DSN should end with sslmode=" + mode);
      }
    }

    @Test
    @DisplayName("URL-encodes special characters in credentials and database")
    void buildDsn_specialCharactersInCredentials_encodedCorrectly() throws Exception {
      Datasource ds = new Datasource();
      ds.setHost("db.local");
      ds.setPort(5432);

      String dsn =
          DatasourceParser.buildDsn(
              ds,
              Map.of(
                  "database", "my/db",
                  "username", "user@domain",
                  "password", "p:ss/word"));

      assertNotNull(dsn);
      // Verify that special characters are percent-encoded
      assertTrue(dsn.contains("user%40domain"), "@ in username should be encoded");
      assertTrue(dsn.contains("p%3Ass%2Fword"), ":/  in password should be encoded");
      assertTrue(dsn.contains("/my%2Fdb?"), "/ in database should be encoded");
      // Verify the overall structure is still a valid postgres URI
      assertTrue(dsn.startsWith("postgres://user%40domain:p%3Ass%2Fword@db.local:5432/my%2Fdb"));
    }
  }

  // ─── ConnectorType ────────────────────────────────────────────────────────

  @Nested
  @DisplayName("ConnectorType.fromRaw()")
  class ConnectorTypeFromRaw {

    @Test
    @DisplayName("returns empty for null")
    void fromRaw_null_returnsEmpty() {
      assertEquals(Optional.empty(), ConnectorType.fromRaw(null));
    }

    @Test
    @DisplayName("returns empty for unknown type")
    void fromRaw_unknown_returnsEmpty() {
      assertEquals(Optional.empty(), ConnectorType.fromRaw("redis"));
    }

    @Test
    @DisplayName("resolves 'mqtt' case-insensitively")
    void fromRaw_mqtt_resolves() {
      assertEquals(Optional.of(ConnectorType.MQTT), ConnectorType.fromRaw("mqtt"));
      assertEquals(Optional.of(ConnectorType.MQTT), ConnectorType.fromRaw("MQTT"));
      assertEquals(Optional.of(ConnectorType.MQTT), ConnectorType.fromRaw("Mqtt"));
    }

    @Test
    @DisplayName("resolves all SQL aliases")
    void fromRaw_sqlAliases_resolve() {
      assertEquals(Optional.of(ConnectorType.SQL), ConnectorType.fromRaw("sql_raw"));
      assertEquals(Optional.of(ConnectorType.SQL), ConnectorType.fromRaw("postgresql"));
      assertEquals(Optional.of(ConnectorType.SQL), ConnectorType.fromRaw("sql"));
      assertEquals(Optional.of(ConnectorType.SQL), ConnectorType.fromRaw("POSTGRESQL"));
      assertEquals(Optional.of(ConnectorType.SQL), ConnectorType.fromRaw("SQL"));
    }
  }

  // ─── DatasourceField ─────────────────────────────────────────────────────

  @Nested
  @DisplayName("DatasourceField")
  class DatasourceFieldTests {

    @Test
    @DisplayName("asList returns empty for missing key")
    void asList_missingKey_returnsEmptyList() {
      assertEquals(List.of(), DatasourceField.URLS.asList(Map.of()));
    }

    @Test
    @DisplayName("asList returns list when present")
    void asList_present_returnsList() {
      assertEquals(
          List.of("tcp://a:1883"),
          DatasourceField.URLS.asList(Map.of("urls", List.of("tcp://a:1883"))));
    }

    @Test
    @DisplayName("asString tries aliases in order")
    void asString_multipleAliases_triesInOrder() {
      // USER has aliases: "user", "username"
      assertEquals(Optional.of("val"), DatasourceField.USER.asString(Map.of("user", "val")));
      assertEquals(Optional.of("val2"), DatasourceField.USER.asString(Map.of("username", "val2")));
    }

    @Test
    @DisplayName("asObject returns null for missing key")
    void asObject_missingKey_returnsNull() {
      assertNull(DatasourceField.PASSWORD.asObject(Map.of()));
    }

    @Test
    @DisplayName("asBooleanNested reads flat key")
    void asBooleanNested_flatKey_returnsTrue() {
      assertTrue(DatasourceField.TLS_ENABLED.asBooleanNested(Map.of("tls.enabled", true)));
    }

    @Test
    @DisplayName("asBooleanNested reads nested map")
    void asBooleanNested_nestedMap_returnsTrue() {
      assertTrue(
          DatasourceField.TLS_ENABLED.asBooleanNested(Map.of("tls", Map.of("enabled", true))));
    }

    @Test
    @DisplayName("asBooleanNested returns false when absent")
    void asBooleanNested_absent_returnsFalse() {
      assertFalse(DatasourceField.TLS_ENABLED.asBooleanNested(Map.of()));
    }

    @Test
    @DisplayName("asBooleanNested returns false for nested false")
    void asBooleanNested_nestedFalse_returnsFalse() {
      assertFalse(
          DatasourceField.TLS_ENABLED.asBooleanNested(Map.of("tls", Map.of("enabled", false))));
    }

    @Test
    @DisplayName("asInt accepts Number subtype")
    void asInt_longValue_convertsToInt() {
      assertEquals(Optional.of(5432), DatasourceField.PORT.asInt(Map.of("port", 5432L)));
    }

    @Test
    @DisplayName("asList converts non-String elements to strings")
    void asList_nonStringElements_convertsToStrings() {
      Map<String, Object> map = Map.of("urls", List.of(123, true, "tcp://a:1883"));
      assertEquals(List.of("123", "true", "tcp://a:1883"), DatasourceField.URLS.asList(map));
    }
  }

  // ─── configuration() ─────────────────────────────────────────────────────

  @Nested
  @DisplayName("configuration()")
  class Configuration {

    @Test
    @DisplayName("returns nested map when 'configuration' key exists")
    void configuration_withNestedKey_returnsNestedMap() {
      Datasource ds = new Datasource();
      Map<String, Object> nested = Map.of("key", "value");
      ds.handleUnknownProperty("configuration", nested);

      assertEquals(nested, DatasourceParser.configuration(ds));
    }

    @Test
    @DisplayName("returns empty map when additionalProperties is null")
    void configuration_nullAdditionalProperties_returnsEmptyMap() {
      Datasource ds = new Datasource();
      // Do not call handleUnknownProperty — additionalProperties may be null

      Map<String, Object> result = DatasourceParser.configuration(ds);

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("returns additionalProperties when no 'configuration' key")
    void configuration_withoutNestedKey_returnsProps() {
      Datasource ds = new Datasource();
      ds.handleUnknownProperty("key", "value");

      Map<String, Object> result = DatasourceParser.configuration(ds);

      assertEquals("value", result.get("key"));
    }
  }
}

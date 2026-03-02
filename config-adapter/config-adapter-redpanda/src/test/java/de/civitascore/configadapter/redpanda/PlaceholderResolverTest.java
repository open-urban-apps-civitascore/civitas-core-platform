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

import static de.civitascore.configadapter.redpanda.RedpandaTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlaceholderResolverTest {

  // ─── Test helpers ──────────────────────────────────────────────────────────

  private static Datasource createMqttDatasource(String id) {
    Datasource ds = new Datasource();
    ds.setId(id);
    ds.setType("mqtt");
    ds.setName("MQTT Broker");
    ds.setHost("broker.local");
    ds.setPort(1883);
    ds.handleUnknownProperty("topics", List.of("sensor/#"));
    return ds;
  }

  // ─── FROST_BASE ────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("${FROST_BASE}")
  class FrostBase {

    @Test
    @DisplayName("replaces ${FROST_BASE} with targetUrl")
    void resolve_frostBase_replacesWithTargetUrl() throws Exception {
      Map<String, Object> data = Map.of("url", "${FROST_BASE}/Things");
      Map<String, Object> result =
          PlaceholderResolver.resolve(data, "https://frost.example.com", List.of());
      assertEquals("https://frost.example.com/Things", result.get("url"));
    }

    @Test
    @DisplayName("replaces ${FROST_BASE} in deeply nested map")
    void resolve_frostBaseInNestedMap_replacesCorrectly() throws Exception {
      Map<String, Object> data =
          Map.of("output", Map.of("http_client", Map.of("url", "${FROST_BASE}/Datastreams")));

      Map<String, Object> result =
          PlaceholderResolver.resolve(data, "https://frost.local", List.of());

      @SuppressWarnings("unchecked")
      Map<String, Object> output = (Map<String, Object>) result.get("output");
      @SuppressWarnings("unchecked")
      Map<String, Object> httpClient = (Map<String, Object>) output.get("http_client");
      assertEquals("https://frost.local/Datastreams", httpClient.get("url"));
    }

    @Test
    @DisplayName("throws FatalAdapterException when targetUrl is null")
    void resolve_frostBaseNullTargetUrl_throwsFatalException() {
      Map<String, Object> data = Map.of("url", "${FROST_BASE}/Things");
      assertThrows(
          FatalAdapterException.class, () -> PlaceholderResolver.resolve(data, null, List.of()));
    }

    @Test
    @DisplayName("preserves Bloblang ${!...} while resolving ${FROST_BASE}")
    void resolve_frostBaseMixedWithBloblang_onlyResolvesFrostBase() throws Exception {
      String mixed =
          "${FROST_BASE}/Things?$filter=properties/id%20eq%20'${! meta(\"req_id\").string() }'";
      Map<String, Object> data = Map.of("url", mixed);

      Map<String, Object> result =
          PlaceholderResolver.resolve(data, "https://frost.local", List.of());

      String resolved = (String) result.get("url");
      assertTrue(resolved.startsWith("https://frost.local/Things"));
      assertTrue(resolved.contains("${! meta(\"req_id\").string() }"));
    }
  }

  // ─── DATASOURCE[n] DSN ────────────────────────────────────────────────────

  @Nested
  @DisplayName("${DATASOURCE[n]} DSN")
  class DatasourceDsn {

    @Test
    @DisplayName("builds postgres DSN for ${DATASOURCE[0]}")
    void resolve_datasource0_buildsPostgresDsn() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "db.local", 5432, "mydb", "user", "pass");
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[0]}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds));

      String dsn = (String) result.get("dsn");
      assertEquals("postgres://user:pass@db.local:5432/mydb?sslmode=require", dsn);
    }

    @Test
    @DisplayName("builds DSN with password included")
    void resolve_datasource0WithPassword_includesPassword() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "db", "admin", "s3cret");
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[0]}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds));
      assertTrue(((String) result.get("dsn")).contains("admin:s3cret@"));
    }

    @Test
    @DisplayName("builds DSN without password when null")
    void resolve_datasource0WithoutPassword_omitsPassword() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "db", "admin", null);
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[0]}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds));
      String dsn = (String) result.get("dsn");
      assertTrue(dsn.contains("admin@host"));
      assertFalse(dsn.contains(":null@"));
      assertFalse(dsn.contains("admin:@"));
    }

    @Test
    @DisplayName("includes sslmode in DSN")
    void resolve_datasource0WithSslMode_includesSslMode() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "db", "user", "pass");
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[0]}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds));
      assertTrue(((String) result.get("dsn")).contains("sslmode=require"));
    }

    @Test
    @DisplayName("references second datasource with ${DATASOURCE[1]}")
    void resolve_datasource1_referencesSecondDatasource() throws Exception {
      Datasource ds0 = createPostgresDatasource("ds-0", "host0", 5432, "db0", "u0", "p0");
      Datasource ds1 = createPostgresDatasource("ds-1", "host1", 5433, "db1", "u1", "p1");
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[1]}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds0, ds1));

      assertTrue(((String) result.get("dsn")).contains("host1:5433"));
    }

    @Test
    @DisplayName("throws FatalAdapterException when ${DATASOURCE[0]} references MQTT type (no DSN)")
    void resolve_datasource0OnMqttType_throwsFatalException() {
      Datasource ds = new Datasource();
      ds.setId("mqtt-ds");
      ds.setType("mqtt");
      ds.setHost("broker.local");
      ds.setPort(1883);
      ds.handleUnknownProperty("topics", List.of("sensor/#"));

      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[0]}");

      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () -> PlaceholderResolver.resolve(data, null, List.of(ds)));
      assertTrue(ex.getMessage().contains("missing host, database, or username"));
    }

    @Test
    @DisplayName("throws FatalAdapterException for invalid index")
    void resolve_invalidIndex_throwsFatalException() {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "db", "user", "pass");
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[5]}");

      assertThrows(
          FatalAdapterException.class, () -> PlaceholderResolver.resolve(data, null, List.of(ds)));
    }

    @Test
    @DisplayName("throws FatalAdapterException for overflowing index")
    void resolve_overflowIndex_throwsFatalException() {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "db", "user", "pass");
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[99999999999999999999]}");

      assertThrows(
          FatalAdapterException.class, () -> PlaceholderResolver.resolve(data, null, List.of(ds)));
    }
  }

  // ─── DATASOURCE[n].property ───────────────────────────────────────────────

  @Nested
  @DisplayName("${DATASOURCE[n].property}")
  class DatasourceProperty {

    @Test
    @DisplayName("extracts additionalProperty 'database'")
    void resolve_datasource0Database_extractsProperty() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "mydb", "user", "pass");
      // 'database' is in additionalProperties via handleUnknownProperty
      Map<String, Object> data = Map.of("table", "${DATASOURCE[0].database}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds));
      assertEquals("mydb", result.get("table"));
    }

    @Test
    @DisplayName("extracts core field 'host'")
    void resolve_datasource0Host_extractsCoreField() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "db.host.local", 5432, "db", "u", "p");
      Map<String, Object> data = Map.of("server", "${DATASOURCE[0].host}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds));
      assertEquals("db.host.local", result.get("server"));
    }

    @Test
    @DisplayName("extracts port as string")
    void resolve_datasource0Port_extractsPortAsString() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5433, "db", "u", "p");
      Map<String, Object> data = Map.of("p", "${DATASOURCE[0].port}");

      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of(ds));
      assertEquals("5433", result.get("p"));
    }

    @Test
    @DisplayName("throws FatalAdapterException for missing property")
    void resolve_missingProperty_throwsFatalException() {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "db", "u", "p");
      Map<String, Object> data = Map.of("val", "${DATASOURCE[0].nonexistent}");

      assertThrows(
          FatalAdapterException.class, () -> PlaceholderResolver.resolve(data, null, List.of(ds)));
    }
  }

  // ─── Deep nesting ─────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Deep nesting")
  class DeepNesting {

    @Test
    @DisplayName("resolves URL in deeply nested map")
    void resolve_deeplyNestedUrl_resolvesCorrectly() throws Exception {
      Map<String, Object> data =
          Map.of("a", Map.of("b", Map.of("c", Map.of("url", "${FROST_BASE}/Things"))));

      Map<String, Object> result = PlaceholderResolver.resolve(data, "https://frost.io", List.of());

      @SuppressWarnings("unchecked")
      Map<String, Object> c =
          (Map<String, Object>)
              ((Map<String, Object>) ((Map<String, Object>) result.get("a")).get("b")).get("c");
      assertEquals("https://frost.io/Things", c.get("url"));
    }

    @Test
    @DisplayName("resolves placeholders in list of maps")
    void resolve_listOfMaps_resolvesAllEntries() throws Exception {
      Map<String, Object> data =
          Map.of(
              "items", List.of(Map.of("url", "${FROST_BASE}/a"), Map.of("url", "${FROST_BASE}/b")));

      Map<String, Object> result = PlaceholderResolver.resolve(data, "https://frost.io", List.of());

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
      assertEquals("https://frost.io/a", items.get(0).get("url"));
      assertEquals("https://frost.io/b", items.get(1).get("url"));
    }

    @Test
    @DisplayName("resolves multiple placeholders in one string")
    void resolve_multiplePlaceholdersInOneString_resolvesAll() throws Exception {
      Datasource ds = createPostgresDatasource("ds-1", "host", 5432, "mydb", "user", "pass");
      Map<String, Object> data = Map.of("combined", "${FROST_BASE}/data/${DATASOURCE[0].database}");

      Map<String, Object> result =
          PlaceholderResolver.resolve(data, "https://frost.io", List.of(ds));
      assertEquals("https://frost.io/data/mydb", result.get("combined"));
    }
  }

  // ─── Passthrough ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Passthrough (no resolution)")
  class Passthrough {

    @Test
    @DisplayName("returns same content when no placeholders present")
    void resolve_noPlaceholders_returnsSameContent() throws Exception {
      Map<String, Object> data = Map.of("key", "value", "num", 42);
      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of());
      assertEquals("value", result.get("key"));
      assertEquals(42, result.get("num"));
    }

    @Test
    @DisplayName("returns null when data is null")
    void resolve_nullData_returnsNull() throws Exception {
      assertNull(PlaceholderResolver.resolve(null, "url", List.of()));
    }

    @Test
    @DisplayName("preserves Bloblang ${!...} interpolation")
    void resolve_bloblangInterpolation_preserved() throws Exception {
      Map<String, Object> data = Map.of("url", "${! meta(\"frost_url\") }");
      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of());
      assertEquals("${! meta(\"frost_url\") }", result.get("url"));
    }

    @Test
    @DisplayName("preserves env() function references")
    void resolve_envFunction_preserved() throws Exception {
      Map<String, Object> data = Map.of("mapping", "env(\"FROST_BASE\") + \"/Things\"");
      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of());
      assertEquals("env(\"FROST_BASE\") + \"/Things\"", result.get("mapping"));
    }

    @Test
    @DisplayName("preserves unrecognized ${...} placeholders unchanged")
    void resolve_unknownPlaceholder_preserved() throws Exception {
      Map<String, Object> data = Map.of("key", "prefix-${UNKNOWN}-suffix");
      Map<String, Object> result = PlaceholderResolver.resolve(data, null, List.of());
      assertEquals("prefix-${UNKNOWN}-suffix", result.get("key"));
    }
  }

  // ─── Malformed DATASOURCE ─────────────────────────────────────────────────

  @Nested
  @DisplayName("Malformed DATASOURCE placeholders")
  class MalformedDatasource {

    @Test
    @DisplayName("rejects ${DATASOURCE[abc]} with non-numeric index")
    void resolve_datasourceNonNumericIndex_throwsFatalException() {
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[abc]}");
      assertThrows(
          FatalAdapterException.class, () -> PlaceholderResolver.resolve(data, null, List.of()));
    }

    @Test
    @DisplayName("rejects ${DATASOURCE[-1]} with negative index")
    void resolve_datasourceNegativeIndex_throwsFatalException() {
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[-1]}");
      assertThrows(
          FatalAdapterException.class, () -> PlaceholderResolver.resolve(data, null, List.of()));
    }

    @Test
    @DisplayName("rejects ${DATASOURCE[]} with empty brackets")
    void resolve_datasourceEmptyBrackets_throwsFatalException() {
      Map<String, Object> data = Map.of("dsn", "${DATASOURCE[]}");
      assertThrows(
          FatalAdapterException.class, () -> PlaceholderResolver.resolve(data, null, List.of()));
    }
  }

  // ─── Dataset event pattern ────────────────────────────────────────────────

  @Nested
  @DisplayName("Dataset event pipeline pattern")
  class DatasetEventPattern {

    @Test
    @DisplayName("resolves all placeholders from dataset-event.json structure")
    void resolve_datasetEventPipeline_resolvesAllPlaceholders() throws Exception {
      Datasource pgDs =
          createPostgresDatasource(
              "ds-pg", "pg-mobility.neustadt.de", 5432, "mobility", "mobility_reader", null);
      // Add 'table' to additionalProperties
      pgDs.handleUnknownProperty("table", "traffic_counts");

      Map<String, Object> sqlSelect = new HashMap<>();
      sqlSelect.put("driver", "postgres");
      sqlSelect.put("dsn", "${DATASOURCE[0]}");
      sqlSelect.put("table", "${DATASOURCE[0].table}");
      sqlSelect.put("columns", List.of("*"));

      Map<String, Object> httpProcessor = new HashMap<>();
      httpProcessor.put(
          "url",
          "${FROST_BASE}/Things?$filter=properties/id%20eq%20'${! meta(\"req_id\").string() }'");
      httpProcessor.put("verb", "GET");

      Map<String, Object> data = new HashMap<>();
      data.put(
          "pipeline",
          Map.of(
              "processors",
              List.of(
                  Map.of(
                      "branch",
                      Map.of(
                          "processors",
                          List.of(
                              Map.of("sql_select", sqlSelect), Map.of("http", httpProcessor)))))));

      Map<String, Object> result =
          PlaceholderResolver.resolve(
              data, "https://frost.neustadt.de/FROST-Server/v1.1", List.of(pgDs));

      // Verify sql_select.dsn was resolved
      @SuppressWarnings("unchecked")
      Map<String, Object> pipeline = (Map<String, Object>) result.get("pipeline");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> processors = (List<Map<String, Object>>) pipeline.get("processors");
      @SuppressWarnings("unchecked")
      Map<String, Object> branch = (Map<String, Object>) processors.get(0).get("branch");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> branchProcessors =
          (List<Map<String, Object>>) branch.get("processors");

      @SuppressWarnings("unchecked")
      Map<String, Object> resolvedSql =
          (Map<String, Object>) branchProcessors.get(0).get("sql_select");
      String dsn = (String) resolvedSql.get("dsn");
      assertTrue(dsn.startsWith("postgres://mobility_reader@pg-mobility.neustadt.de"));
      assertEquals("traffic_counts", resolvedSql.get("table"));

      // Verify http.url has FROST_BASE resolved but Bloblang preserved
      @SuppressWarnings("unchecked")
      Map<String, Object> resolvedHttp = (Map<String, Object>) branchProcessors.get(1).get("http");
      String url = (String) resolvedHttp.get("url");
      assertTrue(url.startsWith("https://frost.neustadt.de/FROST-Server/v1.1/Things"));
      assertTrue(url.contains("${! meta(\"req_id\").string() }"));
    }
  }
}

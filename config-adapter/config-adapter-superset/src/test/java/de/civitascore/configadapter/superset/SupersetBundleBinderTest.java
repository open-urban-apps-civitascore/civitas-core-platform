/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.superset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.superset.SupersetBundleBinder.BundleBindingException;
import de.civitascore.configadapter.superset.SupersetBundleBinder.DatasetTarget;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class SupersetBundleBinderTest {

  private static final String CORE_DASHBOARD_ID = "94dbe2fd-50f3-4810-8777-f49eb9882a06";
  private static final String TARGET_DATASET_UUID = "4240b845-98ef-4f59-bcda-7f50959f6a31";
  private static final String TARGET_DATABASE_UUID = "ca901373-dd27-40d5-9191-20b3d73041cd";

  @Test
  void bindsDashboardChartDatasetAndDatabaseToThisInstance() {
    Map<String, Object> bound = SupersetBundleBinder.bind(frostBundle(), binding());

    Map<?, ?> dashboard = (Map<?, ?>) bound.get("dashboards/Luftqualitaet_1.yaml");
    assertEquals(CORE_DASHBOARD_ID, dashboard.get("uuid"));
    assertEquals("Luftqualität", dashboard.get("dashboard_title"));
    assertEquals(true, dashboard.get("published"));
    assertNull(dashboard.get("slug"));

    String chartUuid = SupersetBundleBinder.derivedUuid(CORE_DASHBOARD_ID, "chart-old");
    Map<?, ?> chartMeta =
        (Map<?, ?>)
            ((Map<?, ?>) ((Map<?, ?>) dashboard.get("position")).get("CHART-x")).get("meta");
    assertEquals(chartUuid, chartMeta.get("uuid"));

    Map<?, ?> chart = (Map<?, ?>) bound.get("charts/Mittelwert_1.yaml");
    assertEquals(chartUuid, chart.get("uuid"));
    assertEquals(TARGET_DATASET_UUID, chart.get("dataset_uuid"));

    String columnUuid = SupersetBundleBinder.derivedUuid(CORE_DASHBOARD_ID, "column-old");
    Map<?, ?> metric =
        (Map<?, ?>) ((List<?>) ((Map<?, ?>) chart.get("params")).get("metrics")).get(0);
    assertEquals(columnUuid, ((Map<?, ?>) metric.get("column")).get("uuid"));

    Map<?, ?> dataset = (Map<?, ?>) bound.get("datasets/CORE/Observations_3.yaml");
    assertEquals(TARGET_DATASET_UUID, dataset.get("uuid"));
    assertEquals("luftqualit_t_sensorthings_20ca9551", dataset.get("schema"));
    assertEquals("Luftqualität (SensorThings) - Observations (1)", dataset.get("table_name"));
    assertEquals(
        "SELECT * FROM sensordata.luftqualit_t_sensorthings_20ca9551.observations_scoped",
        dataset.get("sql"));
    assertEquals("new-dataset", ((Map<?, ?>) dataset.get("extra")).get("civitasDatasetId"));
    assertEquals(TARGET_DATABASE_UUID, dataset.get("database_uuid"));
    Map<?, ?> column = (Map<?, ?>) ((List<?>) dataset.get("columns")).get(0);
    assertEquals(columnUuid, column.get("uuid"));

    Map<?, ?> database = (Map<?, ?>) bound.get("databases/CORE.yaml");
    assertEquals(TARGET_DATABASE_UUID, database.get("uuid"));
    assertEquals("CORE", database.get("database_name"));
  }

  @Test
  void leavesTheInputUnchanged() {
    Map<String, Object> bundle = frostBundle();
    SupersetBundleBinder.bind(bundle, binding());
    assertEquals(
        "dash-old", ((Map<?, ?>) bundle.get("dashboards/Luftqualitaet_1.yaml")).get("uuid"));
  }

  @Test
  void refusesAFreeSqlDataset() {
    Map<String, Object> bundle = frostBundle();
    Map<String, Object> dataset = map("uuid", "ds-old", "table_name", "joined", "sql", "SELECT 1");
    bundle.put("datasets/CORE/Observations_3.yaml", dataset);

    assertThrows(BundleBindingException.class, () -> SupersetBundleBinder.bind(bundle, binding()));
  }

  @Test
  void refusesADatasetThatThisCoreDatasetLacks() {
    Map<String, Object> bundle = frostBundle();
    Map<String, Object> dataset = map("uuid", "ds-old", "table_name", "verkehrsmessung", "sql", "");
    bundle.put("datasets/CORE/Observations_3.yaml", dataset);

    assertThrows(BundleBindingException.class, () -> SupersetBundleBinder.bind(bundle, binding()));
  }

  @Test
  void refusesAnExportWithoutDashboard() {
    Map<String, Object> bundle = frostBundle();
    bundle.remove("dashboards/Luftqualitaet_1.yaml");

    assertThrows(BundleBindingException.class, () -> SupersetBundleBinder.bind(bundle, binding()));
  }

  @Test
  void putsEveryFileOneFolderDeepInTheZip() throws IOException {
    Map<String, Object> bound = SupersetBundleBinder.bind(frostBundle(), binding());

    List<String> names = new ArrayList<>();
    try (ZipInputStream zip =
        new ZipInputStream(new ByteArrayInputStream(SupersetBundleBinder.toZip(bound)))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        names.add(entry.getName());
        if (entry.getName().endsWith("metadata.yaml")) {
          Map<?, ?> metadata = new ObjectMapper().readValue(zip.readAllBytes(), Map.class);
          assertEquals("Dashboard", metadata.get("type"));
        }
      }
    }
    assertEquals(bound.size(), names.size());
    assertTrue(names.stream().allMatch(name -> name.startsWith("dashboard_export/")));
  }

  private static SupersetBundleBinder.Binding binding() {
    DatasetTarget observations =
        new DatasetTarget(
            TARGET_DATASET_UUID,
            "luftqualit_t_sensorthings_20ca9551",
            "geodata",
            "Luftqualität (SensorThings) - Observations (1)",
            "SELECT * FROM sensordata.luftqualit_t_sensorthings_20ca9551.observations_scoped",
            map("civitasDatasetId", "new-dataset", "frostView", "observations_scoped"));
    return new SupersetBundleBinder.Binding(
        CORE_DASHBOARD_ID,
        "Luftqualität",
        true,
        TARGET_DATABASE_UUID,
        "CORE",
        Map.of(),
        Map.of("observations_scoped", observations));
  }

  /** The shape of a real Superset 7 export of a dashboard on a FROST view. */
  private static Map<String, Object> frostBundle() {
    Map<String, Object> bundle = new LinkedHashMap<>();
    bundle.put("metadata.yaml", map("version", "1.0.0", "type", "Dashboard"));
    bundle.put(
        "dashboards/Luftqualitaet_1.yaml",
        map(
            "dashboard_title",
            "Testdashboard-1",
            "uuid",
            "dash-old",
            "slug",
            "old-slug",
            "published",
            false,
            "position",
            map("CHART-x", map("type", "CHART", "meta", map("uuid", "chart-old", "chartId", 7)))));
    bundle.put(
        "charts/Mittelwert_1.yaml",
        map(
            "slice_name",
            "diagram-1",
            "uuid",
            "chart-old",
            "dataset_uuid",
            "ds-old",
            "params",
            map(
                "datasource",
                "3__table",
                "metrics",
                List.of(
                    map(
                        "aggregate",
                        "AVG",
                        "column",
                        map("column_name", "result_number", "uuid", "column-old"))))));
    bundle.put(
        "datasets/CORE/Observations_3.yaml",
        map(
            "table_name",
            "Luftqualität (SensorThings) - Observations (1)",
            "catalog",
            "geodata",
            "schema",
            "old_schema_12345678",
            "sql",
            "SELECT * FROM sensordata.old_schema_12345678.observations_scoped",
            "extra",
            map("civitasDatasetId", "old-dataset", "frostView", "observations_scoped"),
            "uuid",
            "ds-old",
            "database_uuid",
            "db-old",
            "columns",
            List.of(map("column_name", "result_number", "uuid", "column-old")),
            "metrics",
            List.of(map("metric_name", "count", "uuid", "metric-old"))));
    bundle.put("databases/CORE.yaml", map("database_name", "CORE", "uuid", "db-old"));
    return bundle;
  }

  /** A mutable map from key-value pairs; {@code Map.of} rejects the null values an export holds. */
  private static Map<String, Object> map(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }
}

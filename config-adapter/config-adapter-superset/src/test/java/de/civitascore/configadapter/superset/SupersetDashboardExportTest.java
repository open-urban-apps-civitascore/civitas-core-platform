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

import de.civitascore.configadapter.model.dashboardexport.DashboardExportException;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult.Reason;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class SupersetDashboardExportTest {

  private static final String ROOT = "dashboard_export_20261110T120000/";

  private static final String METADATA =
      """
      version: 1.0.0
      type: Dashboard
      timestamp: '2026-11-10T12:00:00.000000+00:00'
      """;

  private static final String DASHBOARD =
      """
      dashboard_title: Luftmessung
      uuid: 11111111-1111-1111-1111-111111111111
      position: {}
      version: 1.0.0
      """;

  private static final String CHART =
      """
      slice_name: Feinstaub
      uuid: 22222222-2222-2222-2222-222222222222
      dataset_uuid: 33333333-3333-3333-3333-333333333333
      version: 1.0.0
      """;

  private static final String FROST_DATASET =
      """
      table_name: Luftqualitaet - Observations
      schema: luftqualitaet_frost
      catalog: sensordata
      sql: SELECT * FROM frost.observations_scoped
      extra:
        frostView: observations_scoped
        civitasDatasetId: 0a1b2c3d-0000-0000-0000-000000000000
      metrics:
      - metric_name: count
        expression: COUNT(*)
        uuid: 55555555-5555-5555-5555-555555555555
      uuid: 33333333-3333-3333-3333-333333333333
      database_uuid: 44444444-4444-4444-4444-444444444444
      version: 1.0.0
      """;

  private static final String DATABASE =
      """
      database_name: CORE
      sqlalchemy_uri: trino://superset_trino:XXXXXXXXXX@civitas-trino:8446/geodata/information_schema
      expose_in_sqllab: true
      extra:
        engine_params: {}
      uuid: 44444444-4444-4444-4444-444444444444
      version: 1.0.0
      """;

  @Test
  void keepsTheFilesOfTheExportWithoutTheRootFolder() throws IOException {
    Map<String, Object> document = SupersetDashboardExport.fromZip(zip(export()));

    assertEquals("superset", document.get("tool"));
    Map<?, ?> files = (Map<?, ?>) document.get("files");
    assertEquals(
        Set.of(
            "metadata.yaml",
            "dashboards/Luftmessung_1.yaml",
            "charts/Feinstaub_1.yaml",
            "datasets/CORE/Luftqualitaet_Observations_1.yaml",
            "databases/CORE.yaml"),
        files.keySet());
    Map<String, Object> dashboard = file(document, "dashboards/Luftmessung_1.yaml");
    assertEquals("Luftmessung", dashboard.get("dashboard_title"));
  }

  @Test
  void leavesOutFilesThatAreNoPartOfADashboardExport() throws IOException {
    Map<String, String> files = export();
    files.put(ROOT + "tags.yaml", "tags: []\n");
    files.put("outside.yaml", "a: 1\n");

    Map<?, ?> kept = (Map<?, ?>) SupersetDashboardExport.fromZip(zip(files)).get("files");

    assertTrue(kept.keySet().stream().noneMatch(path -> "tags.yaml".equals(path)));
    assertTrue(kept.keySet().stream().noneMatch(path -> "outside.yaml".equals(path)));
  }

  @Test
  void keepsOnlyTheFrostViewMarkerOfADatasetAndDropsWhatNamesThisInstance() throws IOException {
    Map<String, Object> dataset =
        file(
            SupersetDashboardExport.fromZip(zip(export())),
            "datasets/CORE/Luftqualitaet_Observations_1.yaml");

    assertNull(dataset.get("schema"));
    assertNull(dataset.get("catalog"));
    assertNull(dataset.get("sql"));
    assertEquals(Map.of("frostView", "observations_scoped"), dataset.get("extra"));
    assertEquals("Luftqualitaet - Observations", dataset.get("table_name"));
    assertEquals("33333333-3333-3333-3333-333333333333", dataset.get("uuid"));
  }

  @Test
  void rebuildsTheDatabaseFileFromTheFieldsThatNameTheConnection() throws IOException {
    Map<String, Object> database =
        file(SupersetDashboardExport.fromZip(zip(export())), "databases/CORE.yaml");

    assertEquals(
        Map.of(
            "database_name", "CORE",
            "uuid", "44444444-4444-4444-4444-444444444444",
            "version", "1.0.0",
            "sqlalchemy_uri", "trino://"),
        database);
  }

  @Test
  void refusesADatasetWithItsOwnSql() throws IOException {
    Map<String, String> files = export();
    files.put(
        ROOT + "datasets/CORE/Luftqualitaet_Observations_1.yaml",
        FROST_DATASET.replace("  frostView: observations_scoped\n", ""));

    DashboardExportException refusal = refusal(files);

    assertEquals(Reason.REFUSED, refusal.reason());
    assertTrue(refusal.getMessage().contains("own SQL query"));
  }

  @Test
  void refusesAnExportFormatItDoesNotKnow() throws IOException {
    Map<String, String> files = export();
    files.put(ROOT + "metadata.yaml", METADATA.replace("1.0.0", "2.0.0"));

    DashboardExportException refusal = refusal(files);

    assertEquals(Reason.REFUSED, refusal.reason());
    assertTrue(refusal.getMessage().contains("2.0.0"));
  }

  @Test
  void refusesADashboardThatReadsTwoDatabases() throws IOException {
    Map<String, String> files = export();
    files.put(ROOT + "databases/OTHER.yaml", DATABASE.replace("CORE", "OTHER"));

    assertEquals(Reason.REFUSED, refusal(files).reason());
  }

  @Test
  void refusesAFileNameThatLeavesItsFolder() throws IOException {
    Map<String, String> files = export();
    files.put(ROOT + "charts/../escape.yaml", CHART);

    assertEquals(Reason.REFUSED, refusal(files).reason());
  }

  @Test
  void refusesAnExportThatIsTooLargeUnpacked() throws IOException {
    Map<String, String> files = export();
    files.put(
        ROOT + "charts/Large_2.yaml",
        "slice_name: " + "x".repeat(SupersetDashboardExport.MAX_UNZIPPED_BYTES) + "\n");

    assertEquals(Reason.REFUSED, refusal(files).reason());
  }

  @Test
  void reportsAnAnswerThatIsNoZipAsUnavailable() {
    DashboardExportException failure =
        assertThrows(
            DashboardExportException.class,
            () ->
                SupersetDashboardExport.fromZip(
                    "<html>error</html>".getBytes(StandardCharsets.UTF_8)));

    assertEquals(Reason.UNAVAILABLE, failure.reason());
  }

  private static Map<String, String> export() {
    Map<String, String> files = new LinkedHashMap<>();
    files.put(ROOT + "metadata.yaml", METADATA);
    files.put(ROOT + "dashboards/Luftmessung_1.yaml", DASHBOARD);
    files.put(ROOT + "charts/Feinstaub_1.yaml", CHART);
    files.put(ROOT + "datasets/CORE/Luftqualitaet_Observations_1.yaml", FROST_DATASET);
    files.put(ROOT + "databases/CORE.yaml", DATABASE);
    return files;
  }

  private static DashboardExportException refusal(Map<String, String> files) throws IOException {
    byte[] zip = zip(files);
    return assertThrows(DashboardExportException.class, () -> SupersetDashboardExport.fromZip(zip));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> file(Map<String, Object> document, String path) {
    return (Map<String, Object>) ((Map<?, ?>) document.get("files")).get(path);
  }

  private static byte[] zip(Map<String, String> files) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (Map.Entry<String, String> file : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(file.getKey()));
        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }
}

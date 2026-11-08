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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Binds a Superset dashboard export to this instance before it is imported, so that the import
 * overwrites the dashboard shell and the datasets the saga created instead of adding copies.
 *
 * <p>The bundle is the export in package form: each file path without the export's root folder,
 * mapped to the parsed file. The binding rules:
 *
 * <ul>
 *   <li>The dashboard takes the CORE dashboard ID as its UUID. The saga creates the shell under
 *       that UUID, so the import replaces exactly that shell.
 *   <li>Each dataset of the export is bound to a dataset the saga registered for this CORE dataset:
 *       by its {@code extra.frostView} marker for FROST views, else by its table name. The dataset
 *       takes that dataset's UUID, schema, catalog, table name, SQL and {@code extra}, so the
 *       import keeps the platform's definition and its stamp. A SQL dataset that is not a FROST
 *       view is refused: its SQL would come from the package, not from the platform.
 *   <li>The database takes the UUID and name of the platform's connection.
 *   <li>Charts, dataset columns and metrics get UUIDs derived from the dashboard UUID, so a second
 *       dashboard from the same export never collides with the first.
 * </ul>
 */
final class SupersetBundleBinder {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ROOT_FOLDER = "dashboard_export/";
  private static final String DASHBOARDS = "dashboards/";
  private static final String CHARTS = "charts/";
  private static final String DATASETS = "datasets/";
  private static final String DATABASES = "databases/";
  private static final String UUID_KEY = "uuid";

  /** A dataset the saga registered, as Superset reports it. */
  record DatasetTarget(
      String uuid,
      String schema,
      String catalog,
      String tableName,
      String sql,
      Map<String, Object> extra) {
    DatasetTarget {
      // Not Map.copyOf: Superset's extra may hold null values.
      extra = extra == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }
  }

  /**
   * What the export is bound to.
   *
   * @param dashboardUuid the CORE dashboard ID, which is also the UUID of the Superset shell
   * @param dashboardTitle the title from the CORE dashboard model
   * @param published whether CORE releases the dashboard
   * @param databaseUuid UUID of the platform's Superset database connection
   * @param databaseName name of that connection; {@code null} keeps the name in the export
   * @param tableTargets registered PostGIS datasets by table name
   * @param frostViewTargets registered FROST datasets by view name (e.g. {@code
   *     observations_scoped})
   */
  record Binding(
      String dashboardUuid,
      String dashboardTitle,
      boolean published,
      String databaseUuid,
      String databaseName,
      Map<String, DatasetTarget> tableTargets,
      Map<String, DatasetTarget> frostViewTargets) {
    Binding {
      tableTargets = tableTargets == null ? Map.of() : Map.copyOf(tableTargets);
      frostViewTargets = frostViewTargets == null ? Map.of() : Map.copyOf(frostViewTargets);
    }
  }

  /** The export cannot be bound to this instance. */
  static final class BundleBindingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    BundleBindingException(String message) {
      super(message);
    }
  }

  private SupersetBundleBinder() {}

  /** Returns a bound copy of {@code bundle}; the input is not changed. */
  static Map<String, Object> bind(Map<?, ?> bundle, Binding binding) {
    Map<String, Object> files =
        MAPPER.convertValue(bundle, new TypeReference<LinkedHashMap<String, Object>>() {});
    validate(files);

    Map<String, String> replacements = new HashMap<>();
    Map<String, DatasetTarget> targetsByPath = new HashMap<>();
    for (Map.Entry<String, Object> file : files.entrySet()) {
      String path = file.getKey();
      Map<String, Object> content = asMap(file.getValue(), path);
      if (path.startsWith(DASHBOARDS)) {
        replacements.put(requireUuid(content, path), binding.dashboardUuid());
      } else if (path.startsWith(CHARTS)) {
        String uuid = requireUuid(content, path);
        replacements.put(uuid, derivedUuid(binding.dashboardUuid(), uuid));
      } else if (path.startsWith(DATASETS)) {
        DatasetTarget target = targetFor(content, path, binding);
        targetsByPath.put(path, target);
        replacements.put(requireUuid(content, path), target.uuid());
        for (String nested : nestedUuids(content)) {
          replacements.put(nested, derivedUuid(binding.dashboardUuid(), nested));
        }
      } else if (path.startsWith(DATABASES)) {
        replacements.put(requireUuid(content, path), binding.databaseUuid());
      }
    }

    Map<String, Object> bound = new LinkedHashMap<>();
    for (Map.Entry<String, Object> file : files.entrySet()) {
      String path = file.getKey();
      Map<String, Object> copy = asMap(replaceUuids(file.getValue(), replacements), path);
      if (path.startsWith(DASHBOARDS)) {
        copy.put("dashboard_title", binding.dashboardTitle());
        copy.put("published", binding.published());
        // Superset needs unique slugs; the portal links by ID.
        copy.put("slug", null);
      } else if (path.startsWith(DATASETS)) {
        DatasetTarget target = targetsByPath.get(path);
        copy.put("schema", target.schema());
        copy.put("catalog", target.catalog());
        copy.put("table_name", target.tableName());
        copy.put("sql", target.sql());
        copy.put("extra", new LinkedHashMap<>(target.extra()));
      } else if (path.startsWith(DATABASES) && binding.databaseName() != null) {
        copy.put("database_name", binding.databaseName());
      }
      bound.put(path, copy);
    }
    return bound;
  }

  /**
   * The upload for Superset's import endpoint: every file one folder deep, because Superset drops
   * the first path segment. Written as JSON, which is valid YAML: Superset reads the files with
   * PyYAML, as it reads the YAML it exports.
   */
  static byte[] toZip(Map<String, Object> files) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (Map.Entry<String, Object> file : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(ROOT_FOLDER + file.getKey()));
        zip.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(file.getValue()));
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not build the dashboard import ZIP", e);
    }
    return bytes.toByteArray();
  }

  /** A stable UUID per dashboard for an object of the export. */
  static String derivedUuid(String dashboardUuid, String uuid) {
    return UUID.nameUUIDFromBytes((dashboardUuid + ":" + uuid).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private static void validate(Map<String, Object> files) {
    if (!(files.get("metadata.yaml") instanceof Map<?, ?> metadata)
        || !"Dashboard".equals(metadata.get("type"))) {
      throw new BundleBindingException("metadata.yaml does not describe a dashboard export");
    }
    long dashboards = files.keySet().stream().filter(path -> path.startsWith(DASHBOARDS)).count();
    if (dashboards != 1) {
      throw new BundleBindingException("exactly one dashboard expected, found " + dashboards);
    }
    long databases = files.keySet().stream().filter(path -> path.startsWith(DATABASES)).count();
    if (databases > 1) {
      throw new BundleBindingException(
          "the dashboard reads "
              + databases
              + " databases; only the platform's connection is bound");
    }
  }

  private static DatasetTarget targetFor(
      Map<String, Object> dataset, String path, Binding binding) {
    String frostView = stringField(extraOf(dataset), SupersetResourceClient.FROST_VIEW_KEY);
    if (frostView != null) {
      DatasetTarget target = binding.frostViewTargets().get(frostView);
      if (target == null) {
        throw new BundleBindingException(
            "'" + path + "' reads the FROST view " + frostView + ", which this dataset lacks");
      }
      return target;
    }
    if (dataset.get("sql") instanceof String sql && !sql.isBlank()) {
      throw new BundleBindingException(
          "'"
              + path
              + "' is a SQL dataset; only table datasets and the platform's FROST views are bound");
    }
    String tableName = stringField(dataset, "table_name");
    DatasetTarget target = tableName == null ? null : binding.tableTargets().get(tableName);
    if (target == null) {
      throw new BundleBindingException(
          "'" + path + "' reads the table " + tableName + ", which this dataset lacks");
    }
    return target;
  }

  /** Superset exports {@code extra} as a mapping; older exports carry it as a JSON string. */
  private static Map<?, ?> extraOf(Map<String, Object> dataset) {
    Object extra = dataset.get("extra");
    if (extra instanceof Map<?, ?> map) {
      return map;
    }
    if (extra instanceof String text && !text.isBlank()) {
      try {
        return MAPPER.readValue(text, Map.class);
      } catch (JsonProcessingException e) {
        return Map.of();
      }
    }
    return Map.of();
  }

  private static List<String> nestedUuids(Map<String, Object> dataset) {
    List<String> uuids = new ArrayList<>();
    for (String key : List.of("columns", "metrics")) {
      if (dataset.get(key) instanceof List<?> items) {
        for (Object item : items) {
          String uuid = item instanceof Map<?, ?> entry ? stringField(entry, UUID_KEY) : null;
          if (uuid != null) {
            uuids.add(uuid);
          }
        }
      }
    }
    return uuids;
  }

  /** A deep copy in which every string and every key equal to a mapped UUID is replaced. */
  private static Object replaceUuids(Object value, Map<String, String> replacements) {
    if (value instanceof String text) {
      return replacements.getOrDefault(text, text);
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      for (Object item : list) {
        copy.add(replaceUuids(item, replacements));
      }
      return copy;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = String.valueOf(entry.getKey());
        copy.put(replacements.getOrDefault(key, key), replaceUuids(entry.getValue(), replacements));
      }
      return copy;
    }
    return value;
  }

  private static String requireUuid(Map<String, Object> content, String path) {
    String uuid = stringField(content, UUID_KEY);
    if (uuid == null) {
      throw new BundleBindingException("'" + path + "' has no uuid");
    }
    return uuid;
  }

  private static String stringField(Map<?, ?> map, String key) {
    return map.get(key) instanceof String value && !value.isBlank() ? value : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value, String path) {
    if (value instanceof Map<?, ?>) {
      return (Map<String, Object>) value;
    }
    throw new BundleBindingException("'" + path + "' is not a mapping");
  }
}

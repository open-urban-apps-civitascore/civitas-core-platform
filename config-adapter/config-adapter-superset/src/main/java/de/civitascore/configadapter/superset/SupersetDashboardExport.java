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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportException;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult.Reason;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns Superset's export ZIP of one dashboard into the document that is shared: {@code tool} and
 * {@code files}, each file path without the export's root folder mapped to the parsed YAML file.
 * The receiving instance gives this document to {@link SupersetBundleBinder}, so the rules follow
 * the binder's:
 *
 * <ul>
 *   <li>Only the export format this class knows is read ({@code metadata.yaml}: {@code version
 *       1.0.0}, {@code type Dashboard}). A new format is refused, so that it is checked before it
 *       is shared.
 *   <li>Only the files of a dashboard export are kept: {@code metadata.yaml} and the folders {@code
 *       dashboards/}, {@code charts/}, {@code datasets/} and {@code databases/}.
 *   <li>Exactly one dashboard and at most one database.
 *   <li>A dataset with its own SQL is refused, unless it is one of the platform's FROST views: the
 *       receiving instance would run SQL that came in the package.
 *   <li>Of each dataset, what names this instance stays here: schema, catalog, SQL and every {@code
 *       extra} field but {@code frostView}. The binder sets them from the datasets that the
 *       receiving instance registered.
 *   <li>Each database file keeps only the fields that name the connection. Its address, user and
 *       engine settings stay here.
 * </ul>
 *
 * <p>Limits keep a broken or hostile ZIP small: {@value #MAX_ZIP_BYTES} bytes packed, {@value
 * #MAX_ENTRIES} entries, {@value #MAX_UNZIPPED_BYTES} bytes unpacked and {@value
 * #MAX_DOCUMENT_BYTES} bytes for the document. Metric and column expressions of a dataset are SQL
 * too; they travel unchanged, as on install.
 */
final class SupersetDashboardExport {

  static final String KNOWN_VERSION = "1.0.0";
  static final int MAX_ZIP_BYTES = 2 * 1024 * 1024;
  static final int MAX_ENTRIES = 200;
  static final int MAX_UNZIPPED_BYTES = 4 * 1024 * 1024;
  static final int MAX_DOCUMENT_BYTES = 512 * 1024;

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<LinkedHashMap<String, Object>> MAPPING =
      new TypeReference<>() {};

  private static final String METADATA = "metadata.yaml";
  private static final String DASHBOARDS = "dashboards/";
  private static final String CHARTS = "charts/";
  private static final String DATASETS = "datasets/";
  private static final String DATABASES = "databases/";
  private static final List<String> FOLDERS = List.of(DASHBOARDS, CHARTS, DATASETS, DATABASES);
  private static final List<String> DATABASE_FIELDS = List.of("database_name", "uuid", "version");

  /** File names as Superset writes them into an export. */
  private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

  private static final Pattern URI_SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://");

  private SupersetDashboardExport() {}

  /**
   * @throws DashboardExportException {@link Reason#REFUSED} if the dashboard holds what must not be
   *     shared, {@link Reason#UNAVAILABLE} if Superset's answer cannot be read
   */
  static Map<String, Object> fromZip(byte[] zip) {
    Map<String, Map<String, Object>> files = readFiles(zip);
    requireKnownFormat(files.get(METADATA));
    long dashboards = count(files, DASHBOARDS);
    if (dashboards != 1) {
      throw refused("Exactly one dashboard is expected, the export has " + dashboards + ".");
    }
    long databases = count(files, DATABASES);
    if (databases > 1) {
      throw refused(
          "The dashboard reads "
              + databases
              + " databases. Only one is shared, bound to the connection of the platform.");
    }

    Map<String, Object> cleaned = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Object>> file : files.entrySet()) {
      String path = file.getKey();
      if (path.startsWith(DATASETS)) {
        cleaned.put(path, cleanDataset(path, file.getValue()));
      } else if (path.startsWith(DATABASES)) {
        cleaned.put(path, cleanDatabase(file.getValue()));
      } else {
        cleaned.put(path, file.getValue());
      }
    }
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("tool", "superset");
    document.put("files", cleaned);
    requireSize(document);
    return document;
  }

  private static Map<String, Map<String, Object>> readFiles(byte[] zip) {
    if (zip.length > MAX_ZIP_BYTES) {
      throw refused("The export is larger than " + MAX_ZIP_BYTES / (1024 * 1024) + " MB.");
    }
    Map<String, Map<String, Object>> files = new TreeMap<>();
    int entries = 0;
    int unzipped = 0;
    try (ZipInputStream in =
        new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        entries++;
        if (entries > MAX_ENTRIES) {
          throw refused("The export has more than " + MAX_ENTRIES + " files.");
        }
        String path = withoutRootFolder(entry.getName());
        if (entry.isDirectory() || !isKept(path)) {
          continue;
        }
        requireSafe(path);
        // One byte more than is left tells a file that is too large from one that fits exactly.
        byte[] content = in.readNBytes(MAX_UNZIPPED_BYTES - unzipped + 1);
        unzipped += content.length;
        if (unzipped > MAX_UNZIPPED_BYTES) {
          throw refused(
              "The export is larger than " + MAX_UNZIPPED_BYTES / (1024 * 1024) + " MB unpacked.");
        }
        if (files.put(path, parse(content, path)) != null) {
          throw refused("The export holds the file " + path + " twice.");
        }
      }
    } catch (IOException e) {
      throw unavailable("The answer of Superset is not a readable ZIP file.");
    }
    // A ZipInputStream reads any other content, such as an error page, as an empty ZIP.
    if (entries == 0) {
      throw unavailable("The answer of Superset is not a readable ZIP file.");
    }
    return files;
  }

  /** Superset puts every file under one root folder; a file outside it is no part of the export. */
  private static String withoutRootFolder(String name) {
    int slash = name.indexOf('/');
    return slash < 0 ? null : name.substring(slash + 1);
  }

  private static boolean isKept(String path) {
    return path != null && (METADATA.equals(path) || FOLDERS.stream().anyMatch(path::startsWith));
  }

  /** The path becomes a key of the shared document; it must stay a plain relative file name. */
  private static void requireSafe(String path) {
    for (String segment : path.split("/", -1)) {
      if (!SAFE_SEGMENT.matcher(segment).matches() || ".".equals(segment) || "..".equals(segment)) {
        throw refused("A file of the export has a name that a Superset export does not use.");
      }
    }
  }

  private static Map<String, Object> parse(byte[] content, String path) {
    try {
      Map<String, Object> file = YAML.readValue(content, MAPPING);
      if (file == null) {
        throw unavailable("The file " + path + " of the export is empty.");
      }
      return file;
    } catch (IOException e) {
      throw unavailable("The file " + path + " of the export is not a readable YAML mapping.");
    }
  }

  private static void requireKnownFormat(Map<String, Object> metadata) {
    if (metadata == null || !"Dashboard".equals(metadata.get("type"))) {
      throw refused("The export does not describe a dashboard: metadata.yaml is missing or wrong.");
    }
    Object version = metadata.get("version");
    if (!KNOWN_VERSION.equals(version)) {
      String found = version == null ? "none" : String.valueOf(version);
      throw refused(
          "The export format "
              + found.substring(0, Math.min(found.length(), 20))
              + " is unknown (known: "
              + KNOWN_VERSION
              + "). A new format is checked before it is shared.");
    }
  }

  private static long count(Map<String, Map<String, Object>> files, String folder) {
    return files.keySet().stream().filter(path -> path.startsWith(folder)).count();
  }

  private static Map<String, Object> cleanDataset(String path, Map<String, Object> dataset) {
    String frostView = frostViewOf(dataset.get("extra"));
    boolean ownSql = dataset.get("sql") instanceof String sql && !sql.isBlank();
    if (ownSql && frostView == null) {
      throw refused(
          "The dataset "
              + path
              + " reads with its own SQL query. Only tables and the FROST views of the platform"
              + " are shared.");
    }
    Map<String, Object> copy = new LinkedHashMap<>(dataset);
    copy.put("schema", null);
    copy.put("catalog", null);
    copy.put("sql", null);
    Map<String, Object> extra = new LinkedHashMap<>();
    if (frostView != null) {
      extra.put(SupersetResourceClient.FROST_VIEW_KEY, frostView);
    }
    copy.put("extra", extra);
    return copy;
  }

  /** Superset exports {@code extra} as a mapping; older exports carry it as a JSON string. */
  private static String frostViewOf(Object extra) {
    Object parsed = extra;
    if (extra instanceof String text && !text.isBlank()) {
      try {
        parsed = JSON.readValue(text, MAPPING);
      } catch (JsonProcessingException e) {
        return null;
      }
    }
    if (parsed instanceof Map<?, ?> map
        && map.get(SupersetResourceClient.FROST_VIEW_KEY) instanceof String view
        && !view.isBlank()) {
      return view;
    }
    return null;
  }

  private static Map<String, Object> cleanDatabase(Map<String, Object> database) {
    Map<String, Object> kept = new LinkedHashMap<>();
    for (String field : DATABASE_FIELDS) {
      if (database.containsKey(field)) {
        kept.put(field, database.get(field));
      }
    }
    kept.put("sqlalchemy_uri", schemeOf(database.get("sqlalchemy_uri")) + "://");
    return kept;
  }

  private static String schemeOf(Object uri) {
    if (uri instanceof String text) {
      Matcher scheme = URI_SCHEME.matcher(text);
      if (scheme.find()) {
        return scheme.group(1);
      }
    }
    return "trino";
  }

  private static void requireSize(Map<String, Object> document) {
    byte[] json;
    try {
      json = JSON.writeValueAsBytes(document);
    } catch (JsonProcessingException e) {
      throw unavailable("The export cannot be written as JSON.");
    }
    if (json.length > MAX_DOCUMENT_BYTES) {
      throw refused("The export is larger than " + MAX_DOCUMENT_BYTES / 1024 + " KB.");
    }
  }

  private static DashboardExportException refused(String message) {
    return new DashboardExportException(Reason.REFUSED, message);
  }

  private static DashboardExportException unavailable(String message) {
    return new DashboardExportException(Reason.UNAVAILABLE, message);
  }
}

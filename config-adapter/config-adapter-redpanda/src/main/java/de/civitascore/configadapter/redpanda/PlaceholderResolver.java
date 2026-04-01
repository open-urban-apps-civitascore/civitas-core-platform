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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recursively resolves {@code ${...}} placeholders in pipeline data maps.
 *
 * <p>Supported placeholders:
 *
 * <ul>
 *   <li>{@code ${FROST_BASE}} — replaced with the target URL. When followed by {@code
 *       /Observations} or {@code /Datastreams}, any trailing {@code /Projects(...)} segment is
 *       stripped from the target URL so those entity types resolve against the FROST root. When
 *       followed by {@code /Things} (or anything else), the full target URL is used as-is.
 *   <li>{@code ${DATASOURCE[n]}} — replaced with a DSN string for the n-th datasource
 *   <li>{@code ${DATASOURCE[n].property}} — replaced with a single property value
 * </ul>
 *
 * <p>Bloblang interpolation ({@code ${!...}}) is explicitly preserved by a negative lookahead in
 * the regex pattern.
 */
final class PlaceholderResolver {

  private static final Logger log = LoggerFactory.getLogger(PlaceholderResolver.class);

  private static final Pattern FROST_SUFFIX_PATTERN =
      Pattern.compile("^/(Things|Observations|Datastreams)");

  private static final Pattern PROJECTS_SUFFIX_PATTERN = Pattern.compile("/Projects\\([^)]*\\)$");

  // Core Datasource field names used in getCoreField switch
  private static final String FIELD_ID = "id";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_DESCRIPTION = "description";
  private static final String FIELD_HOST = "host";
  private static final String FIELD_PORT = "port";

  /**
   * Pattern matching our placeholders while ignoring Bloblang {@code ${!...}} interpolation.
   *
   * <ul>
   *   <li>Group 0: full match {@code ${FROST_BASE}} or {@code ${DATASOURCE[0].host}}
   *   <li>Group 1: entire expression inside {@code ${}} (e.g. {@code FROST_BASE} or {@code
   *       DATASOURCE[0].host})
   *   <li>Group 2: datasource index (e.g. {@code 0}) — null for FROST_BASE
   *   <li>Group 3: datasource property (e.g. {@code host}) — null if absent
   * </ul>
   */
  private static final Pattern PLACEHOLDER_PATTERN =
      Pattern.compile("\\$\\{(?!!)(FROST_BASE|DATASOURCE\\[(\\d+)](?:\\.(\\w+))?)\\}");

  private PlaceholderResolver() {}

  /**
   * Returns a deep copy of {@code pipelineData} with all supported placeholders resolved.
   *
   * @param pipelineData the pipeline definition as a Map (may be null)
   * @param targetUrl the FROST target URL for {@code ${FROST_BASE}} resolution (may contain a
   *     trailing {@code /Projects(...)} segment that is stripped for non-Things entity types)
   * @param datasources the datasource list for {@code ${DATASOURCE[n]}} resolution
   * @return the resolved map, or null if pipelineData was null
   * @throws FatalAdapterException on invalid placeholder references
   */
  static Map<String, Object> resolve(
      Map<String, Object> pipelineData, String targetUrl, List<Datasource> datasources)
      throws FatalAdapterException {
    if (pipelineData == null) return null;
    return resolveMap(pipelineData, targetUrl, datasources != null ? datasources : List.of());
  }

  // ─── Recursive traversal ──────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static Object resolveValue(Object value, String targetUrl, List<Datasource> datasources)
      throws FatalAdapterException {
    if (value instanceof String s) {
      return resolveString(s, targetUrl, datasources);
    }
    if (value instanceof Map<?, ?> m) {
      return resolveMap((Map<String, Object>) m, targetUrl, datasources);
    }
    if (value instanceof List<?> list) {
      return resolveList(list, targetUrl, datasources);
    }
    return value;
  }

  private static Map<String, Object> resolveMap(
      Map<String, Object> map, String targetUrl, List<Datasource> datasources)
      throws FatalAdapterException {
    if (map == null) return Map.of();
    Map<String, Object> result = new LinkedHashMap<>(map.size());
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      result.put(entry.getKey(), resolveValue(entry.getValue(), targetUrl, datasources));
    }
    return result;
  }

  private static List<Object> resolveList(
      List<?> list, String targetUrl, List<Datasource> datasources) throws FatalAdapterException {
    if (list == null) return List.of();
    List<Object> result = new ArrayList<>(list.size());
    for (Object item : list) {
      result.add(resolveValue(item, targetUrl, datasources));
    }
    return result;
  }

  // ─── String placeholder resolution ────────────────────────────────────────

  private static String resolveString(String input, String targetUrl, List<Datasource> datasources)
      throws FatalAdapterException {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
    if (!matcher.find()) {
      validateNoMalformedDatasource(input);
      return input;
    }

    StringBuilder sb = new StringBuilder();
    do {
      String expression = matcher.group(1);
      String replacement = resolvePlaceholder(expression, matcher, input, targetUrl, datasources);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    } while (matcher.find());
    matcher.appendTail(sb);
    String resolved = sb.toString();
    validateNoMalformedDatasource(resolved);
    return resolved;
  }

  private static final Pattern MALFORMED_DATASOURCE =
      Pattern.compile("\\$\\{DATASOURCE\\[[^}]*\\}");

  private static void validateNoMalformedDatasource(String resolved) throws FatalAdapterException {
    Matcher m = MALFORMED_DATASOURCE.matcher(resolved);
    if (m.find()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Malformed datasource placeholder: " + Encode.forJava(m.group()));
    }
  }

  private static String resolvePlaceholder(
      String expression,
      Matcher matcher,
      String input,
      String targetUrl,
      List<Datasource> datasources)
      throws FatalAdapterException {

    if ("FROST_BASE".equals(expression)) {
      return resolveFrostBase(targetUrl, input, matcher.end());
    }

    String indexStr = matcher.group(2);
    String property = matcher.group(3);
    int index;
    try {
      index = Integer.parseInt(indexStr);
    } catch (NumberFormatException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Placeholder ${DATASOURCE[" + Encode.forJava(indexStr) + "]} has an invalid index");
    }

    if (index < 0 || index >= datasources.size()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Placeholder ${DATASOURCE["
              + index
              + "]} references index "
              + index
              + " but only "
              + datasources.size()
              + " datasource(s) available");
    }

    Datasource ds = datasources.get(index);
    if (ds == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "Datasource at index " + index + " is null");
    }

    if (property != null) {
      return resolveDatasourceProperty(ds, index, property);
    }

    return resolveDatasourceDsn(ds, index);
  }

  private static String resolveFrostBase(String targetUrl, String input, int matchEnd)
      throws FatalAdapterException {
    if (targetUrl == null || targetUrl.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Placeholder ${FROST_BASE} requires a targetUrl but none was provided");
    }

    // Lookahead: check if ${FROST_BASE} is followed by /Observations or /Datastreams
    Matcher suffixMatcher = FROST_SUFFIX_PATTERN.matcher(input.substring(matchEnd));
    if (suffixMatcher.find()) {
      String entityType = suffixMatcher.group(1);
      if (!"Things".equals(entityType)) {
        // Strip trailing /Projects(...) so Observations and Datastreams resolve against FROST root
        String stripped = PROJECTS_SUFFIX_PATTERN.matcher(targetUrl).replaceFirst("");
        log.debug("Resolved ${{FROST_BASE}} → [targetUrl without Projects] for /{}", entityType);
        return stripped;
      }
    }

    log.debug("Resolved ${{FROST_BASE}} → [targetUrl]");
    return targetUrl;
  }

  private static String resolveDatasourceDsn(Datasource ds, int index)
      throws FatalAdapterException {
    String dsn = DatasourceParser.buildDsnFromDatasource(ds);
    if (dsn == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Cannot build DSN for ${DATASOURCE[" + index + "]}: missing host, database, or username");
    }
    log.debug("Resolved ${{DATASOURCE[{}]}} → [DSN]", index);
    return dsn;
  }

  private static String resolveDatasourceProperty(Datasource ds, int index, String property)
      throws FatalAdapterException {
    Object value = getCoreField(ds, property);
    if (value == null) {
      Map<String, Object> additionalProps = ds.getAdditionalProperties();
      if (additionalProps != null) {
        value = additionalProps.get(property);
      }
    }
    if (value == null) {
      Map<String, Object> cfg = DatasourceParser.configuration(ds);
      if (cfg != null) {
        value = cfg.get(property);
      }
    }
    if (value == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Placeholder ${DATASOURCE["
              + index
              + "]."
              + Encode.forJava(property)
              + "} — property not found on datasource");
    }
    log.debug("Resolved ${{DATASOURCE[{}].{}}}", index, Encode.forJava(property));
    return String.valueOf(value);
  }

  /**
   * Returns the value of a core {@link Datasource} field by name, or {@code null} if the property
   * does not match any core field. The six core fields are: {@code id}, {@code type}, {@code name},
   * {@code description}, {@code host}, and {@code port}. The caller falls through to {@link
   * Datasource#getAdditionalProperties()} for dynamic fields not covered here.
   */
  private static Object getCoreField(Datasource ds, String property) {
    return switch (property) {
      case FIELD_ID -> ds.getId();
      case FIELD_TYPE -> ds.getType();
      case FIELD_NAME -> ds.getName();
      case FIELD_DESCRIPTION -> ds.getDescription();
      case FIELD_HOST -> ds.getHost();
      case FIELD_PORT -> ds.getPort();
      default -> null;
    };
  }
}

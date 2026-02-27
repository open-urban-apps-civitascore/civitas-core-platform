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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@code label: ${<uuid>}} placeholders in pipeline input blocks.
 *
 * <p>The pipeline JSON only needs a {@code label} inside the {@code input} block. The connector
 * type and full configuration are derived entirely from the matching datasource in the Kafka
 * trigger payload — nothing else is needed in the pipeline model:
 *
 * <pre>{@code
 * input:
 *   label: "${<uuid>}"
 * }</pre>
 *
 * <p>On resolution the entire input block is replaced by the typed {@link ConnectorConfig} built by
 * {@link DatasourceParser}. All Map key access is encapsulated there.
 */
class DatasourceInjector {

  private static final Logger log = LoggerFactory.getLogger(DatasourceInjector.class);
  private static final String LABEL_KEY = "label";

  private DatasourceInjector() {}

  /**
   * Returns a copy of {@code pipelineData} with the input placeholder resolved, or the original map
   * unchanged if no placeholder is present.
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> resolve(
      Map<String, Object> pipelineData, List<Map<String, Object>> datasources) {

    if (pipelineData == null
        || pipelineData.isEmpty()
        || datasources == null
        || datasources.isEmpty()) {
      return pipelineData;
    }

    Object inputObj = pipelineData.get("input");
    if (!(inputObj instanceof Map<?, ?> inputMap)) {
      return pipelineData;
    }

    return extractPlaceholder((Map<String, Object>) inputMap)
        .map(placeholder -> inject(pipelineData, placeholder, datasources))
        .orElse(pipelineData);
  }

  // ─── Private ───────────────────────────────────────────────────────────────

  /**
   * Reads {@code label} from the top-level of the input map and extracts the UUID from {@code
   * ${<uuid>}}.
   */
  private static Optional<Placeholder> extractPlaceholder(Map<String, Object> inputMap) {
    Object labelObj = inputMap.get(LABEL_KEY);
    if (!(labelObj instanceof String label)) return Optional.empty();

    String trimmed = label.trim();
    if (!trimmed.startsWith("${") || !trimmed.endsWith("}")) return Optional.empty();

    String uuid = trimmed.substring(2, trimmed.length() - 1).trim();
    if (uuid.isEmpty()) return Optional.empty();

    return Optional.of(new Placeholder(uuid, trimmed));
  }

  /** Resolves the placeholder against the datasource list and injects the connector config. */
  private static Map<String, Object> inject(
      Map<String, Object> pipelineData,
      Placeholder placeholder,
      List<Map<String, Object>> datasources) {

    Optional<Map<String, Object>> matchOpt =
        datasources.stream()
            .filter(ds -> placeholder.datasourceId().equals(ds.get("id")))
            .findFirst();

    if (matchOpt.isEmpty()) {
      log.warn("Pipeline input references unknown datasource '{}'", placeholder.datasourceId());
      return pipelineData;
    }

    Optional<ConnectorConfig> configOpt = DatasourceParser.parse(matchOpt.get());
    if (configOpt.isEmpty()) {
      log.warn(
          "Unsupported datasource type for '{}' — placeholder not resolved",
          placeholder.datasourceId());
      return pipelineData;
    }

    ConnectorConfig config = configOpt.get();
    log.info(
        "Resolved datasource placeholder for '{}' → {}",
        placeholder.datasourceId(),
        config.getClass().getSimpleName());

    Map<String, Object> resolved = new LinkedHashMap<>(pipelineData);
    resolved.put("input", config.toInputMap(placeholder.datasourceId()));
    return resolved;
  }

  /** Captured datasource UUID and original label string from the pipeline input. */
  private record Placeholder(String datasourceId, String originalLabel) {}
}

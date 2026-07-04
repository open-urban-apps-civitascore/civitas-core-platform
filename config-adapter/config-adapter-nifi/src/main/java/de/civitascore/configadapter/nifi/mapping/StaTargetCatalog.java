/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The closed catalog of SensorThings envelope paths a FROST mapping may target. The envelope is a
 * fixed adapter interface — the find-or-create legs consume exactly this shape — not a
 * tenant-modelled structure, so the target vocabulary is a reviewable constant like the fragment
 * whitelist: every JSON key of the generated envelope template comes from here, never from tenant
 * input. The list order is the byte-deterministic key order of the generated template.
 */
public final class StaTargetCatalog {

  /** The two envelope groups; each mapped record contributes one element per mapped group. */
  public enum StaGroup {
    THINGS("$.things[]", "things"),
    OBSERVATIONS("$.observations[]", "observations");

    private final String pathPrefix;
    private final String envelopeKey;

    StaGroup(String pathPrefix, String envelopeKey) {
      this.pathPrefix = pathPrefix;
      this.envelopeKey = envelopeKey;
    }

    /** The target-path prefix of this group ({@code $.things[]} / {@code $.observations[]}). */
    public String pathPrefix() {
      return pathPrefix;
    }

    /** The top-level envelope key of this group. */
    public String envelopeKey() {
      return envelopeKey;
    }
  }

  /** The JSON type a target serializes as — drives the template placeholder form. */
  public enum StaJsonType {
    /** Always a JSON string (quoted, escaped). */
    STRING,
    /**
     * SensorThings {@code any}: the JSON type is inferred from the mapped value expression
     * (number/boolean render unquoted, everything else as a string).
     */
    ANY
  }

  /**
   * One targetable envelope path.
   *
   * @param path the target path exactly as the mapping editor emits it
   * @param group the envelope group the path belongs to
   * @param type the JSON type of the serialized value
   * @param required whether the path must be mapped once its group is mapped at all (the {@code
   *     reference}/{@code name} paths are the find-or-create lookup keys)
   */
  public record StaTarget(String path, StaGroup group, StaJsonType type, boolean required) {

    /** The path segments below the group prefix (e.g. {@code properties.reference}). */
    public String relativePath() {
      return path.substring(group.pathPrefix().length() + 1);
    }
  }

  private static final List<StaTarget> TARGETS =
      List.of(
          new StaTarget("$.things[].name", StaGroup.THINGS, StaJsonType.STRING, true),
          new StaTarget("$.things[].description", StaGroup.THINGS, StaJsonType.STRING, true),
          new StaTarget(
              "$.things[].properties.reference", StaGroup.THINGS, StaJsonType.STRING, true),
          new StaTarget("$.observations[].result", StaGroup.OBSERVATIONS, StaJsonType.ANY, true),
          new StaTarget(
              "$.observations[].phenomenonTime", StaGroup.OBSERVATIONS, StaJsonType.STRING, false),
          new StaTarget(
              "$.observations[].resultTime", StaGroup.OBSERVATIONS, StaJsonType.STRING, false),
          new StaTarget(
              "$.observations[].parameters.reference",
              StaGroup.OBSERVATIONS,
              StaJsonType.STRING,
              true),
          new StaTarget(
              "$.observations[].parameters.name", StaGroup.OBSERVATIONS, StaJsonType.STRING, true));

  private StaTargetCatalog() {}

  /** All targetable paths in template key order. */
  public static List<StaTarget> targets() {
    return TARGETS;
  }

  /** The catalog entry for a target path, or empty if the path is not targetable. */
  public static Optional<StaTarget> byPath(String path) {
    return TARGETS.stream().filter(target -> target.path().equals(path)).findFirst();
  }

  /** The supported paths as a comma-separated list (for rejection messages). */
  public static String supportedPaths() {
    return TARGETS.stream().map(StaTarget::path).collect(Collectors.joining(", "));
  }
}

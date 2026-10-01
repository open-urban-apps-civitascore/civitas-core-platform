/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The CORE URN identifying a platform artifact, and its validation.
 *
 * <p>Layout: {@code urn:core:<scope>:<owner>:<artifact-type>:<domain>:<uuid>:<version>}. The UUID
 * is the artifact's identity; the URN carries no display name, so a rename never changes it.
 *
 * <p>Lives in {@code config-adapter-api} so every module validates the same shape. The
 * config-adapter calls {@link #isValid(String)} on the {@code source}/{@code target} URNs a tenant
 * ships on a mapping.
 *
 * <p>Unlike a generic {@code urn:core:} parser that only enforces the prefix and positional layout,
 * these are the CORE platform's own conventions: {@code scope} and {@code artifact-type} are drawn
 * from fixed vocabularies, the remaining segments from fixed shapes. {@code owner} and {@code
 * domain} are open slug shapes rather than enums — new publishers and domains appear without a code
 * change, so pinning them to a whitelist would reject legitimate URNs.
 */
public final class CoreUrn {

  /** Fixed vocabulary for the {@code scope} segment. */
  public static final List<String> SCOPES =
      List.of("platform", "tenant", "standard", "dataset", "project");

  /** Fixed vocabulary for the {@code artifact-type} segment. */
  public static final List<String> ARTIFACT_TYPES =
      List.of(
          "element", "datastructure", "mapping", "pipeline", "datasource", "datasink", "dataset");

  private static final String SCOPE = group(SCOPES);
  private static final String ARTIFACT_TYPE = group(ARTIFACT_TYPES);

  private static final String SLUG = "[a-z0-9]+(?:-[a-z0-9]+)*";
  private static final String DOMAIN = SLUG + "(?:\\." + SLUG + ")*";
  private static final String ID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  private static final String VERSION = "\\d+\\.\\d+\\.\\d+";

  /**
   * Regex matching a full CORE URN, with named {@code scope}, {@code artifactType}, {@code id} and
   * {@code version} groups. Anchored so {@code @Pattern} and {@link #COMPILED_PATTERN} agree —
   * {@code @Pattern} matches the whole value, {@code Matcher#matches} likewise.
   */
  public static final String PATTERN =
      "urn:core:(?<scope>"
          + SCOPE
          + "):"
          + SLUG
          + ":(?<artifactType>"
          + ARTIFACT_TYPE
          + "):"
          + DOMAIN
          + ":(?<id>"
          + ID
          + "):(?<version>"
          + VERSION
          + ")";

  /** Compiled form of {@link #PATTERN}. */
  public static final Pattern COMPILED_PATTERN = Pattern.compile(PATTERN);

  /** Whether the value is a well-formed CORE URN honouring the platform conventions. */
  public static boolean isValid(String urn) {
    return urn != null && COMPILED_PATTERN.matcher(urn).matches();
  }

  /**
   * The logical (version-stripped) form of a CORE URN: the value without its trailing {@code
   * :<major>.<minor>.<patch>} version segment. A URN that carries no recognizable SemVer tail (an
   * already-logical URN, or an opaque correlation key) is returned unchanged, so two URNs can be
   * compared on their logical form even when one is already logical.
   *
   * @param urn the URN (nullable)
   * @return the version-stripped URN, or the input unchanged when it has no version tail
   */
  public static String logicalUrn(String urn) {
    if (urn == null) {
      return null;
    }
    int lastColon = urn.lastIndexOf(':');
    if (lastColon < 0) {
      return urn;
    }
    return urn.substring(lastColon + 1).matches(VERSION) ? urn.substring(0, lastColon) : urn;
  }

  /**
   * Whether two references identify the same artifact, tolerating a version drift between a
   * pinned-at-authoring-time reference and a resolved-at-publish-time catalog key: equal verbatim,
   * or equal once both are reduced to their {@link #logicalUrn(String) logical} form. Two nulls (or
   * a null on either side) never match.
   *
   * @param a the first reference (nullable)
   * @param b the second reference (nullable)
   * @return whether they name the same artifact
   */
  public static boolean sameArtifact(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return a.equals(b) || logicalUrn(a).equals(logicalUrn(b));
  }

  /**
   * Whether two URNs name the same DataStructure at the same version, and therefore the same shape.
   * The artifact type is not compared: a DataStructure and the root Element built from it share
   * their UUID and version.
   *
   * @return false if either URN is null or malformed — an unverifiable pair is not a matching one
   */
  public static boolean sameStructureVersion(String one, String other) {
    if (one == null || other == null) {
      return false;
    }
    Matcher first = COMPILED_PATTERN.matcher(one);
    Matcher second = COMPILED_PATTERN.matcher(other);
    return first.matches()
        && second.matches()
        && first.group("id").equals(second.group("id"))
        && first.group("version").equals(second.group("version"));
  }

  private CoreUrn() {}

  private static String group(List<String> alternatives) {
    return "(?:" + String.join("|", alternatives) + ")";
  }
}

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

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The CORE URN identifying a platform artifact, and its validation.
 *
 * <p>Layout: {@code
 * urn:core:<scope>:<owner>:<artifact-type>:<domain>:<name>:<disambiguator>:<version>}.
 *
 * <p>Lives in {@code config-adapter-api} as the single source of truth so both trust boundaries
 * validate the same shape: the portal-backend guards the DataStructure model {@code $id} it
 * persists, and the config-adapter guards the {@code source}/{@code target} URNs a tenant ships on
 * a mapping. Producers reference {@link #PATTERN} on {@code @Pattern}; consumers call {@link
 * #isValid(String)} as the runtime guard.
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

  /** Length of the base36 disambiguator; must mirror {@code toDisambiguator} in the frontend. */
  public static final int DISAMBIGUATOR_LENGTH = 10;

  private static final String SCOPE = group(SCOPES);
  private static final String ARTIFACT_TYPE = group(ARTIFACT_TYPES);

  private static final String SLUG = "[a-z0-9]+(?:-[a-z0-9]+)*";
  private static final String DOMAIN = SLUG + "(?:\\." + SLUG + ")*";
  // The registry derives this segment from a schema title and keeps dots, underscores and
  // hyphens (XOEV identifiers such as Lokation.0002_Bundesland rely on them), so the segment
  // has to accept them here too; identity is carried by the disambiguator, not the name.
  private static final String NAME = "[A-Za-z0-9._-]+";
  private static final String DISAMBIGUATOR = "[0-9a-z]{" + DISAMBIGUATOR_LENGTH + "}";
  private static final String VERSION = "\\d+\\.\\d+\\.\\d+";

  /**
   * Regex matching a full CORE URN, with named {@code scope}, {@code artifactType} and {@code
   * disambiguator} groups. Anchored so {@code @Pattern} and {@link #COMPILED_PATTERN} agree —
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
          + ":"
          + NAME
          + ":(?<disambiguator>"
          + DISAMBIGUATOR
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
   * The base36 disambiguator derived from a DataStructure id: the UUID's 128 bits read as one
   * integer, encoded in base36, then the least-significant {@link #DISAMBIGUATOR_LENGTH} characters
   * (left-padded). Must stay bit-for-bit identical to {@code toDisambiguator} in the frontend's
   * {@code urn.ts}, so the URN built there round-trips here.
   */
  public static String disambiguatorFor(UUID id) {
    String base36 = new BigInteger(1, toBytes(id)).toString(36);
    String tail =
        base36.length() > DISAMBIGUATOR_LENGTH
            ? base36.substring(base36.length() - DISAMBIGUATOR_LENGTH)
            : base36;
    return "0".repeat(DISAMBIGUATOR_LENGTH - tail.length()) + tail;
  }

  /**
   * Whether {@code urn} is a well-formed platform-scoped {@code datastructure} URN whose
   * disambiguator was in fact derived from {@code id}. Guards against a URN whose disambiguator was
   * hand-forged or copied from another DataStructure — the disambiguator only disambiguates if it
   * provably belongs to this id. Scope and artifact-type are pinned because the disambiguator is a
   * pure function of the UUID: a {@code mapping} or tenant-scoped URN reusing the same token would
   * otherwise pass as this DataStructure's identity.
   */
  public static boolean matchesId(String urn, UUID id) {
    if (urn == null || id == null) {
      return false;
    }
    Matcher matcher = COMPILED_PATTERN.matcher(urn);
    return matcher.matches()
        && "platform".equals(matcher.group("scope"))
        && "datastructure".equals(matcher.group("artifactType"))
        && matcher.group("disambiguator").equals(disambiguatorFor(id));
  }

  /**
   * Whether two URNs name the same DataStructure at the same version, and therefore the same shape.
   * Only the disambiguator and the version are compared: the disambiguator is derived from the
   * DataStructure id and the version is the shape, while the name segment is a display name that a
   * rename changes without touching the structure. Comparing the URNs verbatim would reject a chain
   * whose shape never changed.
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
        && first.group("disambiguator").equals(second.group("disambiguator"))
        && first.group("version").equals(second.group("version"));
  }

  private CoreUrn() {}

  private static byte[] toBytes(UUID id) {
    byte[] bytes = new byte[16];
    long msb = id.getMostSignificantBits();
    long lsb = id.getLeastSignificantBits();
    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte) (msb >>> (8 * (7 - i)));
      bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
    }
    return bytes;
  }

  private static String group(List<String> alternatives) {
    return "(?:" + String.join("|", alternatives) + ")";
  }
}

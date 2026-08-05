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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConcatNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.CopyNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.GeoPointNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The array fan-out a mapping requires: one {@code ForkRecord} over the innermost source array, so
 * that each element becomes its own record and therefore its own row or entity.
 *
 * <p>How many records a payload carries is a question only the <em>source</em> side can answer, and
 * only the mapping as a whole — never a single rule. A target array selector multiplies nothing:
 * for FROST it merely marks an entity tier, and for PostGIS it selects into an array the record
 * already carries.
 *
 * <p>The target side contributes no fan-out of its own. A rule whose target keeps its array level
 * is an in-place rewrite within each element, rendered relative ({@code ../field}), and its own
 * source array must therefore survive rather than be flattened. Such a rule can only coexist with a
 * fan-out derived from elsewhere in the mapping if that fan-out leaves it intact — which no fan-out
 * does, so mixing the two is rejected.
 *
 * <p>Several source paths sharing one hierarchical line (for example {@code stations[].name} and
 * {@code stations[].measurements[].value}) are a single fan-out over the innermost array. Sources
 * on independent lines are rejected, as are two sources that collapse onto the same post-fork path.
 */
public record ForkPlan(String recordPath) {

  /** The plan for a mapping that reads no array and therefore needs no fan-out. */
  public static final ForkPlan NONE = new ForkPlan(null);

  /** Whether the mapping needs a fan-out at all. */
  public boolean required() {
    return recordPath != null;
  }

  /**
   * Derives the fan-out from every source path a mapping reads.
   *
   * @param mapping the parsed mapping
   * @return the plan, or a plan with {@link #required()} {@code false} if no source selects an
   *     array
   * @throws FatalAdapterException if the sources span independent arrays, if a rule rewrites an
   *     array in place that the fan-out would destroy, or if two sources collapse onto one
   *     post-fork path — none of which can be resolved without guessing
   */
  public static ForkPlan forMapping(MappingConfig mapping) throws FatalAdapterException {
    return forMapping(mapping, true);
  }

  /**
   * Derives the fan-out, optionally honouring the mapping's own target paths.
   *
   * @param mapping the parsed mapping
   * @param targetsKeepTheirShape whether the compiled targets still carry the mapping's array
   *     levels. True for a record-shaped sink such as PostGIS, where a target array selector marks
   *     an in-place rewrite that must not be flattened. False where every target is flattened to a
   *     root-level field first (FROST): there the declared selectors are entity-tier markers, no
   *     target keeps an array level, and vetoing on them would suppress the fan-out entirely.
   */
  public static ForkPlan forMapping(MappingConfig mapping, boolean targetsKeepTheirShape)
      throws FatalAdapterException {
    Map<List<String>, String> contexts = new LinkedHashMap<>();
    List<String> inPlaceTargets = new ArrayList<>();
    List<Read> reads = new ArrayList<>();
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      if (targetsKeepTheirShape && keepsItsArrayLevel(field.getKey())) {
        // Only collected, not decided: whether an in-place rule survives depends on the fan-out the
        // remaining rules produce, which is not known until they have all been read.
        inPlaceTargets.add(field.getKey());
        continue;
      }
      collectArrayContexts(field.getKey(), field.getValue(), contexts, reads);
    }
    if (contexts.isEmpty()) {
      return NONE;
    }

    List<String> innermost = innermostSharedContext(contexts);
    // Rejected even when the fork is over a different array: that one repeats the in-place array
    // into every fanned-out record, so the rewrite runs once per record and each copy is written.
    if (!inPlaceTargets.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR,
          "cannot combine the in-place array target(s) "
              + quoted(inPlaceTargets)
              + " with the fan-out over source array '"
              + describe(innermost)
              + "' (from rule '"
              + contexts.get(innermost)
              + "'): the fan-out flattens each record to one element, leaving no array for those"
              + " targets to rewrite; map the array-valued rules in their own mapping node instead");
    }
    rejectIndexedReadsInsideFork(innermost, reads);
    ForkPlan plan = new ForkPlan(forkRecordPath(innermost));
    plan.rejectCollidingSources(reads);
    return plan;
  }

  /** One source path a rule reads, and the target field the rule writes. */
  private record Read(String targetPath, String sourcePath) {}

  /**
   * The innermost of several array contexts, which must all lie on one hierarchical line — the
   * outer levels then ride along as parent fields.
   *
   * @param contexts each array context mapped to the target field of the rule that introduced it
   */
  private static List<String> innermostSharedContext(Map<List<String>, String> contexts)
      throws FatalAdapterException {
    List<String> innermost =
        contexts.keySet().stream().max(Comparator.comparingInt(List::size)).orElseThrow();
    for (Map.Entry<List<String>, String> context : contexts.entrySet()) {
      if (!isPrefixOf(context.getKey(), innermost)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_MAPPING_ERROR,
            "cannot map independent source arrays '"
                + describe(context.getKey())
                + "' (rule '"
                + context.getValue()
                + "') and '"
                + describe(innermost)
                + "' (rule '"
                + contexts.get(innermost)
                + "') onto one target: their elements pair in no defined order; map them in"
                + " separate mapping nodes");
      }
    }
    return innermost;
  }

  /**
   * Rejects a source that reads a concrete array index inside the array the fan-out consumes.
   * {@code ForkRecord} drops the forked array field from the record it emits, so the indexed path
   * resolves against nothing and the rule writes NULL on every fanned-out record without failing.
   */
  private static void rejectIndexedReadsInsideFork(List<String> innermost, List<Read> reads)
      throws FatalAdapterException {
    List<String> forkedField = withoutSelectors(innermost);
    for (Read read : reads) {
      List<String> segments = withoutSelectors(parse(read.sourcePath()).segments());
      if (parse(read.sourcePath()).hasArrayContext() || !isPrefixOf(forkedField, segments)) {
        continue;
      }
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR,
          "source '"
              + read.sourcePath()
              + "' (rule '"
              + read.targetPath()
              + "') reads a fixed element of '"
              + describe(innermost)
              + "', the array the fan-out consumes: the fanned-out record no longer carries that"
              + " array, so the rule would read nothing; map it in its own mapping node instead");
    }
  }

  /** Rejects two sources that read different payload fields but the same post-fork one. */
  private void rejectCollidingSources(List<Read> reads) throws FatalAdapterException {
    Map<String, String> originBySelection = new LinkedHashMap<>();
    for (Read read : reads) {
      String sourcePath = read.sourcePath();
      String selection = rewriteSource(parse(sourcePath));
      String collides = originBySelection.putIfAbsent(selection, sourcePath);
      if (collides != null && !collides.equals(sourcePath)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_MAPPING_ERROR,
            "sources '"
                + collides
                + "' and '"
                + sourcePath
                + "' both read '"
                + selection
                + "' after the fan-out over '"
                + recordPath
                + "': the element field hides the ancestor one, so both rules would carry the same"
                + " value; rename one of them in the payload or drop one rule");
      }
    }
  }

  /**
   * Rewrites a source path so it resolves against an already forked record. {@code ForkRecord} in
   * extract mode promotes the element's own fields to the record root and, with parent fields
   * included, copies each ancestor field up under its own name — values keep their shape, so a
   * nested ancestor object stays addressable through it. Only the array levels themselves are gone,
   * which is why the segments below a path's innermost array are exactly what remains.
   *
   * <p>The copy is skipped where an element field and an ancestor field share a name: the element
   * wins wherever it carries a value, and the ancestor value is then no longer reachable under any
   * path. (Where the element field is null or absent, the ancestor's value is copied up after all.)
   * Reading both is therefore not expressible after a fan-out, and the element value is the
   * defensible one — it is the more specific of the two.
   *
   * @param source the parsed source path
   * @return the RecordPath to read after the fan-out
   * @throws IllegalStateException if there is no fan-out, since the rewrite would then silently
   *     strip the array levels a path still has to traverse
   */
  public String rewriteSource(JsonPaths.ParsedPath source) {
    if (!required()) {
      throw new IllegalStateException("no fan-out to rewrite against");
    }
    return "/"
        + String.join("/", source.suffixWithinArray().stream().map(ForkPlan::allElements).toList());
  }

  /**
   * The RecordPath naming the array to fork. Every array selector on the way stays a wildcard, but
   * the innermost one is dropped: the property must point <em>at</em> the array, not into it.
   */
  private static String forkRecordPath(List<String> context) {
    List<String> segments = new ArrayList<>(context);
    int last = segments.size() - 1;
    segments.set(last, segments.get(last).replace("[]", ""));
    return "/" + String.join("/", segments.stream().map(ForkPlan::allElements).toList());
  }

  /**
   * Whether a rule reads at least one source from below an array — a value that varies per element
   * rather than being shared by every record the fan-out produces.
   *
   * @param rule the rule's value node; a rule with no source at all (a literal) reads no element
   */
  public static boolean readsBelowTheArray(ValueNode rule) {
    return switch (rule) {
      case CopyNode copy -> {
        try {
          yield JsonPaths.parse(copy.sourcePath()).hasArrayContext();
        } catch (IllegalArgumentException malformed) {
          // Left for the compiler to reject with its own message rather than second-guessed here.
          yield false;
        }
      }
      case ConstNode ignored -> false;
      case ConcatNode concat -> concat.inputs().stream().anyMatch(ForkPlan::readsBelowTheArray);
      case ConvertNode convert -> readsBelowTheArray(convert.input());
      case GeoPointNode geoPoint ->
          readsBelowTheArray(geoPoint.lon()) || readsBelowTheArray(geoPoint.lat());
    };
  }

  private static void collectArrayContexts(
      String targetPath, ValueNode node, Map<List<String>, String> contexts, List<Read> reads)
      throws FatalAdapterException {
    switch (node) {
      case CopyNode copy -> {
        reads.add(new Read(targetPath, copy.sourcePath()));
        JsonPaths.ParsedPath path = parse(copy.sourcePath());
        if (path.hasArrayContext()) {
          if (path.suffixWithinArray().isEmpty()) {
            // The path stops at the array itself, so its elements are values rather than records.
            // ForkRecord's extract mode only emits RECORD elements and skips anything else without
            // failing. This catches only the spelling that names the array: '$.temps[].value' also
            // describes a scalar array and cannot be told apart without the declared source
            // structure. The zero-record guard on the fork's output is the backstop for the rest.
            throw new FatalAdapterException(
                AdapterErrorCode.NIFI_MAPPING_ERROR,
                "cannot fan out source '"
                    + copy.sourcePath()
                    + "': it selects the array itself, so its elements carry no field to map; select"
                    + " a field below the array instead");
          }
          contexts.putIfAbsent(path.arrayContext(), targetPath);
        }
      }
      case ConstNode ignored -> {}
      case ConcatNode concat -> {
        for (ValueNode input : concat.inputs()) {
          collectArrayContexts(targetPath, input, contexts, reads);
        }
      }
      case ConvertNode convert ->
          collectArrayContexts(targetPath, convert.input(), contexts, reads);
      case GeoPointNode geoPoint -> {
        collectArrayContexts(targetPath, geoPoint.lon(), contexts, reads);
        collectArrayContexts(targetPath, geoPoint.lat(), contexts, reads);
      }
    }
  }

  private static JsonPaths.ParsedPath parse(String path) throws FatalAdapterException {
    try {
      return JsonPaths.parse(path);
    } catch (IllegalArgumentException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR,
          e,
          "invalid source CORE path '" + path + "': " + e.getMessage());
    }
  }

  private static boolean isPrefixOf(List<String> candidate, List<String> path) {
    return candidate.size() <= path.size() && path.subList(0, candidate.size()).equals(candidate);
  }

  /**
   * Whether a target path selects into an array of its own. Such a rule is element-wise in place —
   * the compiler renders it relative ({@code ../field}) — and a malformed path is left for the
   * compiler to reject with its own message rather than being second-guessed here.
   */
  private static boolean keepsItsArrayLevel(String targetPath) {
    try {
      return JsonPaths.parse(targetPath).hasArrayContext();
    } catch (IllegalArgumentException malformed) {
      return false;
    }
  }

  private static String allElements(String segment) {
    return segment.replace("[]", "[*]");
  }

  /** Segments stripped of every selector, so {@code items[]} and {@code items[0]} compare equal. */
  private static List<String> withoutSelectors(List<String> segments) {
    return segments.stream().map(segment -> segment.replaceAll("\\[[^]]*]", "")).toList();
  }

  private static String describe(List<String> context) {
    return "$." + String.join(".", context);
  }

  private static String quoted(List<String> paths) {
    return paths.stream().map(path -> "'" + path + "'").collect(Collectors.joining(", "));
  }
}

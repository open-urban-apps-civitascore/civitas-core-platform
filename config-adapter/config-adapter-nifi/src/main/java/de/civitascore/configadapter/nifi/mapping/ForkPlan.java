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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    Set<List<String>> contexts = new LinkedHashSet<>();
    List<String> inPlaceTargets = new ArrayList<>();
    List<String> sourcePaths = new ArrayList<>();
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      if (targetsKeepTheirShape && keepsItsArrayLevel(field.getKey())) {
        // An in-place rule rewrites fields within each element and needs its array to survive, so
        // it asks for no fan-out. Whether it can tolerate one derived from the other rules is
        // decided below, once the fan-out is known.
        inPlaceTargets.add(field.getKey());
        continue;
      }
      collectArrayContexts(field.getValue(), contexts, sourcePaths);
    }
    if (contexts.isEmpty()) {
      return NONE;
    }

    List<String> innermost = innermostSharedContext(contexts);
    // A fan-out anywhere in the mapping rewrites the record shape for every rule, so an in-place
    // rule cannot survive it: forking its own array flattens the level its target addresses, and
    // forking a different one repeats the whole array across the fanned-out records and rewrites it
    // once per record. Both are silent — a vanished field or duplicated data, with no bulletin.
    if (!inPlaceTargets.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR,
          "cannot combine the in-place array target '"
              + inPlaceTargets.get(0)
              + "' with the fan-out over source array '"
              + describe(innermost)
              + "': the fan-out flattens each record to one element, leaving no array for that"
              + " target to rewrite; map the array-valued rule in its own mapping node instead");
    }
    ForkPlan plan = new ForkPlan(forkRecordPath(innermost));
    plan.rejectCollidingSources(sourcePaths);
    return plan;
  }

  /**
   * The innermost of several array contexts, which must all lie on one hierarchical line — the
   * outer levels then ride along as parent fields. Independent sibling arrays are rejected: pairing
   * 3 measurements with 2 alarms yields neither 3, 2 nor 6 records, so any choice would be a silent
   * guess.
   */
  private static List<String> innermostSharedContext(Set<List<String>> contexts)
      throws FatalAdapterException {
    List<String> innermost =
        contexts.stream().max(Comparator.comparingInt(List::size)).orElseThrow();
    for (List<String> context : contexts) {
      if (!isPrefixOf(context, innermost)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_MAPPING_ERROR,
            "cannot map independent source arrays '"
                + describe(context)
                + "' and '"
                + describe(innermost)
                + "' onto one target: their elements pair in no defined order");
      }
    }
    return innermost;
  }

  /**
   * Rejects two sources that read different payload fields but the same post-fork one. The fork
   * hoists an element field over an ancestor field of the same name, so both rules would resolve to
   * the element value and the author's two distinct fields would silently carry one value.
   */
  private void rejectCollidingSources(List<String> sourcePaths) throws FatalAdapterException {
    Map<String, String> originBySelection = new LinkedHashMap<>();
    for (String sourcePath : sourcePaths) {
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
   * wins and the ancestor value is no longer reachable under any path. Reading both is therefore
   * not expressible after a fan-out, and the element value is the defensible one — it is the more
   * specific of the two.
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

  private static void collectArrayContexts(
      ValueNode node, Set<List<String>> contexts, List<String> sourcePaths)
      throws FatalAdapterException {
    switch (node) {
      case CopyNode copy -> {
        sourcePaths.add(copy.sourcePath());
        JsonPaths.ParsedPath path = parse(copy.sourcePath());
        if (path.hasArrayContext()) {
          if (path.suffixWithinArray().isEmpty()) {
            // The path stops at the array itself, so its elements are values rather than records.
            // ForkRecord's extract mode only emits RECORD elements and skips anything else without
            // failing, which would deploy a healthy-looking flow that writes nothing at all.
            throw new FatalAdapterException(
                AdapterErrorCode.NIFI_MAPPING_ERROR,
                "cannot fan out source '"
                    + copy.sourcePath()
                    + "': it selects the array itself, so its elements carry no field to map; select"
                    + " a field below the array instead");
          }
          contexts.add(path.arrayContext());
        }
      }
      case ConstNode ignored -> {
        // a literal reads no source
      }
      case ConcatNode concat -> {
        for (ValueNode input : concat.inputs()) {
          collectArrayContexts(input, contexts, sourcePaths);
        }
      }
      case ConvertNode convert -> collectArrayContexts(convert.input(), contexts, sourcePaths);
      case GeoPointNode geoPoint -> {
        collectArrayContexts(geoPoint.lon(), contexts, sourcePaths);
        collectArrayContexts(geoPoint.lat(), contexts, sourcePaths);
      }
    }
  }

  private static JsonPaths.ParsedPath parse(String path) throws FatalAdapterException {
    try {
      return JsonPaths.parse(path);
    } catch (IllegalArgumentException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR, e, "invalid source CORE path '" + path + "'");
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

  private static String describe(List<String> context) {
    return "$." + String.join(".", context);
  }
}

package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.util.JsonSchema;

import java.util.*;

/**
 * Produces derived views of an Element schema:
 *
 * <ul>
 *   <li><b>Inlined</b> – all CORE URN {@code $ref} values are recursively
 *       inlined; the result contains no CORE URN refs.</li>
 *   <li><b>Bundled</b> – all transitive dependencies are embedded under
 *       {@code $defs} as JSON Schema <em>embedded resources</em> (each keeps its
 *       own {@code $id}). CORE URN {@code $ref} values are left untouched and
 *       resolve against those embedded {@code $id}s per JSON Schema 2020-12.
 *       This stays correct for dependency→dependency references (chains,
 *       diamonds, cycles), which a {@code #/$defs/Name} rewrite would break
 *       inside an embedded resource.</li>
 * </ul>
 */
public class ViewService {


    private final ArtifactRegistry         registry;
    private final DependencyGraphService graph;
    private final ObjectMapper           mapper;

    public ViewService(ArtifactRegistry registry,
                       DependencyGraphService graph,
                       ObjectMapper mapper) {
        this.registry = registry;
        this.graph    = graph;
        this.mapper   = mapper;
    }

    // ── 6a  Inlined view ─────────────────────────────────────────────────

    /**
     * Returns a copy of the schema for {@code urn} with all CORE URN
     * {@code $ref} values fully inlined (recursive, cycle-safe).
     * Supports both JSON Schema and XSD URNs (XSD is converted transparently).
     *
     * <p>Keywords that sit next to a {@code $ref} (valid in JSON Schema 2020-12,
     * e.g. {@code description}, {@code title}, additional constraints) are
     * preserved: the referenced schema is inlined and the local sibling keywords
     * are overlaid on top, taking precedence on key collisions.
     *
     * <p>This unbounded overload inlines as deep as the graph goes (cycle-guarded). Prefer
     * {@link #inline(String, int)} from request handlers to bound output size.
     */
    public Optional<JsonNode> inline(String urn) {
        return inline(urn, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #inline(String)} but inlines cross-document {@code $ref}s only up to
     * {@code maxDepth} hops. Beyond that, a CORE URN {@code $ref} is left untouched (the
     * consumer can resolve it on demand), bounding the size of deep dependency graphs.
     *
     * @param maxDepth maximum number of cross-document URN-ref expansions along any path
     *                 ({@code <= 0} = no inlining, just the entity with its URN refs intact)
     */
    public Optional<JsonNode> inline(String urn, int maxDepth) {
        int depth = Math.max(0, maxDepth);
        return registry.fetchElementOrXsd(urn)
                // Inlined refs disappear; any ref left intact (depth/cycle/latest) is rewritten to
                // its concrete resolved version so the output carries no unresolved :latest pointer.
                .map(root -> {
                    Map<String, String> memberPins = memberPins(root);
                    return rewriteRefsToConcrete(
                        inlineNode(root, new LinkedHashSet<>(), depth, memberPins), memberPins);
                });
    }

    /**
     * Recursively inline CORE URN {@code $ref} nodes.
     *
     * @param node      current JSON node to process
     * @param resolving set of URNs currently being resolved (cycle guard)
     * @param depth     remaining cross-document inlining hops; a URN ref is only inlined when {@code > 0}
     */
    private JsonNode inlineNode(JsonNode node, Set<String> resolving, int depth,
                                Map<String, String> memberPins) {
        if (node == null || node.isNull() || node.isMissingNode()) return node;

        // If this node IS a $ref to a CORE URN, replace it with the inlined schema.
        // Uses fetchElementOrXsd so XSD URNs are transparently converted.
        if (node.isObject() && node.has("$ref")) {
            String authored = node.get("$ref").asText();
            // A logical sibling reference expands to the version the root pins, not the target's
            // current one. An explicit version (or :latest) is the caller's choice and stands.
            String ref = UrnParser.versionFromUrn(authored) == null
                ? memberPins.getOrDefault(UrnParser.logicalUrn(authored), authored)
                : authored;
            if (depth > 0 && UrnParser.isUrn(ref) && !resolving.contains(ref)) {
                Optional<JsonNode> fetched = registry.fetchElementOrXsd(ref);
                if (fetched.isPresent()) {
                    Set<String> next = new LinkedHashSet<>(resolving);
                    next.add(ref);
                    // Crossing one cross-document boundary consumes one depth level.
                    JsonNode inlined = inlineNode(fetched.get(), next, depth - 1, memberPins);

                    // No sibling keywords besides $ref → inline the target directly.
                    if (node.size() == 1) return inlined;

                    // Keywords adjacent to $ref are valid in 2020-12 and must not be
                    // dropped. Inline the target, then overlay the local sibling keywords
                    // (recursively inlined); local siblings win on key collisions.
                    ObjectNode merged = mapper.createObjectNode();
                    if (inlined.isObject()) {
                        inlined.properties().forEach(e -> merged.set(e.getKey(), e.getValue()));
                    } else {
                        // Non-object target (e.g. boolean schema): keep it via allOf so it still applies
                        merged.set("allOf", mapper.createArrayNode().add(inlined));
                    }
                    // Siblings live in the same document level → keep the current depth.
                    node.properties().forEach(e -> {
                        if (!"$ref".equals(e.getKey()))
                            merged.set(e.getKey(), inlineNode(e.getValue(), resolving, depth, memberPins));
                    });
                    return merged;
                }
            }
            // Depth exhausted, cycle, non-URN ref or fetch failed — leave as-is (siblings kept)
            return node;
        }

        if (node.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            node.properties().forEach(entry ->
                copy.set(entry.getKey(), inlineNode(entry.getValue(), resolving, depth, memberPins)));
            return copy;
        }

        if (node.isArray()) {
            ArrayNode arr = mapper.createArrayNode();
            node.forEach(child -> arr.add(inlineNode(child, resolving, depth, memberPins)));
            return arr;
        }

        // Scalar – return unchanged
        return node;
    }

    // ── 6b  Bundled view ──────────────────────────────────────────────────────

    /**
     * Returns a bundled document containing the root schema and all its
     * transitive dependencies embedded under {@code $defs}.
     *
     * <p>Each dependency is embedded <em>verbatim</em> as a JSON Schema embedded
     * resource — its own {@code $id} is preserved. Per JSON Schema 2020-12, an
     * in-place subschema carrying {@code $id} establishes a new base URI, so the
     * CORE URN {@code $ref} values throughout the document resolve against these
     * embedded {@code $id}s with no ref rewriting required.
     *
     * <p>This is the only flattening that stays correct when one dependency
     * references another (chains, diamonds, cycles): rewriting a cross-document
     * ref to {@code #/$defs/Name} breaks <em>inside</em> an embedded resource,
     * because the JSON Pointer fragment would resolve against the embedded base
     * URI instead of the document root.
     *
     * <p>This unbounded overload embeds the full transitive closure. Prefer
     * {@link #bundle(String, int)} from request handlers to bound output size.
     */
    public Optional<JsonNode> bundle(String urn) {
        return bundle(urn, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #bundle(String)} but embeds only dependencies reachable within
     * {@code maxDepth} hops. References to dependencies beyond that depth remain absolute
     * CORE URNs (resolvable on demand but not embedded), bounding the bundle size.
     *
     * @param maxDepth maximum dependency-graph depth to embed ({@code <= 0} = root only)
     */
    public Optional<JsonNode> bundle(String urn, int maxDepth) {
        // fetchElementOrXsd: transparently handles both JSON Schema and XSD URNs
        Optional<JsonNode> rootOpt = registry.fetchElementOrXsd(urn);
        if (rootOpt.isEmpty()) return Optional.empty();
        JsonNode root = rootOpt.get();

        // Collect transitive dependency URNs up to the requested depth.
        Set<String> allDeps = graph.getTransitiveDependencies(urn, Math.max(0, maxDepth));

        // The root is emitted as the document root with its own $id; never embed it again
        // (a cycle back to the root would otherwise create a duplicate $id in the document).
        String rootId      = root.has("$id") ? root.get("$id").asText() : urn;
        String rootLogical = UrnParser.logicalUrn(rootId);
        Map<String, String> memberPins = memberPins(root);

        ObjectNode defsNode = mapper.createObjectNode();

        // 1. The root's own local $defs first — their keys are load-bearing because
        //    the root's internal "#/$defs/X" pointers resolve against them.
        if (root.has("$defs")) {
            root.path("$defs").properties().forEach(entry ->
                defsNode.set(entry.getKey(), entry.getValue().deepCopy()));
        }

        // 2. CORE dependencies as embedded resources under a unique, readable key.
        //    The key is cosmetic (resolution is by $id), so on a name collision we
        //    disambiguate rather than overwrite — every embedded $id must survive,
        //    otherwise refs to the dropped resource would dangle.
        //    Uses fetchElementOrXsd so XSD deps are resolved transparently.
        Set<String> embedded = new LinkedHashSet<>();
        for (String depUrn : allDeps) {
            if (UrnParser.logicalUrn(depUrn).equals(rootLogical)) continue; // root self-cycle
            // Resolved before the fetch, so a sibling reached logically embeds at the version the
            // root pins. The same target can be reached both ways (pinned as a member, logical from a
            // sibling), so it is embedded once — two copies would give the document duplicate $ids.
            String concreteDepUrn = concreteRef(depUrn, memberPins);
            if (embedded.contains(concreteDepUrn)) continue;
            Optional<JsonNode> depSchema = registry.fetchElementOrXsd(concreteDepUrn);
            if (depSchema.isEmpty()) continue;
            embedded.add(concreteDepUrn);
            // The root's $ref to this dependency is rewritten to its concrete versioned URN
            // (see concreteRef/rewriteRefsToConcrete). The embedded resource's $id must equal
            // that exact URN, otherwise the $ref dangles. For an XSD-authored Element the
            // converted $id is type-/bundle-level (one segment longer than the artifact URN) and
            // would never match — so we stamp the concrete dep URN as the embedded $id.
            String key = uniqueDefKey(defsNode, UrnParser.nameFromUrn(depUrn), depUrn);
            defsNode.set(key, ensureEmbeddedId(depSchema.get(), concreteDepUrn));
        }

        // Build the root document
        ObjectNode bundled = mapper.createObjectNode();
        bundled.put("$schema", JsonSchema.DRAFT_2020_12);

        // Carry $id from the original schema or fall back to the requested URN
        bundled.put("$id", rootId);

        // Copy all root fields verbatim except $schema, $id, and $defs (handled above).
        // CORE URN $ref values are intentionally left untouched — they resolve against
        // the embedded $ids assembled above.
        root.properties().forEach(entry -> {
            String key = entry.getKey();
            if (!key.equals("$schema") && !key.equals("$id") && !key.equals("$defs")) {
                bundled.set(key, entry.getValue().deepCopy());
            }
        });

        if (!defsNode.isEmpty()) {
            bundled.set("$defs", defsNode);
        }

        // Embedded resources carry concrete versioned $ids; rewrite any :latest/logical $ref to the
        // concrete resolved version so refs resolve against those embedded $ids (pinned refs unchanged).
        return Optional.of(rewriteRefsToConcrete(bundled, memberPins));
    }

    /**
     * The concrete versioned URN a view should emit for a reference: a {@code :latest} or logical
     * CORE URN resolves to the target's current version; a pinned URN (or non-URN ref) is unchanged.
     */
    private String concreteRef(String ref, Map<String, String> memberPins) {
        if (ref != null && UrnParser.isUrn(ref)
                && (UrnParser.isLatest(ref) || UrnParser.versionFromUrn(ref) == null)) {
            // A pin the root declares wins over the target's current version. An explicit :latest is
            // a request for current and is left to resolve normally.
            if (!UrnParser.isLatest(ref)) {
                String pinned = memberPins.get(UrnParser.logicalUrn(ref));
                if (pinned != null) return pinned;
            }
            return registry.resolveReference(ref).orElse(ref);
        }
        return ref;
    }

    /**
     * The member versions a DataStructure manifest pins, keyed by logical URN. A member's reference to
     * a sibling is logical — two members referencing each other could not each carry the other's
     * assigned version — so these pins, not the target's current version, decide what a view embeds.
     * Empty for any root that is not a manifest of bare URN {@code $ref}s.
     */
    private static Map<String, String> memberPins(JsonNode root) {
        JsonNode defs = root.path("$defs");
        if (!defs.isObject()) return Map.of();
        Map<String, String> pins = new LinkedHashMap<>();
        defs.properties().forEach(e -> {
            String ref = e.getValue().path("$ref").asText(null);
            if (UrnParser.isUrn(ref) && UrnParser.versionFromUrn(ref) != null) {
                pins.put(UrnParser.logicalUrn(ref), ref);
            }
        });
        return pins;
    }

    /**
     * Recursively rewrite every CORE-URN {@code $ref} that is {@code :latest} or logical to its
     * concrete resolved version. Pinned refs and non-URN (JSON-pointer) refs are left untouched, so
     * no extra registry lookups happen for them.
     */
    private JsonNode rewriteRefsToConcrete(JsonNode node, Map<String, String> memberPins) {
        if (node == null) return node;
        if (node.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            node.properties().forEach(e -> {
                if ("$ref".equals(e.getKey()) && e.getValue().isTextual()) {
                    copy.put("$ref", concreteRef(e.getValue().asText(), memberPins));
                } else {
                    copy.set(e.getKey(), rewriteRefsToConcrete(e.getValue(), memberPins));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode arr = mapper.createArrayNode();
            node.forEach(child -> arr.add(rewriteRefsToConcrete(child, memberPins)));
            return arr;
        }
        return node;
    }

    /**
     * Pick a {@code $defs} key that is not already used: prefer the readable schema
     * name; on collision fall back to the logical URN (guaranteed unique per artifact),
     * then to a numeric suffix as a last resort. Resolution is by {@code $id}, so the
     * key only affects readability — but it must be unique so no embedded resource is
     * silently dropped.
     */
    private static String uniqueDefKey(ObjectNode defs, String preferred, String depUrn) {
        if (!defs.has(preferred)) return preferred;
        String logical = UrnParser.logicalUrn(depUrn);
        if (!defs.has(logical)) return logical;
        int i = 2;
        while (defs.has(preferred + "_" + i)) i++;
        return preferred + "_" + i;
    }

    /**
     * Return a deep copy of an embedded dependency whose {@code $id} is exactly
     * {@code concreteDepUrn} — the concrete versioned artifact URN the document's
     * {@code $ref} to this dependency resolves to. Stamping (rather than merely backfilling)
     * is required for XSD-authored Elements: the converted schema carries a
     * type-/bundle-level {@code $id} (e.g. {@code …:Person:Personentyp:1.0.0} or
     * {@code …:Person:bundle:1.0.0}) that is one segment longer than the artifact URN and would
     * never equal the {@code $ref}, leaving it dangling. Overwriting the {@code $id} is safe even
     * for the multi-type {@code bundle} conversion: its internal {@code #/$defs/Type} and
     * {@code properties} pointers are document-relative and rebase against whatever {@code $id} is
     * stamped. JSON-authored deps already carry the artifact {@code $id}, so this is a no-op for them.
     */
    private JsonNode ensureEmbeddedId(JsonNode schema, String concreteDepUrn) {
        ObjectNode copy = (ObjectNode) schema.deepCopy();
        String existing = copy.path("$id").asText(null);
        if (existing == null || !existing.equals(concreteDepUrn)) {
            copy.put("$id", concreteDepUrn);
        }
        return copy;
    }

}

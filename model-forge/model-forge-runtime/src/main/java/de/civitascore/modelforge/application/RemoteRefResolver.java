package de.civitascore.modelforge.application;

import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Replaces the {@code http(s)} {@code $ref}s of a fetched document with the schemas they point at,
 * so the imported artifact is self-contained.
 *
 * <p>Public catalogues compose a model from shared documents: an entity declares itself as several
 * common schemas plus its own fields, and the properties that matter are often in the common ones.
 * Storing such a document without resolving its references would record a model missing most of its
 * properties, so the references are resolved before the document is accepted.
 *
 * <p>Resolution happens only here, on the import path, which is the only path with a guarded
 * fetcher. The stored artifact carries no remote references, so reading or validating it afterwards
 * never reaches the network. CORE-URN references are left alone — they resolve against the registry.
 */
public class RemoteRefResolver {

    /** Distinct upstream documents one import may fetch. Each is separately size- and time-capped. */
    private static final int MAX_DOCUMENTS = 10;

    /** Nesting of reference-inside-reference an import may follow. */
    private static final int MAX_DEPTH = 6;

    private final RemoteSchemaRepository fetcher;
    private final ObjectMapper mapper;

    public RemoteRefResolver(RemoteSchemaRepository fetcher, ObjectMapper mapper) {
        this.fetcher = fetcher;
        this.mapper  = mapper;
    }

    /**
     * The document with every {@code http(s)} reference replaced by the schema it points at.
     *
     * <p>Local {@code #/...} pointers of the document itself are preserved: they already resolve
     * within it. Inside a fetched document they are resolved against that document, because once its
     * fragment is inlined here, a pointer into its own {@code $defs} would otherwise dangle.
     *
     * @throws IllegalArgumentException when a reference cannot be resolved, is circular, or the
     *     document exceeds the fetch or nesting caps (→ 400: the document cannot be imported as
     *     authored)
     */
    public JsonNode inlineRemoteRefs(JsonNode document) {
        if (document == null) return null;
        return rewrite(document, null, new Budget(), new LinkedHashSet<>(), 0);
    }

    /**
     * @param base the URL the node's relative and {@code #} references resolve against, or
     *     {@code null} for the imported document itself, whose own pointers stay as authored
     */
    private JsonNode rewrite(JsonNode node, URI base, Budget budget, Set<String> active, int depth) {
        if (node == null || !(node.isObject() || node.isArray())) return node;
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            node.forEach(child -> out.add(rewrite(child, base, budget, active, depth)));
            return out;
        }
        String target = remoteRefTarget(node, base);
        if (target != null) {
            return inline(node, target, base, budget, active, depth);
        }
        ObjectNode out = mapper.createObjectNode();
        node.properties().forEach(e -> out.set(e.getKey(), rewrite(e.getValue(), base, budget, active, depth)));
        return out;
    }

    /**
     * The absolute {@code http(s)} URL a node's {@code $ref} resolves to, or {@code null} when the
     * node carries no reference or one this class must not touch — a CORE URN, or a pointer local to
     * the imported document.
     */
    private static String remoteRefTarget(JsonNode node, URI base) {
        JsonNode ref = node.get("$ref");
        if (ref == null || !ref.isTextual()) return null;
        String value = ref.asText();
        if (value.isBlank() || value.startsWith("urn:")) return null;
        // A '#' pointer in the imported document is local and already resolves; the same pointer
        // inside a fetched document must be resolved against that document.
        if (value.startsWith("#") && base == null) return null;
        URI resolved;
        try {
            URI candidate = URI.create(value);
            resolved = candidate.isAbsolute() ? candidate : base == null ? null : base.resolve(candidate);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (resolved == null) return null;
        String scheme = resolved.getScheme() == null ? "" : resolved.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("http") || scheme.equals("https") ? resolved.toString() : null;
    }

    private JsonNode inline(JsonNode node, String target, URI base, Budget budget,
                            Set<String> active, int depth) {
        if (!active.add(target)) {
            throw new IllegalArgumentException(
                "Circular remote $ref cannot be resolved for import: " + target);
        }
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException(
                "Remote $ref nesting exceeds " + MAX_DEPTH + " levels at: " + target);
        }
        try {
            JsonNode fragment = fetchFragment(target, budget);
            URI fragmentBase = URI.create(documentOf(target));
            JsonNode resolved = stripForeignIdentity(
                rewrite(fragment, fragmentBase, budget, active, depth + 1));
            // A lone $ref is replaced outright. With siblings it becomes one branch of an allOf,
            // which is what a 2020-12 $ref alongside other keywords already means.
            if (node.size() == 1) return resolved;
            ObjectNode siblings = mapper.createObjectNode();
            node.properties().forEach(e -> {
                if (!"$ref".equals(e.getKey())) {
                    siblings.set(e.getKey(), rewrite(e.getValue(), base, budget, active, depth));
                }
            });
            ObjectNode out = mapper.createObjectNode();
            ArrayNode allOf = out.putArray("allOf");
            allOf.add(resolved);
            allOf.add(siblings);
            return out;
        } finally {
            active.remove(target);
        }
    }

    private JsonNode fetchFragment(String target, Budget budget) {
        String documentUrl = documentOf(target);
        JsonNode document = budget.fetched.get(documentUrl);
        if (document == null) {
            if (budget.fetched.size() >= MAX_DOCUMENTS) {
                throw new IllegalArgumentException(
                    "Import would fetch more than " + MAX_DOCUMENTS + " upstream documents");
            }
            document = fetcher.fetchJson(documentUrl);
            if (document == null) {
                throw new IllegalArgumentException("Remote $ref returned no schema: " + documentUrl);
            }
            budget.fetched.put(documentUrl, document);
        }
        int hash = target.indexOf('#');
        String pointer = hash < 0 ? "" : target.substring(hash + 1);
        if (pointer.isEmpty()) return document;
        JsonNode fragment = document.at(pointer);
        if (fragment.isMissingNode()) {
            throw new IllegalArgumentException("Remote $ref does not resolve: " + target);
        }
        return fragment;
    }

    private static String documentOf(String target) {
        int hash = target.indexOf('#');
        return hash < 0 ? target : target.substring(0, hash);
    }

    /**
     * The inlined schema without the {@code $id} and {@code $schema} of the document it came from.
     * Both assert an identity for the whole upstream document, which an embedded fragment must not
     * claim inside the importing artifact.
     */
    private static JsonNode stripForeignIdentity(JsonNode node) {
        if (!node.isObject()) return node;
        ObjectNode copy = (ObjectNode) node.deepCopy();
        copy.remove("$id");
        copy.remove("$schema");
        return copy;
    }

    /** Per-import fetch cache, which also bounds how many upstream documents one import may pull. */
    private static final class Budget {
        private final Map<String, JsonNode> fetched = new HashMap<>();
    }
}

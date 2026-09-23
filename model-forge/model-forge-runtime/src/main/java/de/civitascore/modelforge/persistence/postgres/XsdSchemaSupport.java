package de.civitascore.modelforge.persistence.postgres;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.util.JsonSchema;
import de.civitascore.modelforge.util.SecureXsdParser;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * XSD domain logic shared by the registry: transparent XSD → JSON Schema 2020-12 conversion
 * (with a per-URN cache), {@code targetNamespace} extraction and {@code xs:import} resolution.
 *
 * <p>The conversion behaviour is kept verbatim (placeholder / single / {@code $defs} bundle,
 * {@code x-xsd-source} annotation) so
 * the inlined/bundled views and {@code allSchemas} are byte-for-byte unchanged.
 * Storage is injected as a raw-XSD supplier and a namespace lookup, so the support is unaware
 * of the persistence backend.
 */
class XsdSchemaSupport {

    private static final Logger log = LoggerFactory.getLogger(XsdSchemaSupport.class);

    private final XsdSchemaConverter converter;
    private final ObjectMapper mapper;

    /** versioned XSD URN → converted JSON Schema (cache, evicted on store). */
    private final java.util.Map<String, JsonNode> cache = new ConcurrentHashMap<>();

    XsdSchemaSupport(XsdSchemaConverter converter, ObjectMapper mapper) {
        this.converter = converter;
        this.mapper = mapper;
    }

    /** Evict every cached version of the given model_forge.artifact (the cache is keyed per version). */
    void evict(String urn) {
        String logical = UrnParser.logicalUrn(urn);
        cache.keySet().removeIf(k -> logical.equals(UrnParser.logicalUrn(k)));
    }
    void clearCache()        { cache.clear(); }

    /**
     * Converts an XSD model_forge.artifact to JSON Schema 2020-12, caching the result per <em>version</em>
     * so a multi-version XSD never returns another version's conversion.
     *
     * @param urn         the (versioned or logical) XSD URN
     * @param rawXsd      supplier of the raw XSD text for {@code urn}
     */
    Optional<JsonNode> toJsonSchema(String urn, Supplier<Optional<String>> rawXsd) {
        if (converter == null) return Optional.empty();
        String reqVersion = UrnParser.versionFromUrn(urn);
        // A concrete version pins the cache key; a logical/:latest URN (no resolvable version)
        // falls back to the logical key — degraded but correct for a single-version artifact.
        String cacheKey = (reqVersion != null && !UrnParser.isLatest(urn))
            ? UrnParser.withVersion(UrnParser.logicalUrn(urn), reqVersion)
            : UrnParser.logicalUrn(urn);
        JsonNode cached = cache.get(cacheKey);
        if (cached != null) return Optional.of(cached);

        return rawXsd.get().map(xsd -> {
            String logical    = UrnParser.logicalUrn(urn);
            String version    = UrnParser.versionFromUrn(urn);
            // The logical URN is already the canonical ':element:' identity (the format is no
            // longer part of the URN), so it is the prefix for the converted schema's $id verbatim.
            String jsonPrefix = logical;
            String ver        = version != null ? version : "1.0.0";

            var schemas = converter.convert(xsd, jsonPrefix);
            if (schemas.isEmpty()) {
                ObjectNode placeholder = mapper.createObjectNode();
                placeholder.put("$schema", JsonSchema.DRAFT_2020_12);
                placeholder.put("$id",     jsonPrefix + ":empty:" + ver);
                placeholder.put("x-xsd-source", urn);
                placeholder.put("type",    "object");
                return (JsonNode) placeholder;  // not cached — may get content later
            }
            if (schemas.size() == 1) {
                ObjectNode single = (ObjectNode) schemas.values().iterator().next().deepCopy();
                single.put("x-xsd-source", urn);
                cache.put(cacheKey, single);
                return (JsonNode) single;
            }
            ObjectNode bundle = mapper.createObjectNode();
            bundle.put("$schema", JsonSchema.DRAFT_2020_12);
            bundle.put("$id",     jsonPrefix + ":bundle:" + ver);
            bundle.put("title",   UrnParser.nameFromUrn(urn));
            bundle.put("x-xsd-source", urn);
            bundle.put("type",    "object");
            var defs  = mapper.createObjectNode();
            var props = mapper.createObjectNode();
            schemas.forEach((name, schema) -> {
                ObjectNode s = (ObjectNode) schema.deepCopy();
                s.put("x-xsd-source", urn);
                defs.set(name, s);
                props.set(name, mapper.createObjectNode().put("$ref", "#/$defs/" + name));
            });
            bundle.set("$defs", defs);
            bundle.set("properties", props);
            cache.put(cacheKey, bundle);
            return (JsonNode) bundle;
        });
    }

    /** The {@code targetNamespace} of an XSD, or empty when absent/unparseable. */
    Optional<String> extractTargetNamespace(String xsdContent) {
        if (xsdContent == null) return Optional.empty();
        try {
            XmlSchema schema = SecureXsdParser.parse(xsdContent);
            String ns = schema.getTargetNamespace();
            return ns != null && !ns.isBlank() ? Optional.of(ns) : Optional.empty();
        } catch (Exception e) {
            log.debug("Could not extract targetNamespace: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolves the {@code xs:import} namespaces of an XSD to referenced Element URNs.
     *
     * <p>Two cases, supporting cross-format linking:
     * <ul>
     *   <li>If the {@code @namespace} is itself a CORE URN
     *       ({@code xs:import namespace="urn:core:…:element:…:Foo:1.0.0"}), it directly
     *       names the target Element (of any format) — used as the reference verbatim
     *       (reduced to its logical URN).
     *   <li>Otherwise a classic XML namespace: resolved to the logical URN of the XSD model_forge.artifact
     *       that declares it, via {@code nsLookup} ({@code model_forge.xsd_namespace} index).
     * </ul>
     * Unresolvable imports are dropped.
     */
    Set<String> extractImportRefs(String xsdContent, Function<String, Optional<String>> nsLookup) {
        if (xsdContent == null) return Set.of();
        try {
            XmlSchema schema = SecureXsdParser.parse(xsdContent);
            Set<String> refs = new LinkedHashSet<>();
            schema.getExternals().stream()
                .filter(XmlSchemaImport.class::isInstance)
                .map(XmlSchemaImport.class::cast)
                .map(XmlSchemaImport::getNamespace)
                .filter(Objects::nonNull)
                .forEach(ns -> {
                    if (UrnParser.isUrn(ns)) {
                        // A CORE-URN namespace is the reference verbatim (pinned version or :latest);
                        // resolution to a concrete version happens on read.
                        refs.add(ns);
                    } else {
                        nsLookup.apply(ns).ifPresent(refs::add);
                    }
                });
            return Collections.unmodifiableSet(refs);
        } catch (Exception e) {
            log.debug("Could not extract xs:import refs: {}", e.getMessage());
            return Set.of();
        }
    }
}

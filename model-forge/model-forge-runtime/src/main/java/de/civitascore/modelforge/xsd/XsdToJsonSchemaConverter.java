package de.civitascore.modelforge.xsd;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.core.port.XsdSchemaConversionException;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.util.JsonSchema;
import de.civitascore.modelforge.util.SecureXsdParser;
import de.civitascore.modelforge.util.XsdDocumentation;
import de.civitascore.modelforge.util.XsdTypeMapping;
import org.apache.ws.commons.schema.*;
import org.apache.ws.commons.schema.constants.Constants;
import org.apache.ws.commons.schema.utils.XmlSchemaObjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.*;

/**
 * Converts an XSD document to a map of JSON Schema 2020-12 documents.
 *
 * <p>Each named {@code xs:complexType} and {@code xs:simpleType} becomes one JSON Schema
 * with a CORE URN as {@code $id}. Uses Apache WS-Commons XmlSchema Core for robust,
 * namespace-aware XSD parsing — no hand-rolled DOM traversal.
 *
 * <h3>Mapping</h3>
 * <ul>
 *   <li>{@code xs:complexType}         → {@code {"type":"object","properties":{…}}}
 *   <li>{@code xs:sequence / xs:all}   → properties + required array
 *   <li>{@code xs:choice}              → {@code oneOf}
 *   <li>{@code xs:complexContent/extension} → {@code allOf: [$ref, {properties}]}
 *   <li>{@code xs:simpleContent/extension}  → {@code {"type":"object","properties":{"_value":{…}}}}
 *   <li>{@code xs:simpleType/restriction}   → type + constraints
 *   <li>{@code xs:attribute}           → property with {@code @}-prefix convention
 *   <li>{@code xs:annotation/documentation} → {@code description}
 * </ul>
 */
public class XsdToJsonSchemaConverter implements XsdSchemaConverter {

    private static final Logger log    = LoggerFactory.getLogger(XsdToJsonSchemaConverter.class);
    private static final String SCHEMA = JsonSchema.DRAFT_2020_12;
    private static final String XSD_NS = Constants.URI_2001_SCHEMA_XSD;

    private final ObjectMapper mapper;

    public XsdToJsonSchemaConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Convert an XSD document to a map of {@code typeName → JSON Schema 2020-12}.
     *
     * <p>{@code $id} and cross-type {@code $ref}s are minted as <em>logical</em> (version-less)
     * CORE URNs — Model Forge is the sole version authority and always assigns a brand-new
     * artifact version {@code 1.0.0} regardless of any version segment supplied by a caller, so
     * baking the XÖV/XSD standard's own version string into the URN would produce a pinned
     * reference that can never resolve. Logical URNs resolve to the registry's actual current
     * version at read time (see {@code DependencyGraphService#resolveToVersioned}) — the same
     * pattern already used for cross-format ({@code xs:import}-based) references below.
     *
     * @param xsdContent raw XSD string
     * @param urnPrefix  CORE URN prefix up to (but not including) the type name,
     *                   e.g. {@code urn:core:standard:xoev:element:xmeld}
     * @return one schema per named type; empty only when the XSD genuinely declares no named types
     * @throws XsdSchemaConversionException when the XSD is malformed or cannot be converted —
     *         callers must not mistake a broken document for an empty one
     */
    @Override
    public Map<String, ObjectNode> convert(String xsdContent, String urnPrefix) {
        if (xsdContent == null || xsdContent.isBlank()) return Map.of();
        try {
            XmlSchema schema = SecureXsdParser.parse(xsdContent);
            Map<String, ObjectNode> result = new LinkedHashMap<>();
            // getSchemaTypes() is backed by a HashMap, so its entrySet iteration order is not
            // stable across JVM runs. Sort by the QName local part so $defs/property order is
            // deterministic; content is unchanged, only ordering becomes stable.
            List<Map.Entry<QName, XmlSchemaType>> entries =
                    new ArrayList<>(schema.getSchemaTypes().entrySet());
            entries.sort(Comparator.comparing(en -> en.getKey().getLocalPart()));
            for (Map.Entry<QName, XmlSchemaType> e : entries) {
                String name = e.getKey().getLocalPart();
                String urn  = urnPrefix + ":" + name + ":" + UrnParser.deriveDisambiguator(name);
                ObjectNode json = e.getValue() instanceof XmlSchemaComplexType ct
                        ? complexType(ct, urnPrefix)
                        : simpleType((XmlSchemaSimpleType) e.getValue());
                // Prepend $schema and $id so they appear first
                ObjectNode ordered = mapper.createObjectNode();
                ordered.put("$schema", SCHEMA);
                ordered.put("$id",     urn);
                ordered.put("title",   name);
                json.properties().forEach(f -> ordered.set(f.getKey(), f.getValue()));
                result.put(name, ordered);
            }
            return result;
        } catch (Exception e) {
            log.warn("XSD conversion failed: {}", e.getMessage());
            throw new XsdSchemaConversionException("XSD conversion failed: " + e.getMessage(), e);
        }
    }

    // ── Complex type ───────────────────────────────────────────────────────────

    private ObjectNode complexType(XmlSchemaComplexType ct, String urnPrefix) {
        ObjectNode node = mapper.createObjectNode();
        descriptionOf(ct).ifPresent(d -> node.put("description", d));

        // xs:complexContent/xs:extension → allOf: [$ref, {own properties + required}]
        if (ct.getContentModel() instanceof XmlSchemaComplexContent cc
                && cc.getContent() instanceof XmlSchemaComplexContentExtension ext) {
            String baseUrn = refForType(ext.getBaseTypeName(), urnPrefix);
            ObjectNode ownProps = propsFromParticleAndAttrs(ext.getParticle(), ext.getAttributes(), urnPrefix);
            ArrayNode allOf = mapper.createArrayNode();
            allOf.add(mapper.createObjectNode().put("$ref", baseUrn));
            ObjectNode own = mapper.createObjectNode();
            if (!ownProps.isEmpty()) own.set("properties", ownProps);
            List<String> ownRequired = requiredFrom(ext.getParticle(), ext.getAttributes());
            if (!ownRequired.isEmpty()) {
                ArrayNode req = mapper.createArrayNode();
                ownRequired.forEach(req::add);
                own.set("required", req);
            }
            if (!own.isEmpty()) allOf.add(own);
            node.set("allOf", allOf);
            return node;
        }

        // xs:simpleContent/xs:extension → _value + attribute properties
        if (ct.getContentModel() instanceof XmlSchemaSimpleContent sc
                && sc.getContent() instanceof XmlSchemaSimpleContentExtension ext) {
            ObjectNode props = mapper.createObjectNode();
            ObjectNode valueSchema = mapper.createObjectNode();
            typeAndFormat(ext.getBaseTypeName(), valueSchema);
            props.set("_value", valueSchema);
            addAttrProps(ext.getAttributes(), props);
            node.put("type", "object");
            node.set("properties", props);
            return node;
        }

        // Standard sequence/choice/attributes
        node.put("type", "object");
        ObjectNode props = propsFromParticleAndAttrs(ct.getParticle(), ct.getAttributes(), urnPrefix);
        if (!props.isEmpty()) node.set("properties", props);
        List<String> required = requiredFrom(ct.getParticle(), ct.getAttributes());
        if (!required.isEmpty()) {
            ArrayNode req = mapper.createArrayNode();
            required.forEach(req::add);
            node.set("required", req);
        }
        // xs:choice → oneOf: each branch must be present exclusively. Properties stay
        // declared (and optional) above; the oneOf constraint enforces the exclusivity.
        ArrayNode oneOf = choiceAlternatives(ct.getParticle());
        if (oneOf != null) node.set("oneOf", oneOf);
        return node;
    }

    private ObjectNode propsFromParticleAndAttrs(XmlSchemaParticle particle,
                                                  List<XmlSchemaAttributeOrGroupRef> attrs,
                                                  String urnPrefix) {
        ObjectNode props = mapper.createObjectNode();
        addParticleProps(particle, props, urnPrefix);
        addAttrProps(attrs, props);
        return props;
    }

    private void addParticleProps(XmlSchemaParticle particle, ObjectNode props,
                                   String urnPrefix) {
        if (particle == null) return;
        List<? extends XmlSchemaObjectBase> items = switch (particle) {
            case XmlSchemaSequence s -> s.getItems();
            case XmlSchemaAll      a -> a.getItems();
            case XmlSchemaChoice   c -> c.getItems();
            default                  -> List.of();
        };
        for (XmlSchemaObjectBase item : items) {
            if (item instanceof XmlSchemaElement el && el.getName() != null) {
                ObjectNode elSchema = elementSchema(el, urnPrefix);
                props.set(el.getName(), elSchema);
            } else if (item instanceof XmlSchemaParticle nested) {
                addParticleProps(nested, props, urnPrefix);
            }
        }
    }

    /**
     * Builds the {@code oneOf} alternatives for an {@code xs:choice} that is either the
     * type's particle itself or directly nested in its {@code xs:sequence}. Each branch
     * becomes {@code {"required": [<branch element names>]}}; branches without named
     * elements are skipped. Returns {@code null} when there is no choice (or fewer than
     * two usable branches).
     */
    private ArrayNode choiceAlternatives(XmlSchemaParticle particle) {
        XmlSchemaChoice choice = findChoice(particle);
        if (choice == null) return null;
        ArrayNode oneOf = mapper.createArrayNode();
        for (XmlSchemaObjectBase item : choice.getItems()) {
            List<String> branchNames = new ArrayList<>();
            collectElementNames(item, branchNames);
            if (branchNames.isEmpty()) continue;
            ObjectNode alternative = mapper.createObjectNode();
            ArrayNode req = mapper.createArrayNode();
            branchNames.forEach(req::add);
            alternative.set("required", req);
            oneOf.add(alternative);
        }
        return oneOf.size() >= 2 ? oneOf : null;
    }

    private static XmlSchemaChoice findChoice(XmlSchemaParticle particle) {
        if (particle instanceof XmlSchemaChoice c) return c;
        if (particle instanceof XmlSchemaSequence s) {
            for (XmlSchemaObjectBase item : s.getItems()) {
                if (item instanceof XmlSchemaChoice c) return c;
            }
        }
        return null;
    }

    private static void collectElementNames(XmlSchemaObjectBase item, List<String> names) {
        if (item instanceof XmlSchemaElement el && el.getName() != null) {
            names.add(el.getName());
        } else if (item instanceof XmlSchemaSequence s) {
            s.getItems().forEach(child -> collectElementNames(child, names));
        } else if (item instanceof XmlSchemaAll a) {
            a.getItems().forEach(child -> collectElementNames(child, names));
        }
    }

    private ObjectNode elementSchema(XmlSchemaElement el, String urnPrefix) {
        ObjectNode s = mapper.createObjectNode();
        descriptionOf(el).ifPresent(d -> s.put("description", d));
        QName typeName = el.getSchemaTypeName();
        if (typeName != null && XSD_NS.equals(typeName.getNamespaceURI())) {
            typeAndFormat(typeName, s);
        } else if (typeName != null) {
            s.put("$ref", refForType(typeName, urnPrefix));
        } else {
            s.put("type", "object");
        }
        if (el.getMaxOccurs() == Long.MAX_VALUE || el.getMaxOccurs() > 1) {
            ObjectNode itemSchema = (ObjectNode) s.deepCopy();
            s.removeAll();
            s.put("type", "array");
            s.set("items", itemSchema);
        }
        return s;
    }

    private void addAttrProps(List<XmlSchemaAttributeOrGroupRef> attrs, ObjectNode props) {
        for (var entry : attrs) {
            if (entry instanceof XmlSchemaAttribute a && a.getName() != null) {
                ObjectNode s = mapper.createObjectNode();
                descriptionOf(a).ifPresent(d -> s.put("description", d));
                typeAndFormat(a.getSchemaTypeName(), s);
                props.set("@" + a.getName(), s);
            }
        }
    }

    private List<String> requiredFrom(XmlSchemaParticle particle,
                                       List<XmlSchemaAttributeOrGroupRef> attrs) {
        List<String> required = new ArrayList<>();
        // xs:sequence and xs:all children with minOccurs >= 1 are required.
        // xs:choice children are deliberately NOT required here — their exclusive
        // presence is expressed via the oneOf constraint (see choiceAlternatives).
        collectRequired(particle, required);
        for (var entry : attrs) {
            if (entry instanceof XmlSchemaAttribute a && a.getName() != null
                    && a.getUse() == XmlSchemaUse.REQUIRED) {
                required.add("@" + a.getName());
            }
        }
        return required;
    }

    /**
     * Collects required element names, descending into nested {@code xs:sequence}/{@code xs:all}
     * groups the same way {@code addParticleProps} does when it declares the properties. Without the
     * recursion an element with {@code minOccurs="1"} inside a nested group became an optional
     * property, so the generated schema accepted instances the XSD rejects.
     *
     * <p>A nested group whose own {@code minOccurs} is 0 contributes nothing: its children are only
     * required when the group is present. {@code xs:choice} is skipped at every level — exclusivity
     * is expressed by the {@code oneOf} that {@code choiceAlternatives} builds.
     */
    private void collectRequired(XmlSchemaParticle particle, List<String> required) {
        List<? extends XmlSchemaObjectBase> items = switch (particle) {
            case XmlSchemaSequence s -> s.getItems();
            case XmlSchemaAll      a -> a.getItems();
            case null, default       -> List.of();
        };
        for (var item : items) {
            if (item instanceof XmlSchemaElement el && el.getName() != null
                    && el.getMinOccurs() >= 1) {
                required.add(el.getName());
            } else if (item instanceof XmlSchemaParticle nested && nested.getMinOccurs() >= 1) {
                collectRequired(nested, required);
            }
        }
    }

    // ── Simple type ────────────────────────────────────────────────────────────

    private ObjectNode simpleType(XmlSchemaSimpleType st) {
        ObjectNode node = mapper.createObjectNode();
        descriptionOf(st).ifPresent(d -> node.put("description", d));
        if (!(st.getContent() instanceof XmlSchemaSimpleTypeRestriction r)) {
            node.put("type", "string");
            return node;
        }
        typeAndFormat(r.getBaseTypeName(), node);
        List<XmlSchemaFacet> facets = r.getFacets();
        List<String> enums = facets.stream()
                .filter(XmlSchemaEnumerationFacet.class::isInstance)
                .map(f -> (String) ((XmlSchemaEnumerationFacet) f).getValue()).toList();
        if (!enums.isEmpty()) {
            // Enumeration values are extracted as raw strings, so the base type may only be
            // kept when it is "string" — any other type would contradict the string values.
            if (!"string".equals(node.path("type").asText(null))) {
                node.remove("type");
            }
            ArrayNode arr = mapper.createArrayNode();
            enums.forEach(arr::add);
            node.set("enum", arr);
            return node;
        }
        facets.forEach(f -> applyFacet(f, node));
        return node;
    }

    /**
     * The {@code $ref} URN for a referenced named type — always logical (version-less), so it
     * resolves against whatever version the registry actually assigns on write (see the
     * {@link #convert} javadoc).
     *
     * <p>When the type's QName namespace is itself a CORE URN — i.e. it came from an
     * {@code xs:import namespace="urn:core:…:element:…:Foo:1.0.0"} — the reference points at
     * that target Element (cross-format link), reduced to its logical URN. Otherwise the type
     * is local to this schema, so the reference is built from this schema's own {@code urnPrefix}.
     */
    private static String refForType(QName typeName, String urnPrefix) {
        String ns = typeName.getNamespaceURI();
        if (ns != null && UrnParser.isUrn(ns)) {
            return UrnParser.logicalUrn(ns);
        }
        String local = typeName.getLocalPart();
        return urnPrefix + ":" + local + ":" + UrnParser.deriveDisambiguator(local);
    }

    // ── Type mapping ───────────────────────────────────────────────────────────

    private void typeAndFormat(QName qname, ObjectNode target) {
        if (qname == null || !XSD_NS.equals(qname.getNamespaceURI())) {
            target.put("type", "string");
            return;
        }
        switch (XsdTypeMapping.kindOf(qname.getLocalPart())) {
            case INTEGER   -> target.put("type", "integer");
            case NUMBER    -> target.put("type", "number");
            case BOOLEAN   -> target.put("type", "boolean");
            case DATE      -> { target.put("type", "string"); target.put("format", "date"); }
            case DATE_TIME -> { target.put("type", "string"); target.put("format", "date-time"); }
            case TIME      -> { target.put("type", "string"); target.put("format", "time"); }
            case BINARY, STRING -> target.put("type", "string");
        }
    }

    /**
     * Maps one XSD facet onto its JSON Schema keyword. A bound that is not numeric — legal XSD, e.g.
     * {@code xs:minInclusive value="2020-01-01"} on an {@code xs:date} restriction — is skipped
     * rather than allowed to abort the conversion: the facet has no JSON Schema equivalent, and
     * failing here would reject the whole document over one untranslatable bound.
     */
    private void applyFacet(XmlSchemaFacet f, ObjectNode target) {
        String value = String.valueOf(f.getValue());
        try {
            if      (f instanceof XmlSchemaMinLengthFacet)  target.put("minLength",  Integer.parseInt(value));
            else if (f instanceof XmlSchemaMaxLengthFacet)  target.put("maxLength",  Integer.parseInt(value));
            else if (f instanceof XmlSchemaPatternFacet)    target.put("pattern",    value);
            else if (f instanceof XmlSchemaMinInclusiveFacet) target.put("minimum",  Double.parseDouble(value));
            else if (f instanceof XmlSchemaMaxInclusiveFacet) target.put("maximum",  Double.parseDouble(value));
            else if (f instanceof XmlSchemaMinExclusiveFacet) target.put("exclusiveMinimum", Double.parseDouble(value));
            else if (f instanceof XmlSchemaMaxExclusiveFacet) target.put("exclusiveMaximum", Double.parseDouble(value));
            else if (f instanceof XmlSchemaFractionDigitsFacet) {
                int digits = Integer.parseInt(value);
                if (digits > 0) target.put("multipleOf", Math.pow(10, -digits));
            }
        } catch (NumberFormatException e) {
            log.debug("Skipping non-numeric {} facet value '{}'", f.getClass().getSimpleName(), value);
        }
    }

    // ── Documentation ──────────────────────────────────────────────────────────

    private Optional<String> descriptionOf(XmlSchemaAnnotated annotated) {
        return XsdDocumentation.firstText(annotated);
    }
}

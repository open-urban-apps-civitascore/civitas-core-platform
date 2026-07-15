package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.urn.UrnParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Registry-aware existence check for strongly-typed {@code x-core-ref} foreign keys.
 *
 * <p>When an Element declares a property as a foreign key with a <em>concrete</em> target
 * (e.g. {@code "x-core-ref": { "type": "urn:core:...:Strasse:1.0.0" }}), the referenced
 * Element must exist in the registry. This is something pure JSON Schema validation cannot
 * express, so it is verified here against the {@link ArtifactRegistry}.
 *
 * <p>Category targets ({@code urn:core:type:<Kind>}, used by the generic CORE-IR meta-schemas)
 * carry no concrete target and are not checked; a present {@code type} that is neither the
 * category marker nor a CORE URN is reported as malformed rather than silently ignored. When no
 * registry is configured the check is a no-op — existence cannot be established in degraded mode.
 */
public class ReferenceExistenceValidator {

    /** Stable diagnostic code for a foreign key whose target artifact is not in the registry. */
    public static final String UNRESOLVED_CODE = "unresolved-core-ref";

    /** Stable diagnostic code for an {@code x-core-ref} whose {@code type} is not a CORE URN. */
    public static final String INVALID_CODE = "invalid-core-ref";

    private final ArtifactRegistry registry;
    private final SchemaRefExtractor      refExtractor;

    public ReferenceExistenceValidator(ArtifactRegistry registry, SchemaRefExtractor refExtractor) {
        this.registry     = registry;
        this.refExtractor = refExtractor;
    }

    /**
     * Checks that every concrete {@code x-core-ref} target in {@code schema} resolves to an
     * existing artifact in the registry.
     *
     * @param schema                   the Element JSON Schema to scan for {@code x-core-ref}s
     * @param alsoAvailableLogicalUrns logical URNs that count as present even if not yet stored —
     *                                 the artifacts created by the same request (co-imported
     *                                 targets and self-references); pass {@code Set.of()} if none
     * @return one error diagnostic per unresolved foreign-key target; empty when all resolve or
     *         when no registry is configured
     */
    public List<Diagnostic> checkForeignKeys(JsonNode schema, Set<String> alsoAvailableLogicalUrns) {
        Set<String> types = refExtractor.extractCoreRefTypes(schema);
        if (types.isEmpty()) return List.of();

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (String type : types) {
            if (isCategoryType(type)) continue;  // urn:core:type:<Kind> marker — no concrete target
            if (!UrnParser.isUrn(type)) {
                diagnostics.add(new Diagnostic(DiagnosticSeverity.ERROR,
                    "x-core-ref target is not a CORE URN: " + type, INVALID_CODE, null));
                continue;
            }
            if (alsoAvailableLogicalUrns.contains(UrnParser.logicalUrn(type))) continue;
            if (registry.resolveReference(type).isEmpty()) {
                diagnostics.add(new Diagnostic(DiagnosticSeverity.ERROR,
                    "x-core-ref target does not exist in the registry: " + type,
                    UNRESOLVED_CODE, null));
            }
        }
        return diagnostics;
    }

    /**
     * A category marker is exactly {@code urn:core:type:<Kind>} (no further segments). Matched
     * structurally rather than by prefix so a concrete artifact URN whose scope happens to be
     * {@code type} (e.g. {@code urn:core:type:civitas:element:common:Foo:1.0.0}) is still
     * treated as a concrete target and existence-checked.
     */
    private static boolean isCategoryType(String type) {
        return type.matches("urn:core:type:[A-Za-z0-9._-]+");
    }
}

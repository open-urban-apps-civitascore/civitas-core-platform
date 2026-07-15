package de.civitascore.modelforge.application;

import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReferenceExistenceValidator}: concrete {@code x-core-ref} foreign keys
 * are checked against the registry; category targets, co-imported targets and the degraded
 * (no-registry) mode are skipped.
 */
class ReferenceExistenceValidatorTest {

    private static final String STRASSE =
        "urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg:1.0.0";
    private static final String STRASSE_LOGICAL =
        "urn:core:platform:civitas:element:common:Strasse:u8pwgr2zzg";
    private static final String PLATZ =
        "urn:core:platform:civitas:element:common:Platz:dutffafv67:1.0.0";

    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaRefExtractor extractor = new SchemaRefExtractor();
    private final ArtifactRegistry registry = mock(ArtifactRegistry.class);
    private final ReferenceExistenceValidator validator =
        new ReferenceExistenceValidator(registry, extractor);

    /** An Element with one string property per given x-core-ref target type. */
    private JsonNode schemaWithFk(String... types) {
        ObjectNode props = mapper.createObjectNode();
        int i = 0;
        for (String type : types) {
            ObjectNode xcr = mapper.createObjectNode();
            xcr.put("type", type);
            ObjectNode fk = mapper.createObjectNode();
            fk.put("type", "string");
            fk.set("x-core-ref", xcr);
            props.set("fk" + (i++), fk);
        }
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", props);
        return schema;
    }

    @Test
    void concreteTargetMissing_reportsUnresolvedCoreRef() {
        when(registry.resolveReference(STRASSE)).thenReturn(Optional.empty());

        List<Diagnostic> diags = validator.checkForeignKeys(schemaWithFk(STRASSE), Set.of());

        assertThat(diags).hasSize(1);
        assertThat(diags.get(0).code()).isEqualTo(ReferenceExistenceValidator.UNRESOLVED_CODE);
        assertThat(diags.get(0).severity()).isEqualTo(DiagnosticSeverity.ERROR);
        assertThat(diags.get(0).message()).contains(STRASSE);
    }

    @Test
    void concreteTargetExists_noDiagnostics() {
        when(registry.resolveReference(STRASSE)).thenReturn(Optional.of(STRASSE));

        assertThat(validator.checkForeignKeys(schemaWithFk(STRASSE), Set.of())).isEmpty();
    }

    @Test
    void categoryTypeUrn_isNotChecked() {

        assertThat(validator.checkForeignKeys(schemaWithFk("urn:core:type:Element"), Set.of()))
            .isEmpty();
        verify(registry, never()).resolveReference(anyString());
    }

    @Test
    void coImportedTarget_isTreatedAsPresent() {

        // STRASSE's logical URN is among the artifacts created by the same request.
        assertThat(validator.checkForeignKeys(schemaWithFk(STRASSE), Set.of(STRASSE_LOGICAL)))
            .isEmpty();
        verify(registry, never()).resolveReference(anyString());
    }

    @Test
    void multipleTargets_reportOnlyTheMissingOnes() {
        when(registry.resolveReference(STRASSE)).thenReturn(Optional.empty());
        when(registry.resolveReference(PLATZ)).thenReturn(Optional.of(PLATZ));

        List<Diagnostic> diags = validator.checkForeignKeys(schemaWithFk(STRASSE, PLATZ), Set.of());

        assertThat(diags).hasSize(1);
        assertThat(diags.get(0).message()).contains(STRASSE);
    }

    @Test
    void malformedType_reportsInvalidCoreRef_withoutHittingTheRegistry() {

        List<Diagnostic> diags = validator.checkForeignKeys(schemaWithFk("Element"), Set.of());

        assertThat(diags).hasSize(1);
        assertThat(diags.get(0).code()).isEqualTo(ReferenceExistenceValidator.INVALID_CODE);
        verify(registry, never()).resolveReference(anyString());
    }

    @Test
    void concreteUrnWhoseScopeIsType_isExistenceChecked_notTreatedAsCategory() {
        // A real model_forge.artifact URN (>= 7 segments) whose scope segment happens to be "type" must NOT
        // be mistaken for the 4-segment urn:core:type:<Kind> category marker.
        String scopeTypeUrn = "urn:core:type:civitas:element:common:Foo:sajjwrc28m:1.0.0";
        when(registry.resolveReference(scopeTypeUrn)).thenReturn(Optional.empty());

        List<Diagnostic> diags = validator.checkForeignKeys(schemaWithFk(scopeTypeUrn), Set.of());

        assertThat(diags).hasSize(1);
        assertThat(diags.get(0).code()).isEqualTo(ReferenceExistenceValidator.UNRESOLVED_CODE);
    }
}

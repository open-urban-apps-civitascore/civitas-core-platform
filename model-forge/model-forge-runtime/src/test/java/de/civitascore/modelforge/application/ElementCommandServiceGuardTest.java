package de.civitascore.modelforge.application;

import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write path that does not go through {@link SchemaImportService}: {@code saveArtifact(ELEMENT)}
 * reaches the registry through here, so the identity and conformance invariants have to hold here
 * too or the import-path guards are merely a detour around them.
 */
class ElementCommandServiceGuardTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ArtifactRegistry registry;
    private ElementCommandService elements;

    private static final String URN = "urn:core:platform:civitas:element:common:Station:ab12cd34";
    private static final String XSD = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";

    @BeforeEach
    void setUp() {
        registry = mock(ArtifactRegistry.class);
        when(registry.extractImportRefs(anyString())).thenReturn(java.util.Set.of());
        elements = new ElementCommandService(registry, mock(DependencyGraphService.class),
            new SchemaRefExtractor(), new ModelValidator());
    }

    private JsonNode schema(String body) {
        return mapper.readTree(body);
    }

    @Test
    void storeXsdRefusesADisplayNameAsIdentity() {
        assertThatThrownBy(() -> elements.storeXsd("StationXsd", XSD, VersionBump.PATCH))
            .isInstanceOf(ValidationFailedException.class)
            .hasMessageContaining("StationXsd");

        verify(registry, never()).storeXsdElement(anyString(), anyString(), anySet(), any(VersionBump.class));
    }

    @Test
    void storeJsonSchemaRefusesADisplayNameAsIdentity() {
        assertThatThrownBy(() -> elements.storeJsonSchema("StationJson",
            schema("""
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object"}"""),
            VersionBump.PATCH, null))
            .isInstanceOf(ValidationFailedException.class)
            .hasMessageContaining("StationJson");

        verify(registry, never()).storeElement(anyString(), any(), anySet(), anySet(),
            nullable(String.class), any(VersionBump.class), nullable(String.class));
    }

    /** A rejected identity that surfaced as a fault would reach the caller as a 500, not a 400. */
    @Test
    void aRefusedIdentityCarriesTheDiagnosticThatMakesItABadRequest() {
        assertThatThrownBy(() -> elements.storeXsd("StationXsd", XSD, VersionBump.PATCH))
            .asInstanceOf(throwable(ValidationFailedException.class))
            .extracting(ValidationFailedException::diagnostics, list(Diagnostic.class))
            .singleElement()
            .satisfies(d -> {
                assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
                assertThat(d.code()).isEqualTo("invalid-artifact-id");
            });
    }

    @Test
    void storeJsonSchemaRefusesANonConformingSchema() {
        assertThatThrownBy(() -> elements.storeJsonSchema(URN,
            schema("""
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":42}"""),
            VersionBump.PATCH, null))
            .isInstanceOf(ValidationFailedException.class)
            .satisfies(e -> assertThat(((ValidationFailedException) e).diagnostics())
                .extracting(d -> d.path()).contains("/type"));

        verify(registry, never()).storeElement(anyString(), any(), anySet(), anySet(),
            nullable(String.class), any(VersionBump.class), nullable(String.class));
    }

    @Test
    void storeJsonSchemaAcceptsAConformingSchema() {
        when(registry.storeElement(anyString(), any(), anySet(), anySet(), nullable(String.class),
            any(VersionBump.class), nullable(String.class))).thenReturn(URN + ":1.0.0");

        String pin = elements.storeJsonSchema(URN,
            schema("""
                {"$schema":"https://json-schema.org/draft/2020-12/schema",
                 "type":"object","properties":{"a":{"type":"string"}}}"""),
            VersionBump.PATCH, null);

        assertThat(pin).isEqualTo(URN + ":1.0.0");
    }

    @Test
    void storeXsdAcceptsAWellFormedUrn() {
        when(registry.storeXsdElement(anyString(), anyString(), anySet(), any(VersionBump.class)))
            .thenReturn(URN + ":1.0.0");

        assertThat(elements.storeXsd(URN, XSD, VersionBump.PATCH)).isEqualTo(URN + ":1.0.0");
    }
}

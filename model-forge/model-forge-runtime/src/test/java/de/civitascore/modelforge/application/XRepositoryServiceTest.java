package de.civitascore.modelforge.application;

import de.civitascore.modelforge.core.port.XRepositoryCatalog;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.urn.UrnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XRepositoryService#importArtifact}: the opt-in
 * {@code preserveUpstreamVersion} flag (off by default, threaded through to whichever storage
 * path the request selects), and — for the JSON Schema path — that every type the converter
 * extracts is bundled into one {@code $defs} document and imported with a single
 * {@code importSchema} call, so {@code importArtifact} returns one {@link SchemaImportResult}
 * whose {@code importedResourceIds} lists every created Element (matching {@code importSchema}'s
 * own single-document, multi-Element convention).
 */
class XRepositoryServiceTest {

    private static final String XSD = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private XRepositoryCatalog client;
    private XsdSchemaConverter converter;
    private SchemaImportService importService;
    private XRepositoryService svc;

    @BeforeEach
    void setUp() {
        client = mock(XRepositoryCatalog.class);
        converter = mock(XsdSchemaConverter.class);
        importService = mock(SchemaImportService.class);
        UrnService urns = new UrnService("platform", "civitas", "common", "1.0.0");
        svc = new XRepositoryService(client, converter, importService, urns, MAPPER);
        when(client.downloadXsd(anyString())).thenReturn(XSD);
        when(importService.importXsd(org.mockito.ArgumentMatchers.any(XsdImportRequest.class)))
            .thenReturn(new SchemaImportResult("urn:core:test:owner:element:domain:name:disc:1.0.0", java.util.List.of()));
        when(importService.importSchema(org.mockito.ArgumentMatchers.any(SchemaImportRequest.class)))
            .thenReturn(new SchemaImportResult("urn:core:test:owner:element:domain:name:disc:1.0.0", java.util.List.of()));
    }

    private static XRepositoryImportCommand req(boolean importAsXsd, boolean preserveUpstreamVersion) {
        return new XRepositoryImportCommand("urn:xoev-de:xmeld:standard:xmeld_5.5", "5.5",
            "standard", "xoev", importAsXsd, preserveUpstreamVersion);
    }

    @Test
    void rawXsdImport_defaultDoesNotPreserveUpstreamVersion() {
        svc.importArtifact(req(true, false));

        verify(importService).importXsd(argThatXsdVersionAndPreserve("5.5", false));
    }

    @Test
    void rawXsdImport_preserveUpstreamVersionOptIn_passedThrough() {
        svc.importArtifact(req(true, true));

        verify(importService).importXsd(argThatXsdVersionAndPreserve("5.5", true));
    }

    @Test
    void rawXsdImport_returnsTheServiceResultDirectly() {
        var result = svc.importArtifact(req(true, false));

        assertThat(result.resourceId()).isEqualTo("urn:core:test:owner:element:domain:name:disc:1.0.0");
    }

    /** Matches an {@link XsdImportRequest} carrying the given version, xsdContent and preserveVersion flag. */
    private static XsdImportRequest argThatXsdVersionAndPreserve(String version, boolean preserveVersion) {
        return org.mockito.ArgumentMatchers.argThat(cmd ->
            cmd != null && version.equals(cmd.version()) && XSD.equals(cmd.xsdContent())
                && cmd.preserveVersion() == preserveVersion);
    }

    @Test
    void jsonSchemaImport_defaultDoesNotPreserveUpstreamVersion() {
        ObjectNode schema = MAPPER.createObjectNode().put("title", "XMeld");
        when(converter.convert(anyString(), anyString())).thenReturn(Map.of("XMeld", schema));

        svc.importArtifact(req(false, false));

        verify(importService).importSchema(argThatVersionAndPreserve("5.5", false));
    }

    @Test
    void jsonSchemaImport_bundlesEveryExtractedTypeIntoOneDefsDocument_singleImportSchemaCall() {
        ObjectNode a = MAPPER.createObjectNode().put("title", "A");
        ObjectNode b = MAPPER.createObjectNode().put("title", "B");
        when(converter.convert(anyString(), anyString())).thenReturn(Map.of("A", a, "B", b));

        svc.importArtifact(req(false, true));

        // One call, not one per extracted type — the bundle carries both.
        verify(importService, times(1)).importSchema(org.mockito.ArgumentMatchers.argThat(cmd -> {
            if (cmd == null || !"5.5".equals(cmd.version()) || !cmd.preserveVersion()) return false;
            var schema = cmd.schema();
            return !schema.has("type") && !schema.has("properties") // pure $defs container, no shape of its own
                && schema.path("$defs").has("A") && schema.path("$defs").has("B");
        }));
    }

    @Test
    void jsonSchemaImport_bundleTitleIsTheXRepositoryIdentifier() {
        ObjectNode a = MAPPER.createObjectNode().put("title", "A");
        when(converter.convert(anyString(), anyString())).thenReturn(Map.of("A", a));

        svc.importArtifact(req(false, false));

        verify(importService).importSchema(org.mockito.ArgumentMatchers.argThat(cmd ->
            "urn:xoev-de:xmeld:standard:xmeld_5.5".equals(cmd.schema().path("title").asText(null))));
    }

    /** Matches a {@link SchemaImportRequest} carrying the given version and preserveVersion flag. */
    private static SchemaImportRequest argThatVersionAndPreserve(String version, boolean preserveVersion) {
        return org.mockito.ArgumentMatchers.argThat(cmd ->
            cmd != null && version.equals(cmd.version()) && cmd.preserveVersion() == preserveVersion);
    }
}

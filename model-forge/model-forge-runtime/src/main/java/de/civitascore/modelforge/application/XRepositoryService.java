package de.civitascore.modelforge.application;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.core.port.XRepositoryCatalog;
import de.civitascore.modelforge.core.port.XRepositorySearchResult;
import de.civitascore.modelforge.core.port.XsdSchemaConversionException;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.util.JsonSchema;

import de.civitascore.modelforge.util.SecureXsdParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates XRepository search, XSD download, conversion, and import into ModelForge.
 */
public class XRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(XRepositoryService.class);

    private final XRepositoryCatalog       client;
    private final XsdSchemaConverter       converter;
    private final SchemaImportService      importService;
    private final UrnService               urns;
    private final ObjectMapper             mapper;

    public XRepositoryService(
            XRepositoryCatalog client,
            XsdSchemaConverter converter,
            SchemaImportService importService,
            UrnService urns,
            ObjectMapper mapper) {
        this.client        = client;
        this.converter     = converter;
        this.importService = importService;
        this.urns          = urns;
        this.mapper        = mapper;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public XRepositorySearchPage search(String query, int page, int size) {
        try {
            XRepositorySearchResult result = client.search(query, page, size);
            List<XRepositorySearchPage.XRepositoryArtifactSummary> dtos = result.items().stream()
                .map(a -> new XRepositorySearchPage.XRepositoryArtifactSummary(
                    a.identifier(), a.parentIdentifier(), a.name(), a.version(),
                    a.description(), a.namespace(), a.kind(), a.status()))
                .toList();
            return new XRepositorySearchPage(dtos, result.total(), page, size);
        } catch (Exception e) {
            throw new UpstreamException("XRepository not reachable: " + e.getMessage(), e);
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Downloads the XSD from XRepository and imports it into ModelForge, as a single atomic
     * import producing one {@link SchemaImportResult} — {@code importedResourceIds} lists every
     * Element it created, matching {@code importSchema}'s convention.
     *
     * <p>Two modes controlled by {@code req.importAsXsd()}:
     * <ul>
     *   <li>{@code false} (default) — convert XSD to JSON Schema and store as Elements
     *   <li>{@code true} — store raw XSD as an XSD-Structure artifact (no conversion)
     * </ul>
     */
    public SchemaImportResult importArtifact(XRepositoryImportCommand req) {
        XRepositoryImportCommand.requireValid(req);
        log.info("Importing XRepository artifact: {} v{} (importAsXsd={}, preserveUpstreamVersion={})",
            req.identifier(), req.version(), req.effectiveImportAsXsd(), req.effectivePreserveUpstreamVersion());

        String xsd = downloadOrThrowUpstream(req.identifier());

        // ── XSD path: store raw XSD ──────────────────────────
        if (req.effectiveImportAsXsd()) {
            String domain = domainFromIdentifier(req.identifier());
            // The name segment is derived from the per-artifact identifier (not the domain), so two
            // distinct XSDs in the same domain map to distinct URNs instead of silently overwriting
            // each other's version history.
            String name = nameFromIdentifier(req.identifier(), domain);
            // Format-agnostic identity: the URN uses ':element:'; the XSD-ness is recorded as
            // the version's stored representation format, not the URN type segment. scope/owner are
            // sanitized like domain/name (sanitize() maps ':' to '-') so a ':' in urnScope/urnOwner
            // cannot inject extra URN segments (identity forgery); the version is guarded by importXsd.
            // The disambiguator is DERIVED (not random) from the stable external identity so
            // re-importing the same standard resolves to the same URN instead of a duplicate.
            String disc = urns.disambiguatorFor(name);
            String xsdUrn = String.format("urn:core:%s:%s:element:%s:%s:%s:%s",
                sanitize(req.effectiveScope()), sanitize(req.effectiveOwner()), domain, name, disc, req.version());
            // Delegate to the import service so the DataSet link (elementRefs) and the
            // dependency-graph registration happen exactly like for direct XSD imports.
            // Use the per-artifact name as the import label so the XSD import title reflects the
            // individual artifact rather than the (potentially shared) domain.
            return importService.importXsd(
                new XsdImportRequest(name, req.version(), xsd, xsdUrn, req.effectivePreserveUpstreamVersion()));
        }

        // ── JSON Schema path: convert ────────────────────────
        String urnPrefix = buildUrnPrefix(req.effectiveScope(), req.effectiveOwner(),
                                          domainFromIdentifier(req.identifier()));

        Map<String, ObjectNode> schemas;
        try {
            schemas = converter.convert(xsd, urnPrefix);
        } catch (XsdSchemaConversionException e) {
            // Log the raw parser/library detail server-side only; return a stable, sanitized
            // message to the client (mirrors the generic 5xx handlers — no internal detail leaks).
            log.warn("XSD-to-JSON-Schema conversion failed for {}: {}", req.identifier(), e.getMessage());
            return new SchemaImportResult(null,
                List.of(new Diagnostic(DiagnosticSeverity.ERROR,
                    "XSD could not be converted to JSON Schema", "xsd-conversion", null)));
        }
        if (schemas.isEmpty()) {
            return new SchemaImportResult(null,
                List.of(new Diagnostic(DiagnosticSeverity.ERROR, "XSD contains no named types", "xsd-empty", null)));
        }

        // Bundle every extracted type into one pure-$defs container (no shape of its own — see
        // SchemaImportService#hasOwnShape) so ONE importSchema call creates them all atomically:
        // cross-type $ref/x-core-ref targets resolve from the co-import corpus regardless of
        // extraction order, and the single result reports every created Element via
        // importedResourceIds instead of the caller flattening a list of independent results.
        ObjectNode bundle = mapper.createObjectNode();
        bundle.put("$schema", JsonSchema.DRAFT_2020_12);
        bundle.put("title", req.identifier());
        ObjectNode defs = bundle.putObject("$defs");
        schemas.forEach(defs::set);

        SchemaImportResult result = importService.importSchema(
            new SchemaImportRequest(bundle, req.version(), req.effectivePreserveUpstreamVersion()));

        boolean ok = result.diagnostics().stream().noneMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
        log.info("XRepository import complete: {} ({} artifact(s) imported, artifact: {})",
            ok ? "OK" : "errors", result.importedResourceIds().size(), req.identifier());
        return result;
    }

    /** Download via XRepository client, translating connectivity failures to 502. */
    private String downloadOrThrowUpstream(String identifier) {
        try {
            return client.downloadXsd(identifier);
        } catch (IllegalStateException e) {
            throw new UpstreamException("XRepository not reachable: " + e.getMessage(), e);
        }
    }

    private static String sanitize(String s) {
        return UrnParser.sanitize(s).toLowerCase(Locale.ROOT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildUrnPrefix(String scope, String owner, String domain) {
        // Sanitize scope/owner exactly like domain/name: client-supplied values must not be able to
        // inject extra colon-delimited URN segments (identity forgery). sanitize() maps ':' (and any
        // other non-slug char) to '-' and lowercases. domain is already sanitized by domainFromIdentifier.
        return String.format("urn:core:%s:%s:element:%s", sanitize(scope), sanitize(owner), domain);
    }

    /** Derives a CORE domain slug from an XRepository identifier.
     *  "urn:xoev-de:xmeld:standard:xmeld_5.5" → "xmeld"  */
    private String domainFromIdentifier(String identifier) {
        if (identifier == null) return "xoev";
        String[] parts = identifier.split(":");
        // Try segment 2 (e.g. "xmeld" in "urn:xoev-de:xmeld:...")
        for (int i = 2; i < parts.length; i++) {
            String seg = parts[i].replaceAll("[^a-zA-Z0-9-]", "").toLowerCase(Locale.ROOT);
            if (!seg.isBlank() && !seg.equals("standard") && !seg.equals("xoev")
                    && !seg.equals("de") && !seg.equals("kosit")) {
                return seg;
            }
        }
        return "xoev";
    }

    /**
     * A per-artifact name slug from the XRepository identifier (its last meaningful segment with any
     * trailing version suffix stripped), so two distinct XSDs in the same domain do not collide on
     * one URN, while the <em>same</em> schema keeps a stable name across versions. Falls back to the
     * domain. Example: {@code urn:xoev-de:xmeld:profil:nachricht_5.5} → {@code nachricht}.
     */
    private String nameFromIdentifier(String identifier, String domain) {
        if (identifier != null && !identifier.isBlank()) {
            String[] parts = identifier.split(":");
            for (int i = parts.length - 1; i >= 0; i--) {
                // Strip a trailing version suffix (e.g. "_5.5", "-6.0") so it does not leak into the
                // stable name segment — the concrete version lives in the URN's version segment.
                String seg = sanitize(parts[i].replaceAll("[_-]?\\d+(\\.\\d+)*$", ""));
                if (!seg.isBlank()) return seg;
            }
        }
        return sanitize(domain);
    }

    private String extractNamespaceFromXsd(String xsd) {
        try {
            return SecureXsdParser.parse(xsd).getTargetNamespace();
        } catch (Exception e) {
            log.debug("Could not extract targetNamespace from XRepository XSD: {}", e.getMessage());
            return null;
        }
    }
}

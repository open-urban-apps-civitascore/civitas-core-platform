package de.civitascore.modelforge.application;


import java.util.regex.Pattern;

/**
 * Imports JSON Schemas from the public <a href="https://smartdatamodels.org">Smart Data Models</a>
 * initiative, the FIWARE/TM-Forum-curated catalogue of open data models.
 *
 * <p>A model is addressed by its <em>subject</em> (the {@code dataModel.<Subject>} GitHub
 * repository, e.g. {@code Weather}) and its <em>data model</em> name (the folder, e.g.
 * {@code WeatherObserved}). The canonical raw schema URL follows the project's fixed convention:
 *
 * <pre>{@code
 *   https://raw.githubusercontent.com/smart-data-models/dataModel.<Subject>/master/<DataModel>/schema.json
 * }</pre>
 *
 * <p>The resolved URL is handed to {@link SchemaImportService#importFromUrl}, an SSRF-checked,
 * no-redirect server-side fetch (so the browser avoids CORS). This service only constructs and
 * validates the reference; the host is fixed and the path segments are restricted to a safe
 * character set, so a caller cannot redirect the fetch elsewhere. It is currently the only caller
 * of that fetch — there is no generic import-by-URL surface on the {@code ModelForge} facade.
 */
public class SmartDataModelsService {

    static final String RAW_BASE = "https://raw.githubusercontent.com/smart-data-models/";

    /** Safe path-segment characters — no slashes, so a segment can never traverse or change host. */
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final SchemaImportService importService;

    public SmartDataModelsService(SchemaImportService importService) {
        this.importService = importService;
    }

    /**
     * The canonical raw {@code schema.json} URL for a Smart Data Models {@code (subject, dataModel)}
     * reference. Both segments are validated against {@link #SAFE_SEGMENT}.
     *
     * @throws IllegalArgumentException when a segment is blank or contains unsafe characters (→ 400)
     */
    public String schemaUrl(String subject, String dataModel) {
        return RAW_BASE + "dataModel." + safe(subject, "subject")
            + "/master/" + safe(dataModel, "dataModel") + "/schema.json";
    }

    /**
     * Resolves a Smart Data Models reference to its raw schema URL and imports it via
     * {@link SchemaImportService#importFromUrl}. A malformed reference or unsafe URL surfaces as
     * HTTP 400, an unreachable host as HTTP 502.
     */
    public SchemaImportResult importModel(String subject, String dataModel) {
        // (subject, dataModel) is the catalogue's own identifier for the model, so it is a stable
        // key: re-importing the same model versions the existing artifact instead of duplicating it,
        // while two subjects sharing an entity name stay distinct.
        return importService.importFromUrl(
            schemaUrl(subject, dataModel),
            "smart-data-models:" + safe(subject, "subject") + "/" + safe(dataModel, "dataModel"));
    }

    private static String safe(String segment, String field) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("Smart Data Models '" + field + "' is required");
        }
        String trimmed = segment.trim();
        if (!SAFE_SEGMENT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                "Smart Data Models '" + field + "' may only contain letters, digits, '.', '-' and '_': " + segment);
        }
        // SAFE_SEGMENT admits "." and ".." on its own; URI.create does not normalise, so the origin
        // would resolve the traversal and serve a different path than the one requested.
        if (trimmed.equals(".") || trimmed.equals("..")) {
            throw new IllegalArgumentException(
                "Smart Data Models '" + field + "' must name a resource, not a relative path: " + segment);
        }
        return trimmed;
    }
}

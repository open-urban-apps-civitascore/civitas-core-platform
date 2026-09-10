package de.civitascore.modelforge.integrations.xrepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.core.port.XRepositoryArtifact;
import de.civitascore.modelforge.core.port.XRepositoryCatalog;
import de.civitascore.modelforge.core.port.XRepositorySearchResult;
import de.civitascore.modelforge.integrations.util.UrlGuard;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for the XRepository REST API (https://www.xrepository.de/api).
 *
 * <h3>Endpoints used</h3>
 * <ul>
 *   <li>{@code POST /xrepository/suche}
 *       — Paged search. Body: {@code {"match":"…","limit":N,"offset":N}}.
 *       Response: {@code {"hits":[…], "total":N}}.
 *   <li>{@code GET /version_standard/{versionKennung}/xmlschema}
 *       — Downloads XSD as a ZIP archive. Use the version kennung from
 *       {@code referenzen[statusVerwendung=AKTUELL].kennung}.
 * </ul>
 *
 * <h3>Key response field names</h3>
 * <ul>
 *   <li>{@code kennung}                       — parent standard URN
 *   <li>{@code i18n.nameKurz}                 — short display name
 *   <li>{@code i18n["no-lang"].beschreibung}  — description
 *   <li>{@code typ}                           — "STANDARD", "CODELISTE", …
 *   <li>{@code referenzen[].kennung}          — version URN (download identifier)
 *   <li>{@code referenzen[].version}          — version string
 *   <li>{@code referenzen[].statusVerwendung} — "AKTUELL" | "VERALTET"
 * </ul>
 *
 * <p>Uses {@link HttpClient} (JDK, no external dependency) — this module implements core ports and
 * must stay free of {@code org.springframework.web}/{@code org.springframework.http}.
 */
public class XRepositoryClient implements XRepositoryCatalog {

    private static final Logger log = LoggerFactory.getLogger(XRepositoryClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_XSD_ENTRY_BYTES = 2 * 1024 * 1024;   // 2 MB per entry
    private static final int MAX_XSD_TOTAL_BYTES = 20 * 1024 * 1024;  // 20 MB across the whole archive
    private static final int MAX_XSD_FILES = 50;

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public XRepositoryClient(String baseUrl, ObjectMapper mapper) {
        this.baseUrl = UrlGuard.requireHttpUrl(baseUrl, "xrepository.base-url");
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    /**
     * Searches XRepository via {@code POST /xrepository/suche}.
     *
     * @param query search term (empty → returns recent items)
     * @param page  0-based page index
     * @param size  page size
     * @throws IllegalStateException when XRepository is unreachable or returns an error
     */
    @Override
    public XRepositorySearchResult search(String query, int page, int size) {
        try {
            return callSearch(query, page, size);
        } catch (IllegalArgumentException e) {
            throw e; // bad paging input — a caller error, not an upstream outage
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("XRepository search interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("XRepository not reachable: " + e.getMessage(), e);
        }
    }

    private XRepositorySearchResult callSearch(String query, int page, int size) throws IOException, InterruptedException {
        if (page < 0) {
            throw new IllegalArgumentException("XRepository page must not be negative: " + page);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("XRepository page size must be positive: " + size);
        }
        ObjectNode body = mapper.createObjectNode();
        if (query != null && !query.isBlank()) body.put("match", query);
        body.put("limit", size);
        body.put("offset", (long) page * size);

        String url = baseUrl + "/xrepository/suche";
        log.debug("XRepository POST suche: {}", url);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(READ_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() / 100 != 2 || resp.body() == null) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        return parseSearchResponse(resp.body());
    }

    private XRepositorySearchResult parseSearchResponse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode hitsNode = root.has("hits") ? root.get("hits") : root;
        long total = root.path("total").asLong(-1);

        List<XRepositoryArtifact> items = new ArrayList<>();
        if (hitsNode.isArray()) {
            for (JsonNode hit : hitsNode) {
                XRepositoryArtifact a = parseHit(hit);
                if (a != null) items.add(a);
            }
        }
        if (total < 0) total = items.size();
        return new XRepositorySearchResult(items, total);
    }

    /**
     * Parses one hit from the {@code hits} array. The current downloadable version is
     * resolved from {@code referenzen[statusVerwendung=AKTUELL, typ=VERSION_STANDARD]}.
     */
    private XRepositoryArtifact parseHit(JsonNode hit) {
        String parentKennung = text(hit, "kennung");
        if (parentKennung == null) return null;

        JsonNode i18n = hit.path("i18n");
        String nameKurz = text(i18n, "nameKurz");
        String desc = descriptionFromI18n(i18n);
        String typ = text(hit, "typ");

        JsonNode refs = hit.path("referenzen");
        JsonNode currentRef = findCurrentRef(refs);

        String versionKennung = currentRef != null ? text(currentRef, "kennung") : null;
        String version = currentRef != null ? text(currentRef, "version") : null;
        String statusVerw = currentRef != null ? text(currentRef, "statusVerwendung") : null;

        // Fallback: first VERSION_STANDARD reference
        if (versionKennung == null && refs.isArray()) {
            for (JsonNode ref : refs) {
                if ("VERSION_STANDARD".equals(text(ref, "typ"))) {
                    versionKennung = text(ref, "kennung");
                    version = text(ref, "version");
                    statusVerw = text(ref, "statusVerwendung");
                    break;
                }
            }
        }

        String identifier = versionKennung != null ? versionKennung : parentKennung;
        return new XRepositoryArtifact(identifier, parentKennung, nameKurz, version,
            desc, null, typ, statusVerw);
    }

    private JsonNode findCurrentRef(JsonNode refs) {
        if (!refs.isArray()) return null;
        for (JsonNode ref : refs) {
            if ("VERSION_STANDARD".equals(text(ref, "typ"))
                    && "AKTUELL".equals(text(ref, "statusVerwendung"))
                    && "ENDFASSUNG".equals(text(ref, "statusFassung"))) {
                return ref;
            }
        }
        for (JsonNode ref : refs) {
            if ("VERSION_STANDARD".equals(text(ref, "typ"))
                    && "AKTUELL".equals(text(ref, "statusVerwendung"))) {
                return ref;
            }
        }
        return null;
    }

    private String descriptionFromI18n(JsonNode i18n) {
        if (i18n.isMissingNode()) return null;
        for (String lang : new String[]{"no-lang", "de", "en"}) {
            JsonNode langNode = i18n.path(lang);
            if (!langNode.isMissingNode()) {
                String d = text(langNode, "beschreibung");
                if (d != null) return d;
            }
        }
        return text(i18n, "beschreibung", "nameLang");
    }

    // ── XSD Download ───────────────────────────────────────────────────────────

    /**
     * Downloads the XSD for the given version kennung.
     * Calls {@code GET /version_standard/{versionKennung}/xmlschema}.
     * The response is typically a ZIP archive; this method extracts the root XSD.
     *
     * @throws IllegalStateException when XRepository is unreachable or returns an error
     */
    @Override
    public String downloadXsd(String identifier) {
        try {
            return callDownload(identifier);
        } catch (IllegalStateException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            // A local size/count cap or a malformed archive — retrying cannot help, so this must
            // not be reported to the caller as an upstream outage.
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("XSD download interrupted for '" + identifier + "'", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                "XSD download failed for '" + identifier + "': " + e.getMessage(), e);
        }
    }

    /**
     * Percent-encodes one URL <em>path</em> segment. {@link URLEncoder} does form encoding, which
     * maps a space to {@code +} — read as a literal plus sign inside a path — so the space is
     * re-encoded. A literal plus in the input is already {@code %2B} and stays intact.
     */
    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String callDownload(String versionKennung) throws IOException, InterruptedException {
        String url = baseUrl + "/version_standard/"
            + encodePathSegment(versionKennung)
            + "/xmlschema";
        log.debug("XRepository xmlschema download: {}", url);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(READ_TIMEOUT)
            .header("Accept", "application/octet-stream, application/zip, */*")
            .GET()
            .build();
        HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        byte[] body = resp.body();
        if (resp.statusCode() / 100 != 2 || body == null) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }

        if (body.length > 4 && body[0] == 0x50 && body[1] == 0x4B) {
            try {
                return extractXsdFromZip(body);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read XSD ZIP archive: " + e.getMessage(), e);
            }
        }
        return decodeXml(body);
    }

    /**
     * Decodes XML bytes honouring the prolog's {@code encoding} declaration.
     * German XOEV standards frequently declare ISO-8859-1 — decoding them as UTF-8
     * produces mojibake umlauts in documentation and enum values.
     */
    static String decodeXml(byte[] bytes) {
        String prolog = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.ISO_8859_1);
        Matcher m = XML_ENCODING.matcher(prolog);
        Charset charset = StandardCharsets.UTF_8;
        if (m.find()) {
            try {
                charset = Charset.forName(m.group(1));
            } catch (Exception e) {
                log.warn("Unknown XML encoding declaration '{}' — falling back to UTF-8", m.group(1));
            }
        }
        return new String(bytes, charset);
    }

    private static final Pattern XML_ENCODING =
        Pattern.compile("encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    /**
     * Extracts XSD files from a ZIP archive. Returns the root schema:
     * if only one .xsd file is present, returns it directly; otherwise returns
     * the longest file (heuristic for the root schema that includes others).
     */
    String extractXsdFromZip(byte[] zipBytes) throws IOException {
        Map<String, String> xsdFiles = new LinkedHashMap<>();
        long totalBytes = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.length() >= 4 && name.regionMatches(true, name.length() - 4, ".xsd", 0, 4)) {
                    if (xsdFiles.size() >= MAX_XSD_FILES) {
                        throw new IllegalArgumentException("ZIP contains too many XSD files (max " + MAX_XSD_FILES + ")");
                    }
                    // Cap each entry (2 MB) AND the whole archive (20 MB), checked
                    // mid-stream, so a high-ratio "zip bomb" cannot amplify a small
                    // download into a large heap allocation.
                    byte[] raw = readLimitedEntry(zis, name, MAX_XSD_TOTAL_BYTES - totalBytes);
                    totalBytes += raw.length;
                    xsdFiles.put(name, decodeXml(raw));
                }
                zis.closeEntry();
            }
        }
        if (xsdFiles.isEmpty()) throw new IllegalStateException("ZIP contains no XSD files");
        log.debug("XSD ZIP: {} file(s): {}", xsdFiles.size(), sanitizeForLog(xsdFiles.keySet()));
        if (xsdFiles.size() == 1) return xsdFiles.values().iterator().next();
        String rootName = xsdFiles.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().length()))
            .orElseThrow().getKey();
        log.info("XSD ZIP contains {} schemas — picked '{}' as root (longest-file heuristic)",
            xsdFiles.size(), sanitizeForLog(rootName));
        return xsdFiles.get(rootName);
    }

    private static byte[] readLimitedEntry(ZipInputStream zis, String name, long remainingTotalBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int read;
        while ((read = zis.read(buf)) != -1) {
            total += read;
            if (total > MAX_XSD_ENTRY_BYTES) throw new IllegalArgumentException("XSD entry too large: " + name);
            if (total > remainingTotalBytes) {
                throw new IllegalArgumentException(
                    "ZIP total decompressed size exceeds limit (max " + MAX_XSD_TOTAL_BYTES + " bytes)");
            }
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String text(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.path(f);
            if (!v.isMissingNode() && !v.isNull() && !v.asText("").isBlank()) return v.asText();
        }
        return null;
    }

    /**
     * Strips CR/LF/tab and other control characters from an upstream-controlled value before it is
     * logged, so a crafted ZIP entry name cannot inject forged log lines (CWE-117).
     */
    private static String sanitizeForLog(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", "_");
    }

    /** Sanitizes each entry-name collection element for safe logging (see {@link #sanitizeForLog(String)}). */
    private static List<String> sanitizeForLog(Collection<String> values) {
        List<String> safe = new ArrayList<>(values.size());
        for (String v : values) safe.add(sanitizeForLog(v));
        return safe;
    }
}

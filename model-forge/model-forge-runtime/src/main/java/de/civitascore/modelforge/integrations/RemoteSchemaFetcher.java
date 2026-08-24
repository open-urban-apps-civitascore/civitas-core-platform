package de.civitascore.modelforge.integrations;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import de.civitascore.modelforge.integrations.util.UrlGuard;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches a JSON document over HTTP for the schema-import paths (today only Smart Data Models,
 * whose host is a compile-time constant), so a host application's UI can import a schema without
 * the browser hitting CORS.
 *
 * <p>The URL is SSRF-checked against {@link UrlGuard#resolveSafeRemoteUrl} and fetched with
 * timeouts and without following redirects. The body is size-bounded (streamed with a hard byte
 * cap) and parsed as JSON.
 *
 * <p>The check is defence in depth, not a boundary against an untrusted host: every caller builds
 * its URL from a fixed or operator-configured host, so nothing here narrows an attacker-chosen
 * target. Note in particular that the host is resolved once for the check and again by
 * {@link HttpClient} on connect, so a caller that <em>did</em> accept an untrusted hostname would
 * still be open to DNS rebinding between the two. Closing that window needs connection-level
 * control of the target address, which {@link HttpClient} does not expose — it would mean driving
 * the fetch over a socket and taking over SNI and hostname verification. Do that deliberately, as
 * part of adding such a caller; do not assume this class already protects one.
 *
 * <p>Uses {@link HttpClient} (JDK, no external dependency) rather than Spring's
 * {@code RestTemplate}/{@code RestClient} — this module implements core ports and must stay free of
 * {@code org.springframework.web}/{@code org.springframework.http}, same as {@code contract} and
 * {@code core}.
 */
public class RemoteSchemaFetcher implements RemoteSchemaRepository {

    /** Cap on the fetched body size — a remote import must not stream an unbounded payload. */
    static final int MAX_BYTES = 1_048_576; // 1 MB

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper mapper;
    private final HttpClient http;

    public RemoteSchemaFetcher(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    /**
     * Fetches {@code url} and parses the body as JSON.
     *
     * @throws IllegalArgumentException when the URL is not a safe public http(s) URL, or the body
     *         is empty, not JSON, or exceeds {@link #MAX_BYTES}
     * @throws IllegalStateException when the remote host is unreachable or returns an error
     *         (callers, e.g. {@code SchemaImportService}, translate this to a domain-level
     *         upstream-failure exception — the same pattern {@link
     *         de.civitascore.modelforge.core.port.XRepositoryCatalog} adapters use)
     */
    @Override
    public JsonNode fetchJson(String url) {
        UrlGuard.SafeRemoteUrl safe = UrlGuard.resolveSafeRemoteUrl(url);
        byte[] body;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(safe.url()))
                .timeout(READ_TIMEOUT)
                .GET()
                .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            // The body stream must be closed on every path — including the non-2xx throw —
            // or the underlying connection is never released.
            try (InputStream in = response.body()) {
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " fetching " + safe.url());
                }
                body = readLimited(in);
            }
        } catch (IllegalArgumentException e) {
            throw e; // over-limit / bad input
        } catch (IOException | InterruptedException | UncheckedIOException e) {
            // HttpClient clears the interrupt flag before it rethrows InterruptedException, so the
            // status has to be restored from the exception type — testing the flag here never fires.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to fetch schema from " + safe.url() + ": " + e.getMessage(), e);
        }
        if (body.length == 0) {
            throw new IllegalArgumentException("Remote URL returned an empty body: " + safe.url());
        }
        try {
            return mapper.readTree(body);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("Remote document is not valid JSON: " + safe.url());
        }
    }

    /**
     * Reads the response body into memory, aborting with an {@link IllegalArgumentException}
     * the moment the running total exceeds {@link #MAX_BYTES}. The caller owns closing the stream.
     */
    private static byte[] readLimited(InputStream in) throws IOException {
        var buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            total += n;
            if (total > MAX_BYTES) {
                throw new IllegalArgumentException(
                    "Remote document exceeds the " + MAX_BYTES + "-byte import limit");
            }
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}

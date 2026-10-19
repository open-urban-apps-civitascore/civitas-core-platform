package de.civitascore.modelforge.validation;

import de.civitascore.modelforge.contract.Diagnostic;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code $ref} in a submitted document must never make the server fetch anything. The validator
 * resolves references eagerly, so an {@code http} or {@code file} reference would otherwise be
 * dereferenced during a plain save — reaching whatever the server can reach, with no SSRF check
 * (UrlGuard guards the import fetcher, not this path), no timeout, and following redirects.
 *
 * <p>A real local HTTP server stands in for the remote host, so the assertion is that no request
 * arrives rather than that some exception was thrown for an unrelated reason.
 */
class RemoteRefResolutionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ModelValidator validator = new ModelValidator();

    private HttpServer server;
    private AtomicInteger hits;

    @BeforeEach
    void startServer() throws IOException {
        hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            byte[] body = "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/remote.schema.json";
    }

    @Test
    void validateSchema_doesNotFetchAnHttpRef() {
        JsonNode schema = mapper.readTree("""
            { "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "leak": { "$ref": "%s" } } }
            """.formatted(url()));

        validator.validateSchema(schema);

        assertThat(hits.get()).as("the server must not dereference a submitted http $ref").isZero();
    }

    @Test
    void validateData_doesNotFetchAnHttpRef() {
        JsonNode schema = mapper.readTree("""
            { "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "leak": { "$ref": "%s" } } }
            """.formatted(url()));

        validator.validateData(schema, mapper.readTree("{\"leak\":\"x\"}"));

        assertThat(hits.get()).as("the server must not dereference a submitted http $ref").isZero();
    }

    @Test
    void validateSchema_doesNotReadAFileRef() throws IOException {
        Path secret = Files.createTempFile("mf-ref-target", ".json");
        Files.writeString(secret, "{\"type\":\"string\"}");
        JsonNode schema = mapper.readTree("""
            { "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "leak": { "$ref": "%s" } } }
            """.formatted(secret.toUri()));

        List<Diagnostic> diagnostics = validator.validateSchema(schema);

        // Rejected rather than silently resolved — a local file is never a schema source.
        assertThat(diagnostics).isNotEmpty();
        Files.deleteIfExists(secret);
    }

    /**
     * The restriction must not touch what legitimately reaches this validator: local pointers, and
     * the standard 2020-12 {@code $schema} which resolves from the classpath.
     */
    @Test
    void localRefsAndTheStandardMetaSchemaStillWork() {
        JsonNode schema = mapper.readTree("""
            { "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "child": { "$ref": "#/$defs/Child" } },
              "$defs": { "Child": { "type": "string" } } }
            """);

        assertThat(validator.validateSchema(schema)).isEmpty();
        assertThat(validator.validateData(schema, mapper.readTree("{\"child\":\"ok\"}"))).isEmpty();
        assertThat(validator.validateData(schema, mapper.readTree("{\"child\":42}"))).isNotEmpty();
    }
}

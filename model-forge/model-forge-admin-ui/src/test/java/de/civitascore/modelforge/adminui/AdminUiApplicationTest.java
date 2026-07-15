package de.civitascore.modelforge.adminui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full UI against a throwaway Testcontainers Postgres — the admin-ui always runs
 * against a real registry. Skipped when no Docker daemon is available (e.g. plain CI runners).
 *
 * <p>The STA seed runs at startup (default {@code seed.enabled=true}), so the registry is populated
 * and the sidebar renders real groups. These are server-side render smoke tests: they catch Wicket
 * markup/component mismatches across every page type. The CodeMirror editor itself is client-side
 * (CDN ESM) and needs a browser to exercise; the tests only assert its bootstrap is emitted.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUiApplicationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15");

    @DynamicPropertySource
    static void registryDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    /*
     * Plain JDK HttpClient instead of TestRestTemplate: Spring Boot 4.0 dropped
     * TestRestTemplate (its RestTestClient successor lives in spring-boot-resttestclient),
     * and the Wicket UI needs a real HTTP round-trip anyway — MockMvc would bypass the
     * Wicket servlet filter entirely.
     */
    @Test
    void homePageRendersSidebarWithSeededArtifacts() throws Exception {
        String body = get("/artifacts");
        assertThat(body).contains("Model Forge");
        // Sidebar group header — present because the STA seed imported Element artifacts.
        assertThat(body).contains("Elements");
    }

    @Test
    void seedPageListsBundledSet() throws Exception {
        String body = get("/seed");
        assertThat(body).contains("Seed models");
        assertThat(body).contains("SensorThings");
    }

    @Test
    void editorPagesEmitCodeMirrorBootstrapAndSchema() throws Exception {
        // Import page: the CodeMirror 6 loader and the JSON-Schema data island must both be emitted.
        String importBody = get("/import");
        assertThat(importBody).contains("esm.sh/codemirror");
        assertThat(importBody).contains("application/json");

        // New-artifact and validate pages render their forms without component errors.
        assertThat(get("/artifacts/edit")).contains("esm.sh/codemirror");
        assertThat(get("/validate")).contains("esm.sh/codemirror");
    }

    private String get(String path) throws Exception {
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            var response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
            return response.body();
        }
    }
}

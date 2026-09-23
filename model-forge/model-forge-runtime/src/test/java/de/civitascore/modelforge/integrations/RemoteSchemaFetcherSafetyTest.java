package de.civitascore.modelforge.integrations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The remote fetch refuses a target it must not reach.
 *
 * <p>These go through {@link RemoteSchemaFetcher} rather than through the guard it calls: the guard
 * has its own tests, but those pass whether or not the fetcher invokes it. A refusal observed here
 * is evidence that the check is on the fetch path.
 *
 * <p>Every case is refused before a connection is attempted, so no server is needed and the tests
 * make no outbound request.
 */
class RemoteSchemaFetcherSafetyTest {

    private final RemoteSchemaFetcher fetcher = new RemoteSchemaFetcher(new ObjectMapper());

    @ParameterizedTest(name = "refuses {0}")
    @ValueSource(strings = {
        "http://127.0.0.1/schema.json",          // loopback
        "http://localhost/schema.json",          // loopback by name
        "http://10.0.0.1/schema.json",           // private 10/8
        "http://192.168.1.1/schema.json",        // private 192.168/16
        "http://172.16.0.1/schema.json",         // private 172.16/12
        "http://172.31.255.254/schema.json",     // private 172.16/12, upper end
        "http://169.254.169.254/latest/meta-data/", // link-local: the cloud metadata endpoint
        "http://0.0.0.0/schema.json",            // any-local
        "http://[::1]/schema.json",              // IPv6 loopback
        "http://[::ffff:127.0.0.1]/schema.json", // IPv4-mapped loopback
        "http://[fc00::1]/schema.json",          // IPv6 unique-local
        "http://100.64.0.1/schema.json",         // carrier-grade NAT
    })
    @DisplayName("A non-public target is refused by the fetch itself")
    void refusesNonPublicTargets(String url) {
        assertThatThrownBy(() -> fetcher.fetchJson(url))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-public address");
    }

    @ParameterizedTest(name = "refuses {0}")
    @ValueSource(strings = {
        "file:///etc/passwd",
        "gopher://example.com/schema.json",
        "ftp://example.com/schema.json",
        "jar:file:/tmp/x.jar!/schema.json",
    })
    @DisplayName("A target that is not http(s) is refused")
    void refusesNonHttpSchemes(String url) {
        assertThatThrownBy(() -> fetcher.fetchJson(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A blank target is refused")
    void refusesBlankUrl() {
        assertThatThrownBy(() -> fetcher.fetchJson("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A host that cannot be resolved is refused rather than attempted")
    void refusesUnresolvableHost() {
        assertThatThrownBy(() -> fetcher.fetchJson("http://not-a-real-host.invalid/schema.json"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be resolved");
    }
}

package de.civitascore.modelforge.integrations.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the SSRF blocklist. The interesting cases are the ones the JDK's own {@link InetAddress}
 * predicates do not cover: the RFC 6890 non-globally-reachable IPv4 blocks, IPv6 unique-local
 * addresses, and every IPv6 form that embeds an IPv4 address — where a blocked target can be
 * disguised as a public one.
 *
 * <p>Addresses are built from raw bytes via {@link InetAddress#getByAddress(byte[])} so no test
 * here performs a DNS lookup.
 */
class UrlGuardTest {

    private static boolean blocked(String literal) throws Exception {
        return UrlGuard.isBlockedAddress(InetAddress.getByName(literal));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "127.0.0.1",            // loopback
        "0.0.0.0",              // any-local
        "169.254.169.254",      // link-local: the cloud metadata endpoint
        "10.0.0.1",             // site-local
        "172.16.0.1",           // site-local
        "192.168.1.1",          // site-local
        "224.0.0.1",            // multicast
        "100.64.0.1",           // carrier-grade NAT 100.64/10
        "100.127.255.255",      // carrier-grade NAT, upper bound
        "0.1.2.3",              // 0.0.0.0/8 "this network"
        "192.0.0.170",          // 192.0.0.0/24 protocol assignments
        "198.18.0.1",           // 198.18/15 benchmarking
        "198.19.255.255",       // 198.18/15, upper bound
        "240.0.0.1",            // 240.0.0.0/4 reserved
        "255.255.255.255",      // broadcast
    })
    void ipv4_nonPublicAddressesAreBlocked(String literal) throws Exception {
        assertThat(blocked(literal)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "93.184.216.34",
        "8.8.8.8",
        "100.63.255.255",       // just below carrier-grade NAT
        "100.128.0.0",          // just above carrier-grade NAT
        "198.17.255.255",       // just below benchmarking
        "198.20.0.0",           // just above benchmarking
        "223.255.255.255",      // highest unicast address below the multicast/reserved blocks
    })
    void ipv4_publicAddressesAreAllowed(String literal) throws Exception {
        assertThat(blocked(literal)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "::1",                  // loopback
        "::",                   // any-local
        "fe80::1",              // link-local
        "fc00::1",              // unique-local fc00::/7
        "fd12:3456::1",         // unique-local, second half of the /7
        "ff02::1",              // multicast
    })
    void ipv6_nonPublicAddressesAreBlocked(String literal) throws Exception {
        assertThat(blocked(literal)).isTrue();
    }

    @Test
    void ipv6_publicAddressIsAllowed() throws Exception {
        assertThat(blocked("2606:2800:220:1:248:1893:25c8:1946")).isFalse();
    }

    /**
     * Embedded-IPv4 IPv6 must be unwrapped and re-checked, or a blocked IPv4 target reaches the
     * fetcher wearing an IPv6 disguise. NAT64 matters in an IPv6-only cluster with a DNS64
     * gateway: an attacker-controlled AAAA record in the well-known prefix would otherwise resolve
     * straight to the metadata endpoint.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "::ffff:127.0.0.1",       // IPv4-mapped loopback
        "::ffff:169.254.169.254", // IPv4-mapped metadata endpoint
        "::ffff:10.0.0.1",        // IPv4-mapped site-local
        "::127.0.0.1",            // IPv4-compatible loopback
        "64:ff9b::7f00:1",        // NAT64 well-known prefix, loopback
        "64:ff9b::a9fe:a9fe",     // NAT64 well-known prefix, metadata endpoint
        "64:ff9b::a00:1",         // NAT64 well-known prefix, site-local
    })
    void embeddedIpv4_blockedTargetsCannotBeDisguised(String literal) throws Exception {
        assertThat(blocked(literal)).isTrue();
    }

    @Test
    void embeddedIpv4_publicTargetIsStillAllowed() throws Exception {
        assertThat(blocked("::ffff:93.184.216.34")).isFalse();
        assertThat(blocked("64:ff9b::5db8:d822")).isFalse();   // 93.184.216.34 via NAT64
    }

    /**
     * Outside the well-known /96 the embedding format is set by the operator, so the trailing four
     * bytes are not necessarily the IPv4 address. Nothing in the translation prefix can be
     * validated here, so it must fail closed.
     */
    @Test
    void nat64_nonWellKnownEmbeddingFailsClosed() throws Exception {
        assertThat(blocked("64:ff9b:1::1")).isTrue();          // RFC 8215 local-use NAT64
        assertThat(blocked("64:ff9b:0:1::5db8:d822")).isTrue();
    }

    @Test
    void resolveSafeRemoteUrl_rejectsBlankMalformedAndNonHttpUrls() {
        assertThatThrownBy(() -> UrlGuard.resolveSafeRemoteUrl(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlGuard.resolveSafeRemoteUrl("  "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlGuard.resolveSafeRemoteUrl("file:///etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlGuard.resolveSafeRemoteUrl("/relative/path"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveSafeRemoteUrl_rejectsAHostThatResolvesToALoopbackAddress() {
        assertThatThrownBy(() -> UrlGuard.resolveSafeRemoteUrl("http://127.0.0.1/schema.json"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void safeRemoteUrl_copiesAddressesSoTheValidatedSetCannotBeMutated() throws Exception {
        InetAddress[] addresses = {InetAddress.getByName("93.184.216.34")};
        UrlGuard.SafeRemoteUrl safe =
            new UrlGuard.SafeRemoteUrl("http://example.com", "example.com", addresses);

        addresses[0] = InetAddress.getByName("127.0.0.1");
        assertThat(safe.addresses()[0].getHostAddress()).isEqualTo("93.184.216.34");

        safe.addresses()[0] = InetAddress.getByName("127.0.0.1");
        assertThat(safe.addresses()[0].getHostAddress()).isEqualTo("93.184.216.34");
    }

    @Test
    void requireHttpUrl_treatsBlankAsNotConfiguredAndRejectsNonHttp() {
        assertThat(UrlGuard.requireHttpUrl(null, "model-forge.xrepository.base-url")).isNull();
        assertThat(UrlGuard.requireHttpUrl("", "model-forge.xrepository.base-url")).isEmpty();
        assertThat(UrlGuard.requireHttpUrl("  https://x.test/api  ", "k")).isEqualTo("https://x.test/api");

        assertThatThrownBy(() -> UrlGuard.requireHttpUrl("ftp://x.test", "k"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("k");
    }
}

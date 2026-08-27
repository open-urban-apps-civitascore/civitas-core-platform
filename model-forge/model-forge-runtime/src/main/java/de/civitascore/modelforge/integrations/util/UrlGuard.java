package de.civitascore.modelforge.integrations.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Startup validation for operator-configured external upstream URLs (XRepository, …) and SSRF
 * checks for the URLs the schema-import paths fetch.
 *
 * <p>A malformed or non-HTTP(S) URL is rejected fast with a clear message instead of
 * surfacing later as an opaque connection failure, and combined with the
 * redirect-disabling in the HTTP client this keeps outbound calls pinned to
 * the configured, vetted hosts (SSRF hardening).
 */
public final class UrlGuard {

    private UrlGuard() {}

    /**
     * Validates that {@code url} is a syntactically valid absolute {@code http}/{@code https}
     * URL with a host. A blank/null URL is treated as "not configured" and accepted
     * (the feature it gates is simply disabled).
     *
     * @param url       the configured URL (may be blank)
     * @param configKey the property name, used in the error message
     * @return the trimmed URL (or the original blank/null value)
     * @throws IllegalStateException when a non-blank URL is not a valid http(s) URL
     */
    public static String requireHttpUrl(String url, String configKey) {
        if (url == null || url.isBlank()) return url;
        String trimmed = url.trim();
        try {
            URI u = URI.create(trimmed);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https")) || u.getHost() == null) {
                throw new IllegalArgumentException("not an absolute http(s) URL");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Invalid " + configKey + ": '" + trimmed + "' must be a valid http(s) URL");
        }
        return trimmed;
    }

    /**
     * Validates a remote URL for a server-side fetch and resolves it to the public IP address(es)
     * it points at. Beyond the http(s) check it
     * rejects hosts that resolve to a non-public address — see {@link #isBlockedAddress(InetAddress)}
     * (loopback, any-local, link-local, site-local, multicast, IPv4 CGN, IPv6 ULA, IPv4-mapped IPv6)
     * — so a caller cannot steer the server at internal services or the cloud metadata endpoint
     * (169.254.169.254).
     *
     * <p>The returned {@link SafeRemoteUrl} carries the addresses this check validated, so a caller
     * can report or assert on them.
     *
     * <p><strong>This does not close the DNS-rebinding / TOCTOU window.</strong> The host is
     * resolved here and resolved again by the HTTP client on connect, so a host that answers
     * differently the second time is connected to unvalidated. Every current caller builds its URL
     * from a fixed or operator-configured host, which is what makes that acceptable: an attacker
     * chooses neither the scheme nor the host, so there is nothing to rebind. A caller that accepts
     * an untrusted hostname must not rely on this method alone — see
     * {@code RemoteSchemaFetcher} for what closing the window would take.
     *
     * @throws IllegalArgumentException when the URL is blank, malformed, not http(s), or resolves
     *         to a non-public address
     */
    public static SafeRemoteUrl resolveSafeRemoteUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }
        String trimmed = url.trim();
        URI u;
        try {
            u = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed URL: " + trimmed);
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || u.getHost() == null) {
            throw new IllegalArgumentException("URL must be an absolute http(s) URL: " + trimmed);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(u.getHost());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("URL host cannot be resolved: " + u.getHost());
        }
        for (InetAddress addr : addresses) {
            if (isBlockedAddress(addr)) {
                throw new IllegalArgumentException(
                    "URL resolves to a non-public address (SSRF protection): " + u.getHost());
            }
        }
        return new SafeRemoteUrl(trimmed, u.getHost(), addresses);
    }

    /**
     * A validated remote URL together with the public address(es) its host resolved to at check
     * time. The address array is defensively copied on construction and on access
     * ({@link InetAddress} itself is immutable) so the validated set cannot be mutated after the
     * SSRF check.
     */
    public record SafeRemoteUrl(String url, String host, InetAddress[] addresses) {
        public SafeRemoteUrl {
            addresses = addresses.clone();
        }

        @Override
        public InetAddress[] addresses() {
            return addresses.clone();
        }
    }

    /**
     * Whether an address is non-public and so must not be the target of a server-side fetch.
     * Covers the {@link InetAddress} categories (loopback, any-local, link-local, site-local,
     * multicast) plus ranges the JDK predicates miss: the non-globally-reachable IPv4 blocks of
     * RFC 6890, IPv6 unique-local addresses (fc00::/7), and every embedded-IPv4 IPv6 form, which
     * is unwrapped and re-checked so an embedded private, loopback or metadata address cannot slip
     * past as "public".
     */
    static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            return isBlockedIpv4(b);
        }
        if (b.length == 16) {
            // IPv6 unique-local address fc00::/7 (RFC 4193).
            if ((b[0] & 0xFE) == 0xFC) return true;
            // Embedded IPv4 in IPv6 — unwrap and re-check the trailing IPv4:
            //   - IPv4-mapped     ::ffff:a.b.c.d (::ffff:0:0/96): bytes 0..9 zero, 10..11 = 0xFF
            //   - IPv4-compatible ::a.b.c.d      (::/96):         bytes 0..11 all zero
            //   - NAT64 well-known 64:ff9b::/96 (RFC 6052):       bytes 0..3 = 00 64 ff 9b, 4..11 zero
            // :: and ::1 are already handled above by isAnyLocal/isLoopback.
            boolean prefixZero = true;
            for (int i = 0; i < 10 && prefixZero; i++) {
                if (b[i] != 0) prefixZero = false;
            }
            boolean mapped = prefixZero && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
            boolean compatible = prefixZero && b[10] == 0 && b[11] == 0;
            if (mapped || compatible || isNat64WellKnown(b)) {
                try {
                    return isBlockedAddress(InetAddress.getByAddress(
                        new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException ignored) {
                    return true;   // unparseable embedded address: fail closed
                }
            }
            // Any other address in 64:ff9b::/32 is a NAT64 translation prefix (IANA
            // "IPv4-IPv6 Translat.", incl. the RFC 8215 local-use 64:ff9b:1::/48) whose embedding
            // format is operator-defined, so the embedded IPv4 is not readable here: fail closed.
            return isNat64Prefix(b);
        }
        return false;
    }

    /**
     * IPv4 blocks RFC 6890 marks as not globally reachable and the JDK predicates miss. The
     * private ranges (10/8, 172.16/12, 192.168/16), loopback and link-local are already covered by
     * {@link InetAddress#isSiteLocalAddress()} and friends.
     */
    private static boolean isBlockedIpv4(byte[] b) {
        int first = b[0] & 0xFF, second = b[1] & 0xFF, third = b[2] & 0xFF;
        if (first == 0) return true;                                    // 0.0.0.0/8    this network
        if (first == 100 && second >= 64 && second <= 127) return true; // 100.64/10    carrier-grade NAT
        if (first == 192 && second == 0 && third == 0) return true;     // 192.0.0.0/24 protocol assignments
        if (first == 198 && (second == 18 || second == 19)) return true; // 198.18/15   benchmarking
        return (first & 0xF0) == 0xF0;                                  // 240.0.0.0/4 reserved + broadcast
    }

    /** The RFC 6052 well-known NAT64 prefix 64:ff9b::/96, which embeds IPv4 in the last 4 bytes. */
    private static boolean isNat64WellKnown(byte[] b) {
        if (!isNat64Prefix(b)) return false;
        for (int i = 4; i < 12; i++) {
            if (b[i] != 0) return false;
        }
        return true;
    }

    /** The IANA IPv4-IPv6 translation prefix 64:ff9b::/32. */
    private static boolean isNat64Prefix(byte[] b) {
        return b[0] == 0x00 && (b[1] & 0xFF) == 0x64
            && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B;
    }
}

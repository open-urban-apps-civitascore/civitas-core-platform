package de.civitascore.modelforge.integrations.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Startup validation for operator-configured external upstream URLs (XRepository, …) and SSRF
 * checks for client-supplied ones (import-by-URL).
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
     * Validates a <em>client-supplied</em> remote URL for a server-side fetch (import-by-URL) and
     * resolves it to the safe public IP address(es) to connect to. Beyond the http(s) check it
     * rejects hosts that resolve to a non-public address — see {@link #isBlockedAddress(InetAddress)}
     * (loopback, any-local, link-local, site-local, multicast, IPv4 CGN, IPv6 ULA, IPv4-mapped IPv6)
     * — so a caller cannot steer the server at internal services or the cloud metadata endpoint
     * (169.254.169.254).
     *
     * <p>The returned {@link SafeRemoteUrl} carries the validated addresses so the caller can
     * <em>pin</em> the connection to exactly those IPs (via {@link PinnedDnsResolver}) for the
     * duration of the fetch. That closes the DNS-rebinding / TOCTOU window: the host is resolved and
     * validated once here, and the HTTP client — re-resolving through the pinned resolver — can only
     * connect to an already-validated address, never a rebound private one. TLS SNI and certificate
     * hostname verification are unaffected (the URL keeps its original hostname). Combined with a
     * no-redirect HTTP client this keeps the fetch on a vetted public host.
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
     * A validated client-supplied remote URL together with the safe public address(es) its host
     * resolves to — handed to {@link PinnedDnsResolver} so the fetch connects only to these IPs.
     * The address array is defensively copied on construction and on access ({@link InetAddress}
     * itself is immutable) so the validated set cannot be mutated after the SSRF check.
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
     * multicast) plus ranges the JDK predicates miss: IPv4 carrier-grade NAT (100.64.0.0/10),
     * IPv6 unique-local addresses (fc00::/7), and embedded-IPv4 IPv6 — both IPv4-mapped
     * ({@code ::ffff:a.b.c.d}) and IPv4-compatible ({@code ::a.b.c.d}) — which are unwrapped and
     * re-checked so neither {@code ::ffff:127.0.0.1} nor {@code ::169.254.169.254} can slip past
     * as "public".
     */
    static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF, second = b[1] & 0xFF;
            // Carrier-grade NAT 100.64.0.0/10 (RFC 6598) — not covered by the JDK predicates.
            return first == 100 && second >= 64 && second <= 127;
        }
        if (b.length == 16) {
            // IPv6 unique-local address fc00::/7 (RFC 4193).
            if ((b[0] & 0xFE) == 0xFC) return true;
            // Embedded IPv4 in IPv6 — unwrap and re-check the trailing IPv4 so the embedded
            // private/loopback/metadata address cannot slip past as "public":
            //   - IPv4-mapped     ::ffff:a.b.c.d (::ffff:0:0/96): bytes 0..9 zero, 10..11 = 0xFF
            //   - IPv4-compatible ::a.b.c.d       (::/96):        bytes 0..11 all zero
            // ::/:: 1 themselves are already handled above by isAnyLocal/isLoopback.
            boolean prefixZero = true;
            for (int i = 0; i < 10 && prefixZero; i++) {
                if (b[i] != 0) prefixZero = false;
            }
            boolean mapped = prefixZero && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
            boolean compatible = prefixZero && b[10] == 0 && b[11] == 0;
            if (mapped || compatible) {
                try {
                    return isBlockedAddress(InetAddress.getByAddress(
                        new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException ignored) {
                    return true;   // unparseable embedded address: fail closed
                }
            }
        }
        return false;
    }
}

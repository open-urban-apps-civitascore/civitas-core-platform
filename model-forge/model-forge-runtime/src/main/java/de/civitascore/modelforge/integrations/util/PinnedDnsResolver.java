package de.civitascore.modelforge.integrations.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * JVM-wide {@link InetAddressResolverProvider} that lets a single thread temporarily <em>pin</em> a
 * hostname to a set of pre-validated IP addresses for the duration of one outbound fetch.
 *
 * <p>It exists to close the SSRF DNS-rebinding (TOCTOU) gap on the import-by-URL path:
 * {@link UrlGuard#resolveSafeRemoteUrl} resolves and validates the host once, then the HTTP-fetching
 * adapter pins those validated addresses while it performs the request. Because the HTTP client
 * re-resolves the host through this resolver, it can only ever connect to an already-validated IP —
 * an attacker controlling authoritative DNS cannot answer the guard with a public address and the
 * fetch with a private one. TLS SNI and certificate hostname verification are unaffected, since the
 * request URL still carries the original hostname.
 *
 * <p>When no pin is active for the host being resolved (every other thread, and the vast majority of
 * lookups on the pinning thread too) resolution is delegated unchanged to the platform's built-in
 * resolver, so this provider is transparent to the rest of the JVM. It is registered via
 * {@code META-INF/services/java.net.spi.InetAddressResolverProvider}.
 */
public final class PinnedDnsResolver extends InetAddressResolverProvider {

    private static final ThreadLocal<Map<String, InetAddress[]>> PINS = new ThreadLocal<>();

    /**
     * Pins {@code host} to {@code addresses} for the current thread until {@link #clear()}. Always
     * pair with a {@code try { … } finally { clear(); }} so the override never outlives the fetch.
     */
    public static void pin(String host, InetAddress[] addresses) {
        PINS.set(Map.of(host.toLowerCase(Locale.ROOT), addresses.clone()));
    }

    /** Removes any pin set on the current thread. */
    public static void clear() {
        PINS.remove();
    }

    @Override
    public InetAddressResolver get(Configuration configuration) {
        return delegatingTo(configuration.builtinResolver());
    }

    /**
     * The pin-aware resolver: applies the current thread's pin and delegates everything else to
     * {@code builtin}. Package-private seam so it can be unit-tested without the sealed
     * {@link Configuration} ({@code builtinResolver()} comes from the platform at runtime).
     */
    InetAddressResolver delegatingTo(InetAddressResolver builtin) {
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
                    throws UnknownHostException {
                Map<String, InetAddress[]> pins = PINS.get();
                if (pins != null && host != null) {
                    InetAddress[] pinned = pins.get(host.toLowerCase(Locale.ROOT));
                    if (pinned != null) {
                        return Stream.of(pinned);
                    }
                }
                return builtin.lookupByName(host, lookupPolicy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "model-forge-pinned-dns";
    }
}

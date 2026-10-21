/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Checks every later resolution of a name the guard let through, from any client or thread. Without
 * it, a name could answer the guard with a public address and the client with an internal one (DNS
 * rebinding).
 */
final class GuardedResolver implements InetAddressResolver {

  /** Longer than a test runs plus the 30 s the JVM caches an answer. */
  private static final Duration GUARD_PERIOD = Duration.ofMinutes(2);

  /** Guarded names, normalized, with the guard that let them through and when that lapses. */
  private static final Map<String, Guarded> GUARDED = new ConcurrentHashMap<>();

  private final InetAddressResolver builtin;

  GuardedResolver(InetAddressResolver builtin) {
    this.builtin = builtin;
  }

  /**
   * Every later resolution of {@code name} until the guard period ends is checked by {@code guard}.
   */
  static void guard(String name, DestinationGuard guard) {
    long now = System.nanoTime();
    GUARDED.values().removeIf(entry -> entry.lapsesAt() - now < 0);
    GUARDED.put(name, new Guarded(guard, now + GUARD_PERIOD.toNanos()));
  }

  @Override
  public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
      throws UnknownHostException {
    Optional<DestinationGuard> guard = guardOf(DestinationGuard.normalize(host));
    Stream<InetAddress> addresses = builtin.lookupByName(host, lookupPolicy);
    if (guard.isEmpty()) {
      return addresses;
    }
    List<InetAddress> resolved = addresses.toList();
    if (resolved.stream().anyMatch(guard.get()::isDenied)) {
      throw new UnknownHostException(host + ": not an allowed destination of a connection test");
    }
    return resolved.stream();
  }

  @Override
  public String lookupByAddress(byte[] addr) throws UnknownHostException {
    return builtin.lookupByAddress(addr);
  }

  private static Optional<DestinationGuard> guardOf(String name) {
    Guarded entry = GUARDED.get(name);
    return entry == null || entry.lapsesAt() - System.nanoTime() < 0
        ? Optional.empty()
        : Optional.of(entry.guard());
  }

  /**
   * @param guard the guard that let the name through
   * @param lapsesAt the {@link System#nanoTime()} after which the name is no longer guarded
   */
  private record Guarded(DestinationGuard guard, long lapsesAt) {}
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Rebinding: a name the guard let through must not resolve to a denied address afterwards. */
class GuardedResolverTest {

  private static final LookupPolicy ANY = LookupPolicy.of(LookupPolicy.IPV4 | LookupPolicy.IPV6);

  /** What the DNS answers right now; a rebinding server changes it between two lookups. */
  private final AtomicReference<InetAddress> answer = new AtomicReference<>();

  private final InetAddressResolver dns =
      new InetAddressResolver() {
        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) {
          return Stream.of(answer.get());
        }

        @Override
        public String lookupByAddress(byte[] address) throws UnknownHostException {
          throw new UnknownHostException("no reverse lookup");
        }
      };

  private final GuardedResolver resolver = new GuardedResolver(dns);
  private final DestinationGuard guard =
      new DestinationGuard(
          ConnectionTestFactory.deniedNetworks("10.0.0.0/8"),
          host -> new InetAddress[] {answer.get()});

  @Test
  void aCheckedNameThatRebindsToADeniedAddressDoesNotResolve() throws Exception {
    answer.set(address(93, 184, 216, 34));
    guard.check("rebind.attacker.example");

    answer.set(address(10, 43, 0, 1));

    assertThrows(
        UnknownHostException.class, () -> resolver.lookupByName("rebind.attacker.example", ANY));
    assertThrows(
        UnknownHostException.class,
        () -> resolver.lookupByName("Rebind.Attacker.Example.", ANY),
        "another spelling is the same name");
  }

  @Test
  void aCheckedNameKeepsResolvingToAllowedAddresses() throws Exception {
    answer.set(address(93, 184, 216, 34));
    guard.check("stable.example.org");

    assertEquals(
        List.of(address(93, 184, 216, 34)),
        resolver.lookupByName("stable.example.org", ANY).toList());
  }

  @Test
  void aNameNoTestCheckedResolvesUnchanged() throws Exception {
    answer.set(address(10, 43, 0, 1));

    assertEquals(
        List.of(address(10, 43, 0, 1)),
        resolver.lookupByName("kafka.platform.example", ANY).toList());
  }

  @Test
  void isTheSystemWideResolverProvider() {
    assertEquals(
        List.of(GuardedResolverProvider.class),
        ServiceLoader.load(InetAddressResolverProvider.class).stream()
            .map(ServiceLoader.Provider::type)
            .toList());
  }

  private static InetAddress address(int a, int b, int c, int d) throws UnknownHostException {
    return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
  }
}

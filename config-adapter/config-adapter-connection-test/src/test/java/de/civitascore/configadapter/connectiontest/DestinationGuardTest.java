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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.connectiontest.DestinationGuard.HostResolver;
import de.civitascore.configadapter.connectiontest.DestinationGuard.Network;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DestinationGuardTest {

  private static final List<Network> DENIED = Network.parseAll("10.0.0.0/8,fc00::/7");

  private static InetAddress ip(String literal) {
    try {
      return InetAddress.getByName(literal);
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  private static HostResolver resolvingTo(String... addresses) {
    return host -> {
      InetAddress[] result = new InetAddress[addresses.length];
      for (int i = 0; i < addresses.length; i++) {
        result[i] = ip(addresses[i]);
      }
      return result;
    };
  }

  private static DestinationGuard guard(String... resolved) {
    return new DestinationGuard(DENIED, List.of(), resolvingTo(resolved));
  }

  private static DestinationGuard unreachableResolver(List<String> deniedHostSuffixes) {
    return new DestinationGuard(
        DENIED,
        deniedHostSuffixes,
        host -> {
          throw new AssertionError("the resolver must not be asked for " + host);
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"localhost", "LOCALHOST", "localhost.", "foo.localhost", "Foo.LocalHost."})
  void localhostNamesAreRefusedWithoutResolving(String host) {
    ConnectionTestFailure e =
        assertThrows(ConnectionTestFailure.class, () -> unreachableResolver(List.of()).check(host));

    assertTrue(e.getMessage().contains("not an allowed destination"), e.getMessage());
    assertEquals(Reason.INVALID_CONNECTOR, e.reason());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "x.svc",
        "x.svc.",
        "frost.civitas.svc.cluster.local",
        "Frost.Civitas.SVC.Cluster.Local.",
        "svc.cluster.local"
      })
  void deniedHostSuffixesAreRefusedWithoutResolving(String host) {
    DestinationGuard guard = unreachableResolver(List.of(".svc", "SVC.cluster.local."));

    assertThrows(ConnectionTestFailure.class, () -> guard.check(host));
  }

  @ParameterizedTest
  @ValueSource(strings = {"frost", "tenant-db.tenant-a.svc.cluster.local", "broker.tenant-a"})
  void clusterNamesAreResolvedAndCheckedByAddressWhenNoSuffixIsDenied(String host) {
    assertDoesNotThrow(() -> guard("93.184.216.34").check(host));
    assertThrows(ConnectionTestFailure.class, () -> guard("10.43.0.5").check(host));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "127.0.0.1",
        "::1",
        "169.254.169.254",
        "fe80::1",
        "0.0.0.0",
        "::",
        "224.0.0.1",
        "ff02::1",
        "0.1.2.3",
        "10.43.0.5",
        "fd12:3456::1"
      })
  void deniedAddressesAreRefused(String address) {
    assertThrows(ConnectionTestFailure.class, () -> guard(address).check("example.org"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "::a2b:5", // IPv4-compatible 10.43.0.5
        "64:ff9b::a9fe:a9fe", // NAT64 169.254.169.254
        "64:ff9b::a2b:5", // NAT64 10.43.0.5
        "2002:a2b:5::1" // 6to4 10.43.0.5
      })
  void ipv6FormsOfADeniedIpv4AddressAreRefused(String address) {
    ConnectionTestFailure e =
        assertThrows(ConnectionTestFailure.class, () -> guard(address).check("tunnel.example.org"));

    assertTrue(e.getMessage().contains("not an allowed destination"), e.getMessage());
  }

  @Test
  void ipv6FormOfAPublicIpv4AddressIsAllowed() {
    assertDoesNotThrow(() -> guard("64:ff9b::5db8:d822").check("tunnel.example.org"));
  }

  @Test
  void hostIsRefusedIfAnyResolvedAddressIsDenied() {
    DestinationGuard guard = guard("93.184.216.34", "10.43.0.5");

    ConnectionTestFailure e =
        assertThrows(ConnectionTestFailure.class, () -> guard.check("example.org"));

    assertTrue(e.getMessage().contains("example.org"));
  }

  @Test
  void deniedIpv4LiteralIsRefused() {
    assertThrows(ConnectionTestFailure.class, () -> guard("10.1.2.3").check("10.1.2.3"));
  }

  @Test
  void publicNameIsAllowed() {
    assertDoesNotThrow(() -> guard("93.184.216.34").check("example.org"));
  }

  @Test
  void publicIpv4LiteralIsAllowedAndStillResolved() {
    List<String> asked = new ArrayList<>();
    DestinationGuard guard =
        new DestinationGuard(
            DENIED,
            List.of(),
            host -> {
              asked.add(host);
              return new InetAddress[] {ip("93.184.216.34")};
            });

    assertDoesNotThrow(() -> guard.check("93.184.216.34"));
    assertEquals(List.of("93.184.216.34"), asked);
  }

  @Test
  void bracketedPublicIpv6LiteralIsAllowedAndStillResolved() {
    List<String> asked = new ArrayList<>();
    DestinationGuard guard =
        new DestinationGuard(
            DENIED,
            List.of(),
            host -> {
              asked.add(host);
              return new InetAddress[] {ip("2606:2800:220:1::1")};
            });

    assertDoesNotThrow(() -> guard.check("[2606:2800:220:1::1]"));
    assertEquals(List.of("2606:2800:220:1::1"), asked);
  }

  @Test
  void bracketedLoopbackIpv6LiteralIsRefused() {
    assertThrows(ConnectionTestFailure.class, () -> guard("::1").check("[::1]"));
  }

  @Test
  void unknownHostFailsNamingTheHost() {
    DestinationGuard guard =
        new DestinationGuard(
            DENIED,
            List.of(),
            host -> {
              throw new UnknownHostException(host);
            });

    ConnectionTestFailure e =
        assertThrows(ConnectionTestFailure.class, () -> guard.check("nope.example.org"));

    assertTrue(e.getMessage().contains("nope.example.org"), e.getMessage());
    assertTrue(e.getMessage().contains("unknown"), e.getMessage());
    assertEquals(Reason.SOURCE_ERROR, e.reason());
  }

  @Test
  void blankOrNullHostIsRefused() {
    assertThrows(ConnectionTestFailure.class, () -> guard("93.184.216.34").check(" "));
    assertThrows(ConnectionTestFailure.class, () -> guard("93.184.216.34").check(null));
  }

  @Test
  void noDeniedNetworksStillRefusesLoopbackAndAllowsPrivateAddresses() {
    DestinationGuard open = new DestinationGuard(List.of(), List.of(), resolvingTo("10.1.1.1"));

    assertDoesNotThrow(() -> open.check("internal.example.org"));
    assertThrows(
        ConnectionTestFailure.class,
        () ->
            new DestinationGuard(List.of(), List.of(), resolvingTo("127.0.0.1"))
                .check("a.example.org"));
  }

  @Test
  void networkParseReadsIpv4AndIpv6() {
    Network v4 = Network.parse("192.168.0.0/16");
    Network v6 = Network.parse("fc00::/7");

    assertEquals(16, v4.prefixLength());
    assertEquals(7, v6.prefixLength());
    assertEquals(4, v4.address().length);
    assertEquals(16, v6.address().length);
  }

  @Test
  void networkOfPrefixZeroContainsEveryAddressOfItsFamily() {
    Network all = Network.parse("0.0.0.0/0");

    assertTrue(all.contains(ip("93.184.216.34")));
    assertFalse(all.contains(ip("::1")));
  }

  @Test
  void networkOfPrefix32ContainsOnlyItself() {
    Network single = Network.parse("93.184.216.34/32");

    assertTrue(single.contains(ip("93.184.216.34")));
    assertFalse(single.contains(ip("93.184.216.35")));
  }

  @Test
  void networkOfPrefix12CoversExactlyItsRange() {
    Network network = Network.parse("172.16.0.0/12");

    assertTrue(network.contains(ip("172.16.0.0")));
    assertTrue(network.contains(ip("172.31.255.255")));
    assertFalse(network.contains(ip("172.32.0.0")));
    assertFalse(network.contains(ip("172.15.255.255")));
  }

  @Test
  void ipv6NetworkDoesNotContainIpv4Address() {
    assertFalse(Network.parse("fc00::/7").contains(ip("10.0.0.1")));
    assertTrue(Network.parse("fc00::/7").contains(ip("fd00::1")));
    assertFalse(Network.parse("fc00::/7").contains(ip("fe00::1")));
  }

  @Test
  void networkParseNeverResolvesHostNames() {
    assertThrows(IllegalArgumentException.class, () -> Network.parse("example.org/8"));
    assertThrows(IllegalArgumentException.class, () -> Network.parse("localhost/8"));
  }

  @Test
  void networkParseRejectsMissingOrInvalidPrefix() {
    assertThrows(IllegalArgumentException.class, () -> Network.parse("10.0.0.0"));
    assertThrows(IllegalArgumentException.class, () -> Network.parse("10.0.0.0/x"));
    assertThrows(IllegalArgumentException.class, () -> Network.parse("10.0.0.0/33"));
    assertThrows(IllegalArgumentException.class, () -> Network.parse("10.0.0.0/-1"));
    assertThrows(IllegalArgumentException.class, () -> Network.parse("fc00::/129"));
  }

  @Test
  void networkParseAllSkipsBlankEntries() {
    List<Network> networks = Network.parseAll(" 10.0.0.0/8 , ,, 192.168.0.0/16 ,");

    assertEquals(2, networks.size());
    assertEquals(Network.parse("10.0.0.0/8"), networks.get(0));
  }

  @Test
  void networksAreEqualByAddressContentAndPrefix() {
    Network a = Network.parse("10.0.0.0/8");
    Network b = Network.parse("10.0.0.0/8");

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, Network.parse("10.0.0.0/9"));
    assertNotEquals(a, Network.parse("11.0.0.0/8"));
  }

  @Test
  void networkAddressIsDefensivelyCopied() {
    Network network = Network.parse("10.0.0.0/8");

    network.address()[0] = 99;

    assertEquals(Network.parse("10.0.0.0/8"), network);
  }
}

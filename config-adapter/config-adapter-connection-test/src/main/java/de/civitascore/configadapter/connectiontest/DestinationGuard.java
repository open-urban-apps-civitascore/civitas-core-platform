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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Refuses destinations where no data source lives: loopback, link-local (cloud metadata), any-local
 * and multicast. Operators may deny more networks and host name suffixes, and network policies keep
 * tests away from cluster services.
 *
 * <p>Every address of the host is checked. {@link GuardedResolver} repeats the check when the
 * client resolves the name again, against DNS rebinding.
 */
final class DestinationGuard implements DestinationCheck {

  private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

  /** The "this network" block, which Linux routes to the local host. */
  private static final Network THIS_NETWORK = Network.parse("0.0.0.0/8");

  private static final Network NAT64 = Network.parse("64:ff9b::/96");
  private static final Network SIX_TO_FOUR = Network.parse("2002::/16");

  private final List<Network> deniedNetworks;
  private final List<String> deniedHostSuffixes;
  private final HostResolver resolver;

  /**
   * @param deniedNetworks networks refused in addition to the ones that hold no data source
   * @param deniedHostSuffixes host name suffixes refused before the name is resolved, such as
   *     {@code .svc.cluster.local}
   */
  DestinationGuard(
      List<Network> deniedNetworks, List<String> deniedHostSuffixes, HostResolver resolver) {
    this.deniedNetworks = List.copyOf(deniedNetworks);
    this.deniedHostSuffixes = deniedHostSuffixes.stream().map(DestinationGuard::suffix).toList();
    this.resolver = resolver;
  }

  /**
   * @throws ConnectionTestFailure if {@code host} is not an allowed destination or does not resolve
   */
  @Override
  public void check(String host) throws ConnectionTestFailure {
    String name = normalize(host);
    if (name.isEmpty()) {
      throw ConnectionTestFailure.invalid("The connector names no host.");
    }
    boolean literal = name.indexOf(':') >= 0 || IPV4_LITERAL.matcher(name).matches();
    if (!literal && isDeniedName(name)) {
      throw notAllowed(name);
    }
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(name);
    } catch (UnknownHostException e) {
      throw new ConnectionTestFailure("The host '" + name + "' is unknown.", e);
    }
    for (InetAddress address : addresses) {
      if (isDenied(address)) {
        throw notAllowed(name);
      }
    }
    if (!literal) {
      GuardedResolver.guard(name, this);
    }
  }

  boolean isDenied(InetAddress address) {
    return address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isAnyLocalAddress()
        || address.isMulticastAddress()
        || THIS_NETWORK.contains(address)
        || deniedNetworks.stream().anyMatch(network -> network.contains(address))
        || embeddedIpv4(address).map(this::isDenied).orElse(false);
  }

  /**
   * The IPv4 address an IPv6 address carries and a gateway may connect to: IPv4-compatible ({@code
   * ::a.b.c.d}), NAT64 ({@code 64:ff9b::a.b.c.d}) or 6to4 ({@code 2002:aabb:ccdd::}).
   */
  private static Optional<InetAddress> embeddedIpv4(InetAddress address) {
    if (!(address instanceof Inet6Address)) {
      return Optional.empty();
    }
    byte[] bytes = address.getAddress();
    int offset;
    if (NAT64.contains(address) || ((Inet6Address) address).isIPv4CompatibleAddress()) {
      offset = 12;
    } else if (SIX_TO_FOUR.contains(address)) {
      offset = 2;
    } else {
      return Optional.empty();
    }
    try {
      return Optional.of(InetAddress.getByAddress(Arrays.copyOfRange(bytes, offset, offset + 4)));
    } catch (UnknownHostException e) {
      return Optional.empty();
    }
  }

  private boolean isDeniedName(String name) {
    return "localhost".equals(name)
        || name.endsWith(".localhost")
        || deniedHostSuffixes.stream()
            .anyMatch(suffix -> name.endsWith(suffix) || name.equals(suffix.substring(1)));
  }

  /** A suffix as a name ending: lower case, with one leading dot and no trailing dot. */
  private static String suffix(String value) {
    String name = normalize(value);
    return name.startsWith(".") ? name : "." + name;
  }

  /** Lower case, without IPv6 brackets and trailing dots: one spelling per name. */
  static String normalize(String host) {
    String name = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    if (name.startsWith("[") && name.endsWith("]")) {
      name = name.substring(1, name.length() - 1);
    }
    while (name.endsWith(".")) {
      name = name.substring(0, name.length() - 1);
    }
    return name;
  }

  private static ConnectionTestFailure notAllowed(String name) {
    return ConnectionTestFailure.invalid(
        "The host '" + name + "' is not an allowed destination for a connection test.");
  }

  /** Resolves a host name to all its addresses. */
  @FunctionalInterface
  interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  /**
   * An IPv4 or IPv6 network in CIDR notation.
   *
   * @param address the network address
   * @param prefixLength the number of leading bits that identify the network
   */
  record Network(byte[] address, int prefixLength) {

    Network {
      address = address.clone();
    }

    /**
     * Parses {@code a.b.c.d/n} or {@code x:y::/n}. Only an address literal is accepted, so parsing
     * never asks the DNS.
     *
     * @throws IllegalArgumentException if {@code cidr} is not a network in CIDR notation
     */
    static Network parse(String cidr) {
      String text = cidr.trim();
      int slash = text.indexOf('/');
      if (slash < 0) {
        throw new IllegalArgumentException("not a CIDR network: " + text);
      }
      String host = text.substring(0, slash);
      if (host.indexOf(':') < 0 && !IPV4_LITERAL.matcher(host).matches()) {
        throw new IllegalArgumentException("not an address literal: " + text);
      }
      byte[] bytes;
      try {
        bytes = InetAddress.getByName(host).getAddress();
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException("not an address literal: " + text, e);
      }
      int prefix;
      try {
        prefix = Integer.parseInt(text.substring(slash + 1));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("not a prefix length: " + text, e);
      }
      if (prefix < 0 || prefix > bytes.length * 8) {
        throw new IllegalArgumentException("prefix length out of range: " + text);
      }
      return new Network(bytes, prefix);
    }

    /** Parses a comma-separated list; blank entries are skipped. */
    static List<Network> parseAll(String cidrs) {
      List<Network> networks = new ArrayList<>();
      for (String cidr : cidrs.split(",")) {
        if (!cidr.isBlank()) {
          networks.add(parse(cidr));
        }
      }
      return networks;
    }

    boolean contains(InetAddress candidate) {
      byte[] bytes = candidate.getAddress();
      if (bytes.length != address.length) {
        return false;
      }
      int fullBytes = prefixLength / 8;
      for (int i = 0; i < fullBytes; i++) {
        if (bytes[i] != address[i]) {
          return false;
        }
      }
      int remainingBits = prefixLength % 8;
      if (remainingBits == 0) {
        return true;
      }
      int mask = 0xFF << (8 - remainingBits) & 0xFF;
      return (bytes[fullBytes] & mask) == (address[fullBytes] & mask);
    }

    @Override
    public byte[] address() {
      return address.clone();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Network network
          && prefixLength == network.prefixLength
          && Arrays.equals(address, network.address);
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(address) + prefixLength;
    }

    @Override
    public String toString() {
      try {
        return InetAddress.getByAddress(address).getHostAddress() + "/" + prefixLength;
      } catch (UnknownHostException e) {
        return "invalid network";
      }
    }
  }
}

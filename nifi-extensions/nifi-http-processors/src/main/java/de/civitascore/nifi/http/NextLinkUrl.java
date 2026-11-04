/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Java URI.resolve uses RFC 2396, which changes the path of a query-only reference. */
final class NextLinkUrl {

  private NextLinkUrl() {}

  static boolean sameHost(URI apiHost, URI link) {
    return apiHost.getScheme().equalsIgnoreCase(link.getScheme())
        && apiHost.getHost().equalsIgnoreCase(link.getHost())
        && port(apiHost) == port(link);
  }

  private static int port(URI uri) {
    if (uri.getPort() == -1) {
      return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
    return uri.getPort();
  }

  static URI resolve(URI base, URI reference) {
    if (reference.isOpaque()) {
      return reference;
    }
    if (reference.getScheme() != null) {
      return components(
          reference.getScheme(),
          reference.getRawAuthority(),
          removeDotSegments(reference.getRawPath()),
          reference.getRawQuery(),
          reference.getRawFragment());
    }
    if (reference.getRawAuthority() != null) {
      return components(
          base.getScheme(),
          reference.getRawAuthority(),
          removeDotSegments(reference.getRawPath()),
          reference.getRawQuery(),
          reference.getRawFragment());
    }
    if (reference.getRawPath().isEmpty()) {
      return components(
          base.getScheme(),
          base.getRawAuthority(),
          base.getRawPath(),
          reference.getRawQuery() == null ? base.getRawQuery() : reference.getRawQuery(),
          reference.getRawFragment());
    }
    return components(
        base.getScheme(),
        base.getRawAuthority(),
        removeDotSegments(merge(base, reference.getRawPath())),
        reference.getRawQuery(),
        reference.getRawFragment());
  }

  private static String merge(URI base, String path) {
    if (path.startsWith("/")) {
      return path;
    }
    String basePath = base.getRawPath();
    return (basePath.isEmpty() ? "/" : basePath.substring(0, basePath.lastIndexOf('/') + 1)) + path;
  }

  private static URI components(
      String scheme, String authority, String path, String query, String fragment) {
    return URI.create(
        scheme
            + ":"
            + (authority == null ? "" : "//" + authority)
            + path
            + (query == null ? "" : "?" + query)
            + (fragment == null ? "" : "#" + fragment));
  }

  private static String removeDotSegments(String path) {
    boolean absolute = path.startsWith("/");
    String[] input = (absolute ? path.substring(1) : path).split("/", -1);
    List<String> output = new ArrayList<>();
    for (String segment : input) {
      switch (segment) {
        case "." -> {}
        case ".." -> {
          if (!output.isEmpty()) {
            output.removeLast();
          }
        }
        default -> output.add(segment);
      }
    }
    String last = input[input.length - 1];
    if (".".equals(last) || "..".equals(last)) {
      output.add("");
    }
    return (absolute ? "/" : "") + String.join("/", output);
  }
}

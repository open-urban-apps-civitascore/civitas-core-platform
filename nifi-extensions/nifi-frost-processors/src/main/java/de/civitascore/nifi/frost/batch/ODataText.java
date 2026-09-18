/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

import java.nio.charset.StandardCharsets;

/**
 * Renders record values into the URL of a sub-request. Every value that reaches a {@code $filter}
 * or a path segment passes through here: a reference carrying a quotation mark must not end the
 * literal and start a new filter term.
 */
public final class ODataText {

  /** The characters a percent-encoded value may keep, per RFC 3986 "unreserved". */
  private static final String UNRESERVED =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private ODataText() {}

  /**
   * An OData string literal, quoted and safe to interpolate into a {@code $filter}. The quotation
   * mark is doubled first, which is how OData escapes it, and the result is percent-encoded
   * afterwards: FROST decodes the URL before it parses the filter, so {@code %27%27} arrives as the
   * doubled quote the literal needs, and no input can close the literal early.
   */
  public static String literal(String value) {
    if (value == null) {
      throw new IllegalArgumentException("a filter literal must not be null");
    }
    return "'" + percentEncode(value.replace("'", "''")) + "'";
  }

  /**
   * Percent-encodes a value for use inside a URL query. Everything outside the unreserved set is
   * encoded, so a reference containing {@code &}, {@code ?}, {@code #} or a space cannot change the
   * shape of the sub-request URL.
   */
  public static String percentEncode(String value) {
    StringBuilder encoded = new StringBuilder(value.length());
    for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
      int octet = raw & 0xFF;
      if (octet < 0x80 && UNRESERVED.indexOf((char) octet) >= 0) {
        encoded.append((char) octet);
      } else {
        encoded.append('%').append(HEX[octet >> 4]).append(HEX[octet & 0x0F]);
      }
    }
    return encoded.toString();
  }
}

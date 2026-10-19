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

import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * The identifier of a sub-request, scoped to one record.
 *
 * <p>The JSON batch extension does not demand unique identifiers: a back-reference resolves to the
 * latest successful request that carries the identifier. The find-or-create idiom uses that — the
 * lookup and the conditional create share one identifier, so {@code $id} names the found entity or
 * the created one. Two records in one batch must not share identifiers for the same reason, which
 * is what the record prefix here gives.
 *
 * <p>The extension limits the identifier to {@code a-zA-Z0-9_.:,;-}.
 */
public final class SubRequestId {

  private static final Pattern ALLOWED = Pattern.compile("[a-zA-Z0-9_.:,;-]+");

  private static final char SEPARATOR = '-';

  private SubRequestId() {}

  /** The atomicity group of a record: one group per record, so a defect stops only its own. */
  public static String group(int recordIndex) {
    return "r" + recordIndex;
  }

  /** The record-scoped identifier of one sub-request, for example {@code r0-thing}. */
  public static String of(int recordIndex, String suffix) {
    if (!ALLOWED.matcher(suffix).matches()) {
      // The suffixes are constants of this module, so a violation is a programming error, not
      // input. It is checked because a rejected batch names no offender.
      throw new IllegalArgumentException("illegal sub-request id suffix: " + suffix);
    }
    return group(recordIndex) + SEPARATOR + suffix;
  }

  /**
   * The record a response identifier belongs to, or empty when the identifier is not one this
   * processor built. FROST echoes the identifier unchanged, and the prefix is the only carrier of
   * the assignment: the response holds no atomicity group.
   *
   * <p>A suffix may carry the separator itself, so the record index ends at the first one.
   */
  public static OptionalInt recordIndexOf(String id) {
    int separator = id.indexOf(SEPARATOR);
    if (separator < 2 || id.charAt(0) != 'r') {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(id.substring(1, separator)));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }
}

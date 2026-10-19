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

/**
 * Renders record values into the URL of a sub-request. Every value that reaches a {@code $filter}
 * passes through here: a reference carrying a quotation mark must not end the literal and start a
 * new filter term.
 *
 * <p>The value is <b>not</b> percent-encoded. A sub-request URL of a batch document never passes a
 * URL decoder — FROST decodes the query string of an HTTP request, but hands the URL of a batch
 * item to the query parser as it stands — so an escape would reach the comparison as its own
 * characters and the filter would look for a value nobody stored.
 *
 * <p>One character stays out of reach: a value carrying a {@code ?} splits the sub-request URL at
 * the wrong place, and FROST answers that sub-request with a path error. The record then goes to
 * the error sink, which is the outcome an unmatchable reference has to have anyway.
 */
public final class ODataText {

  private ODataText() {}

  /**
   * An OData string literal, quoted and safe to interpolate into a {@code $filter}. The quotation
   * mark is doubled, which is how OData escapes it, so no input can close the literal early. Every
   * other character stays as it is: the literal of the grammar ends at the next single quotation
   * mark, which leaves the separators of the query — {@code &}, {@code ,}, {@code ;} — and a space
   * without meaning inside it.
   */
  public static String literal(String value) {
    if (value == null) {
      throw new IllegalArgumentException("a filter literal must not be null");
    }
    return "'" + value.replace("'", "''") + "'";
  }
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A schema-derived key name is tenant input that ends up verbatim in an OData {@code $filter} and a
 * generated JSON template, so the whole injection defence rests on this whitelist. The negative
 * table pins the alphabet directly: loosening the pattern (e.g. to admit a dot for nested keys)
 * must break a test here, not silently open a filter-injection hole.
 */
class StaTargetCatalogTest {

  @ParameterizedTest
  @ValueSource(strings = {"reference", "Reference", "_id", "a", "A1", "primary_key_2", "_"})
  void acceptsPlainIdentifierKeyNames(String keyName) {
    assertTrue(StaTargetCatalog.isSafeKeyName(keyName));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "1ref", // leading digit
        "ref.name", // dot — would break out of a filter segment
        "ref/id", // slash — OData navigation
        "ref name", // whitespace
        "ref'", // quote — filter-string breakout
        "ref or true", // spaced injection payload
        "ref;drop", // statement separator
        "réf", // non-ASCII letter
        "ref-id", // hyphen
        "ref(", // parenthesis — OData function call
        " ref" // leading whitespace
      })
  void rejectsUnsafeKeyNames(String keyName) {
    assertFalse(StaTargetCatalog.isSafeKeyName(keyName));
  }
}

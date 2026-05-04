/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamedApiRouteIdsTest {

  @Test
  void derive_goldenValues_lockTheFormula() {
    // Hand-computed via UUID.nameUUIDFromBytes(...). Any change to the separator,
    // encoding, or hashing scheme MUST update these values — and any code that
    // persisted route IDs against the old formula.
    assertEquals(
        "1167d772-7ca4-30cd-8a4b-3facc9caf80e", NamedApiRouteIds.derive("fixed-ds-id", "traffic"));
    assertEquals(
        "02f85d38-1866-3907-96c8-c72bb536525a",
        NamedApiRouteIds.derive("550e8400-e29b-41d4-a716-446655440000", "traffic"));
    assertEquals(
        "9477f601-6d56-3517-86ca-148577f4136a",
        NamedApiRouteIds.derive("550e8400-e29b-41d4-a716-446655440000", "weather"));
  }
}

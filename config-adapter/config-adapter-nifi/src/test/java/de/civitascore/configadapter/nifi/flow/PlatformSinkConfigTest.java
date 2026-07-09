/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformSinkConfigTest {

  @Test
  void toStringMasksThePlatformPassword() {
    PlatformSinkConfig config =
        new PlatformSinkConfig("jdbc:postgresql://db:5432/civitas", "nifi", "sup3r-s3cret");

    String rendered = config.toString();

    assertFalse(rendered.contains("sup3r-s3cret"), "the platform credential must never render");
    assertTrue(rendered.contains("jdbc:postgresql://db:5432/civitas"));
    assertTrue(rendered.contains("nifi"));
  }
}

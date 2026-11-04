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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PageLoopTest {

  @Test
  void constructor_withMaxPagesBelow1_throws() {
    ApiCall apiCall =
        new ApiCall(
            "Stations",
            HttpMethod.GET,
            "/stations",
            List.of(),
            List.of(),
            Optional.empty(),
            new SinglePage());

    assertThrows(
        IllegalArgumentException.class,
        () -> new PageLoop(apiCall, null, null, 0, () -> true, null));
  }
}

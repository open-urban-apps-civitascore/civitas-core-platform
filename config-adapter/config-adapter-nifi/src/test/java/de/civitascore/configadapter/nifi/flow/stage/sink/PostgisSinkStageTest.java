/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.SinkResolutionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostgisSinkStageTest {

  @Test
  void parseSpecRejectsAMissingTableNameAsInvalidPayload() {
    // The construction-time IllegalArgumentException must be wrapped like the FROST stage does:
    // unwrapped it bypasses the saga handler's safe-external-message discipline.
    PostgisSinkStage stage = new PostgisSinkStage(null);
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                stage.parseSpec(
                    Map.of("id", "sk-1", "type", "POSTGIS", "configuration", Map.of()),
                    new SinkResolutionContext(null)));
    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, ex.getErrorCode());
  }

  @Test
  void withStringtypeUnspecifiedCoversAllBranches() {
    // no query string → append with '?'
    assertEquals(
        "jdbc:postgresql://db:5432/civitas?stringtype=unspecified",
        PostgisSinkStage.withStringtypeUnspecified("jdbc:postgresql://db:5432/civitas"));
    // existing query string → append with '&'
    assertEquals(
        "jdbc:postgresql://db:5432/civitas?ssl=true&stringtype=unspecified",
        PostgisSinkStage.withStringtypeUnspecified("jdbc:postgresql://db:5432/civitas?ssl=true"));
    // already configured → unchanged
    String already = "jdbc:postgresql://db:5432/civitas?stringtype=unspecified";
    assertEquals(already, PostgisSinkStage.withStringtypeUnspecified(already));
    // null/blank → unchanged
    assertNull(PostgisSinkStage.withStringtypeUnspecified(null));
    assertEquals("", PostgisSinkStage.withStringtypeUnspecified(""));
  }
}

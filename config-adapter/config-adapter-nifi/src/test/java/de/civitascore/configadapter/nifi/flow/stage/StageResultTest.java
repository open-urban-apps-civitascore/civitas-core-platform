/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageResultTest {

  private static Processor processor(String id) {
    return new Processor(JsonNodeFactory.instance.objectNode(), id, "success");
  }

  @Test
  void emptyChainIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new StageResult(List.of(), List.of()));
  }

  @Test
  void exitReturnsTheLastProcessorInChainOrder() {
    Processor first = processor("a");
    Processor last = processor("b");

    StageResult result = new StageResult(List.of(first, last), List.of());

    assertEquals(last, result.exit());
  }
}

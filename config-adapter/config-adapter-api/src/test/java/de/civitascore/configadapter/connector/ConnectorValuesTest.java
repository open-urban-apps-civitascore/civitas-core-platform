/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorValuesTest {

  @Test
  void trimmedNonBlankDropsNullAndBlankListElements() {
    // A null element must not surface as the literal "null"; blanks are dropped, values trimmed.
    List<String> result =
        ConnectorValues.trimmedNonBlank(Arrays.asList(" a ", null, "", "  ", "b"));

    assertEquals(List.of("a", "b"), result);
  }

  @Test
  void trimmedNonBlankTreatsNullValueAsEmpty() {
    assertEquals(List.of(), ConnectorValues.trimmedNonBlank(null));
  }

  @Test
  void trimmedNonBlankWrapsAScalar() {
    assertEquals(List.of("x"), ConnectorValues.trimmedNonBlank(" x "));
  }
}

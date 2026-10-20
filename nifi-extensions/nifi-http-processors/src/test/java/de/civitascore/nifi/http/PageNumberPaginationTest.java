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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageNumberPaginationTest {

  @ParameterizedTest
  @CsvSource({"0, 0, 0", "1, -1, 0", "1, 2, 1"})
  void constructor_withANumberOutsideItsRange_throws(int pageSize, int startPage, int endPage) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PageNumberPagination("page", "size", pageSize, startPage, OptionalInt.of(endPage)));
  }

  @Test
  void constructor_withTheEndPageEqualToTheStartPage_accepts() {
    assertDoesNotThrow(() -> new PageNumberPagination("page", "size", 1, 2, OptionalInt.of(2)));
  }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusClassificationTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "200, success",
    "201, success",
    "204, success",
    "299, success",
    "300, fatal",
    "301, fatal",
    "302, fatal",
    "304, fatal",
    "399, fatal",
    "400, fatal",
    "401, fatal",
    "403, fatal",
    "404, fatal",
    "407, fatal",
    "409, fatal",
    "499, fatal",
    "408, transient",
    "429, transient",
    "500, transient",
    "502, transient",
    "503, transient",
    "599, transient",
    "100, transient",
    "199, transient",
    "600, transient",
    "0, transient"
  })
  void errorKind_ofAStatus_isTheKindOfItsRange(int status, String expected) {
    Optional<ErrorKind> kind = StatusClassification.errorKind(status);

    assertEquals(expected, kind.map(ErrorKind::attributeValue).orElse("success"));
  }
}

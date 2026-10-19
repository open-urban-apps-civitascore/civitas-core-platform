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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.nifi.attribute.expression.language.Query;
import org.apache.nifi.attribute.expression.language.StandardEvaluationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NifiExpressionLanguageTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "${HOSTNAME}",
        "$${HOSTNAME}",
        "prefix-${HOSTNAME}-suffix",
        "#{truststore.password}",
        "##{truststore.password}",
        "${#{p}}",
        "${}"
      })
  void requireLiteral_withReferenceStart_isRejected(String value) {
    assertThrows(
        UnsafePropertyValueException.class,
        () -> NifiExpressionLanguage.requireLiteral("Topic Filter", value));
  }

  @Test
  void requireLiteral_rejection_namesThePropertyButNotTheValue() {
    UnsafePropertyValueException ex =
        assertThrows(
            UnsafePropertyValueException.class,
            () -> NifiExpressionLanguage.requireLiteral("Password", "s3cret-${X}"));

    assertTrue(ex.getMessage().contains("Password"), ex.getMessage());
    assertFalse(ex.getMessage().contains("s3cret"), ex.getMessage());
  }

  @Test
  void requireLiteral_null_isReturnedUnchanged() {
    assertNull(NifiExpressionLanguage.requireLiteral("Username", null));
  }

  /**
   * Evaluated through NiFi's own parser, because a value that passes the guard but still changes
   * under NiFi's evaluation would be corrupted on its way into the flow.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "sensors/+/temp",
        "US$5",
        "a$b",
        "$",
        "$$",
        "trailing$",
        "{}",
        "{$}",
        "$ {X}",
        "#",
        "a#b",
        "# {X}",
        "^\\{",
        "$.value[0]['@iot.id']",
        "tcp://broker:1883"
      })
  void requireLiteral_acceptedValue_isLeftUnchangedByNifiEvaluation(String value) {
    String accepted = NifiExpressionLanguage.requireLiteral("Table Name", value);

    assertEquals(value, accepted);
    assertEquals(
        value,
        Query.prepare(accepted).evaluateExpressions(new StandardEvaluationContext(Map.of()), null));
  }
}

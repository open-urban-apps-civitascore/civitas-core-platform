/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AbstractApiModel}. */
class AbstractApiModelTest {

  /** Concrete test subclass for testing the abstract base class. */
  static class TestModel extends AbstractApiModel {
    @Override
    public Map<String, Object> toApiMap() {
      return Collections.unmodifiableMap(additionalProperties());
    }
  }

  @Test
  void handleUnknownProperty_shouldStoreInAdditionalProperties() {
    TestModel model = new TestModel();
    model.handleUnknownProperty("customKey", "customValue");

    assertEquals("customValue", model.getAdditionalProperties().get("customKey"));
    assertEquals(1, model.getAdditionalProperties().size());
  }

  @Test
  void getAdditionalProperties_shouldReturnUnmodifiableMap() {
    TestModel model = new TestModel();
    model.handleUnknownProperty("key", "value");

    Map<String, Object> props = model.getAdditionalProperties();
    assertThrows(UnsupportedOperationException.class, () -> props.put("new", "val"));
  }

  @Test
  void handleUnknownProperty_shouldLogWithSubclassName() {
    // Exercises the logging path with a concrete subclass; ensures no exception is thrown
    // and that the property is stored correctly. The logger uses getClass() so it logs
    // "TestModel", not "AbstractApiModel".
    TestModel model = new TestModel();
    model.handleUnknownProperty("unknownField", 42);

    assertEquals(42, model.additionalProperties().get("unknownField"));
  }

  @Test
  void handleUnknownProperty_withSpecialCharacters_shouldStoreAndEncodeForLogging() {
    TestModel model = new TestModel();
    model.handleUnknownProperty("key\twith\nnewline", "value1");
    model.handleUnknownProperty("<script>alert('xss')</script>", "value2");
    model.handleUnknownProperty("normal-key", "value3");

    Map<String, Object> props = model.getAdditionalProperties();
    assertEquals(3, props.size());
    assertEquals("value1", props.get("key\twith\nnewline"));
    assertEquals("value2", props.get("<script>alert('xss')</script>"));
    assertEquals("value3", props.get("normal-key"));
  }

  @Test
  void additionalProperties_shouldBeAccessibleToSubclasses() {
    TestModel model = new TestModel();
    model.handleUnknownProperty("a", 1);
    model.handleUnknownProperty("b", 2);

    Map<String, Object> internal = model.additionalProperties();
    assertEquals(2, internal.size());
    assertTrue(internal.containsKey("a"));
    assertTrue(internal.containsKey("b"));
  }
}

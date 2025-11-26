/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.event.handler.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Factory for creating properly configured ObjectMapper instances. Provides centralized
 * configuration for JSON serialization/deserialization across the application.
 */
public class ObjectMapperFactory {

  /**
   * Creates a new ObjectMapper with standard configuration.
   *
   * <p>Configuration includes:
   *
   * <ul>
   *   <li>JavaTimeModule for Java 8 date/time types (OffsetDateTime, LocalDateTime, etc.)
   * </ul>
   *
   * @return configured ObjectMapper instance
   */
  public static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }
}

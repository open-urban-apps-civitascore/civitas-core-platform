/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
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

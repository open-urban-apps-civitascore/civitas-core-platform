/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.util;

import com.civitas.configadapter.model.dataset.Dataset;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.Map;

/**
 * Shared JSON conversion helper for saga payloads. Provides a single, properly configured {@link
 * ObjectMapper} instance for all modules that depend on {@code config-adapter-api}.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * // Map → typed POJO (e.g. from SagaContext.triggerPayload())
 * Dataset ds = PayloadConverter.toDataset(triggerPayload);
 *
 * // raw JSON bytes → typed POJO (e.g. from Kafka consumer)
 * Dataset ds = PayloadConverter.toDataset(jsonBytes);
 *
 * // generic conversions
 * MyRecord r = PayloadConverter.fromValue(map, MyRecord.class);
 * byte[] json = PayloadConverter.writeValueAsBytes(result);
 * }</pre>
 */
public final class PayloadConverter {

  private static final ObjectMapper MAPPER = createObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private PayloadConverter() {}

  /** Returns the shared, pre-configured ObjectMapper. Prefer the typed helper methods. */
  public static ObjectMapper objectMapper() {
    return MAPPER;
  }

  // ─── Dataset helpers ────────────────────────────────────────────────────────

  /**
   * Converts a trigger payload map to a typed {@link Dataset} record.
   *
   * @param triggerPayload the map representation (e.g. from {@code SagaContext.triggerPayload()})
   * @return a Dataset instance; unknown fields are silently ignored
   */
  public static Dataset toDataset(Map<String, Object> triggerPayload) {
    return MAPPER.convertValue(triggerPayload, Dataset.class);
  }

  /**
   * Deserializes raw JSON bytes directly into a {@link Dataset}.
   *
   * @param json the JSON bytes (e.g. from a Kafka record value)
   * @return a Dataset instance
   * @throws IOException if the JSON is malformed or unreadable
   */
  public static Dataset toDataset(byte[] json) throws IOException {
    return MAPPER.readValue(json, Dataset.class);
  }

  // ─── Generic helpers ────────────────────────────────────────────────────────

  /**
   * Converts any object (typically a Map) to the target type via Jackson.
   *
   * @param fromValue the source object
   * @param targetType the target class
   * @return the converted instance
   */
  public static <T> T fromValue(Object fromValue, Class<T> targetType) {
    return MAPPER.convertValue(fromValue, targetType);
  }

  /**
   * Deserializes raw JSON bytes into the target type.
   *
   * @param json the JSON bytes
   * @param targetType the target class
   * @return the deserialized instance
   * @throws IOException if the JSON is malformed
   */
  public static <T> T readValue(byte[] json, Class<T> targetType) throws IOException {
    return MAPPER.readValue(json, targetType);
  }

  /**
   * Deserializes raw JSON bytes into a {@code Map<String, Object>}.
   *
   * @param json the JSON bytes
   * @return the map representation
   * @throws IOException if the JSON is malformed
   */
  public static Map<String, Object> readMap(byte[] json) throws IOException {
    return MAPPER.readValue(json, MAP_TYPE);
  }

  /**
   * Serializes the given object to JSON bytes.
   *
   * @param value the object to serialize
   * @return the JSON bytes
   * @throws JsonProcessingException if serialization fails
   */
  public static byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
    return MAPPER.writeValueAsBytes(value);
  }

  // ─── ObjectMapper factory ──────────────────────────────────────────────────

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }
}

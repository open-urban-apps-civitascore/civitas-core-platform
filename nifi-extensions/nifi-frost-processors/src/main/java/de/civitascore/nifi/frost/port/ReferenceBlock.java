/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/**
 * The canonical reference block a port reads from and writes into an entity.
 *
 * <p>{@code reference} is the upsert key of the entity and {@code thingReference} scopes it, so a
 * value can stay short and local: {@code temp}, not {@code A7-temp}. Both live in the bag the
 * entity has for free attributes. SensorThings gives that bag the name {@code properties} on every
 * entity but the Observation, which has {@code parameters} instead and rejects {@code properties}.
 */
public final class ReferenceBlock {

  /** The bag of every entity but the Observation. */
  public static final String PROPERTIES = "properties";

  /** The bag of the Observation. */
  public static final String PARAMETERS = "parameters";

  public static final String REFERENCE = "reference";
  public static final String THING_REFERENCE = "thingReference";
  public static final String DATASTREAM_REFERENCE = "datastreamReference";

  private ReferenceBlock() {}

  /** A reference of the entity, or empty when the bag or the field is absent or blank. */
  public static Optional<String> read(JsonNode entity, String bag, String field) {
    JsonNode values = entity == null ? null : entity.get(bag);
    JsonNode value = values == null ? null : values.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.asText());
  }

  /**
   * A reference the port cannot work without.
   *
   * @throws RecordRejectedException when it is absent. A record without its key must not enter the
   *     batch: the lookup would match nothing, the create would write an entity with an empty key,
   *     and every later keyless record would converge on that one entity.
   */
  public static String require(JsonNode entity, String bag, String field, String entityName) {
    return read(entity, bag, field)
        .orElseThrow(
            () ->
                new RecordRejectedException(
                    entityName, "the record carries no " + bag + "/" + field));
  }

  /** Writes a reference into the entity's bag, creating the bag when it is absent. */
  public static void write(ObjectNode entity, String bag, String field, String value) {
    JsonNode existing = entity.get(bag);
    ObjectNode values = existing instanceof ObjectNode object ? object : entity.putObject(bag);
    values.put(field, value);
  }
}

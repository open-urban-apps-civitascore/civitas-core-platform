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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeoJsonCopiesTest {

  private static final Path MODEL_FORGE_COPIES =
      Path.of("../../model-forge/model-forge-runtime/src/main/resources/geojson");

  /** A refresh of the Model Forge copies alone would let the two validations disagree. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "Point",
        "LineString",
        "Polygon",
        "MultiPoint",
        "MultiLineString",
        "MultiPolygon",
        "GeometryCollection"
      })
  void geometrySchema_isTheCopyThatModelForgeValidatesWith(String type) throws IOException {
    try (InputStream copy =
        GeoJsonSchemaLoader.class.getResourceAsStream("/geojson/" + type + ".json")) {
      assertNotNull(copy, type);
      assertArrayEquals(
          Files.readAllBytes(MODEL_FORGE_COPIES.resolve(type + ".json")),
          copy.readAllBytes(),
          type);
    }
  }
}

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

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModelForgeKeywordsTest {

  private static final Path MODEL_FORGE_FACTORY =
      Path.of(
          "../../model-forge/model-forge-runtime/src/main/java/de/civitascore/modelforge"
              + "/validation/CoreJsonSchemaFactory.java");

  private static final Pattern NON_VALIDATION_KEYWORD =
      Pattern.compile("new NonValidationKeyword\\(\"([^\"]+)\"\\)");

  /** A keyword list changed in Model Forge alone would let the two validations disagree. */
  @Test
  void nonValidatingKeywords_areThoseThatModelForgeValidatesWith() throws IOException {
    Set<String> modelForge =
        NON_VALIDATION_KEYWORD
            .matcher(Files.readString(MODEL_FORGE_FACTORY))
            .results()
            .map(match -> match.group(1))
            .collect(toSet());

    assertFalse(modelForge.isEmpty(), MODEL_FORGE_FACTORY::toString);
    assertEquals(modelForge, Set.copyOf(ModelForgeSchemaFactory.NON_VALIDATING_KEYWORDS));
  }
}

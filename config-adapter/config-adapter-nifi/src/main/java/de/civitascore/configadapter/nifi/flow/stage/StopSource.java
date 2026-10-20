/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A relationship on which a stage processor reports a fatal error. The flow builder routes it
 * through a stop stamp to the stop sink of the Pipeline, and the runtime monitor stops the Pipeline
 * when that sink logs.
 *
 * @param processor the processor of the stage
 * @param relationship the relationship that carries the fatal outcome
 * @param causeAttribute the FlowFile attribute that holds the cause; its text reaches the portal,
 *     so it must name no value of the data and no secret
 */
public record StopSource(Processor processor, String relationship, String causeAttribute) {

  private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

  public StopSource {
    Objects.requireNonNull(processor, "processor");
    Objects.requireNonNull(relationship, "relationship");
    // The stop stamp reads the attribute through Expression Language, so its name must not be able
    // to close or open an expression.
    if (causeAttribute == null || !ATTRIBUTE_NAME.matcher(causeAttribute).matches()) {
      throw new IllegalArgumentException("not a plain attribute name: " + causeAttribute);
    }
  }
}

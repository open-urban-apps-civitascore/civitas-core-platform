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

import java.util.List;
import java.util.Objects;

/**
 * The plan-time product of the STA envelope compilation, carried into the flow build: the generated
 * envelope template (JSON with NiFi-EL attribute references) and the flat attribute keys it
 * references.
 *
 * @param template the ReplaceText replacement value rebuilding one record into the STA envelope
 * @param flatKeys the flat record-field/attribute names, in mapping order — an ordered list because
 *     the EvaluateJsonPath capture properties are appended in exactly this order (the snapshot is
 *     byte-deterministic, so no map iteration may decide it)
 */
public record FrostEnvelopePlan(String template, List<String> flatKeys) {

  public FrostEnvelopePlan {
    Objects.requireNonNull(template, "template");
    flatKeys = List.copyOf(Objects.requireNonNull(flatKeys, "flatKeys"));
  }
}

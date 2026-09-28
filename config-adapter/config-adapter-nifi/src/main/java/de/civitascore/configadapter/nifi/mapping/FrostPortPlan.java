/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import java.util.List;
import java.util.Objects;

/**
 * The plan-time product of compiling a record mapping against a FROST sink: the flat capture keys
 * and the one body the sink renders from them.
 *
 * <p>This replaces the entity plan of the generated find-or-create graph. The graph needed a body,
 * a filter and an update body for each of its six entities, because it wrote them in six separate
 * requests. The processor writes the record in one request and decides find-or-create on the
 * server, so one body — the structure of the selected port — is all the flow carries.
 *
 * @param flatKeys the flat record-field names, in mapping order — an ordered list because the
 *     EvaluateJsonPath capture properties are appended in exactly this order, and the snapshot is
 *     byte-deterministic
 * @param body the rendered port structure, with {@code ${…}} references to the flat keys
 */
public record FrostPortPlan(List<String> flatKeys, String body) implements SinkPreRegionPlan {

  public FrostPortPlan {
    flatKeys = List.copyOf(Objects.requireNonNull(flatKeys, "flatKeys"));
    Objects.requireNonNull(body, "body");
    if (flatKeys.isEmpty()) {
      // A body without a capture renders constants only: every record would write the same entity.
      throw new IllegalArgumentException("a FROST port plan requires at least one mapped field");
    }
  }
}

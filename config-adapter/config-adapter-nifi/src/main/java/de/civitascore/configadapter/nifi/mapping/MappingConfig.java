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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A parsed CORE Mapping ({@code .../core/mapping/v1}) as carried inline on a {@code mapping} node
 * of the engine-neutral pipeline graph. Layout-only fields (positions) are intentionally dropped.
 *
 * @param source the versioned CORE URN of the source data structure (may be null)
 * @param target the versioned CORE URN of the target data structure (may be null)
 * @param fields the target-field rules, keyed by target JSONPath, insertion-ordered
 */
public record MappingConfig(String source, String target, Map<String, ValueNode> fields) {

  public MappingConfig {
    // copy into an unmodifiable, insertion-ordered map so field order (and immutability) hold —
    // Map.copyOf is not used because it does not preserve iteration order
    fields =
        Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
  }
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A ready-to-deploy NiFi flow for a single pipeline. The {@code snapshotJson} carries no secrets;
 * sensitive controller-service properties are pushed separately over REST after upload.
 *
 * @param processGroupName the stable process-group name (idempotency key)
 * @param snapshotJson the non-sensitive flow snapshot to upload
 * @param sensitivePropsByComponent sensitive properties to push post-upload, keyed by component
 *     name
 */
public record DeploymentPlan(
    String processGroupName,
    String snapshotJson,
    Map<String, Map<String, String>> sensitivePropsByComponent) {

  public DeploymentPlan {
    if (processGroupName == null || processGroupName.isBlank()) {
      throw new IllegalArgumentException("processGroupName must be non-blank");
    }
    if (snapshotJson == null || snapshotJson.isBlank()) {
      throw new IllegalArgumentException("snapshotJson must be non-blank");
    }
    // deep-immutable copy so the plan cannot be mutated after construction
    sensitivePropsByComponent =
        Objects.requireNonNull(sensitivePropsByComponent, "sensitivePropsByComponent")
            .entrySet()
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
  }
}

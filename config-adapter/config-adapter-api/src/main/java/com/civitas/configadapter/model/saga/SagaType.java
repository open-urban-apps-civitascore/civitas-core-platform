/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.saga;

/** Saga workflow types. Each type defines a distinct step ordering and compensation strategy. */
public enum SagaType {

  /** Dataset provisioning: FROST → APISIX → Redpanda (conditional). */
  DATASET_CREATE("dataset-create"),

  /**
   * Dataset update: FROST → APISIX → Redpanda (conditional). Compensation restores previous state.
   */
  DATASET_UPDATE("dataset-update"),

  /**
   * Dataset deletion: Redpanda (conditional) → APISIX → FROST (reverse order). Best-effort
   * execution, no compensation.
   */
  DATASET_DELETE("dataset-delete");

  private final String key;

  SagaType(String key) {
    this.key = key;
  }

  /** Returns the string key used in topic names and serialization. */
  public String getKey() {
    return key;
  }
}

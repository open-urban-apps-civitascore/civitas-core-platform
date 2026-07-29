/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.saga;

/** Saga workflow types. Each type defines a distinct step ordering and compensation strategy. */
public enum SagaType {

  /** Dataset provisioning: FROST → APISIX → NiFi (conditional). */
  DATASET_CREATE("dataset-create"),

  /** Dataset update: FROST → APISIX → NiFi (conditional). Compensation restores previous state. */
  DATASET_UPDATE("dataset-update"),

  /**
   * Dataset deletion: NiFi (conditional) → APISIX → FROST (reverse order). Best-effort execution,
   * no compensation.
   */
  DATASET_DELETE("dataset-delete"),

  /**
   * Dataset unrelease: NiFi (conditional) → APISIX (reverse order). Tears down only the ingest and
   * consumer access (pipeline + route/upstream); the data-holding sink (PostGIS table, FROST
   * project) is deliberately kept so a later re-release reuses it. Best-effort execution, no
   * compensation.
   */
  DATASET_UNRELEASE("dataset-unrelease");

  private final String key;

  SagaType(String key) {
    this.key = key;
  }

  /** Returns the string key used in topic names and serialization. */
  public String getKey() {
    return key;
  }
}

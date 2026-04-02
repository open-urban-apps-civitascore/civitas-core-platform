/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

/** Redpanda Connect input block types rendered into pipeline definitions. */
enum RedpandaInputType {
  MQTT("mqtt"),
  SQL_RAW("sql_raw"),
  SQL_SELECT("sql_select");

  final String key;

  RedpandaInputType(String key) {
    this.key = key;
  }
}

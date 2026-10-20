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

/** The {@code Record Schema} property holds no schema that the processor can validate with. */
final class InvalidRecordSchemaException extends Exception {

  private static final long serialVersionUID = 1L;

  InvalidRecordSchemaException(String explanation, Throwable cause) {
    super(explanation, cause);
  }
}

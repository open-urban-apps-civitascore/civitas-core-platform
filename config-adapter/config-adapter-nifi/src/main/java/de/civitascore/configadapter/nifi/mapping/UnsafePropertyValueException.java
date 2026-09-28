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

/**
 * A value bound for a NiFi property holds an Expression Language or parameter reference. The
 * message names the property only, since the value may be a secret.
 */
public class UnsafePropertyValueException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  public UnsafePropertyValueException(String property) {
    super(
        "NiFi property '"
            + property
            + "' must not contain an expression or parameter reference ('${' or '#{')");
  }
}

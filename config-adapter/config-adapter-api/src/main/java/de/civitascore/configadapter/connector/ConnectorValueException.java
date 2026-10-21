/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connector;

/**
 * A connector value that the platform cannot use. The message names the value and is safe to show
 * the user: it never carries a secret.
 */
public final class ConnectorValueException extends Exception {

  private static final long serialVersionUID = 1L;

  public ConnectorValueException(String message) {
    super(message);
  }
}

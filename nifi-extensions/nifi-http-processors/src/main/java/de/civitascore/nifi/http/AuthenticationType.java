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

import org.apache.nifi.context.PropertyContext;

/** The values of the {@code Authentication Type} property. */
enum AuthenticationType {
  NONE,
  BASIC;

  /** Reads the credentials of this type from the properties. Call it for each request. */
  Authentication authentication(PropertyContext context) {
    return switch (this) {
      case NONE -> new NoAuthentication();
      case BASIC ->
          new BasicAuthentication(
              context.getProperty(GetHttpApiCall.BASIC_AUTH_USERNAME).getValue(),
              context.getProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD).getValue());
    };
  }
}

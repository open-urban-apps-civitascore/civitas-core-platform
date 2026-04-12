/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.keycloak;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.ConfigEvent;

/**
 * Strategy interface for Keycloak resource-type handlers. One implementation per resource type
 * (REALM, CLIENT, USER, ROLE, GROUP).
 */
interface KeycloakResourceHandler {

  void create(String realm, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException;

  void update(String realm, String resourceId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException;

  void delete(String realm, String resourceId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException;
}

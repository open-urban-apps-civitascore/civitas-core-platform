/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the "config" object within the payload.
 *
 * <p>The value field contains typed configuration data specific to the target adapter:
 *
 * <ul>
 *   <li>{@link IdmConfigValue} - For identity management resources (Keycloak users, realms,
 *       clients)
 *   <li>{@link ApisixConfigValue} - For APISIX API Gateway resources (upstreams, routes)
 *   <li>{@link GenericConfigValue} - Generic fallback for other adapter types
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Config(@JsonProperty("path") String path, @JsonProperty("value") ConfigValue value) {}

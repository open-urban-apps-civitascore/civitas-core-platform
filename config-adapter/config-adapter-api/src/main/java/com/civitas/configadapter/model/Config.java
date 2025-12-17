/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
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

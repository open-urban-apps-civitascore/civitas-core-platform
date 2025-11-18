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

/** Represents the "payload" object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Payload(
    @JsonProperty("targetComponent") String targetComponent,
    @JsonProperty("targetResource") String targetResource,
    @JsonProperty("operation") String operation,
    @JsonProperty("config") Config config) {}

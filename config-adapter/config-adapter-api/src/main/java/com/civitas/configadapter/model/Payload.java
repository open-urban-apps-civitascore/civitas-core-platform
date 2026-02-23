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

/** Represents the "payload" object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Payload(
    @JsonProperty("targetComponent") String targetComponent,
    @JsonProperty("targetResource") String targetResource,
    @JsonProperty("operation") Operation operation,
    @JsonProperty("config") Config config) {}

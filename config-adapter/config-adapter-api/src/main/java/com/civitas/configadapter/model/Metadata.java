/**
 * Copyright (c) 2012 - 2025 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the "metadata" object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Metadata(
    @JsonProperty("messageId") String messageId,
    @JsonProperty("timestamp") String timestamp, // Can also be parsed as OffsetDateTime
    @JsonProperty("source") String source,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("configVersion") String configVersion,
    @JsonProperty("resultTopic") String resultTopic
) {}

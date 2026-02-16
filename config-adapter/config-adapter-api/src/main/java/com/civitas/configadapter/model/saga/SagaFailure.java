/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.saga;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Captures where and why a saga failed. Immutable snapshot of the failure point.
 *
 * @param stepId the step that failed
 * @param adapter the adapter that reported the failure
 * @param error human-readable error message
 * @param errorCode machine-readable error code (optional)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaFailure(String stepId, String adapter, String error, String errorCode) {}

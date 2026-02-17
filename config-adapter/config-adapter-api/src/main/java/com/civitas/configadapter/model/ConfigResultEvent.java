/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Represents the result of processing a configuration event. This event is published back to the
 * result topic to indicate success or failure of the configuration operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigResultEvent(
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("originalMessageId") String originalMessageId,
    @JsonProperty("status") Status status,
    @JsonProperty("message") String message,
    @JsonProperty("resourceId") String resourceId,
    @JsonProperty("operation") Operation operation,
    @JsonProperty("targetResource") String targetResource,
    @JsonProperty("errorCode") String errorCode,
    @JsonProperty("timestamp") OffsetDateTime timestamp,
    @JsonProperty("source") String source,
    @JsonProperty("resultType") String resultType) {

  /** Status of the configuration operation. */
  public enum Status {
    SUCCESS,
    FAILURE
  }

  /**
   * Creates a success result event.
   *
   * @param correlationId the correlation ID from the original event
   * @param originalMessageId the message ID from the original event
   * @param message success message
   * @param resourceId the ID of the created/updated/deleted resource
   * @param operation the operation that was performed
   * @param targetResource the target resource path
   * @param source the source identifier (e.g., "civitas.config-adapter.keycloak")
   * @param resultType the CloudEvent type for this result (e.g.,
   *     "de.civitascore.idm.processing.result")
   * @return a success ConfigResultEvent
   */
  public static ConfigResultEvent success(
      String correlationId,
      String originalMessageId,
      String message,
      String resourceId,
      Operation operation,
      String targetResource,
      String source,
      String resultType) {
    return new ConfigResultEvent(
        correlationId,
        originalMessageId,
        Status.SUCCESS,
        message,
        resourceId,
        operation,
        targetResource,
        null,
        OffsetDateTime.now(),
        source,
        resultType);
  }

  /**
   * Creates a failure result event.
   *
   * @param correlationId the correlation ID from the original event
   * @param originalMessageId the message ID from the original event
   * @param errorCode error code identifying the failure
   * @param errorMessage detailed error message
   * @param operation the operation that was attempted
   * @param targetResource the target resource path
   * @param source the source identifier (e.g., "civitas.config-adapter.keycloak")
   * @param resultType the CloudEvent type for this result (e.g.,
   *     "de.civitascore.idm.processing.result")
   * @return a failure ConfigResultEvent
   */
  public static ConfigResultEvent failure(
      String correlationId,
      String originalMessageId,
      String errorCode,
      String errorMessage,
      Operation operation,
      String targetResource,
      String source,
      String resultType) {
    return new ConfigResultEvent(
        correlationId,
        originalMessageId,
        Status.FAILURE,
        errorMessage,
        null,
        operation,
        targetResource,
        errorCode,
        OffsetDateTime.now(),
        source,
        resultType);
  }
}

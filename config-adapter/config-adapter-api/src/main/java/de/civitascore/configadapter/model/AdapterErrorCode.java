/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model;

/**
 * Centralized error codes for adapter operations. Error codes are categorized:
 *
 * <ul>
 *   <li>1xxx: Validation/Fatal errors - sent directly to DLQ
 *   <li>2xxx: Connectivity/Retryable errors - blocking retry loop
 *   <li>3xxx: Adapter-specific errors (Keycloak, APISIX, etc.)
 *   <li>9xxx: Unknown/unexpected errors
 * </ul>
 *
 * <p>Each error code has:
 *
 * <ul>
 *   <li>Numeric code for programmatic handling
 *   <li>Retryable flag indicating if the error is transient
 *   <li>Internal log template with placeholders for detailed logging
 *   <li>External user message safe for external consumers (no PII, no stack traces)
 * </ul>
 */
public enum AdapterErrorCode {

  // 1xxx: Validation/Fatal errors -> DLQ immediately
  INVALID_PAYLOAD(1001, false, "Invalid payload: %s", "Validation failed"),
  RESOURCE_NOT_FOUND(1002, false, "Resource not found: %s", "Resource not found"),
  MISSING_CONFIG(1003, false, "Missing config: %s", "Configuration error"),
  UNSUPPORTED_OPERATION(1004, false, "Operation %s not supported", "Operation not supported"),
  INVALID_RESOURCE_TYPE(1005, false, "Invalid resource type: %s", "Invalid resource type"),

  // 2xxx: Connectivity/Retryable errors -> Blocking retry loop
  CONNECTION_TIMEOUT(2001, true, "Connection timeout to %s", "Service temporarily unavailable"),
  SERVICE_UNAVAILABLE(
      2002, true, "Service %s unavailable: HTTP %d", "Service temporarily unavailable"),
  NETWORK_ERROR(2003, true, "Network error to %s: %s", "Service temporarily unavailable"),
  RATE_LIMITED(2004, true, "Rate limited by %s", "Service temporarily unavailable"),
  PUBLISH_ERROR(2005, true, "Failed to publish event to %s: %s", "Message delivery failed"),
  PUBLISH_TIMEOUT(2006, true, "Publish timeout to %s after %d ms", "Message delivery timeout"),

  // 3xxx: Adapter-specific errors
  KEYCLOAK_ERROR(3001, false, "Keycloak error: %s", "Identity provider error"),
  KEYCLOAK_CONFLICT(3002, false, "Keycloak conflict: %s", "Resource already exists"),
  KEYCLOAK_REALM_ERROR(3003, false, "Keycloak realm error: %s", "Realm operation failed"),
  KEYCLOAK_USER_ERROR(3004, false, "Keycloak user error: %s", "User operation failed"),
  KEYCLOAK_CLIENT_ERROR(3005, false, "Keycloak client error: %s", "Client operation failed"),
  KEYCLOAK_ROLE_ERROR(3006, false, "Keycloak role error: %s", "Role operation failed"),
  KEYCLOAK_GROUP_ERROR(3007, false, "Keycloak group error: %s", "Group operation failed"),

  APISIX_ERROR(3101, false, "APISIX error: %s", "Gateway error"),
  APISIX_ROUTE_ERROR(3102, false, "APISIX route error: %s", "Route operation failed"),
  APISIX_UPSTREAM_ERROR(3103, false, "APISIX upstream error: %s", "Upstream operation failed"),

  FROST_ENTITY_ERROR(3201, false, "FROST entity error: %s", "Entity operation failed"),

  GEOSERVER_ERROR(3401, true, "GeoServer error: %s", "Geo service error"),
  GEOSERVER_RESOURCE_ERROR(
      3402, false, "GeoServer resource error: %s", "Geo resource operation failed"),

  NIFI_ERROR(3501, true, "NiFi error: %s", "Pipeline service error"),
  NIFI_FLOW_ERROR(3502, false, "NiFi flow error: %s", "Pipeline operation failed"),
  NIFI_TEMPLATE_ERROR(
      3503, false, "No curated NiFi template for %s", "Unsupported pipeline combination"),
  NIFI_MAPPING_ERROR(3504, false, "NiFi mapping compile error: %s", "Pipeline mapping invalid"),

  // 9xxx: Unknown/unexpected errors
  UNKNOWN_ERROR(9001, false, "Unexpected error: %s", "Internal error"),
  SERIALIZATION_ERROR(9002, false, "Serialization error: %s", "Data processing error"),
  DESERIALIZATION_ERROR(9003, false, "Deserialization error: %s", "Data processing error"),
  CONFIGURATION_ERROR(9004, false, "Configuration error: %s", "Configuration error"),
  ;

  private final int code;
  private final boolean retryable;
  private final String internalLogTemplate;
  private final String externalUserMessage;

  AdapterErrorCode(
      int code, boolean retryable, String internalLogTemplate, String externalUserMessage) {
    this.code = code;
    this.retryable = retryable;
    this.internalLogTemplate = internalLogTemplate;
    this.externalUserMessage = externalUserMessage;
  }

  /**
   * Returns the numeric error code.
   *
   * @return the error code
   */
  public int getCode() {
    return code;
  }

  /**
   * Returns whether this error type is retryable (transient).
   *
   * @return true if the error is retryable
   */
  public boolean isRetryable() {
    return retryable;
  }

  /**
   * Returns the internal log template for detailed logging. Contains placeholders for
   * String.format().
   *
   * @return the internal log template
   */
  public String getInternalLogTemplate() {
    return internalLogTemplate;
  }

  /**
   * Returns the safe external user message without PII or stack traces.
   *
   * @return the external user message
   */
  public String getExternalUserMessage() {
    return externalUserMessage;
  }

  /**
   * Formats the internal log message with the provided arguments.
   *
   * @param args the arguments to format into the template
   * @return the formatted internal message
   */
  public String formatInternalMessage(Object... args) {
    if (args == null || args.length == 0) {
      return internalLogTemplate;
    }
    try {
      return String.format(internalLogTemplate, args);
    } catch (Exception e) {
      // Fallback if formatting fails
      return internalLogTemplate + " [args: " + java.util.Arrays.toString(args) + "]";
    }
  }

  /**
   * Returns the full error identifier in format "ERROR_CODE(numeric_code)".
   *
   * @return the full error identifier
   */
  public String getFullIdentifier() {
    return name() + "(" + code + ")";
  }
}

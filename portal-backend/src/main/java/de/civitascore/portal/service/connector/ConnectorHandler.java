package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines all connector-type-specific behavior in one place. Implement this interface (via {@link
 * AbstractConnectorHandler}) and annotate with {@code @Component} to register a new connector type
 * — no other code changes needed.
 */
public interface ConnectorHandler {

  String MASKED_VALUE = "********";

  /**
   * Returns the connector type this handler supports.
   *
   * @return the supported {@link ConnectorType}
   */
  ConnectorType getSupportedType();

  /** Field names that contain sensitive values (passwords, secrets) for encryption and masking. */
  Set<String> getSensitiveFields();

  /**
   * Validates the configuration map against the connector's schema using the specified validation
   * groups.
   *
   * @param configuration the configuration map to validate
   * @param groups optional validation groups (defaults to {@link
   *     jakarta.validation.groups.Default})
   * @return a list of validation error messages, empty if valid
   */
  List<String> validate(Map<String, Object> configuration, Class<?>... groups);

  /**
   * Normalize and validate in one pass: deserializes into the typed POJO, runs Bean Validation,
   * throws {@link de.civitascore.portal.util.InvalidInputException} on any error, then returns the
   * canonical map form.
   */
  Map<String, Object> normalizeAndValidate(Map<String, Object> rawConfig, Class<?>... groups);

  /**
   * Encrypts sensitive field values in the configuration map.
   *
   * @param configuration the configuration map with plaintext sensitive values
   * @return a new map with sensitive fields encrypted
   */
  Map<String, Object> encryptSensitiveFields(Map<String, Object> configuration);

  /**
   * Replaces sensitive field values with a masked placeholder for safe API output.
   *
   * @param configuration the configuration map
   * @return a new map with sensitive fields masked
   */
  Map<String, Object> maskSensitiveFields(Map<String, Object> configuration);

  /**
   * Prepares a configuration map for API output by masking sensitive fields. Default implementation
   * delegates to {@link #maskSensitiveFields(Map)}.
   *
   * @param entityConfig the persisted configuration map
   * @return the output-safe configuration map
   */
  default Map<String, Object> prepareForOutput(Map<String, Object> entityConfig) {
    return maskSensitiveFields(entityConfig);
  }
}

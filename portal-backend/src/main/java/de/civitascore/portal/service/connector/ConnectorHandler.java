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

  ConnectorType getSupportedType();

  /** Field names that contain sensitive values (passwords, secrets) for encryption and masking. */
  Set<String> getSensitiveFields();

  List<String> validate(Map<String, Object> configuration, Class<?>... groups);

  /**
   * Normalize a raw input map into the canonical entity form by round-tripping through the
   * configuration POJO. This validates and cleans URLs/DSN via the POJO setters.
   */
  Map<String, Object> normalizeToEntity(Map<String, Object> rawConfig);

  /**
   * Normalize and validate in one pass: deserializes into the typed POJO, runs Bean Validation,
   * throws {@link de.civitascore.portal.util.InvalidInputException} on any error, then returns the
   * canonical map form.
   */
  Map<String, Object> normalizeAndValidate(Map<String, Object> rawConfig, Class<?>... groups);

  Map<String, Object> encryptSensitiveFields(Map<String, Object> configuration);

  Map<String, Object> maskSensitiveFields(Map<String, Object> configuration);

  default Map<String, Object> prepareForOutput(Map<String, Object> entityConfig) {
    return maskSensitiveFields(entityConfig);
  }
}

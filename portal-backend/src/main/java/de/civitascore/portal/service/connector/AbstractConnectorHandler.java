package de.civitascore.portal.service.connector;

import static de.civitascore.portal.configuration.EncryptionConfig.ENC_PREFIX;
import static de.civitascore.portal.configuration.EncryptionConfig.ENC_SUFFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.util.InvalidInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractConnectorHandler implements ConnectorHandler {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private final ObjectMapper objectMapper;
  private final TextEncryptor textEncryptor;
  private final ConnectorType supportedType;
  private final Class<? extends ConnectorConfiguration> configurationClass;
  private final Set<String> sensitiveFields;

  // --- Validation via Jakarta Bean Validation ---

  @Override
  public List<String> validate(Map<String, Object> configuration, Class<?>... groups) {
    ConnectorConfiguration pojo;
    try {
      pojo = objectMapper.convertValue(configuration, configurationClass);
    } catch (IllegalArgumentException e) {
      String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      return List.of("Invalid configuration format: " + detail);
    }
    Class<?>[] effectiveGroups = groups.length > 0 ? groups : new Class<?>[] {Default.class};
    Set<? extends ConstraintViolation<?>> violations = VALIDATOR.validate(pojo, effectiveGroups);
    return violations.stream().map(ConstraintViolation::getMessage).sorted().toList();
  }

  // --- Normalization ---

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> normalizeToEntity(Map<String, Object> rawConfig) {
    ConnectorConfiguration pojo;
    try {
      pojo = objectMapper.convertValue(rawConfig, configurationClass);
    } catch (IllegalArgumentException e) {
      String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      throw new InvalidInputException("configuration", (String) null, detail);
    }
    return objectMapper.convertValue(pojo, Map.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> normalizeAndValidate(Map<String, Object> rawConfig, Class<?>... groups) {
    ConnectorConfiguration pojo;
    try {
      pojo = objectMapper.convertValue(rawConfig, configurationClass);
    } catch (IllegalArgumentException e) {
      String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      throw new InvalidInputException("configuration", (String) null, detail);
    }
    Class<?>[] effectiveGroups = groups.length > 0 ? groups : new Class<?>[] {Default.class};
    Set<? extends ConstraintViolation<?>> violations = VALIDATOR.validate(pojo, effectiveGroups);
    if (!violations.isEmpty()) {
      List<String> errors =
          violations.stream().map(ConstraintViolation::getMessage).sorted().toList();
      throw new InvalidInputException(
          "configuration", (String) null, "Invalid configuration: " + String.join("; ", errors));
    }
    return objectMapper.convertValue(pojo, Map.class);
  }

  // --- Encryption / masking ---

  @Override
  public Map<String, Object> encryptSensitiveFields(Map<String, Object> configuration) {
    Map<String, Object> result = new HashMap<>(configuration);
    for (String field : sensitiveFields) {
      Object value = result.get(field);
      if (value instanceof String s && !s.isEmpty() && !isEncrypted(s)) {
        result.put(field, textEncryptor.encrypt(s));
      }
    }
    return result;
  }

  private static boolean isEncrypted(String value) {
    return value.startsWith(ENC_PREFIX)
        && value.endsWith(ENC_SUFFIX)
        && value.length() > ENC_PREFIX.length() + ENC_SUFFIX.length();
  }

  @Override
  public Map<String, Object> maskSensitiveFields(Map<String, Object> configuration) {
    Map<String, Object> result = new HashMap<>(configuration);
    for (String field : sensitiveFields) {
      if (result.get(field) != null) {
        result.put(field, MASKED_VALUE);
      }
    }
    return result;
  }
}

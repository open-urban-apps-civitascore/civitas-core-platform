package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.portal.model.connector.OnPublish;
import jakarta.validation.groups.Default;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@DisplayName("MqttConnectorHandler Tests")
class MqttConnectorHandlerTest {

  private static final byte[] TEST_KEY =
      CredentialDecryptor.hexStringToBytes(
          "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
  private static final byte[] TEST_SALT =
      CredentialDecryptor.hexStringToBytes("00112233445566778899aabbccddeeff");

  private MqttConnectorHandler handler;
  private TextEncryptor textEncryptor;

  @BeforeEach
  void setUp() {
    textEncryptor =
        new TextEncryptor() {
          @Override
          public String encrypt(String text) {
            try {
              return CredentialEncryptor.encrypt(text, TEST_KEY, TEST_SALT);
            } catch (GeneralSecurityException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public String decrypt(String encryptedText) {
            throw new UnsupportedOperationException();
          }
        };
    handler = new MqttConnectorHandler(new ObjectMapper(), textEncryptor);
  }

  @Nested
  @DisplayName("Validation")
  class ValidationTests {

    @Test
    @DisplayName("Should pass full validation with valid config")
    void shouldPassWithValidConfig() {
      Map<String, Object> config = new HashMap<>();
      config.put("urls", List.of("tcp://broker:1883"));
      config.put("topics", List.of("sensor/#"));
      config.put("qos", 1);

      assertThat(handler.validate(config, Default.class, OnPublish.class)).isEmpty();
    }

    @Test
    @DisplayName("Should pass type-only validation even without required fields")
    void shouldPassTypeOnlyWithoutRequiredFields() {
      Map<String, Object> config = new HashMap<>();

      assertThat(handler.validate(config, Default.class)).isEmpty();
    }

    @Test
    @DisplayName("Should fail on publish when urls is missing")
    void shouldFailWhenUrlsMissing() {
      Map<String, Object> config = new HashMap<>();
      config.put("topics", List.of("sensor/#"));
      config.put("qos", 1);

      List<String> errors = handler.validate(config, Default.class, OnPublish.class);
      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("urls");
    }

    @Test
    @DisplayName("Should fail type validation when qos is out of range")
    void shouldFailWhenQosOutOfRange() {
      Map<String, Object> config = new HashMap<>();
      config.put("urls", List.of("tcp://broker:1883"));
      config.put("topics", List.of("sensor/#"));
      config.put("qos", 5);

      List<String> errors = handler.validate(config, Default.class);
      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("qos");
    }

    @Test
    @DisplayName("Should report all publish validation errors at once")
    void shouldReportAllErrors() {
      Map<String, Object> config = new HashMap<>();

      List<String> errors = handler.validate(config, Default.class, OnPublish.class);
      assertThat(errors).hasSize(3);
      assertThat(errors).anyMatch(e -> e.contains("urls"));
      assertThat(errors).anyMatch(e -> e.contains("topics"));
      assertThat(errors).anyMatch(e -> e.contains("qos"));
    }
  }

  @Nested
  @DisplayName("normalizeToEntity")
  class NormalizeTests {

    @Test
    @DisplayName("Should normalize MQTT config and strip credentials from URLs")
    void shouldNormalizeAndStripCredentials() {
      Map<String, Object> map = new HashMap<>();
      map.put("urls", List.of("tcp://admin:pass@broker:1883"));
      map.put("topics", List.of("foo/#"));
      map.put("qos", 1);
      map.put("user", "mqttuser");
      map.put("password", "mqttpass");

      Map<String, Object> result = handler.normalizeToEntity(map);

      assertThat(result.get("urls")).isEqualTo(List.of("tcp://broker:1883"));
      assertThat(result.get("user")).isEqualTo("mqttuser");
      assertThat(result.get("password")).isEqualTo("mqttpass");
      assertThat(result.get("topics")).isEqualTo(List.of("foo/#"));
    }

    @Test
    @DisplayName("Should pass through without credentials unchanged")
    void shouldPassThroughWithoutCredentials() {
      Map<String, Object> map = new HashMap<>();
      map.put("urls", List.of("tcp://broker:1883"));
      map.put("topics", List.of("foo/#"));
      map.put("qos", 1);

      Map<String, Object> result = handler.normalizeToEntity(map);

      assertThat(result.get("urls")).isEqualTo(List.of("tcp://broker:1883"));
      assertThat(result.get("user")).isNull();
      assertThat(result.get("password")).isNull();
    }
  }

  @Nested
  @DisplayName("reconstructForOutput")
  class ReconstructTests {

    @Test
    @DisplayName("Should pass through config as-is (no credential embedding)")
    void shouldPassThroughConfig() {
      Map<String, Object> config = new HashMap<>();
      config.put("urls", List.of("tcp://broker1:1883", "tcp://broker2:1883"));
      config.put("user", "admin");
      config.put("password", ConnectorHandler.MASKED_VALUE);
      config.put("topics", List.of("sensor/#"));

      Map<String, Object> result = handler.reconstructForOutput(config);

      assertThat(result.get("urls")).isEqualTo(List.of("tcp://broker1:1883", "tcp://broker2:1883"));
      assertThat(result.get("user")).isEqualTo("admin");
      assertThat(result.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
      assertThat(result.get("topics")).isEqualTo(List.of("sensor/#"));
    }
  }

  @Nested
  @DisplayName("Encryption and Masking")
  class EncryptionTests {

    @Test
    @DisplayName("Should encrypt password field")
    void shouldEncryptPassword() {
      Map<String, Object> config = new HashMap<>();
      config.put("password", "my-secret-password");
      config.put("urls", List.of("tcp://broker:1883"));

      Map<String, Object> result = handler.encryptSensitiveFields(config);

      assertThat(result.get("urls")).isEqualTo(List.of("tcp://broker:1883"));
      assertThat(result.get("password")).isNotEqualTo("my-secret-password");
      assertThat(decrypt((String) result.get("password"))).isEqualTo("my-secret-password");
    }

    @Test
    @DisplayName("Should mask password field")
    void shouldMaskPassword() {
      String encrypted = textEncryptor.encrypt("secret");
      Map<String, Object> config = new HashMap<>();
      config.put("password", encrypted);
      config.put("client_id", "my-client");

      Map<String, Object> result = handler.maskSensitiveFields(config);

      assertThat(result.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
      assertThat(result.get("client_id")).isEqualTo("my-client");
    }
  }

  private static String decrypt(String encrypted) {
    try {
      return CredentialDecryptor.decrypt(encrypted, TEST_KEY, TEST_SALT);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}

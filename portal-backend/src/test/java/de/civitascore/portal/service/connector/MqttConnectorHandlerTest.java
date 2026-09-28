package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.portal.configuration.EncryptionConfig;
import de.civitascore.portal.model.connector.OnRelease;
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
import tools.jackson.databind.json.JsonMapper;

@DisplayName("MqttConnectorHandler Tests")
class MqttConnectorHandlerTest {

  private static final byte[] TEST_STRETCHED_KEY;
  private static final String TEST_CONTEXT = EncryptionConfig.CREDENTIAL_CONTEXT;

  static {
    try {
      byte[] raw =
          CryptoKeyLoader.hexStringToBytes(
              "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
      TEST_STRETCHED_KEY = CryptoKeyLoader.stretchMasterKey(raw);
    } catch (GeneralSecurityException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private MqttConnectorHandler handler;
  private TextEncryptor textEncryptor;

  @BeforeEach
  void setUp() {
    textEncryptor =
        new TextEncryptor() {
          @Override
          public String encrypt(String text) {
            try {
              return CredentialEncryptor.encrypt(text, TEST_STRETCHED_KEY, TEST_CONTEXT);
            } catch (GeneralSecurityException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public String decrypt(String encryptedText) {
            throw new UnsupportedOperationException();
          }
        };
    handler = new MqttConnectorHandler(new JsonMapper(), textEncryptor);
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
      config.put("protocol_version", "3");

      assertThat(handler.validate(config, Default.class, OnRelease.class)).isEmpty();
    }

    @Test
    @DisplayName("Should pass type-only validation even without release-only required fields")
    void shouldPassTypeOnlyWithoutRequiredFields() {
      Map<String, Object> config = new HashMap<>();
      config.put("protocol_version", "3");

      assertThat(handler.validate(config, Default.class)).isEmpty();
    }

    @Test
    @DisplayName("Should fail on release when urls is missing")
    void shouldFailWhenUrlsMissing() {
      Map<String, Object> config = new HashMap<>();
      config.put("topics", List.of("sensor/#"));
      config.put("qos", 1);
      config.put("protocol_version", "3");

      List<String> errors = handler.validate(config, Default.class, OnRelease.class);
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
      config.put("protocol_version", "3");

      List<String> errors = handler.validate(config, Default.class);
      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("qos");
    }

    @Test
    @DisplayName("Should reject unsupported MQTT protocol versions")
    void shouldRejectUnsupportedProtocolVersion() {
      Map<String, Object> config = new HashMap<>();
      config.put("protocol_version", "4");

      List<String> errors = handler.validate(config, Default.class);

      assertThat(errors).containsExactly("'protocol_version' must be 3 or 5");
    }

    @Test
    @DisplayName("Should reject an explicit null protocol_version even on a draft")
    void shouldRejectExplicitNullProtocolVersionOnDraft() {
      Map<String, Object> config = new HashMap<>();
      config.put("protocol_version", null);

      List<String> errors = handler.validate(config, Default.class);

      assertThat(errors).containsExactly("'protocol_version' is required");
    }

    @Test
    @DisplayName("Should default protocol_version to 3 when the key is simply absent")
    void shouldDefaultProtocolVersionWhenAbsent() {
      Map<String, Object> config = new HashMap<>();
      config.put("urls", List.of("tcp://broker:1883"));

      assertThat(handler.validate(config, Default.class)).isEmpty();
    }

    @Test
    @DisplayName("Should report all release validation errors at once")
    void shouldReportAllErrors() {
      Map<String, Object> config = new HashMap<>();

      List<String> errors = handler.validate(config, Default.class, OnRelease.class);
      assertThat(errors).hasSize(3);
      assertThat(errors).anyMatch(e -> e.contains("urls"));
      assertThat(errors).anyMatch(e -> e.contains("topics"));
      assertThat(errors).anyMatch(e -> e.contains("qos"));
    }
  }

  @Nested
  @DisplayName("Single-topic constraint")
  class SingleTopicTests {

    @Test
    @DisplayName("Should accept a single wildcard topic filter on release")
    void shouldAcceptSingleWildcardTopic() {
      assertThat(
              handler.validate(config(List.of("sensors/+/temp")), Default.class, OnRelease.class))
          .isEmpty();
    }

    @Test
    @DisplayName("Should reject more than one topic on release")
    void shouldRejectMultipleTopics() {
      List<String> errors =
          handler.validate(config(List.of("foo/bar", "baz/qux")), Default.class, OnRelease.class);
      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("exactly one topic filter");
    }

    @Test
    @DisplayName("Should reject an empty topic list on release via @NotEmpty")
    void shouldRejectEmptyTopicList() {
      List<String> errors = handler.validate(config(List.of()), Default.class, OnRelease.class);
      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("required and must be a non-empty list");
    }

    @Test
    @DisplayName("Should not enforce the single-topic constraint on a draft (type-only validation)")
    void shouldNotEnforceOnDraft() {
      assertThat(handler.validate(config(List.of("foo/bar", "baz/qux")), Default.class)).isEmpty();
    }

    private Map<String, Object> config(List<String> topics) {
      Map<String, Object> config = new HashMap<>();
      config.put("urls", List.of("tcp://broker:1883"));
      config.put("topics", topics);
      config.put("qos", 1);
      config.put("protocol_version", "3");
      return config;
    }
  }

  @Nested
  @DisplayName("normalizeAndValidate")
  class NormalizeTests {

    @Test
    @DisplayName("Should normalize MQTT config and strip credentials from URLs")
    void shouldNormalizeAndStripCredentials() {
      Map<String, Object> map = new HashMap<>();
      map.put("urls", List.of("tcp://admin:pass@broker:1883"));
      map.put("topics", List.of("foo/#"));
      map.put("qos", 1);
      map.put("protocol_version", "3");
      map.put("user", "mqttuser");
      map.put("password", "mqttpass");

      Map<String, Object> result = handler.normalizeAndValidate(map);

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
      map.put("protocol_version", "3");

      Map<String, Object> result = handler.normalizeAndValidate(map);

      assertThat(result.get("urls")).isEqualTo(List.of("tcp://broker:1883"));
      assertThat(result.get("user")).isNull();
      assertThat(result.get("password")).isNull();
    }

    @Test
    @DisplayName("Should default legacy MQTT configurations to protocol version 3")
    void shouldDefaultLegacyProtocolVersion() {
      Map<String, Object> result = handler.normalizeAndValidate(Map.of());

      assertThat(result.get("protocol_version")).isEqualTo("3");
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

  @Nested
  @DisplayName("prepareForOutput")
  class PrepareForOutputTests {

    @Test
    @DisplayName("Should leave protocol_version absent when the key is missing")
    void shouldLeaveMissingProtocolVersionAbsent() {
      Map<String, Object> config = new HashMap<>();
      config.put("urls", List.of("tcp://broker:1883"));

      Map<String, Object> result = handler.prepareForOutput(config);

      assertThat(result.get("protocol_version")).isNull();
    }

    @Test
    @DisplayName("Should preserve an explicit null protocol_version")
    void shouldPreserveNullProtocolVersion() {
      Map<String, Object> config = new HashMap<>();
      config.put("protocol_version", null);

      Map<String, Object> result = handler.prepareForOutput(config);

      assertThat(result.get("protocol_version")).isNull();
    }

    @Test
    @DisplayName("Should preserve an explicit protocol_version")
    void shouldPreserveExplicitProtocolVersion() {
      Map<String, Object> config = new HashMap<>();
      config.put("protocol_version", "5");

      Map<String, Object> result = handler.prepareForOutput(config);

      assertThat(result.get("protocol_version")).isEqualTo("5");
    }

    @Test
    @DisplayName("Should still mask sensitive fields")
    void shouldStillMaskSensitiveFields() {
      String encrypted = textEncryptor.encrypt("secret");
      Map<String, Object> config = new HashMap<>();
      config.put("password", encrypted);

      Map<String, Object> result = handler.prepareForOutput(config);

      assertThat(result.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }
  }

  private static String decrypt(String encrypted) {
    try {
      return CredentialDecryptor.decrypt(encrypted, TEST_STRETCHED_KEY, TEST_CONTEXT);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}

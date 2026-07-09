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

@DisplayName("SqlConnectorHandler Tests")
class SqlConnectorHandlerTest {

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

  private SqlConnectorHandler handler;
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
    handler = new SqlConnectorHandler(new JsonMapper(), textEncryptor);
  }

  @Nested
  @DisplayName("Validation")
  class ValidationTests {

    @Test
    @DisplayName("Should pass full validation with valid config")
    void shouldPassWithValidConfig() {
      Map<String, Object> config = new HashMap<>();
      config.put("driver", "postgres");
      config.put("dsn", "postgres://host/db");
      config.put("table", "users");
      config.put("columns", List.of("id", "name"));

      assertThat(handler.validate(config, Default.class, OnRelease.class)).isEmpty();
    }

    @Test
    @DisplayName("Should pass type-only validation even without required fields")
    void shouldPassTypeOnlyWithoutRequiredFields() {
      Map<String, Object> config = new HashMap<>();

      assertThat(handler.validate(config, Default.class)).isEmpty();
    }

    @Test
    @DisplayName("Should fail on release when required fields are missing")
    void shouldFailWhenFieldsMissing() {
      Map<String, Object> config = new HashMap<>();

      List<String> errors = handler.validate(config, Default.class, OnRelease.class);
      assertThat(errors).hasSize(4);
    }

    @Test
    @DisplayName("Should fail when columns has invalid type")
    void shouldFailWhenColumnsNotCollection() {
      Map<String, Object> config = new HashMap<>();
      config.put("driver", "postgres");
      config.put("dsn", "postgres://host/db");
      config.put("table", "users");
      config.put("columns", "id,name");

      List<String> errors = handler.validate(config, Default.class, OnRelease.class);
      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).contains("Invalid configuration format");
    }
  }

  @Nested
  @DisplayName("normalizeAndValidate")
  class NormalizeTests {

    @Test
    @DisplayName("Should normalize SQL config through round-trip")
    void shouldNormalizeSqlConfig() {
      Map<String, Object> map = new HashMap<>();
      map.put("driver", "postgres");
      map.put("dsn", "postgres://host/db");
      map.put("table", "users");
      map.put("conn_max_idle", 5);

      Map<String, Object> result = handler.normalizeAndValidate(map);

      assertThat(result.get("driver")).isEqualTo("postgres");
      assertThat(result.get("dsn")).isEqualTo("postgres://host/db");
      assertThat(result.get("table")).isEqualTo("users");
      assertThat(result.get("conn_max_idle")).isEqualTo(5);
      assertThat(result.get("conn_max_open")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should strip credentials from DSN during normalization")
    void shouldStripCredentialsFromDsn() {
      Map<String, Object> map = new HashMap<>();
      map.put("driver", "postgres");
      map.put("dsn", "postgres://admin:secret@host/db");
      map.put("table", "users");
      map.put("user", "dbuser");
      map.put("password", "dbpass");

      Map<String, Object> result = handler.normalizeAndValidate(map);

      assertThat(result.get("dsn")).isEqualTo("postgres://host/db");
      assertThat(result.get("user")).isEqualTo("dbuser");
      assertThat(result.get("password")).isEqualTo("dbpass");
    }
  }

  @Nested
  @DisplayName("Encryption and Masking")
  class EncryptionTests {

    @Test
    @DisplayName("Should encrypt SQL password field")
    void shouldEncryptPassword() {
      Map<String, Object> config = new HashMap<>();
      config.put("driver", "postgres");
      config.put("dsn", "postgres://localhost:5432/db");
      config.put("password", "db-secret");
      config.put("table", "users");

      Map<String, Object> result = handler.encryptSensitiveFields(config);

      assertThat(result.get("driver")).isEqualTo("postgres");
      assertThat(result.get("dsn")).isEqualTo("postgres://localhost:5432/db");
      assertThat(result.get("table")).isEqualTo("users");
      assertThat(result.get("password")).isNotEqualTo("db-secret");
      assertThat(decrypt((String) result.get("password"))).isEqualTo("db-secret");
    }

    @Test
    @DisplayName("Should encrypt masked placeholder like any other value")
    void shouldEncryptMaskedPlaceholder() {
      Map<String, Object> config = new HashMap<>();
      config.put("password", ConnectorHandler.MASKED_VALUE);

      Map<String, Object> result = handler.encryptSensitiveFields(config);

      assertThat(result.get("password")).isNotEqualTo(ConnectorHandler.MASKED_VALUE);
      assertThat(decrypt((String) result.get("password"))).isEqualTo(ConnectorHandler.MASKED_VALUE);
    }

    @Test
    @DisplayName("Should mask SQL sensitive fields")
    void shouldMaskSqlFields() {
      String encrypted = textEncryptor.encrypt("db-secret");
      Map<String, Object> config = new HashMap<>();
      config.put("password", encrypted);
      config.put("dsn", "postgres://localhost:5432/db");
      config.put("driver", "postgres");

      Map<String, Object> result = handler.maskSensitiveFields(config);

      assertThat(result.get("password")).isEqualTo(ConnectorHandler.MASKED_VALUE);
      assertThat(result.get("dsn")).isEqualTo("postgres://localhost:5432/db");
      assertThat(result.get("driver")).isEqualTo("postgres");
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

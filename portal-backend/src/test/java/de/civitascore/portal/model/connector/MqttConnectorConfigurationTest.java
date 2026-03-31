package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MqttConnectorConfiguration Tests")
class MqttConnectorConfigurationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Nested
  @DisplayName("URL validation and credential stripping")
  class UrlValidationTests {

    @Test
    @DisplayName("Should strip embedded credentials from URLs and discard them")
    void shouldStripCredentialsFromUrls() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setUrls(List.of("tcp://user:secret@broker:1883"));

      assertThat(config.getUrls()).containsExactly("tcp://broker:1883");
      // Credentials are discarded, not stored
      assertThat(config.getUser()).isNull();
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should strip credentials from multiple URLs")
    void shouldStripCredentialsFromMultipleUrls() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setUrls(List.of("tcp://user1:pass1@broker1:1883", "tcp://user2:pass2@broker2:1883"));

      assertThat(config.getUrls()).containsExactly("tcp://broker1:1883", "tcp://broker2:1883");
    }

    @Test
    @DisplayName("Should pass through URLs without credentials unchanged")
    void shouldPassThroughCleanUrls() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setUrls(List.of("tcp://broker:1883"));

      assertThat(config.getUrls()).containsExactly("tcp://broker:1883");
    }

    @Test
    @DisplayName("Should handle null URLs")
    void shouldHandleNullUrls() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setUrls(null);

      assertThat(config.getUrls()).isNull();
    }

    @Test
    @DisplayName("Should handle empty URLs list")
    void shouldHandleEmptyUrls() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setUrls(List.of());

      assertThat(config.getUrls()).isEmpty();
    }

    @Test
    @DisplayName("Should throw on unparseable URL")
    void shouldThrowOnUnparseableUrl() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();

      assertThatThrownBy(() -> config.setUrls(List.of("not a valid url with spaces")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid URL");
    }

    @Test
    @DisplayName("Should throw on URL without scheme")
    void shouldThrowOnUrlWithoutScheme() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();

      assertThatThrownBy(() -> config.setUrls(List.of("broker:1883")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("missing scheme or host");
    }

    @Test
    @DisplayName("Should throw on URL without host")
    void shouldThrowOnUrlWithoutHost() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();

      assertThatThrownBy(() -> config.setUrls(List.of("tcp:///path/only")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("missing scheme or host");
    }
  }

  @Nested
  @DisplayName("Dedicated user/password fields")
  class CredentialFieldTests {

    @Test
    @DisplayName("Should accept dedicated user and password fields")
    void shouldAcceptDedicatedCredentials() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setUrls(List.of("tcp://broker:1883"));
      config.setUser("admin");
      config.setPassword("secret");

      assertThat(config.getUser()).isEqualTo("admin");
      assertThat(config.getPassword()).isEqualTo("secret");
      assertThat(config.getUrls()).containsExactly("tcp://broker:1883");
    }

    @Test
    @DisplayName("Should normalize blank password to null")
    void shouldNormalizeBlankPasswordToNull() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setPassword("");
      assertThat(config.getPassword()).isNull();

      config.setPassword("   ");
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should preserve null password")
    void shouldPreserveNullPassword() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setPassword(null);
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should normalize blank optional MQTT strings to null")
    void shouldNormalizeBlankOptionalStringsToNull() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();

      config.setClientId("   ");
      config.setConnectTimeout("");
      config.setKeepalive(" ");
      config.setUser("\t");

      assertThat(config.getClientId()).isNull();
      assertThat(config.getConnectTimeout()).isNull();
      assertThat(config.getKeepalive()).isNull();
      assertThat(config.getUser()).isNull();
    }
  }

  @Nested
  @DisplayName("Jackson deserialization")
  class DeserializationTests {

    @Test
    @DisplayName("Should deserialize from map with all fields")
    void shouldDeserializeFromMapWithAllFields() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("urls", List.of("tcp://broker:1883"));
      map.put("topics", List.of("sensors/#"));
      map.put("client_id", "my-client");
      map.put("qos", 2);
      map.put("connect_timeout", "5s");
      map.put("keepalive", "30s");
      map.put("tls", Map.of("enabled", true));
      map.put("user", "admin");
      map.put("password", "secret");

      MqttConnectorConfiguration config =
          MAPPER.convertValue(map, MqttConnectorConfiguration.class);

      assertThat(config.getUrls()).containsExactly("tcp://broker:1883");
      assertThat(config.getTopics()).isEqualTo(List.of("sensors/#"));
      assertThat(config.getClientId()).isEqualTo("my-client");
      assertThat(config.getQos()).isEqualTo(2);
      assertThat(config.getConnectTimeout()).isEqualTo("5s");
      assertThat(config.getKeepalive()).isEqualTo("30s");
      assertThat(config.getTls().isEnabled()).isTrue();
      assertThat(config.getUser()).isEqualTo("admin");
      assertThat(config.getPassword()).isEqualTo("secret");
    }

    @Test
    @DisplayName("Should ignore unknown fields")
    void shouldIgnoreUnknownFields() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("urls", List.of("tcp://broker:1883"));
      map.put("topics", List.of("test"));
      map.put("qos", 1);
      map.put("unknown_field", "value");

      MqttConnectorConfiguration config =
          MAPPER.convertValue(map, MqttConnectorConfiguration.class);

      assertThat(config.getTopics()).isEqualTo(List.of("test"));
    }

    @Test
    @DisplayName("Should strip credentials from URLs during deserialization")
    void shouldStripCredentialsDuringDeserialization() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("urls", List.of("tcp://admin:pass123@broker:1883"));
      map.put("topics", List.of("test"));
      map.put("qos", 1);

      MqttConnectorConfiguration config =
          MAPPER.convertValue(map, MqttConnectorConfiguration.class);

      assertThat(config.getUrls()).containsExactly("tcp://broker:1883");
    }
  }

  @Nested
  @DisplayName("Jackson serialization")
  class SerializationTests {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should serialize back to map with correct JSON property names")
    void shouldSerializeToMapWithCorrectPropertyNames() {
      MqttConnectorConfiguration config = new MqttConnectorConfiguration();
      config.setUrls(List.of("tcp://broker:1883"));
      config.setTopics(List.of("sensors/#"));
      config.setClientId("my-client");
      config.setQos(2);
      config.setConnectTimeout("5s");
      config.setKeepalive("30s");
      config.getTls().setEnabled(true);
      config.setUser("admin");
      config.setPassword("secret");

      Map<String, Object> map = MAPPER.convertValue(config, Map.class);

      assertThat(map.get("urls")).isEqualTo(List.of("tcp://broker:1883"));
      assertThat(map.get("topics")).isEqualTo(List.of("sensors/#"));
      assertThat(map.get("client_id")).isEqualTo("my-client");
      assertThat(map.get("qos")).isEqualTo(2);
      assertThat(map.get("connect_timeout")).isEqualTo("5s");
      assertThat(map.get("keepalive")).isEqualTo("30s");
      assertThat(map.get("tls")).isEqualTo(Map.of("enabled", true));
      assertThat(map.get("user")).isEqualTo("admin");
      assertThat(map.get("password")).isEqualTo("secret");
    }
  }
}

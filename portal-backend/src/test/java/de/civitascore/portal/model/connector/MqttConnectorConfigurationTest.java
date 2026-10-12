package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MqttConnectorConfigurationTest {

  private static MqttConnectorConfiguration withUrls(List<String> urls) {
    return new MqttConnectorConfiguration(urls, null, null, null, null, null, null, null, null);
  }

  @Test
  void constructor_withCredentialsInUrls_removesThem() {
    assertThat(withUrls(List.of("tcp://user1:pass1@broker1:1883", "tcp://broker2:1883")).urls())
        .containsExactly("tcp://broker1:1883", "tcp://broker2:1883");
  }

  @ParameterizedTest
  @ValueSource(strings = {"not a valid url with spaces", "broker:1883", "tcp:///path-only"})
  void constructor_withUrlWithoutSchemeOrHost_throwsNamingTheElement(String url) {
    assertThatThrownBy(() -> withUrls(List.of("tcp://broker:1883", url)))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("urls[1]");
  }

  @Test
  void constructor_withNullTopic_throwsNamingTheElement() {
    List<String> topics = Arrays.asList("a/#", null);

    assertThatThrownBy(
            () ->
                new MqttConnectorConfiguration(
                    null, topics, null, null, null, null, null, null, null))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("topics[1]");
  }

  @Test
  void constructor_withNullOrBlankValues_appliesDefaults() {
    MqttConnectorConfiguration config =
        new MqttConnectorConfiguration(null, null, null, null, " ", "", null, "", Secret.of(""));

    assertThat(config.protocolVersion()).isEqualTo("3");
    assertThat(config.tls()).isEqualTo(new MqttConnectorConfiguration.Tls(false));
    assertThat(config.connectTimeout()).isNull();
    assertThat(config.keepalive()).isNull();
    assertThat(config.user()).isNull();
    assertThat(config.password()).isNull();
  }
}

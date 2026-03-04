package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttConnectorConfiguration implements ConnectorConfiguration {

  @Schema(
      description = "Broker URLs. Embedded credentials are stripped — use user/password instead.",
      example = "[\"tcp://broker:1883\"]")
  @NotEmpty(groups = OnPublish.class, message = "'urls' is required and must be a non-empty list") private List<String> urls;

  @Schema(description = "MQTT topic filters.", example = "[\"sensor/#\"]")
  @NotEmpty(groups = OnPublish.class, message = "'topics' is required and must be a non-empty list") private List<String> topics;

  @JsonProperty("client_id")
  @Schema(description = "MQTT client identifier.", example = "civitas-client-1")
  private String clientId;

  @Schema(
      description = "QoS level: 0 = at most once, 1 = at least once, 2 = exactly once.",
      example = "1")
  @NotNull(groups = OnPublish.class, message = "'qos' is required") @Min(value = 0, message = "'qos' must be 0, 1, or 2") @Max(value = 2, message = "'qos' must be 0, 1, or 2") private Integer qos;

  @JsonProperty("connect_timeout")
  @Schema(description = "Connection timeout duration.", example = "5s")
  private String connectTimeout;

  @Schema(description = "Keepalive interval.", example = "30s")
  private String keepalive;

  @Schema(description = "TLS configuration.")
  private Tls tls = new Tls();

  @Data
  public static class Tls {
    @Schema(description = "Whether to enable TLS.", example = "false")
    private boolean enabled;
  }

  @Schema(description = "Broker username.", example = "mqttuser")
  private String user;

  @Schema(
      description = "Broker password. Write-only — returned as \"********\" in responses.",
      accessMode = Schema.AccessMode.WRITE_ONLY,
      example = "secret")
  private String password;

  public void setPassword(String password) {
    this.password = (password != null && password.isBlank()) ? null : password;
  }

  // Validates URLs with java.net.URI and strips any embedded credentials (discarding them)
  public void setUrls(List<String> urls) {
    if (urls == null || urls.isEmpty()) {
      this.urls = urls;
      return;
    }

    List<String> cleaned = new ArrayList<>(urls.size());
    for (String url : urls) {
      try {
        URI uri = new URI(url);
        if (uri.getScheme() == null || uri.getHost() == null) {
          throw new IllegalArgumentException(
              "Invalid URL: missing scheme or host in '" + url + "'");
        }
        if (uri.getUserInfo() != null) {
          uri =
              new URI(
                  uri.getScheme(),
                  null,
                  uri.getHost(),
                  uri.getPort(),
                  uri.getPath(),
                  uri.getQuery(),
                  uri.getFragment());
        }
        cleaned.add(uri.toString());
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid URL: " + url, e);
      }
    }
    this.urls = cleaned;
  }
}

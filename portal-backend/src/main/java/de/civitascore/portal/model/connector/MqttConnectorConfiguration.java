package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Connector configuration of an MQTT Data source. */
@Schema(name = "MqttConnector", description = "Connector of an MQTT Data source.")
public record MqttConnectorConfiguration(
    @Schema(
            description =
                "Broker URLs. Embedded credentials are removed; use user and password instead.",
            example = "[\"tcp://broker:1883\"]")
        @NotEmpty(
            groups = OnRelease.class,
            message = "'urls' is required and must be a non-empty list")
        List<String> urls,
    @Schema(
            description =
                "MQTT topic filter. Exactly one filter, which may use wildcards (+ one level, #"
                    + " multiple) to match several topics.",
            example = "[\"sensor/#\"]")
        @NotEmpty(
            groups = OnRelease.class,
            message = "'topics' is required and must be a non-empty list")
        @Size(
            max = 1,
            groups = OnRelease.class,
            message =
                "'topics' must contain exactly one topic filter; use wildcards (+, #) for multiple"
                    + " topics")
        List<String> topics,
    @Schema(
            description = "QoS level: 0 = at most once, 1 = at least once, 2 = exactly once.",
            example = "1")
        @NotNull(groups = OnRelease.class, message = "'qos' is required") @Min(value = 0, message = "'qos' must be 0, 1, or 2") @Max(value = 2, message = "'qos' must be 0, 1, or 2") Integer qos,
    @JsonProperty("protocol_version")
        @Schema(
            description = "MQTT major protocol version. Absent or null is \"3\".",
            allowableValues = {"3", "5"},
            defaultValue = "3",
            example = "3")
        @Pattern(regexp = "3|5", message = "'protocol_version' must be 3 or 5") String protocolVersion,
    @JsonProperty("connect_timeout") @Schema(description = "Connection timeout.", example = "5s")
        String connectTimeout,
    @Schema(description = "Keepalive interval.", example = "30s") String keepalive,
    @Schema(description = "TLS configuration.") @Valid Tls tls,
    @Schema(description = "Broker username.", example = "mqttuser") String user,
    @Schema(
            description = "Broker password. Responses show \"********\" for a stored password.",
            implementation = String.class,
            accessMode = Schema.AccessMode.WRITE_ONLY,
            format = "password",
            example = "secret")
        Secret password)
    implements ConnectorConfiguration {

  private static final String DEFAULT_PROTOCOL_VERSION = "3";

  public MqttConnectorConfiguration {
    urls = ConnectorUris.withoutCredentials(urls, "urls");
    topics = ConnectorValues.listOf(topics, "topics");
    protocolVersion = protocolVersion == null ? DEFAULT_PROTOCOL_VERSION : protocolVersion;
    connectTimeout = ConnectorValues.blankToNull(connectTimeout);
    keepalive = ConnectorValues.blankToNull(keepalive);
    tls = tls == null ? new Tls(false) : tls;
    user = ConnectorValues.blankToNull(user);
  }

  @Override
  @Schema(allowableValues = "MQTT", requiredMode = Schema.RequiredMode.REQUIRED)
  public ConnectorType connectorType() {
    return ConnectorType.MQTT;
  }

  /** TLS settings of the broker connection. */
  @Schema(name = "MqttConnectorTls")
  public record Tls(
      @Schema(description = "Whether to enable TLS.", example = "false") Boolean enabled) {

    public Tls {
      enabled = Boolean.TRUE.equals(enabled);
    }
  }
}

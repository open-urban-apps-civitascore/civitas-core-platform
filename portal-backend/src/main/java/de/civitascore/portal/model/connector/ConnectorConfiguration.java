package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The typed connector configuration of a Data source, one implementation per {@link ConnectorType}.
 * An implementation is immutable, normalizes its values and removes credentials from URLs in its
 * canonical constructor, and keeps every secret as a {@link Secret}. {@link ConnectorJson} reads
 * and writes it; a type is registered by adding it to {@link JsonSubTypes} and to the {@code oneOf}
 * below.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "connectorType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MqttConnectorConfiguration.class, name = "MQTT"),
  @JsonSubTypes.Type(value = SqlConnectorConfiguration.class, name = "SQL"),
})
@Schema(
    description = "Connector of the Data source. The connectorType selects the remaining fields.",
    oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
    discriminatorProperty = "connectorType",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "MQTT", schema = MqttConnectorConfiguration.class),
      @DiscriminatorMapping(value = "SQL", schema = SqlConnectorConfiguration.class),
    })
public interface ConnectorConfiguration {

  ConnectorType connectorType();
}

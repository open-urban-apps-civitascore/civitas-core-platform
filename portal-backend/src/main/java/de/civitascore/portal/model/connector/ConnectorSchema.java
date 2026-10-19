package de.civitascore.portal.model.connector;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The OpenAPI description of a connector. It is a type of its own because swagger describes each
 * subtype of {@link ConnectorConfiguration} as {@code allOf} it, so a {@code oneOf} on {@link
 * ConnectorConfiguration} would contain itself.
 */
@Schema(
    name = "Connector",
    description = "Connector of the Data source. The connectorType selects the remaining fields.",
    oneOf = {MqttConnectorConfiguration.class, SqlConnectorConfiguration.class},
    discriminatorProperty = "connectorType",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "MQTT", schema = MqttConnectorConfiguration.class),
      @DiscriminatorMapping(value = "SQL", schema = SqlConnectorConfiguration.class),
    })
public interface ConnectorSchema {}

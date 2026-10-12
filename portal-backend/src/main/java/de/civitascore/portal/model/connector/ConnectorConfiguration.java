package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.civitascore.portal.model.embedded.ConnectorType;

/**
 * The typed connector configuration of a Data source, one implementation per {@link ConnectorType}.
 * An implementation is immutable, normalizes its values and removes credentials from URLs in its
 * canonical constructor, and keeps every secret as a {@link Secret}; a list element that carries a
 * secret implements {@link IdentifiedElement}. {@link ConnectorJson} reads and writes it. A type is
 * registered by adding it to {@link JsonSubTypes} and to the {@code oneOf} and {@code
 * discriminatorMapping} of {@link ConnectorSchema}, and by naming it in the {@code allowableValues}
 * of its {@link #connectorType()}; {@code datasource.schema.json} in Model Forge lists it too.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "connectorType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MqttConnectorConfiguration.class, name = "MQTT"),
  @JsonSubTypes.Type(value = SqlConnectorConfiguration.class, name = "SQL"),
})
public interface ConnectorConfiguration {

  ConnectorType connectorType();
}

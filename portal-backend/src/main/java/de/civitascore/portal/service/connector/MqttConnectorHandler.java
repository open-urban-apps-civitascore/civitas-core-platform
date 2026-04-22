package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.Set;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Connector handler for MQTT data sources. Configures the {@code password} field as sensitive for
 * encryption and masking, and uses {@link MqttConnectorConfiguration} for validation.
 */
@Component
public class MqttConnectorHandler extends AbstractConnectorHandler {

  public MqttConnectorHandler(ObjectMapper objectMapper, TextEncryptor textEncryptor) {
    super(
        objectMapper,
        textEncryptor,
        ConnectorType.MQTT,
        MqttConnectorConfiguration.class,
        Set.of("password"));
  }
}

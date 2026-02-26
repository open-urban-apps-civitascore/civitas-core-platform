package de.civitascore.portal.service.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.Set;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

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

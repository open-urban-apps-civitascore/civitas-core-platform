package de.civitascore.portal.service.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
public class SqlConnectorHandler extends AbstractConnectorHandler {

  public SqlConnectorHandler(ObjectMapper objectMapper, TextEncryptor textEncryptor) {
    super(
        objectMapper,
        textEncryptor,
        ConnectorType.SQL,
        SqlConnectorConfiguration.class,
        Set.of("password"));
  }

  @Override
  public Map<String, Object> reconstructForOutput(Map<String, Object> entityConfig) {
    return new LinkedHashMap<>(entityConfig);
  }
}

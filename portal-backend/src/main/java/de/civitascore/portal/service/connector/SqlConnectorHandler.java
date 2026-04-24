package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.Set;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Connector handler for SQL data sources. Configures the {@code password} field as sensitive for
 * encryption and masking, and uses {@link SqlConnectorConfiguration} for validation.
 */
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
}

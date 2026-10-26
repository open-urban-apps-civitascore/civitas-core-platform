package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorUris;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The rules of an SQL connector beyond its Bean Validation constraints, at every stage. They apply
 * to what a request writes or tests, so that a stored connector that breaks them stays readable.
 */
@Component
public class SqlConnectorRules implements ConnectorRules {

  @Override
  public ConnectorType connectorType() {
    return ConnectorType.SQL;
  }

  @Override
  public List<ConnectorViolation> validate(
      ConnectorConfiguration connector, ConnectorValidator.Stage stage) {
    String dsn = ((SqlConnectorConfiguration) connector).dsn();
    List<ConnectorViolation> violations = new ArrayList<>();
    if (dsn == null) {
      return violations;
    }
    if (ConnectorUris.hasFragment(dsn)) {
      violations.add(ConnectorViolation.at("dsn", "'dsn' must not contain a fragment ('#')"));
    }
    if (ConnectorUris.repeatsAParameter(dsn)) {
      violations.add(ConnectorViolation.at("dsn", "'dsn' must not repeat a parameter"));
    }
    return violations;
  }
}

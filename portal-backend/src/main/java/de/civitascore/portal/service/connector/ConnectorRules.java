package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.List;

/**
 * The rules of one connector type that the Bean Validation annotations on its records cannot
 * express, such as a field that is required only when another field has a given value. A Spring
 * bean of this type takes part in {@link ConnectorValidator#validate}; at most one bean per
 * connector type.
 */
public interface ConnectorRules {

  ConnectorType connectorType();

  /**
   * @param connector a connector of {@link #connectorType()}
   * @return the violations, built with {@link ConnectorViolation#at}
   */
  List<ConnectorViolation> validate(
      ConnectorConfiguration connector, ConnectorValidator.Stage stage);
}

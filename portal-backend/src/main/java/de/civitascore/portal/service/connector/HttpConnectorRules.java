package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.PageNumber;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Parameter;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The rules of an HTTP/REST connector beyond its Bean Validation constraints, at every stage. */
@Component
public class HttpConnectorRules implements ConnectorRules {

  private static final Set<String> CREDENTIAL_HEADERS =
      Set.of("authorization", "proxy-authorization", "cookie");

  @Override
  public ConnectorType connectorType() {
    return ConnectorType.HTTP;
  }

  @Override
  public List<ConnectorViolation> validate(
      ConnectorConfiguration connector, ConnectorValidator.Stage stage) {
    List<ApiCall> apiCalls = ((HttpConnectorConfiguration) connector).apiCalls();
    List<ConnectorViolation> violations = new ArrayList<>();
    if (apiCalls == null) {
      return violations;
    }
    Set<String> names = new HashSet<>();
    for (int i = 0; i < apiCalls.size(); i++) {
      ApiCall apiCall = apiCalls.get(i);
      String apiCallPath = "apiCalls[" + i + "]";
      if (apiCall.name() != null && !names.add(apiCall.name())) {
        violations.add(
            ConnectorViolation.at(
                apiCallPath + ".name", "'name' must be unique among the API calls"));
      }
      addCredentialHeaderViolations(apiCall.headers(), apiCallPath, violations);
      if (apiCall.pagination() instanceof PageNumber page
          && page.endPage() != null
          && page.endPage() < page.startPage()) {
        violations.add(
            ConnectorViolation.at(
                apiCallPath + ".pagination.endPage", "'endPage' must be at least 'startPage'"));
      }
    }
    return violations;
  }

  private static void addCredentialHeaderViolations(
      List<Parameter> headers, String apiCallPath, List<ConnectorViolation> violations) {
    if (headers == null) {
      return;
    }
    for (int j = 0; j < headers.size(); j++) {
      String name = headers.get(j).name();
      if (name != null && CREDENTIAL_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        violations.add(
            ConnectorViolation.at(
                apiCallPath + ".headers[" + j + "].name",
                "'name' must not be Authorization, Proxy-Authorization or Cookie; use"
                    + " authentication for credentials"));
      }
    }
  }
}

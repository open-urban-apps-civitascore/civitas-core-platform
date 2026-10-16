package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Basic;
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

  /** The headers the JDK HTTP client of the NiFi processor sets itself and refuses to take. */
  private static final Set<String> CLIENT_HEADERS =
      Set.of("connection", "content-length", "expect", "host", "upgrade");

  @Override
  public ConnectorType connectorType() {
    return ConnectorType.HTTP;
  }

  @Override
  public List<ConnectorViolation> validate(
      ConnectorConfiguration connector, ConnectorValidator.Stage stage) {
    HttpConnectorConfiguration http = (HttpConnectorConfiguration) connector;
    List<ApiCall> apiCalls = http.apiCalls();
    List<ConnectorViolation> violations = new ArrayList<>();
    if (sendsCredentialsInClearText(http)) {
      violations.add(
          ConnectorViolation.at("baseUrl", "'baseUrl' must use https with BASIC authentication"));
    }
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
      addHeaderNameViolations(apiCall.headers(), apiCallPath, violations);
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

  private static boolean sendsCredentialsInClearText(HttpConnectorConfiguration http) {
    return http.authentication() instanceof Basic
        && http.baseUrl() != null
        && http.baseUrl().regionMatches(true, 0, "http:", 0, "http:".length());
  }

  private static void addHeaderNameViolations(
      List<Parameter> headers, String apiCallPath, List<ConnectorViolation> violations) {
    if (headers == null) {
      return;
    }
    for (int j = 0; j < headers.size(); j++) {
      String name = headers.get(j).name();
      if (name == null) {
        continue;
      }
      String lowerCase = name.toLowerCase(Locale.ROOT);
      String path = apiCallPath + ".headers[" + j + "].name";
      if (CREDENTIAL_HEADERS.contains(lowerCase)) {
        violations.add(
            ConnectorViolation.at(
                path,
                "'name' must not be Authorization, Proxy-Authorization or Cookie; use"
                    + " authentication for credentials"));
      } else if (CLIENT_HEADERS.contains(lowerCase)) {
        violations.add(
            ConnectorViolation.at(
                path,
                "'name' must not be Connection, Content-Length, Expect, Host or Upgrade; the"
                    + " HTTP client sets it"));
      }
    }
  }
}

package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiToken;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Cursor;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.LimitOffset;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.None;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.PageNumber;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Pagination;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Parameter;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ParameterLocation;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** The rules of an HTTP/REST connector beyond its Bean Validation constraints. */
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
    List<ApiCall> apiCalls = http.apiCalls() == null ? List.of() : http.apiCalls();
    List<ConnectorViolation> violations = new ArrayList<>();
    if (sendsCredentialsInClearText(http)) {
      violations.add(
          ConnectorViolation.at("baseUrl", "'baseUrl' must use https with authentication"));
    }
    if (http.authentication() instanceof ApiToken token) {
      addApiTokenViolations(token, apiCalls, violations);
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
      if (apiCall.pagination() != null) {
        addPaginationViolations(apiCall, apiCallPath + ".pagination", stage, violations);
      }
    }
    return violations;
  }

  /** A name that a request sends as a query parameter or a header, from the field {@code field}. */
  private record SentName(String field, String name, ParameterLocation location) {

    boolean collidesWith(SentName other) {
      return location == other.location
          && (location == ParameterLocation.HEADER
              ? name.equalsIgnoreCase(other.name)
              : name.equals(other.name));
    }

    String locationName() {
      return location == ParameterLocation.HEADER ? "header" : "query parameter";
    }
  }

  private static List<SentName> parameterNames(ApiCall apiCall) {
    return Stream.concat(
            sentNames(apiCall.queryParameters(), ParameterLocation.QUERY),
            sentNames(apiCall.headers(), ParameterLocation.HEADER))
        .toList();
  }

  private static Stream<SentName> sentNames(List<Parameter> parameters, ParameterLocation in) {
    return parameters == null
        ? Stream.empty()
        : parameters.stream()
            .filter(parameter -> parameter.name() != null)
            .map(parameter -> new SentName("name", parameter.name(), in));
  }

  /** The names the pagination sends, in the order of its fields; a missing name is left out. */
  private static List<SentName> paginationNames(Pagination pagination) {
    Stream<SentName> names =
        switch (pagination) {
          case PageNumber page ->
              Stream.of(
                  query("pageParameter", page.pageParameter()),
                  query("pageSizeParameter", page.pageSizeParameter()));
          case LimitOffset limitOffset ->
              Stream.of(
                  query("limitParameter", limitOffset.limitParameter()),
                  query("offsetParameter", limitOffset.offsetParameter()));
          case Cursor cursor ->
              Stream.of(
                  cursor.cursorIn() == null
                      ? null
                      : new SentName(
                          "cursorParameter", cursor.cursorParameter(), cursor.cursorIn()),
                  query("limitParameter", cursor.limitParameter()));
        };
    return names.filter(name -> name != null && name.name() != null).toList();
  }

  private static SentName query(String field, String name) {
    return new SentName(field, name, ParameterLocation.QUERY);
  }

  private static void addPaginationViolations(
      ApiCall apiCall,
      String paginationPath,
      ConnectorValidator.Stage stage,
      List<ConnectorViolation> violations) {
    List<SentName> names = paginationNames(apiCall.pagination());
    List<SentName> parameterNames = parameterNames(apiCall);
    for (int i = 0; i < names.size(); i++) {
      SentName sent = names.get(i);
      String path = paginationPath + "." + sent.field();
      if (sent.location() == ParameterLocation.HEADER) {
        addSentHeaderNameViolations(sent, path, false, violations);
      }
      if (parameterNames.stream().anyMatch(sent::collidesWith)) {
        violations.add(
            ConnectorViolation.at(
                path,
                "'" + sent.field() + "' must differ from the name of each " + sent.locationName()));
      }
      for (SentName earlier : names.subList(0, i)) {
        if (earlier.name().equals(sent.name())) {
          violations.add(
              ConnectorViolation.at(
                  path, "'" + sent.field() + "' must differ from '" + earlier.field() + "'"));
        }
      }
    }
    switch (apiCall.pagination()) {
      case PageNumber page when page.endPage() != null && page.endPage() < page.startPage() ->
          violations.add(
              ConnectorViolation.at(
                  paginationPath + ".endPage", "'endPage' must be at least 'startPage'"));
      case LimitOffset limitOffset
          when stage == ConnectorValidator.Stage.RELEASE && isEmpty(limitOffset) ->
          violations.add(
              ConnectorViolation.at(
                  paginationPath,
                  "A limit/offset pagination needs at least one field; without one, disable the"
                      + " pagination"));
      default -> {}
    }
  }

  private static boolean isEmpty(LimitOffset limitOffset) {
    return limitOffset.limitParameter() == null
        && limitOffset.offsetParameter() == null
        && limitOffset.limit() == null
        && limitOffset.offset() == null
        && limitOffset.countPath() == null;
  }

  private static void addApiTokenViolations(
      ApiToken token, List<ApiCall> apiCalls, List<ConnectorViolation> violations) {
    if (token.name() == null || token.in() == null) {
      return;
    }
    String path = "authentication.name";
    SentName sent = new SentName("name", token.name(), token.in());
    if (sent.location() == ParameterLocation.HEADER) {
      addSentHeaderNameViolations(sent, path, true, violations);
    }
    boolean collides =
        apiCalls.stream()
            .flatMap(
                apiCall ->
                    Stream.concat(
                        parameterNames(apiCall).stream(),
                        apiCall.pagination() == null
                            ? Stream.empty()
                            : paginationNames(apiCall.pagination()).stream()))
            .anyMatch(sent::collidesWith);
    if (collides) {
      violations.add(
          ConnectorViolation.at(
              path,
              "'name' must differ from the name of each "
                  + sent.locationName()
                  + " of the API calls, pagination included"));
    }
  }

  private static boolean sendsCredentialsInClearText(HttpConnectorConfiguration http) {
    return !(http.authentication() instanceof None)
        && http.baseUrl() != null
        && http.baseUrl().regionMatches(true, 0, "http:", 0, "http:".length());
  }

  /**
   * Checks a header name that Bean Validation cannot check as one, because the field is a header
   * name only when its location says so.
   */
  private static void addSentHeaderNameViolations(
      SentName sent,
      String path,
      boolean credentialHeaderAllowed,
      List<ConnectorViolation> violations) {
    if (!sent.name().matches(HttpConnectorConfiguration.HEADER_NAME)) {
      violations.add(
          ConnectorViolation.at(path, "'" + sent.field() + "' must be a valid header name"));
    } else {
      addRefusedHeaderViolation(
          sent.field(), sent.name(), path, credentialHeaderAllowed, violations);
    }
  }

  private static void addHeaderNameViolations(
      List<Parameter> headers, String apiCallPath, List<ConnectorViolation> violations) {
    if (headers == null) {
      return;
    }
    for (int j = 0; j < headers.size(); j++) {
      String name = headers.get(j).name();
      if (name != null) {
        addRefusedHeaderViolation(
            "name", name, apiCallPath + ".headers[" + j + "].name", false, violations);
      }
    }
  }

  private static void addRefusedHeaderViolation(
      String field,
      String name,
      String path,
      boolean credentialHeaderAllowed,
      List<ConnectorViolation> violations) {
    String lowerCase = name.toLowerCase(Locale.ROOT);
    if (!credentialHeaderAllowed && CREDENTIAL_HEADERS.contains(lowerCase)) {
      violations.add(
          ConnectorViolation.at(
              path,
              "'"
                  + field
                  + "' must not be Authorization, Proxy-Authorization or Cookie; use"
                  + " authentication for credentials"));
    } else if (CLIENT_HEADERS.contains(lowerCase)) {
      violations.add(
          ConnectorViolation.at(
              path,
              "'"
                  + field
                  + "' must not be Connection, Content-Length, Expect, Host or Upgrade; the"
                  + " HTTP client sets it"));
    }
  }
}

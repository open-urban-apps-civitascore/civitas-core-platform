package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiToken;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Authentication;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Basic;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.BearerToken;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ClientCertificate;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Cursor;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.LimitOffset;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.OAuth2;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.PageNumber;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Pagination;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Parameter;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ParameterLocation;
import de.civitascore.portal.model.connector.Secret;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpConnectorRulesTest {

  private final HttpConnectorRules rules = new HttpConnectorRules();

  private static ApiCall named(String name) {
    return new ApiCall(name, null, "/x", null, null, null, null);
  }

  private static ApiCall withHeaders(Parameter... headers) {
    return new ApiCall("a", null, "/x", null, List.of(headers), null, null);
  }

  private static ApiCall withPages(Integer startPage, Integer endPage) {
    return new ApiCall(
        null, null, "/x", null, null, null, new PageNumber("page", "size", 10, startPage, endPage));
  }

  private static ApiCall paged(
      String pageParameter, String pageSizeParameter, String... queryParameterNames) {
    List<Parameter> queryParameters =
        Arrays.stream(queryParameterNames).map(name -> new Parameter(name, "1")).toList();
    return new ApiCall(
        null,
        null,
        "/x",
        queryParameters,
        null,
        null,
        new PageNumber(pageParameter, pageSizeParameter, 10, null, null));
  }

  private List<ConnectorViolation> validate(ApiCall... apiCalls) {
    return rules.validate(
        new HttpConnectorConfiguration("https://api.example.org", null, List.of(apiCalls)),
        ConnectorValidator.Stage.DRAFT);
  }

  @Test
  void validate_withDuplicateApiCallNames_reportsEachDuplicateAfterTheFirst() {
    assertThat(validate(named("a"), named("b"), named("a"), named(null), named(null), named("a")))
        .containsExactly(
            ConnectorViolation.at("apiCalls[2].name", "'name' must be unique among the API calls"),
            ConnectorViolation.at("apiCalls[5].name", "'name' must be unique among the API calls"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "proxy-authorization", "COOKIE"})
  void validate_withCredentialHeader_reportsItsName(String header) {
    assertThat(
            validate(
                withHeaders(
                    new Parameter("Accept", "application/json"), new Parameter(header, ""))))
        .extracting(ConnectorViolation::path)
        .containsExactly("connector.apiCalls[0].headers[1].name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Host", "connection", "Content-Length", "EXPECT", "Upgrade"})
  void validate_withHeaderTheHttpClientSets_reportsItsName(String header) {
    assertThat(validate(withHeaders(new Parameter(header, "x"))))
        .extracting(ConnectorViolation::path)
        .containsExactly("connector.apiCalls[0].headers[0].name");
  }

  private static Stream<Authentication> authentications() {
    return Stream.of(
        new Basic("reader", Secret.of("secret")),
        new BearerToken(Secret.of("secret")),
        new ApiToken("key", Secret.of("secret"), ParameterLocation.QUERY),
        new OAuth2("https://auth.example.org/token", "c", Secret.of("secret"), null),
        new ClientCertificate(null, Secret.of("secret")));
  }

  @ParameterizedTest
  @MethodSource("authentications")
  void validate_withAuthentication_requiresHttps(Authentication authentication) {
    assertThat(validate("http://api.example.org", authentication))
        .extracting(ConnectorViolation::path)
        .containsExactly("connector.baseUrl");
    assertThat(validate("HTTP://api.example.org", authentication)).hasSize(1);
    assertThat(validate("https://api.example.org", authentication)).isEmpty();
  }

  @Test
  void validate_withoutAuthentication_acceptsHttp() {
    assertThat(validate("http://api.example.org", null)).isEmpty();
  }

  private List<ConnectorViolation> validate(String baseUrl, Authentication authentication) {
    return rules.validate(
        new HttpConnectorConfiguration(baseUrl, authentication, List.of(named("a"))),
        ConnectorValidator.Stage.DRAFT);
  }

  private List<ConnectorViolation> validateWithApiToken(
      String name, ParameterLocation in, ApiCall... apiCalls) {
    return rules.validate(
        new HttpConnectorConfiguration(
            "https://api.example.org",
            new ApiToken(name, Secret.of("secret"), in),
            List.of(apiCalls)),
        ConnectorValidator.Stage.DRAFT);
  }

  private static ApiCall sending(
      List<Parameter> queryParameters, List<Parameter> headers, Pagination pagination) {
    return new ApiCall(null, null, "/x", queryParameters, headers, null, pagination);
  }

  @ParameterizedTest
  @CsvSource({
    "key, QUERY, false",
    "KEY, QUERY, true",
    "X-Key, HEADER, false",
    "x-key, HEADER, false",
    "limit, QUERY, false",
    "cursor, HEADER, false",
    "Cursor, QUERY, true",
    "status, HEADER, true"
  })
  void validate_withApiToken_refusesTheNameOfAnyParameterAtTheSamePlace(
      String name, ParameterLocation in, boolean accepted) {
    ApiCall plain =
        sending(
            List.of(new Parameter("key", "1"), new Parameter("status", "a")),
            List.of(new Parameter("X-Key", "1")),
            null);
    ApiCall paged =
        sending(null, null, new Cursor("$.next", "cursor", ParameterLocation.HEADER, "limit", 10));

    assertThat(validateWithApiToken(name, in, plain, paged))
        .isEqualTo(
            accepted
                ? List.of()
                : List.of(
                    ConnectorViolation.at(
                        "authentication.name",
                        "'name' must differ from the name of each "
                            + (in == ParameterLocation.HEADER ? "header" : "query parameter")
                            + " of the API calls, pagination included")));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '"',
      value = {
        "Authorization | ",
        "X API Key | 'name' must be a valid header name",
        "Host | 'name' must not be Connection, Content-Length, Expect, Host or Upgrade;"
            + " the HTTP client sets it"
      })
  void validate_withApiTokenInHeader_refusesHeadersTheHttpClientSets(String name, String message) {
    assertThat(validateWithApiToken(name, ParameterLocation.HEADER, named("a")))
        .isEqualTo(
            message == null
                ? List.of()
                : List.of(ConnectorViolation.at("authentication.name", message)));
  }

  @Test
  void validate_withApiTokenInQuery_acceptsAnyName() {
    assertThat(validateWithApiToken("Host name", ParameterLocation.QUERY, named("a"))).isEmpty();
  }

  @Test
  void validate_withCursorNamedAsParameterAtTheSamePlace_reportsCursorParameter() {
    List<Parameter> query = List.of(new Parameter("cursor", "1"));
    List<Parameter> headers = List.of(new Parameter("X-Cursor", "1"));

    assertThat(
            validate(
                sending(
                    query,
                    headers,
                    new Cursor("$.c", "cursor", ParameterLocation.QUERY, "limit", 1)),
                sending(
                    query,
                    headers,
                    new Cursor("$.c", "x-cursor", ParameterLocation.HEADER, "limit", 1)),
                sending(
                    query,
                    headers,
                    new Cursor("$.c", "cursor", ParameterLocation.HEADER, "limit", 1)),
                sending(
                    query,
                    headers,
                    new Cursor("$.c", "X-Cursor", ParameterLocation.QUERY, "limit", 1))))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[0].pagination.cursorParameter",
                "'cursorParameter' must differ from the name of each query parameter"),
            ConnectorViolation.at(
                "apiCalls[1].pagination.cursorParameter",
                "'cursorParameter' must differ from the name of each header"));
  }

  @Test
  void validate_withCursorNamedAsLimitParameter_reportsLimitParameterOnlyAtTheSamePlace() {
    assertThat(
            validate(
                sending(null, null, new Cursor("$.c", "next", ParameterLocation.QUERY, "next", 1)),
                sending(
                    null, null, new Cursor("$.c", "next", ParameterLocation.HEADER, "next", 1))))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[0].pagination.limitParameter",
                "'limitParameter' must differ from 'cursorParameter'"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '"',
      value = {
        "X-Cursor | ",
        "Cookie | 'cursorParameter' must not be Authorization, Proxy-Authorization or Cookie;"
            + " use authentication for credentials",
        "Upgrade | 'cursorParameter' must not be Connection, Content-Length, Expect, Host or"
            + " Upgrade; the HTTP client sets it",
        "X:Cursor | 'cursorParameter' must be a valid header name"
      })
  void validate_withCursorInHeader_appliesTheRulesOfAHeaderName(String name, String message) {
    assertThat(
            validate(
                sending(null, null, new Cursor("$.c", name, ParameterLocation.HEADER, "l", 1))))
        .isEqualTo(
            message == null
                ? List.of()
                : List.of(
                    ConnectorViolation.at("apiCalls[0].pagination.cursorParameter", message)));
  }

  @Test
  void validate_withLimitOffsetParametersNamedAsQueryParameterOrEachOther_reportsTheLaterField() {
    List<Parameter> query = List.of(new Parameter("limit", "1"));

    assertThat(
            validate(
                sending(query, null, new LimitOffset("limit", "offset", null, null, null)),
                sending(null, null, new LimitOffset("p", "p", null, null, null))))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[0].pagination.limitParameter",
                "'limitParameter' must differ from the name of each query parameter"),
            ConnectorViolation.at(
                "apiCalls[1].pagination.offsetParameter",
                "'offsetParameter' must differ from 'limitParameter'"));
  }

  @Test
  void validate_withLimitWithoutItsParameter_reportsTheMissingOneOnlyAtRelease() {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org",
            null,
            List.of(
                sending(null, null, new LimitOffset("limit", "offset", null, null, null)),
                sending(null, null, new LimitOffset(null, "offset", 20, null, null)),
                sending(null, null, new Cursor("$.c", "c", ParameterLocation.QUERY, "limit", null)),
                sending(null, null, new Cursor("$.c", "c", ParameterLocation.QUERY, null, 20)),
                sending(null, null, new LimitOffset(null, "offset", null, null, null)),
                sending(null, null, new Cursor("$.c", "c", ParameterLocation.QUERY, null, null))));

    assertThat(rules.validate(connector, ConnectorValidator.Stage.DRAFT)).isEmpty();
    assertThat(rules.validate(connector, ConnectorValidator.Stage.RELEASE))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[0].pagination.limit", "'limit' is required with 'limitParameter'"),
            ConnectorViolation.at(
                "apiCalls[1].pagination.limitParameter",
                "'limitParameter' is required with 'limit'"),
            ConnectorViolation.at(
                "apiCalls[2].pagination.limit", "'limit' is required with 'limitParameter'"),
            ConnectorViolation.at(
                "apiCalls[3].pagination.limitParameter",
                "'limitParameter' is required with 'limit'"));
  }

  @Test
  void validate_withEndPageBeforeStartPage_reportsEndPage() {
    assertThat(validate(withPages(2, 2), withPages(null, null), withPages(2, 1)))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[2].pagination.endPage", "'endPage' must be at least 'startPage'"));
  }

  @Test
  void validate_withPaginationParameterNamedAsQueryParameter_reportsThePaginationField() {
    assertThat(
            validate(
                paged("page", "size", "status", "page"),
                paged("page", "size", "size"),
                paged("page", "size", "status")))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[0].pagination.pageParameter",
                "'pageParameter' must differ from the name of each query parameter"),
            ConnectorViolation.at(
                "apiCalls[1].pagination.pageSizeParameter",
                "'pageSizeParameter' must differ from the name of each query parameter"));
  }

  @Test
  void validate_withPageParameterNamedAsPageSizeParameter_reportsPageSizeParameter() {
    assertThat(validate(paged("page", "size"), paged("p", "p")))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[1].pagination.pageSizeParameter",
                "'pageSizeParameter' must differ from 'pageParameter'"));
  }

  @Test
  void validate_withPaginationParameterNamesThatDifferOnlyInCase_reportsNothing() {
    assertThat(validate(paged("page", "Page", "PAGE", "Size"), paged("size", "SIZE"))).isEmpty();
  }

  @Test
  void validate_withPaginationParameterNamesMissingInADraft_reportsNothing() {
    ApiCall withoutQueryParameters =
        new ApiCall(null, null, "/x", null, null, null, new PageNumber(null, null, 10, null, null));

    assertThat(validate(withoutQueryParameters, paged(null, null, (String) null))).isEmpty();
  }

  @Test
  void validate_withoutApiCalls_reportsNothing() {
    assertThat(
            rules.validate(
                new HttpConnectorConfiguration(null, null, null), ConnectorValidator.Stage.RELEASE))
        .isEmpty();
  }
}

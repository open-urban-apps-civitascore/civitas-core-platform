package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Authentication;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Basic;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.PageNumber;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Parameter;
import de.civitascore.portal.model.connector.Secret;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpConnectorRulesTest {

  private final HttpConnectorRules rules = new HttpConnectorRules();

  private static ApiCall named(String name) {
    return new ApiCall(name, null, "/x", null, null, null);
  }

  private static ApiCall withHeaders(Parameter... headers) {
    return new ApiCall("a", null, "/x", null, List.of(headers), null);
  }

  private static ApiCall withPages(Integer startPage, Integer endPage) {
    return new ApiCall(
        null, null, "/x", null, null, new PageNumber("page", "size", 10, startPage, endPage));
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

  @ParameterizedTest
  @CsvSource({
    "http://api.example.org, true, connector.baseUrl",
    "HTTP://api.example.org, true, connector.baseUrl",
    "https://api.example.org, true, ",
    "http://api.example.org, false, "
  })
  void validate_withBasicAuthentication_requiresHttps(
      String baseUrl, boolean basic, String expectedPath) {
    Authentication authentication = basic ? new Basic("reader", Secret.of("secret")) : null;

    List<ConnectorViolation> violations =
        rules.validate(
            new HttpConnectorConfiguration(baseUrl, authentication, List.of(named("a"))),
            ConnectorValidator.Stage.DRAFT);

    assertThat(violations)
        .extracting(ConnectorViolation::path)
        .isEqualTo(expectedPath == null ? List.of() : List.of(expectedPath));
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
        new ApiCall(null, null, "/x", null, null, new PageNumber(null, null, 10, null, null));

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

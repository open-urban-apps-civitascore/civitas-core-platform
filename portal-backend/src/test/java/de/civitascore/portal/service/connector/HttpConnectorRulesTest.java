package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.PageNumber;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

  @Test
  void validate_withEndPageBeforeStartPage_reportsEndPage() {
    assertThat(validate(withPages(2, 2), withPages(null, null), withPages(2, 1)))
        .containsExactly(
            ConnectorViolation.at(
                "apiCalls[2].pagination.endPage", "'endPage' must be at least 'startPage'"));
  }

  @Test
  void validate_withoutApiCalls_reportsNothing() {
    assertThat(
            rules.validate(
                new HttpConnectorConfiguration(null, null, null), ConnectorValidator.Stage.RELEASE))
        .isEmpty();
  }
}

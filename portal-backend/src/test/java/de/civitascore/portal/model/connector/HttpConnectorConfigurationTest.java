package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiToken;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Basic;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Cursor;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.LimitOffset;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Method;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.None;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.OAuth2;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.PageNumber;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Parameter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class HttpConnectorConfigurationTest {

  private static HttpConnectorConfiguration withBaseUrl(String baseUrl) {
    return new HttpConnectorConfiguration(baseUrl, null, null);
  }

  private static ApiCall apiCall(List<Parameter> queryParameters, List<Parameter> headers) {
    return new ApiCall("calls", null, "/x", queryParameters, headers, null);
  }

  @ParameterizedTest
  @CsvSource({
    "https://api.example.org/v1, https://api.example.org/v1",
    "HTTP://api.example.org:8080/v1/, HTTP://api.example.org:8080/v1",
    "https://api.example.org//, https://api.example.org",
    "https://reader:secret@api.example.org/v1, https://api.example.org/v1",
    "https://oauth2:tok@gitlab.example.org/projects/group%2Fproject,"
        + " https://gitlab.example.org/projects/group%2Fproject",
    "https://reader:secret@[2001:db8::1]:8443/v1, https://[2001:db8::1]:8443/v1"
  })
  void constructor_withHttpBaseUrl_removesUserInfoAndTrailingSlash(String baseUrl, String kept) {
    assertThat(withBaseUrl(baseUrl).baseUrl()).isEqualTo(kept);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ftp://api.example.org",
        "api.example.org/v1",
        "https:///v1",
        "https://api.example.org/v1?key=1",
        "https://api.example.org/v1#part",
        "https://api example.org"
      })
  void constructor_withBaseUrlThatIsNoHttpHost_throwsNamingTheKey(String baseUrl) {
    assertThatThrownBy(() -> withBaseUrl(baseUrl))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("baseUrl");
  }

  @ParameterizedTest
  @CsvSource({
    "https://auth.example.org/oauth2/token/, https://auth.example.org/oauth2/token/",
    "https://client:secret@auth.example.org/token, https://auth.example.org/token"
  })
  void constructor_withTokenUrl_removesUserInfoAndKeepsTheTrailingSlash(
      String tokenUrl, String kept) {
    assertThat(new OAuth2(tokenUrl, "c", null, null).tokenUrl()).isEqualTo(kept);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://auth.example.org", "https://auth.example.org/token?grant=x"})
  void constructor_withTokenUrlThatIsNoHttpUrl_throwsNamingTheKey(String tokenUrl) {
    assertThatThrownBy(() -> new OAuth2(tokenUrl, "c", null, null))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("tokenUrl");
  }

  @Test
  void constructor_withBlankValuesOfAuthenticationAndPagination_storesNull() {
    OAuth2 oauth2 = new OAuth2(" ", " ", null, " ");
    LimitOffset limitOffset = new LimitOffset(" ", "", null, null, " ");
    Cursor cursor = new Cursor(" ", "", null, " ", null);

    assertThat(new ApiToken(" ", null, null).name()).isNull();
    assertThat(oauth2).isEqualTo(new OAuth2(null, null, null, null));
    assertThat(limitOffset).isEqualTo(new LimitOffset(null, null, null, null, null));
    assertThat(cursor).isEqualTo(new Cursor(null, null, null, null, null));
  }

  @Test
  void constructor_withNullOrBlankValues_appliesDefaults() {
    HttpConnectorConfiguration connector = new HttpConnectorConfiguration(" ", null, null);
    ApiCall apiCall = new ApiCall(" ", null, "", null, null, null);
    PageNumber page = new PageNumber(" ", "", null, null, null);

    assertThat(connector.baseUrl()).isNull();
    assertThat(connector.authentication()).isEqualTo(new None());
    assertThat(connector.apiCalls()).isNull();
    assertThat(apiCall.name()).isNull();
    assertThat(apiCall.path()).isNull();
    assertThat(apiCall.queryParameters()).isNull();
    assertThat(apiCall.pagination()).isNull();
    assertThat(new Basic(" ", Secret.of("")).username()).isNull();
    assertThat(page.pageParameter()).isNull();
    assertThat(page.pageSizeParameter()).isNull();
    assertThat(page.startPage()).isZero();
    assertThat(page.endPage()).isNull();
  }

  @Test
  void constructor_withEmptyParameterValue_keepsIt() {
    Parameter parameter = new Parameter(" ", " ");

    assertThat(parameter.name()).isNull();
    assertThat(parameter.value()).isEqualTo(" ");
  }

  @Test
  void constructor_withNullElement_throwsNamingTheElement() {
    List<Parameter> withNull = Arrays.asList(new Parameter("a", "1"), null);

    assertThatThrownBy(() -> new HttpConnectorConfiguration(null, null, Arrays.asList(null, null)))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("apiCalls[0]");
    assertThatThrownBy(() -> apiCall(withNull, null))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("queryParameters[1]");
    assertThatThrownBy(() -> apiCall(null, withNull))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("headers[1]");
  }

  @Test
  void read_withMethodPost_storesGet() {
    HttpConnectorConfiguration connector =
        (HttpConnectorConfiguration)
            ConnectorJson.read(
                new JsonMapper()
                    .readTree(
                        """
                        {"connectorType": "HTTP", "apiCalls": [{"name": "a", "method": "POST"}]}
                        """));

    assertThat(connector.apiCalls().getFirst().method()).isEqualTo(Method.GET);
  }
}

package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiCall;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ApiToken;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Authentication;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Basic;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.BearerToken;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ClientCertificate;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Cursor;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.LimitOffset;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.NextLink;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.OAuth2;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.PageNumber;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Pagination;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.Parameter;
import de.civitascore.portal.model.connector.HttpConnectorConfiguration.ParameterLocation;
import de.civitascore.portal.model.connector.IdentifiedElement;
import de.civitascore.portal.model.connector.OnRelease;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ClassIntrospector;
import tools.jackson.databind.json.JsonMapper;

class ConnectorValidatorTest {

  private final ConnectorValidator validator = new ConnectorValidator(List.of());

  private final ApplicationContextRunner context =
      new ApplicationContextRunner().withBean(ConnectorValidator.class);

  @Test
  void validate_atDraft_reportsDefaultConstraintsByRequestKeyPath() {
    TestConnector connector = new TestConnector(9, null, List.of(new Call("a", null)));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .containsExactly(new ConnectorViolation("connector.max_retries", "too many"));
  }

  @Test
  void validate_atRelease_addsCompletenessConstraintsInNestedLists() {
    TestConnector connector =
        new TestConnector(null, null, List.of(new Call("a", new Auth(null, List.of()))));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.RELEASE))
        .containsExactly(
            new ConnectorViolation("connector.api_calls[0].auth.token", "token required"),
            new ConnectorViolation("connector.name", "name required"));
  }

  @Test
  void validate_httpConnectorAtDraft_reportsPathsIntoSealedVariantsAndListElements() {
    List<Parameter> parameters = List.of(new Parameter("a b", "x\ny"));
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org",
            new Basic("a:b", null),
            List.of(
                new ApiCall(
                    "a",
                    null,
                    "stations",
                    parameters,
                    parameters,
                    null,
                    new PageNumber("page", null, 0, -1, -1))));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .extracting(ConnectorViolation::path)
        .containsExactly(
            "connector.apiCalls[0].headers[0].name",
            "connector.apiCalls[0].headers[0].value",
            "connector.apiCalls[0].pagination.endPage",
            "connector.apiCalls[0].pagination.pageSize",
            "connector.apiCalls[0].pagination.startPage",
            "connector.apiCalls[0].path",
            "connector.authentication.username");
  }

  @Test
  void validate_httpConnectorAtRelease_reportsEachMissingValue() {
    List<Parameter> parameters = List.of(new Parameter(null, ""));
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            null,
            new Basic(null, null),
            List.of(
                new ApiCall(
                    null,
                    null,
                    null,
                    parameters,
                    parameters,
                    null,
                    new PageNumber(null, null, null, null, null))));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.RELEASE))
        .extracting(ConnectorViolation::path)
        .containsExactly(
            "connector.apiCalls[0].headers[0].name",
            "connector.apiCalls[0].name",
            "connector.apiCalls[0].pagination.pageParameter",
            "connector.apiCalls[0].pagination.pageSize",
            "connector.apiCalls[0].pagination.pageSizeParameter",
            "connector.apiCalls[0].path",
            "connector.apiCalls[0].queryParameters[0].name",
            "connector.authentication.password",
            "connector.authentication.username",
            "connector.baseUrl");
    assertThat(
            validator.validate(
                new HttpConnectorConfiguration(null, null, List.of()),
                ConnectorValidator.Stage.RELEASE))
        .extracting(ConnectorViolation::path)
        .containsExactly("connector.apiCalls", "connector.baseUrl");
  }

  private static Stream<Arguments> incompleteAuthentications() {
    return Stream.of(
        arguments(new BearerToken(null), List.of("token")),
        arguments(new ApiToken(null, null, null), List.of("in", "name", "value")),
        arguments(
            new OAuth2(null, null, null, null), List.of("clientId", "clientSecret", "tokenUrl")),
        arguments(new ClientCertificate(null, null), List.of("certificate", "privateKey")));
  }

  @ParameterizedTest
  @MethodSource("incompleteAuthentications")
  void validate_httpAuthenticationAtRelease_reportsEachMissingValueOnlyAtRelease(
      Authentication authentication, List<String> fields) {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration("https://api.example.org", authentication, List.of());

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT)).isEmpty();
    assertThat(validator.validate(connector, ConnectorValidator.Stage.RELEASE))
        .extracting(ConnectorViolation::path)
        .containsExactlyElementsOf(
            Stream.concat(
                    Stream.of("connector.apiCalls"),
                    fields.stream().map(field -> "connector.authentication." + field))
                .sorted()
                .toList());
  }

  @Test
  void validate_httpOAuth2WithHttpTokenUrl_reportsTheTokenUrl() {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org",
            new OAuth2("http://auth.example.org/token", "c", null, null),
            null);

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .containsExactly(
            new ConnectorViolation(
                "connector.authentication.tokenUrl", "'tokenUrl' must use https"));
  }

  @Test
  void validate_nextLinkAtDraft_allowsAnIncompletePath() {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org", null, List.of(paged(new NextLink(null, null))));
    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT)).isEmpty();
    assertThat(validator.validate(connector, ConnectorValidator.Stage.RELEASE))
        .extracting(ConnectorViolation::path)
        .containsExactly("connector.apiCalls[0].pagination.nextLinkPath");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "$.links.next",
        "$._links.next.href",
        "$['@odata.nextLink']",
        "$['@iot.nextLink']",
        "$.custom[0].url"
      })
  void validate_nextLinkAtRelease_acceptsAnyRestrictedPath(String path) {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org", null, List.of(paged(new NextLink(path, 1))));
    assertThat(validator.validate(connector, ConnectorValidator.Stage.RELEASE)).isEmpty();
  }

  @Test
  void validate_nextLinkAtDraft_namesTheInvalidPathAndMaxLinks() {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org", null, List.of(paged(new NextLink("$..next", 0))));
    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .extracting(ConnectorViolation::path)
        .containsExactly(
            "connector.apiCalls[0].pagination.maxLinks",
            "connector.apiCalls[0].pagination.nextLinkPath");
  }

  @Test
  void validate_httpPaginationsAtRelease_reportsEachMissingRequiredValue() {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org",
            null,
            List.of(
                paged(new Cursor(null, null, null, null, null)),
                paged(new LimitOffset(null, null, null, null, null))));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.RELEASE))
        .extracting(ConnectorViolation::path)
        .containsExactly(
            "connector.apiCalls[0].pagination.cursorIn",
            "connector.apiCalls[0].pagination.cursorParameter",
            "connector.apiCalls[0].pagination.cursorPath",
            "connector.apiCalls[1].pagination.offsetParameter");
  }

  @Test
  void validate_httpPaginationsAtDraft_reportsValuesOutOfRange() {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org",
            null,
            List.of(
                paged(new Cursor("$.c", "c", ParameterLocation.QUERY, "l", 0)),
                paged(new LimitOffset(null, null, 0, -1, null))));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .extracting(ConnectorViolation::path)
        .containsExactly(
            "connector.apiCalls[0].pagination.limit",
            "connector.apiCalls[1].pagination.limit",
            "connector.apiCalls[1].pagination.offset");
  }

  private static ApiCall paged(Pagination pagination) {
    return new ApiCall("a", null, "/x", null, null, null, pagination);
  }

  @ParameterizedTest(name = "{0} accepted: {1}")
  @MethodSource("jsonPaths")
  void validate_httpJsonPathAtDraft_acceptsOnlyMemberNamesAndArrayIndexes(
      String jsonPath, boolean accepted) {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org",
            null,
            List.of(
                paged(new Cursor(jsonPath, "c", ParameterLocation.QUERY, "l", 1)),
                paged(new LimitOffset(null, null, null, null, jsonPath)),
                new ApiCall("e", null, "/x", null, null, jsonPath, null)));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .extracting(ConnectorViolation::path)
        .isEqualTo(
            accepted
                ? List.of()
                : List.of(
                    "connector.apiCalls[0].pagination.cursorPath",
                    "connector.apiCalls[1].pagination.countPath",
                    "connector.apiCalls[2].entriesPath"));
  }

  @Test
  void validate_invalidEntriesPathAtRelease_namesTheField() {
    HttpConnectorConfiguration connector =
        new HttpConnectorConfiguration(
            "https://api.example.org",
            null,
            List.of(new ApiCall("a", null, "/x", null, null, "$..data", null)));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.RELEASE))
        .extracting(ConnectorViolation::path)
        .containsExactly("connector.apiCalls[0].entriesPath");
  }

  private static Stream<Arguments> jsonPaths() {
    return Stream.of(
        arguments("$.next_cursor", true),
        arguments("$.meta.page-info.count", true),
        arguments("$.data[0].id", true),
        arguments("$['next cursor']['a.b']", true),
        arguments("$", false),
        arguments("next_cursor", false),
        arguments("$.data[*]", false),
        arguments("$..cursor", false),
        arguments("$.data[?(@.id > 1)]", false),
        arguments("$.data[-1]", false),
        arguments("$.data.length()", false),
        arguments("$['a\\'b']", false),
        arguments("$.1st", false));
  }

  @ParameterizedTest(name = "{0} \"{1}\" accepted: {2}")
  @MethodSource("httpCharacterSets")
  void validate_httpConnectorAtDraft_acceptsExactlyTheAllowedCharacters(
      String key, String value, boolean accepted) {
    assertThat(validator.validate(httpConnectorWith(key, value), ConnectorValidator.Stage.DRAFT))
        .extracting(ConnectorViolation::path)
        .isEqualTo(accepted ? List.of() : List.of("connector.apiCalls[0]." + key));
  }

  private static Stream<Arguments> httpCharacterSets() {
    return Stream.of(
        arguments("path", "/v1/stations.json~a:b_c-D9/", true),
        arguments("path", "/", true),
        arguments("path", "/x?token=abc", false),
        arguments("path", "/a b", false),
        arguments("path", "/a%2Fb", false),
        arguments("path", "/a#b", false),
        arguments("headers[0].name", "!#$%&'*+.^_`|~09AZaz-", true),
        arguments("headers[0].name", "X:Y", false),
        arguments("headers[0].name", "X(Y)", false),
        arguments("headers[0].value", "text/plain; q=0.5\tMünster ~", true),
        arguments("headers[0].value", "", true),
        arguments("headers[0].value", "a\r\nX-Injected: y", false),
        arguments("headers[0].value", "a\u0000b", false),
        arguments("headers[0].value", "a\u007Fb", false),
        arguments("headers[0].value", "price in €", false));
  }

  @ParameterizedTest(name = "where \"{0}\" accepted: {1}")
  @MethodSource("sqlWhereClauses")
  void validate_sqlConnectorAtDraft_acceptsOnlyAWhereClauseOfOneStatement(
      String where, boolean accepted) {
    SqlConnectorConfiguration connector =
        new SqlConnectorConfiguration(
            "postgres", "postgres://db.example:5432/city", "t", List.of("id"), where, "u", null);

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .extracting(ConnectorViolation::path)
        .isEqualTo(accepted ? List.of() : List.of("connector.where"));
  }

  private static Stream<Arguments> sqlWhereClauses() {
    return Stream.of(
        arguments("id > 100", true),
        arguments("note = 'why?'", true),
        arguments("x > 1; SELECT 1", false),
        arguments("x > 1;", false),
        arguments("note = 'a;b'", false));
  }

  @ParameterizedTest(name = "dsn \"{0}\" accepted: {1}")
  @MethodSource("sqlDsns")
  void validate_sqlConnectorAtDraft_acceptsOnlyAnUnambiguousDsn(String dsn, boolean accepted) {
    SqlConnectorConfiguration connector =
        new SqlConnectorConfiguration("postgres", dsn, "t", List.of("id"), null, "u", null);

    assertThat(
            new ConnectorValidator(List.of(new SqlConnectorRules()))
                .validate(connector, ConnectorValidator.Stage.DRAFT))
        .extracting(ConnectorViolation::path)
        .isEqualTo(accepted ? List.of() : List.of("connector.dsn"));
  }

  private static Stream<Arguments> sqlDsns() {
    return Stream.of(
        arguments("postgres://db.example:5432/city?sslmode=require", true),
        arguments("postgres://db.example:5432/city?sslmode=require&sslmode=disable", false),
        arguments("postgres://db.example:5432/city?SSLMODE=require&sslmode=disable", false),
        arguments("postgres://db.example:5432#@attacker.example/city", false));
  }

  private static HttpConnectorConfiguration httpConnectorWith(String key, String value) {
    String path = key.equals("path") ? value : "/stations";
    String headerName = key.equals("headers[0].name") ? value : "Accept";
    String headerValue = key.equals("headers[0].value") ? value : "v";
    return new HttpConnectorConfiguration(
        "https://api.example.org",
        null,
        List.of(
            new ApiCall(
                "a",
                null,
                path,
                List.of(),
                List.of(new Parameter(headerName, headerValue)),
                null,
                new PageNumber("page", "size", 1, 0, null))));
  }

  @Test
  void validate_withIdentifiedElements_requiresIdentityUniqueWithinItsList() {
    TestConnector connector =
        new TestConnector(
            null,
            "n",
            List.of(
                new Call("a", new Auth("t", List.of(new Header(null), new Header("h")))),
                new Call("a", new Auth("t", List.of(new Header("h"))))));

    assertThat(validator.validate(connector, ConnectorValidator.Stage.DRAFT))
        .containsExactly(
            new ConnectorViolation(
                "connector.api_calls[0].auth.headers[0]", "must have an identity"),
            new ConnectorViolation(
                "connector.api_calls[1]", "must have an identity unique within its list"));
  }

  @Test
  void validate_withRulesBeanOfTheConnectorType_addsItsViolationsForTheStage() {
    context
        .withBean(SqlRules.class)
        .withBean(MqttRules.class)
        .run(
            ctx ->
                assertThat(
                        ctx.getBean(ConnectorValidator.class)
                            .validate(validConnector(), ConnectorValidator.Stage.RELEASE))
                    .containsExactly(ConnectorViolation.at("name", "SQL rule broken at RELEASE")));
  }

  @Test
  void validate_withoutRulesBeans_startsAndAppliesOnlyTheAnnotations() {
    context.run(
        ctx ->
            assertThat(
                    ctx.getBean(ConnectorValidator.class)
                        .validate(validConnector(), ConnectorValidator.Stage.RELEASE))
                .isEmpty());
  }

  @Test
  void validate_withoutConnector_returnsNoViolations() {
    assertThat(validator.validate(null, ConnectorValidator.Stage.RELEASE)).isEmpty();
  }

  @Test
  void requireValid_withViolations_throwsThemAll() {
    TestConnector connector =
        new TestConnector(null, null, List.of(new Call("a", new Auth(null, List.of()))));

    assertThatThrownBy(() -> validator.requireValid(connector, ConnectorValidator.Stage.RELEASE))
        .isInstanceOfSatisfying(
            ConnectorValidationException.class,
            e ->
                assertThat(e.getViolations())
                    .extracting(ConnectorViolation::path)
                    .containsExactly("connector.api_calls[0].auth.token", "connector.name"));
  }

  @Test
  void requireValid_withoutViolations_passes() {
    validator.requireValid(validConnector(), ConnectorValidator.Stage.RELEASE);
  }

  /**
   * Without this test, a connector record named by other Jackson means than {@code @JsonProperty}
   * would get field errors under a key that the client never sent.
   */
  @Test
  void jsonKey_ofEveryConnectorRecordComponent_isTheKeyThatJacksonReads() {
    DeserializationConfig config = new JsonMapper().deserializationConfig();
    ClassIntrospector introspector = config.classIntrospectorInstance().forOperation(config);
    Deque<Class<?>> pending = new ArrayDeque<>();
    Arrays.stream(ConnectorConfiguration.class.getAnnotation(JsonSubTypes.class).value())
        .forEach(type -> pending.add(type.value()));
    Set<Class<?>> seen = new HashSet<>();
    SoftAssertions softly = new SoftAssertions();
    while (!pending.isEmpty()) {
      Class<?> type = pending.pop();
      if (!seen.add(type)) {
        continue;
      }
      JavaType javaType = config.constructType(type);
      Map<String, String> jacksonKeys =
          introspector
              .introspectForDeserialization(
                  javaType, introspector.introspectClassAnnotations(javaType))
              .findProperties()
              .stream()
              .collect(
                  Collectors.toMap(
                      BeanPropertyDefinition::getInternalName, BeanPropertyDefinition::getName));
      for (RecordComponent component : type.getRecordComponents()) {
        softly
            .assertThat(ConnectorValidator.jsonKey(type, component.getName()))
            .as("%s.%s", type.getSimpleName(), component.getName())
            .isEqualTo(jacksonKeys.get(component.getName()));
        if (component.getType().isRecord()) {
          pending.add(component.getType());
        }
      }
    }
    softly.assertAll();
  }

  private static TestConnector validConnector() {
    return new TestConnector(1, "n", List.of(new Call("a", new Auth("t", List.of()))));
  }

  record TestConnector(
      @JsonProperty("max_retries") @Max(value = 3, message = "too many") Integer maxRetries,
      @NotNull(groups = OnRelease.class, message = "name required") String name,
      @JsonProperty("api_calls") List<@Valid Call> apiCalls)
      implements ConnectorConfiguration {

    @Override
    public ConnectorType connectorType() {
      return ConnectorType.SQL;
    }
  }

  record Call(String name, @Valid Auth auth) implements IdentifiedElement {
    @Override
    public Object identity() {
      return name;
    }
  }

  record Auth(
      @NotBlank(groups = OnRelease.class, message = "token required") String token,
      List<Header> headers) {}

  record Header(String key) implements IdentifiedElement {
    @Override
    public Object identity() {
      return key;
    }
  }

  static class SqlRules implements ConnectorRules {
    @Override
    public ConnectorType connectorType() {
      return ConnectorType.SQL;
    }

    @Override
    public List<ConnectorViolation> validate(
        ConnectorConfiguration connector, ConnectorValidator.Stage stage) {
      return List.of(ConnectorViolation.at("name", "SQL rule broken at " + stage));
    }
  }

  static class MqttRules implements ConnectorRules {
    @Override
    public ConnectorType connectorType() {
      return ConnectorType.MQTT;
    }

    @Override
    public List<ConnectorViolation> validate(
        ConnectorConfiguration connector, ConnectorValidator.Stage stage) {
      return List.of(ConnectorViolation.at("name", "MQTT rule broken"));
    }
  }
}

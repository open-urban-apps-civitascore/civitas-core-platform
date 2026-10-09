package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.IdentifiedElement;
import de.civitascore.portal.model.connector.OnRelease;
import de.civitascore.portal.model.embedded.ConnectorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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

package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import de.civitascore.portal.model.embedded.ConnectorType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ConnectorJsonTest {

  private static final JsonMapper MAPPER = new JsonMapper();
  private static final UnaryOperator<String> ENCRYPT = plain -> "ENC(" + plain + ")";
  private static final Pattern SECRET_NAME =
      Pattern.compile("(?i).*(password|secret|token|apikey|credential|privatekey).*");

  private static final NestedSecretsConnector NESTED =
      new NestedSecretsConnector(
          new NestedSecretsConnector.Auth("u", Secret.of("p")),
          List.of(new NestedSecretsConnector.Call("/a", Secret.of("k"))),
          List.of(Secret.of("t")));

  private static JsonNode json(String json) {
    return MAPPER.readTree(json.replace('\'', '"'));
  }

  /** A connector with secrets in a nested object, in list elements and as list elements. */
  record NestedSecretsConnector(Auth auth, List<Call> calls, List<Secret> tokens)
      implements ConnectorConfiguration {

    record Auth(String user, Secret password) {}

    record Call(String path, Secret apiKey) {}

    @Override
    public ConnectorType connectorType() {
      return ConnectorType.SQL;
    }
  }

  @Test
  void read_withSqlConnector_returnsTypedConfiguration() {
    ConnectorConfiguration connector =
        ConnectorJson.read(
            json(
                "{'connectorType':'SQL','driver':'postgres','dsn':'postgres://u:p@db:5432/x',"
                    + "'columns':['id'],'password':'pw'}"));

    assertThat(connector)
        .isEqualTo(
            new SqlConnectorConfiguration(
                "postgres",
                "postgres://db:5432/x",
                null,
                List.of("id"),
                null,
                null,
                Secret.of("pw")));
  }

  @Test
  void read_withExplicitNullProtocolVersion_appliesDefault() {
    MqttConnectorConfiguration connector =
        (MqttConnectorConfiguration)
            ConnectorJson.read(json("{'connectorType':'MQTT','protocol_version':null}"));

    assertThat(connector.protocolVersion()).isEqualTo("3");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{'connectorType':'SQL','table':'t','tabel':'t'} | Unknown key: connector.tabel",
        "{'connectorType':'MQTT','tls':{'enabled':true,'verify':false}}"
            + " | Unknown key: connector.tls.verify",
        "{'connectorType':'HTTP'} | Unknown connectorType: HTTP",
        "{'dsn':'postgres://db/x'} | Missing connector.connectorType",
        "{'connectorType':'MQTT','qos':'high'} | Invalid value: connector.qos",
        "{'connectorType':'MQTT','urls':['tcp://u:pw@broker:1883','no url']}"
            + " | Invalid value: connector.urls[1]",
        "{'connectorType':'SQL','dsn':'postgres://u:pw@ bad'} | Invalid value: connector.dsn",
        "['SQL'] | Invalid value: connector"
      })
  void read_withInvalidConnector_namesTheKeyButNoValue(String connector, String message) {
    assertThatThrownBy(() -> ConnectorJson.read(json(connector)))
        .isInstanceOf(InvalidConnectorException.class)
        .hasMessage(message);
  }

  @Test
  void toMaskedTree_withSecrets_masksSecretsAtEveryDepth() {
    ObjectNode masked = ConnectorJson.toMaskedTree(NESTED);

    assertThat(masked.at("/auth/password").asString()).isEqualTo(Secret.PLACEHOLDER);
    assertThat(masked.at("/calls/0/apiKey").asString()).isEqualTo(Secret.PLACEHOLDER);
    assertThat(masked.at("/tokens/0").asString()).isEqualTo(Secret.PLACEHOLDER);
    assertThat(masked.at("/auth/user").asString()).isEqualTo("u");
  }

  @Test
  void toDocument_withClearTextSecrets_encryptsSecretsAtEveryDepth() {
    Map<String, Object> document = ConnectorJson.toDocument(NESTED, null, ENCRYPT);

    JsonNode tree = MAPPER.valueToTree(document);
    assertThat(tree.at("/auth/password").asString()).isEqualTo("ENC(p)");
    assertThat(tree.at("/calls/0/apiKey").asString()).isEqualTo("ENC(k)");
    assertThat(tree.at("/tokens/0").asString()).isEqualTo("ENC(t)");
    assertThat(tree.path("connectionType").asString()).isEqualTo("sql");
    assertThat(tree.has("connectorType")).isFalse();
  }

  @Test
  void toDocument_withPlaceholders_takesStoredCiphertextAtSamePosition() {
    Secret placeholder = Secret.of(Secret.PLACEHOLDER);
    NestedSecretsConnector masked =
        new NestedSecretsConnector(
            new NestedSecretsConnector.Auth("u", placeholder),
            List.of(
                new NestedSecretsConnector.Call("/a", placeholder),
                new NestedSecretsConnector.Call("/b", placeholder)),
            List.of(placeholder, placeholder));
    Map<String, Object> stored = ConnectorJson.toDocument(NESTED, null, ENCRYPT);

    JsonNode tree = MAPPER.valueToTree(ConnectorJson.toDocument(masked, stored, ENCRYPT));

    assertThat(tree.at("/auth/password").asString()).isEqualTo("ENC(p)");
    assertThat(tree.at("/calls/0/apiKey").asString()).isEqualTo("ENC(k)");
    assertThat(tree.at("/calls/1/apiKey").isNull()).isTrue();
    assertThat(tree.at("/tokens/0").asString()).isEqualTo("ENC(t)");
    assertThat(tree.at("/tokens/1").isNull()).isTrue();
  }

  @Test
  void toDocument_withPlaceholderAndNoStoredSecret_storesNoSecret() {
    ConnectorConfiguration connector =
        ConnectorJson.read(json("{'connectorType':'SQL','password':'********'}"));

    Map<String, Object> document = ConnectorJson.toDocument(connector, null, ENCRYPT);

    assertThat(document).containsEntry("password", null);
  }

  @Test
  void toDocument_withPlaceholderAndStoredDocumentOfOtherType_storesNoSecret() {
    ConnectorConfiguration connector =
        ConnectorJson.read(json("{'connectorType':'MQTT','password':'********'}"));
    Map<String, Object> stored = Map.of("connectionType", "sql", "password", "ENC(sql)");

    Map<String, Object> document = ConnectorJson.toDocument(connector, stored, ENCRYPT);

    assertThat(document).containsEntry("password", null);
  }

  @Test
  void toDocument_withClientSuppliedCiphertext_encryptsItAgain() {
    ConnectorConfiguration connector =
        ConnectorJson.read(json("{'connectorType':'SQL','password':'ENC(foreign)'}"));

    assertThat(ConnectorJson.toDocument(connector, null, ENCRYPT))
        .containsEntry("password", "ENC(ENC(foreign))");
  }

  @Test
  void readStored_withDocumentOfCurrentRelease_readsIt() {
    Map<String, Object> stored =
        Map.of(
            "$schema", "https://civitasconnect.digital/core/datasource/v1",
            "connectionType", "mqtt",
            "urls", List.of("tcp://broker:1883"),
            "protocol_version", "5",
            "tls", Map.of("enabled", true),
            "password", "ENC(x)",
            "removedKey", "ignored");

    MqttConnectorConfiguration connector =
        (MqttConnectorConfiguration) ConnectorJson.readStored(ConnectorType.MQTT, stored);

    assertThat(connector.urls()).containsExactly("tcp://broker:1883");
    assertThat(connector.protocolVersion()).isEqualTo("5");
    assertThat(connector.tls().enabled()).isTrue();
    assertThat(connector.password()).isEqualTo(Secret.of("ENC(x)"));
  }

  @Test
  void toDocument_ofStoredConnectorWithoutChange_returnsStoredContent() {
    Map<String, Object> stored =
        ConnectorJson.toDocument(
            ConnectorJson.read(
                json("{'connectorType':'SQL','dsn':'postgres://db/x','password':'p'}")),
            null,
            ENCRYPT);
    ConnectorConfiguration unchanged =
        ConnectorJson.patch(ConnectorJson.readStored(ConnectorType.SQL, stored), json("{}"));

    assertThat(ConnectorJson.toDocument(unchanged, stored, ENCRYPT)).isEqualTo(stored);
  }

  @Test
  void patch_withNullAndBlankValues_clearsFields() {
    ConnectorConfiguration current =
        ConnectorJson.read(
            json("{'connectorType':'SQL','table':'t','where':'id > 1','user':'u','password':'p'}"));

    SqlConnectorConfiguration patched =
        (SqlConnectorConfiguration)
            ConnectorJson.patch(current, json("{'where':null,'user':'  ','table':'t2'}"));

    assertThat(patched.where()).isNull();
    assertThat(patched.user()).isNull();
    assertThat(patched.table()).isEqualTo("t2");
    assertThat(patched.password()).isEqualTo(Secret.of(Secret.PLACEHOLDER));
  }

  @Test
  void patch_withOtherConnectorType_replacesConnector() {
    ConnectorConfiguration current =
        ConnectorJson.read(json("{'connectorType':'SQL','table':'t','password':'p'}"));

    ConnectorConfiguration patched =
        ConnectorJson.patch(current, json("{'connectorType':'MQTT','qos':1}"));

    assertThat(patched)
        .isEqualTo(
            new MqttConnectorConfiguration(null, null, 1, null, null, null, null, null, null));
  }

  @Test
  void patch_withUnknownKey_throws() {
    ConnectorConfiguration current = ConnectorJson.read(json("{'connectorType':'SQL'}"));

    assertThatThrownBy(() -> ConnectorJson.patch(current, json("{'tabel':'t'}")))
        .isInstanceOf(InvalidConnectorException.class)
        .hasMessage("Unknown key: connector.tabel");
  }

  @Test
  void patch_withNull_removesConnector() {
    ConnectorConfiguration current = ConnectorJson.read(json("{'connectorType':'SQL'}"));

    assertThat(ConnectorJson.patch(current, json("null"))).isNull();
  }

  @Test
  void connectorTypes_eachHasARegisteredConfiguration() {
    Set<String> registered =
        Arrays.stream(ConnectorConfiguration.class.getAnnotation(JsonSubTypes.class).value())
            .map(JsonSubTypes.Type::name)
            .collect(Collectors.toSet());

    assertThat(registered)
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(ConnectorType.values()).map(Enum::name).toList());
  }

  /** Without this test, a secret held as a String would be stored in clear text and returned. */
  @Test
  void registeredConfigurations_holdEverySecretAsSecret() {
    List<String> secretsAsText = new ArrayList<>();
    Deque<Class<?>> pending = new ArrayDeque<>();
    Arrays.stream(ConnectorConfiguration.class.getAnnotation(JsonSubTypes.class).value())
        .forEach(type -> pending.add(type.value()));
    pending.add(NestedSecretsConnector.class);
    Set<Class<?>> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      Class<?> type = pending.pop();
      if (!seen.add(type)) {
        continue;
      }
      assertThat(type.isRecord()).as("%s is a record", type.getName()).isTrue();
      for (RecordComponent component : type.getRecordComponents()) {
        Class<?> valueType = valueType(component.getGenericType());
        if (SECRET_NAME.matcher(component.getName()).matches() && valueType != Secret.class) {
          secretsAsText.add(type.getSimpleName() + "." + component.getName());
        }
        if (valueType.isRecord()) {
          pending.add(valueType);
        }
      }
    }

    assertThat(secretsAsText).isEmpty();
  }

  private static Class<?> valueType(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      return valueType(parameterized.getActualTypeArguments()[0]);
    }
    return (Class<?>) type;
  }
}

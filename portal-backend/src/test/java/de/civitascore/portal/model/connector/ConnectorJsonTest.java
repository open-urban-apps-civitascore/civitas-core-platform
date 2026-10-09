package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
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
          List.of(new NestedSecretsConnector.Call("a", Secret.of("k"))));

  private static JsonNode json(String json) {
    return MAPPER.readTree(json.replace('\'', '"'));
  }

  /** A connector with secrets in a nested object and in list elements. */
  record NestedSecretsConnector(Auth auth, List<Call> calls) implements ConnectorConfiguration {

    record Auth(String user, Secret password) {}

    record Call(String name, Secret apiKey) implements IdentifiedElement {
      @Override
      public Object identity() {
        return name;
      }
    }

    @Override
    public ConnectorType connectorType() {
      return ConnectorType.SQL;
    }
  }

  private static NestedSecretsConnector.Call call(String name, Secret apiKey) {
    return new NestedSecretsConnector.Call(name, apiKey);
  }

  private static NestedSecretsConnector withCalls(NestedSecretsConnector.Call... calls) {
    return new NestedSecretsConnector(
        new NestedSecretsConnector.Auth("u", Secret.of(Secret.PLACEHOLDER)), List.of(calls));
  }

  private static final NestedSecretsConnector.Call STORED_A = call("a", Secret.stored("ENC(a)"));
  private static final NestedSecretsConnector.Call STORED_B = call("b", Secret.stored("ENC(b)"));
  private static final NestedSecretsConnector.Call STORED_C = call("c", Secret.stored("ENC(c)"));

  private static JsonNode documentOf(
      NestedSecretsConnector connector, NestedSecretsConnector stored) {
    return MAPPER.valueToTree(ConnectorJson.writeDocument(connector, stored, ENCRYPT));
  }

  private static List<String> apiKeys(JsonNode document) {
    List<String> keys = new ArrayList<>();
    document
        .path("calls")
        .forEach(
            c -> keys.add(c.path("name").asString() + "=" + c.path("apiKey").asString("null")));
    return keys;
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
    assertThat(masked.at("/auth/user").asString()).isEqualTo("u");
  }

  @Test
  void toDocument_withClearTextSecrets_encryptsSecretsAtEveryDepth() {
    JsonNode document = documentOf(NESTED, null);

    assertThat(document.at("/auth/password").asString()).isEqualTo("ENC(p)");
    assertThat(document.at("/calls/0/apiKey").asString()).isEqualTo("ENC(k)");
    assertThat(document.path("connectionType").asString()).isEqualTo("sql");
    assertThat(document.has("connectorType")).isFalse();
  }

  @Test
  void toDocument_withPlaceholderInNestedObject_takesStoredCiphertext() {
    NestedSecretsConnector stored =
        new NestedSecretsConnector(
            new NestedSecretsConnector.Auth("u", Secret.stored("ENC(p)")), List.of());

    assertThat(documentOf(withCalls(), stored).at("/auth/password").asString()).isEqualTo("ENC(p)");
  }

  @Test
  void toDocument_withReorderedListElements_resolvesEachPlaceholderByIdentity() {
    NestedSecretsConnector stored = withCalls(STORED_A, STORED_B, STORED_C);
    Secret placeholder = Secret.of(Secret.PLACEHOLDER);

    JsonNode document =
        documentOf(
            withCalls(call("c", placeholder), call("a", placeholder), call("b", placeholder)),
            stored);

    assertThat(apiKeys(document)).containsExactly("c=ENC(c)", "a=ENC(a)", "b=ENC(b)");
  }

  @Test
  void toDocument_withInsertedListElement_givesItNoStoredSecret() {
    NestedSecretsConnector stored = withCalls(STORED_A, STORED_B);
    Secret placeholder = Secret.of(Secret.PLACEHOLDER);

    JsonNode document =
        documentOf(
            withCalls(call("new", placeholder), call("a", placeholder), call("b", placeholder)),
            stored);

    assertThat(apiKeys(document)).containsExactly("new=null", "a=ENC(a)", "b=ENC(b)");
  }

  @Test
  void toDocument_withRemovedListElement_keepsTheSecretsOfTheOthers() {
    NestedSecretsConnector stored = withCalls(STORED_A, STORED_B, STORED_C);
    Secret placeholder = Secret.of(Secret.PLACEHOLDER);

    JsonNode document =
        documentOf(withCalls(call("b", placeholder), call("c", placeholder)), stored);

    assertThat(apiKeys(document)).containsExactly("b=ENC(b)", "c=ENC(c)");
  }

  @Test
  void toDocument_withIdentityThatTwoStoredElementsShare_givesNoStoredSecret() {
    NestedSecretsConnector stored = withCalls(STORED_A, call("a", Secret.stored("ENC(a2)")));

    JsonNode document = documentOf(withCalls(call("a", Secret.of(Secret.PLACEHOLDER))), stored);

    assertThat(apiKeys(document)).containsExactly("a=null");
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
    assertThat(connector.password()).isEqualTo(Secret.stored("ENC(x)"));
  }

  @Test
  void toDocument_ofStoredConnector_keepsTheStoredCiphertext() {
    Map<String, Object> stored =
        Map.of("connectionType", "sql", "dsn", "postgres://db/x", "password", "ENC(stored)");

    ConnectorConfiguration connector = ConnectorJson.readStored(ConnectorType.SQL, stored);

    assertThat(ConnectorJson.toDocument(connector, null, ENCRYPT))
        .containsEntry("password", "ENC(stored)");
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

  /**
   * Without this test, a connector type added to one listing but not to another would be rejected,
   * undocumented or unreadable from the registry only at runtime.
   */
  @Test
  void connectorTypes_areListedInEveryPlaceThatListsConnectorTypes() throws IOException {
    List<String> enumTypes = Arrays.stream(ConnectorType.values()).map(Enum::name).toList();
    Map<String, Class<?>> subTypes =
        Arrays.stream(ConnectorConfiguration.class.getAnnotation(JsonSubTypes.class).value())
            .collect(Collectors.toMap(JsonSubTypes.Type::name, JsonSubTypes.Type::value));
    Schema schema = ConnectorConfiguration.class.getAnnotation(Schema.class);
    Map<String, Class<?>> discriminatorMapping =
        Arrays.stream(schema.discriminatorMapping())
            .collect(Collectors.toMap(DiscriminatorMapping::value, DiscriminatorMapping::schema));

    SoftAssertions listings = new SoftAssertions();
    listings.assertThat(subTypes.keySet()).as("@JsonSubTypes").hasSameElementsAs(enumTypes);
    listings
        .assertThat(discriminatorMapping)
        .as("@Schema discriminatorMapping")
        .containsExactlyInAnyOrderEntriesOf(subTypes);
    listings
        .assertThat(List.of(schema.oneOf()))
        .as("@Schema oneOf")
        .hasSameElementsAs(subTypes.values());
    listings
        .assertThat(modelForgeConnectionTypes())
        .as("connectionType of datasource.schema.json in Model Forge")
        .hasSameElementsAs(enumTypes.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList());
    for (ConnectorType type : ConnectorType.values()) {
      listings
          .assertThat(ConnectorJson.readStored(type, Map.of()))
          .as("configuration read for %s", type)
          .isInstanceOf(subTypes.getOrDefault(type.name(), Void.class))
          .extracting(ConnectorConfiguration::connectorType)
          .isEqualTo(type);
    }
    listings.assertAll();
  }

  private static List<String> modelForgeConnectionTypes() throws IOException {
    try (InputStream schema =
        ConnectorJsonTest.class.getClassLoader().getResourceAsStream("datasource.schema.json")) {
      assertThat(schema).as("datasource.schema.json on the class path").isNotNull();
      List<String> connectionTypes = new ArrayList<>();
      MAPPER
          .readTree(schema)
          .path("$defs")
          .forEach(
              variant ->
                  connectionTypes.add(
                      variant.path("properties").path("connectionType").path("const").asString()));
      return connectionTypes;
    }
  }

  /**
   * Without this test, a secret held as a String would be stored in clear text and returned, and a
   * secret in a list element without identity could resolve to the secret of another element.
   */
  @Test
  void registeredConfigurations_reachEverySecret() {
    List<String> violations = new ArrayList<>();
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
        String name = type.getSimpleName() + "." + component.getName();
        Class<?> valueType = valueType(component.getGenericType());
        if (SECRET_NAME.matcher(component.getName()).matches() && valueType != Secret.class) {
          violations.add(name + " holds a secret as text");
        }
        if (component.getGenericType() instanceof ParameterizedType
            && carriesSecret(valueType, new HashSet<>())
            && !IdentifiedElement.class.isAssignableFrom(valueType)) {
          violations.add(name + " lists elements with secrets but without identity");
        }
        if (valueType.isRecord()) {
          pending.add(valueType);
        }
      }
    }

    assertThat(violations).isEmpty();
  }

  private static boolean carriesSecret(Class<?> type, Set<Class<?>> seen) {
    if (type == Secret.class) {
      return true;
    }
    if (!type.isRecord() || !seen.add(type)) {
      return false;
    }
    return Arrays.stream(type.getRecordComponents())
        .anyMatch(c -> carriesSecret(valueType(c.getGenericType()), seen));
  }

  private static Class<?> valueType(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      return valueType(parameterized.getActualTypeArguments()[0]);
    }
    return (Class<?>) type;
  }
}

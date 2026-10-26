package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.GenericArrayType;
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
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ConnectorJsonTest {

  private static final JsonMapper MAPPER = new JsonMapper();
  private static final UnaryOperator<String> ENCRYPT = plain -> "ENC(" + plain + ")";
  private static final Pattern SECRET_NAME =
      Pattern.compile("(?i)(?!.*url$).*(password|secret|token|apikey|credential|privatekey).*");

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
        "{'connectorType':'FTP'} | Unknown connectorType: FTP",
        "{'dsn':'postgres://db/x'} | Missing connector.connectorType",
        "{'connectorType':'MQTT','qos':'high'} | Invalid value: connector.qos",
        "{'connectorType':'MQTT','qos':1.5} | Invalid value: connector.qos",
        "{'connectorType':'MQTT','qos':'1'} | Invalid value: connector.qos",
        "{'connectorType':'MQTT','urls':['tcp://u:pw@broker:1883','no url']}"
            + " | Invalid value: connector.urls[1]",
        "{'connectorType':'SQL','dsn':'postgres://u:pw@ bad'} | Invalid value: connector.dsn",
        "['SQL'] | Invalid value: connector",
        "{'connectorType':'HTTP','apiCalls':[{'foo':1}]} | Unknown key: connector.apiCalls[0].foo",
        "{'connectorType':'HTTP','authentication':{'type':'DIGEST'}}"
            + " | Unknown connector.authentication.type: DIGEST",
        "{'connectorType':'HTTP','authentication':{'type':'OAUTH2','tokenUrl':'ftp://a.example.org'}}"
            + " | Invalid value: connector.authentication.tokenUrl",
        "{'connectorType':'HTTP','authentication':{'type':'API_TOKEN','in':'BODY'}}"
            + " | Invalid value: connector.authentication.in",
        "{'connectorType':'HTTP','apiCalls':[{'pagination':{'pageSize':1}}]}"
            + " | Missing connector.apiCalls[0].pagination.type",
        "{'connectorType':'HTTP','apiCalls':[{'pagination':{'type':'PAGE_NUMBER','pageSize':1.5}}]}"
            + " | Invalid value: connector.apiCalls[0].pagination.pageSize",
        "{'connectorType':'HTTP','apiCalls':[{'pagination':{'type':'PAGE_NUMBER','startPage':'abc'}}]}"
            + " | Invalid value: connector.apiCalls[0].pagination.startPage",
        "{'connectorType':'HTTP','apiCalls':[{'pagination':{'type':'PAGE_NUMBER','startPage':'5'}}]}"
            + " | Invalid value: connector.apiCalls[0].pagination.startPage",
        "{'connectorType':'HTTP','apiCalls':[{'pagination':{'type':'PAGE_NUMBER','pageSize':''}}]}"
            + " | Invalid value: connector.apiCalls[0].pagination.pageSize",
        "{'connectorType':'HTTP','apiCalls':[{},{'headers':[{'name':'a'},null]}]}"
            + " | Invalid value: connector.apiCalls[1].headers[1]",
        "{'connectorType':'HTTP','baseUrl':'ftp://api.example.org'} | Invalid value: connector.baseUrl",
        "{'connectorType':'HTTP','authentication':{'type':'API_TOKEN','in':1}}"
            + " | Invalid value: connector.authentication.in"
      })
  void read_withInvalidConnector_namesTheKeyButNoValue(String connector, String message) {
    assertThatThrownBy(() -> ConnectorJson.read(json(connector)))
        .isInstanceOf(MalformedDataSourceRequestException.class)
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "postgres://db.example:5432/city?sslmode=require&sslmode=require",
        "postgres://db.example:5432#@attacker.example/city"
      })
  void readStored_withADsnThatAWriteRefuses_readsIt(String dsn) {
    ConnectorConfiguration stored =
        ConnectorJson.readStored(
            ConnectorType.SQL, Map.of("connectionType", "sql", "dsn", dsn, "table", "t"));

    assertThat(((SqlConnectorConfiguration) stored).dsn()).isEqualTo(dsn);
  }

  private static final Map<String, Object> STORED_SQL =
      Map.of(
          "connectionType", "sql",
          "dsn", "postgres://db.example:5432/city?sslmode=require",
          "password", "ENC(sql)");

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "postgres://other.example:5432/city?sslmode=require",
        "postgres://db.example:5433/city?sslmode=require",
        "postgres://db.example:5432/city?sslmode=disable",
        "postgres://db.example:5432/city?SSLMODE=require"
      })
  void toDocument_withStoredSecretForOtherDestination_refusesIt(String dsn) {
    ConnectorConfiguration connector =
        ConnectorJson.read(
            json("{'connectorType':'SQL','dsn':'" + dsn + "','password':'********'}"));

    assertThatThrownBy(() -> ConnectorJson.toDocument(connector, STORED_SQL, ENCRYPT))
        .isInstanceOf(StoredSecretRefusedException.class)
        .hasMessage(
            "The stored secret is kept only for the stored destination; enter it again for another one");
  }

  /** A merge patch that omits the password keeps it as a placeholder. */
  @Test
  void toDocument_withPatchOfTheHostOnly_refusesTheStoredSecret() {
    ConnectorConfiguration patched =
        ConnectorJson.patch(
            ConnectorJson.readStored(ConnectorType.SQL, STORED_SQL),
            json("{'dsn':'postgres://other.example:5432/city?sslmode=require'}"));

    assertThatThrownBy(() -> ConnectorJson.toDocument(patched, STORED_SQL, ENCRYPT))
        .isInstanceOf(StoredSecretRefusedException.class);
  }

  @Test
  void toDocument_withStoredSecretForSameDestination_keepsIt() {
    ConnectorConfiguration connector =
        ConnectorJson.read(
            json(
                "{'connectorType':'SQL','dsn':'postgres://db.example:5432/other?sslmode=require',"
                    + "'table':'other','password':'********'}"));

    assertThat(ConnectorJson.toDocument(connector, STORED_SQL, ENCRYPT))
        .containsEntry("password", "ENC(sql)");
  }

  @Test
  void toDocument_withNewSecretForOtherDestination_storesTheNewSecret() {
    ConnectorConfiguration connector =
        ConnectorJson.read(
            json(
                "{'connectorType':'SQL','dsn':'postgres://other.example:5432/city',"
                    + "'password':'new-secret'}"));

    assertThat(ConnectorJson.toDocument(connector, STORED_SQL, ENCRYPT))
        .containsEntry("password", "ENC(new-secret)");
  }

  @Test
  void toDocument_withStoredSecretForOtherBrokerOrApiHost_refusesIt() {
    Map<String, Object> storedMqtt =
        Map.of(
            "connectionType",
            "mqtt",
            "urls",
            List.of("tcp://broker.example:1883"),
            "password",
            "ENC(mqtt)");
    ConnectorConfiguration mqtt =
        ConnectorJson.read(
            json(
                "{'connectorType':'MQTT','urls':['tcp://other.example:1883'],'password':'********'}"));
    ConnectorConfiguration http =
        ConnectorJson.read(
            json(HTTP_BASIC.formatted("********").replace("api.example.org", "other.example")));

    assertThatThrownBy(() -> ConnectorJson.toDocument(mqtt, storedMqtt, ENCRYPT))
        .isInstanceOf(StoredSecretRefusedException.class);
    assertThatThrownBy(() -> ConnectorJson.toDocument(http, storedHttpBasic(), ENCRYPT))
        .isInstanceOf(StoredSecretRefusedException.class);
  }

  @Test
  void requireStoredSecrets_withPlaceholderAndNoStoredDocument_namesThePlaceholder() {
    ConnectorConfiguration connector =
        ConnectorJson.read(json("{'connectorType':'SQL','password':'********'}"));

    assertThatThrownBy(() -> ConnectorJson.requireStoredSecrets(connector, null))
        .isInstanceOf(MalformedDataSourceRequestException.class)
        .hasMessage("No stored secret for ******** at connector.password");
  }

  @Test
  void requireStoredSecrets_withPlaceholderAndStoredSecret_passes() {
    ConnectorConfiguration connector =
        ConnectorJson.read(json("{'connectorType':'SQL','password':'********'}"));
    Map<String, Object> stored = Map.of("connectionType", "sql", "password", "ENC(sql)");

    ConnectorJson.requireStoredSecrets(connector, stored);

    assertThat(ConnectorJson.toDocument(connector, stored, ENCRYPT))
        .containsEntry("password", "ENC(sql)");
  }

  @Test
  void requireStoredSecrets_withStoredDocumentOfOtherType_namesThePlaceholder() {
    ConnectorConfiguration connector =
        ConnectorJson.read(json("{'connectorType':'MQTT','password':'********'}"));
    Map<String, Object> stored = Map.of("connectionType", "sql", "password", "ENC(sql)");

    assertThatThrownBy(() -> ConnectorJson.requireStoredSecrets(connector, stored))
        .isInstanceOf(MalformedDataSourceRequestException.class)
        .hasMessage("No stored secret for ******** at connector.password");
  }

  @Test
  void requireStoredSecrets_withClearTextSecretsOnly_passes() {
    ConnectorConfiguration connector =
        ConnectorJson.read(json("{'connectorType':'SQL','password':'s3cret'}"));

    ConnectorJson.requireStoredSecrets(connector, null);
  }

  @Test
  void unresolvedPlaceholders_namesNestedAndListElementPlaceholdersByKeyPath() {
    NestedSecretsConnector stored = withCalls(STORED_A);
    Secret placeholder = Secret.of(Secret.PLACEHOLDER);

    assertThat(
            PlaceholderResolver.unresolvedPlaceholders(
                withCalls(call("a", placeholder), call("new", placeholder)), stored))
        .containsExactly("connector.auth.password", "connector.calls[1].apiKey");
  }

  @Test
  void unresolvedPlaceholders_withJsonPropertyName_namesTheKeyOfTheRequest() {
    record Renamed(@JsonProperty("api_key") Secret apiKey) implements ConnectorConfiguration {
      @Override
      public ConnectorType connectorType() {
        return ConnectorType.HTTP;
      }
    }

    assertThat(
            PlaceholderResolver.unresolvedPlaceholders(
                new Renamed(Secret.of(Secret.PLACEHOLDER)), null))
        .containsExactly("connector.api_key");
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
        .isInstanceOf(MalformedDataSourceRequestException.class)
        .hasMessage("Unknown key: connector.tabel");
  }

  @Test
  void patch_withNull_removesConnector() {
    ConnectorConfiguration current = ConnectorJson.read(json("{'connectorType':'SQL'}"));

    assertThat(ConnectorJson.patch(current, json("null"))).isNull();
  }

  private static final String HTTP_BASIC =
      "{'connectorType':'HTTP','baseUrl':'https://api.example.org',"
          + "'authentication':{'type':'BASIC','username':'reader','password':'%s'},"
          + "'apiCalls':[{'name':'a','path':'/x'}]}";

  private static Map<String, Object> storedHttpBasic() {
    return ConnectorJson.toDocument(
        ConnectorJson.read(json(HTTP_BASIC.formatted("secret"))), null, ENCRYPT);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '"',
      value = {
        "'type':'BASIC','username':'reader' | password",
        "'type':'BEARER_TOKEN' | token",
        "'type':'API_TOKEN','name':'X-API-Key','in':'HEADER' | value",
        "'type':'OAUTH2','tokenUrl':'https://auth.example.org/token','clientId':'c' | clientSecret",
        "'type':'CLIENT_CERTIFICATE','certificate':'pem' | privateKey",
        "'type':'CLIENT_CERTIFICATE','privateKey':'pem' | certificate"
      })
  void httpAuthenticationSecret_isMaskedInResponseEncryptedInDocumentAndKeptForPlaceholder(
      String fields, String secret) {
    String connector =
        "{'connectorType':'HTTP','authentication':{" + fields + ",'" + secret + "':'%s'}}";
    String pointer = "/authentication/" + secret;
    Map<String, Object> stored =
        ConnectorJson.toDocument(
            ConnectorJson.read(json(connector.formatted("secret"))), null, ENCRYPT);
    ConnectorConfiguration placeholder = ConnectorJson.read(json(connector.formatted("********")));

    assertThat(
            ConnectorJson.toMaskedTree(ConnectorJson.readStored(ConnectorType.HTTP, stored))
                .at(pointer)
                .asString())
        .isEqualTo(Secret.PLACEHOLDER);
    assertThat(MAPPER.valueToTree(stored).at(pointer).asString()).isEqualTo("ENC(secret)");
    assertThat(
            MAPPER
                .valueToTree(ConnectorJson.toDocument(placeholder, stored, ENCRYPT))
                .at(pointer)
                .asString())
        .isEqualTo("ENC(secret)");
  }

  @Test
  void patch_withOtherAuthenticationType_dropsTheStoredPassword() {
    Map<String, Object> stored = storedHttpBasic();
    ConnectorConfiguration withoutAuthentication =
        ConnectorJson.patch(
            ConnectorJson.readStored(ConnectorType.HTTP, stored),
            json("{'authentication':{'type':'NONE'}}"));
    Map<String, Object> storedWithoutAuthentication =
        ConnectorJson.toDocument(withoutAuthentication, stored, ENCRYPT);

    ConnectorConfiguration basicAgain =
        ConnectorJson.patch(
            ConnectorJson.readStored(ConnectorType.HTTP, storedWithoutAuthentication),
            json("{'authentication':{'type':'BASIC','username':'reader','password':'********'}}"));

    assertThat(MAPPER.valueToTree(storedWithoutAuthentication).path("authentication"))
        .isEqualTo(json("{'type':'NONE'}"));
    assertThat(
            MAPPER
                .valueToTree(
                    ConnectorJson.toDocument(basicAgain, storedWithoutAuthentication, ENCRYPT))
                .at("/authentication/password")
                .isNull())
        .isTrue();
  }

  @Test
  void patch_ofStoredHttpConnector_acceptsTheMethodOfTheResponseForm() {
    ConnectorConfiguration stored = ConnectorJson.readStored(ConnectorType.HTTP, storedHttpBasic());

    HttpConnectorConfiguration patched =
        (HttpConnectorConfiguration)
            ConnectorJson.patch(stored, json("{'baseUrl':'https://other.example.org'}"));

    assertThat(ConnectorJson.toMaskedTree(stored).at("/apiCalls/0/method").asString())
        .isEqualTo("GET");
    assertThat(patched.baseUrl()).isEqualTo("https://other.example.org");
    assertThat(patched.apiCalls())
        .extracting(HttpConnectorConfiguration.ApiCall::name)
        .containsExactly("a");
  }

  @Test
  void toMaskedTree_ofHttpConnectorWithOnlyItsType_showsTheDefaults() {
    assertThat(ConnectorJson.toMaskedTree(ConnectorJson.read(json("{'connectorType':'HTTP'}"))))
        .isEqualTo(
            json(
                "{'connectorType':'HTTP','baseUrl':null,'authentication':{'type':'NONE'},"
                    + "'apiCalls':null}"));
  }

  /**
   * Without this test, a connector type added to one listing but not to another would be rejected,
   * undocumented or unreadable from the registry only at runtime.
   */
  @Test
  void connectorTypes_areListedInEveryPlaceThatListsConnectorTypes()
      throws IOException, ReflectiveOperationException {
    List<String> enumTypes = Arrays.stream(ConnectorType.values()).map(Enum::name).toList();
    Map<String, Class<?>> subTypes =
        Arrays.stream(ConnectorConfiguration.class.getAnnotation(JsonSubTypes.class).value())
            .collect(Collectors.toMap(JsonSubTypes.Type::name, JsonSubTypes.Type::value));
    Schema schema = ConnectorSchema.class.getAnnotation(Schema.class);
    Map<String, Class<?>> discriminatorMapping =
        Arrays.stream(schema.discriminatorMapping())
            .collect(Collectors.toMap(DiscriminatorMapping::value, DiscriminatorMapping::schema));

    SoftAssertions listings = new SoftAssertions();
    listings.assertThat(subTypes.keySet()).as("@JsonSubTypes").hasSameElementsAs(enumTypes);
    listings
        .assertThat(discriminatorMapping)
        .as("@Schema discriminatorMapping of ConnectorSchema")
        .containsExactlyInAnyOrderEntriesOf(subTypes);
    listings
        .assertThat(List.of(schema.oneOf()))
        .as("@Schema oneOf of ConnectorSchema")
        .hasSameElementsAs(subTypes.values());
    for (Map.Entry<String, Class<?>> subType : subTypes.entrySet()) {
      Schema typeSchema = subType.getValue().getMethod("connectorType").getAnnotation(Schema.class);
      listings
          .assertThat(typeSchema == null ? List.of() : List.of(typeSchema.allowableValues()))
          .as("@Schema allowableValues of %s.connectorType()", subType.getValue().getSimpleName())
          .containsExactly(subType.getKey());
    }
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
      JsonNode root = MAPPER.readTree(schema);
      List<String> connectionTypes = new ArrayList<>();
      root.path("oneOf")
          .forEach(
              variant ->
                  connectionTypes.add(
                      root.at(variant.path("$ref").asString().substring(1))
                          .path("properties")
                          .path("connectionType")
                          .path("const")
                          .asString()));
      return connectionTypes;
    }
  }

  /**
   * Without this test, a variant of a nested type added to one listing but not to another would be
   * rejected or undocumented only at runtime.
   */
  @Test
  void nestedVariants_areListedInEveryPlaceThatListsThem() {
    SoftAssertions listings = new SoftAssertions();
    List<String> checked = new ArrayList<>();
    Deque<Class<?>> pending = new ArrayDeque<>();
    Arrays.stream(ConnectorConfiguration.class.getAnnotation(JsonSubTypes.class).value())
        .forEach(type -> pending.add(type.value()));
    Set<Class<?>> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      Class<?> type = pending.pop();
      if (!seen.add(type) || !type.isRecord()) {
        continue;
      }
      for (RecordComponent component : type.getRecordComponents()) {
        Class<?> valueType = listElementType(component.getGenericType());
        if (valueType != null && valueType.isSealed()) {
          String name = type.getSimpleName() + "." + component.getName();
          checked.add(name);
          assertVariantListings(listings, name, component, valueType);
          pending.addAll(Arrays.asList(valueType.getPermittedSubclasses()));
        } else if (valueType != null && valueType.isRecord()) {
          pending.add(valueType);
        }
      }
    }
    listings.assertAll();
    assertThat(checked).contains("HttpConnectorConfiguration.authentication", "ApiCall.pagination");
  }

  private static void assertVariantListings(
      SoftAssertions listings, String name, RecordComponent component, Class<?> variantType) {
    JsonSubTypes subTypesAnnotation = variantType.getAnnotation(JsonSubTypes.class);
    listings.assertThat(subTypesAnnotation).as("@JsonSubTypes of %s", name).isNotNull();
    if (subTypesAnnotation == null) {
      return;
    }
    Map<String, Class<?>> subTypes =
        Arrays.stream(subTypesAnnotation.value())
            .collect(Collectors.toMap(JsonSubTypes.Type::name, JsonSubTypes.Type::value));
    listings
        .assertThat(subTypes.values())
        .as("@JsonSubTypes of %s", name)
        .hasSameElementsAs(List.of(variantType.getPermittedSubclasses()));
    Schema typeSchema = variantType.getAnnotation(Schema.class);
    listings
        .assertThat(
            typeSchema == null
                ? Map.of()
                : Arrays.stream(typeSchema.discriminatorMapping())
                    .collect(
                        Collectors.toMap(
                            DiscriminatorMapping::value, DiscriminatorMapping::schema)))
        .as("@Schema discriminatorMapping of %s", variantType.getSimpleName())
        .containsExactlyInAnyOrderEntriesOf(subTypes);
    Schema propertySchema = component.getAccessor().getAnnotation(Schema.class);
    listings
        .assertThat(propertySchema == null ? List.of() : List.of(propertySchema.oneOf()))
        .as("@Schema oneOf of %s", name)
        .hasSameElementsAs(subTypes.values());
  }

  /**
   * Without this test, a secret held as a String would be stored in clear text and returned, and a
   * secret in a list element without identity would be lost on the next save.
   */
  @Test
  void registeredConfigurations_reachEverySecret() {
    List<Class<?>> roots = new ArrayList<>();
    Arrays.stream(ConnectorConfiguration.class.getAnnotation(JsonSubTypes.class).value())
        .forEach(type -> roots.add(type.value()));
    roots.add(NestedSecretsConnector.class);

    assertThat(secretReachViolations(roots)).isEmpty();
  }

  record UnreachedSecretsConnector(
      Map<String, Secret> headers,
      Variant auth,
      Secret[] keys,
      Opaque opaque,
      List<Unidentified> calls)
      implements ConnectorConfiguration {

    sealed interface Variant permits Basic {}

    record Basic(String password) implements Variant {}

    interface Opaque {}

    record Unidentified(String name, Secret apiKey) {}

    @Override
    public ConnectorType connectorType() {
      return ConnectorType.SQL;
    }
  }

  @Test
  void secretReachViolations_reportsEverySecretThatTheWalksCannotHandle() {
    assertThat(secretReachViolations(List.of(UnreachedSecretsConnector.class)))
        .containsExactlyInAnyOrder(
            "UnreachedSecretsConnector.headers holds secrets in a Map",
            "UnreachedSecretsConnector.keys holds secrets in an array",
            "UnreachedSecretsConnector.opaque has an interface type that is not sealed",
            "UnreachedSecretsConnector.calls lists elements with secrets but without identity",
            "Basic.password holds a secret as text");
  }

  private static List<String> secretReachViolations(List<Class<?>> roots) {
    List<String> violations = new ArrayList<>();
    Deque<Class<?>> pending = new ArrayDeque<>(roots);
    Set<Class<?>> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      Class<?> type = pending.pop();
      if (!seen.add(type)) {
        continue;
      }
      if (type.isSealed()) {
        pending.addAll(Arrays.asList(type.getPermittedSubclasses()));
        continue;
      }
      assertThat(type.isRecord()).as("%s is a record", type.getName()).isTrue();
      for (RecordComponent component : type.getRecordComponents()) {
        String name = type.getSimpleName() + "." + component.getName();
        Type generic = component.getGenericType();
        Class<?> valueType = listElementType(generic);
        if (SECRET_NAME.matcher(component.getName()).matches() && valueType != Secret.class) {
          violations.add(name + " holds a secret as text");
        }
        if (valueType == null) {
          if (carriesSecret(generic, new HashSet<>())) {
            violations.add(
                name
                    + (component.getType().isArray()
                        ? " holds secrets in an array"
                        : " holds secrets in a " + component.getType().getSimpleName()));
          }
          continue;
        }
        if (generic instanceof ParameterizedType
            && carriesSecret(valueType, new HashSet<>())
            && !IdentifiedElement.class.isAssignableFrom(valueType)) {
          violations.add(name + " lists elements with secrets but without identity");
        }
        if (valueType.isInterface()
            && !valueType.isSealed()
            && valueType.getPackageName().startsWith("de.civitascore")) {
          violations.add(name + " has an interface type that is not sealed");
        }
        if (valueType.isRecord() || valueType.isSealed()) {
          pending.add(valueType);
        }
      }
    }
    return violations;
  }

  /**
   * The type of a plain component or the element type of a {@code List}, or null for any other
   * container, because the masking and the placeholder resolution walk only records and lists.
   */
  private static Class<?> listElementType(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      return parameterized.getRawType() == List.class
          ? listElementType(parameterized.getActualTypeArguments()[0])
          : null;
    }
    return type instanceof Class<?> plain && !plain.isArray() ? plain : null;
  }

  private static boolean carriesSecret(Type type, Set<Type> seen) {
    if (type == Secret.class) {
      return true;
    }
    if (!seen.add(type)) {
      return false;
    }
    if (type instanceof ParameterizedType parameterized) {
      return Arrays.stream(parameterized.getActualTypeArguments())
          .anyMatch(argument -> carriesSecret(argument, seen));
    }
    if (type instanceof GenericArrayType array) {
      return carriesSecret(array.getGenericComponentType(), seen);
    }
    if (!(type instanceof Class<?> plain)) {
      return false;
    }
    if (plain.isArray()) {
      return carriesSecret(plain.getComponentType(), seen);
    }
    if (plain.isSealed()) {
      return Arrays.stream(plain.getPermittedSubclasses())
          .anyMatch(variant -> carriesSecret(variant, seen));
    }
    return plain.isRecord()
        && Arrays.stream(plain.getRecordComponents())
            .anyMatch(c -> carriesSecret(c.getGenericType(), seen));
  }
}

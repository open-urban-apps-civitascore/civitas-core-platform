package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonMergePatchTest {

  private static final JsonMapper MAPPER = new JsonMapper();

  private static JsonNode json(String json) {
    return MAPPER.readTree(json.replace('\'', '"'));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{'a':1,'b':{'c':2,'d':3}} | {'b':{'c':null,'e':4}} | {'a':1,'b':{'d':3,'e':4}}",
        "{'a':[1,2]} | {'a':[3]} | {'a':[3]}",
        "{'a':1} | {'a':{'b':null,'c':2}} | {'a':{'c':2}}",
      })
  void apply_withoutTypeChange_mergesAsRfc7386(String target, String patch, String result) {
    assertThat(JsonMergePatch.apply(json(target), json(patch))).isEqualTo(json(result));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{'connectorType':'X','a':1} | {'connectorType':'Y','b':null,'c':2}"
            + " | {'connectorType':'Y','c':2}",
        "{'auth':{'type':'basic','user':'u','password':'p'}} | {'auth':{'type':'bearer','token':'t'}}"
            + " | {'auth':{'type':'bearer','token':'t'}}",
        "{'a':{'b':{'type':'x','c':1}},'d':1} | {'a':{'b':{'type':'y','e':2}}}"
            + " | {'a':{'b':{'type':'y','e':2}},'d':1}",
      })
  void apply_withOtherType_replacesObject(String target, String patch, String result) {
    assertThat(JsonMergePatch.apply(json(target), json(patch))).isEqualTo(json(result));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{'connectorType':'X','a':1} | {'connectorType':'X','b':2}"
            + " | {'connectorType':'X','a':1,'b':2}",
        "{'auth':{'type':'basic','user':'u'}} | {'auth':{'type':'basic','password':'p'}}"
            + " | {'auth':{'type':'basic','user':'u','password':'p'}}",
        "{'auth':{'type':'basic','user':'u'}} | {'auth':{'password':'p'}}"
            + " | {'auth':{'type':'basic','user':'u','password':'p'}}",
        "{'auth':{'user':'u'}} | {'auth':{'type':'bearer'}} | {'auth':{'user':'u','type':'bearer'}}",
        "{'auth':{'type':'basic','user':'u'}} | {'auth':{'type':null}} | {'auth':{'user':'u'}}",
        "{'type':'x','a':1} | {'type':'y'} | {'type':'y','a':1}",
      })
  void apply_withSameOrOneSidedType_mergesObject(String target, String patch, String result) {
    assertThat(JsonMergePatch.apply(json(target), json(patch))).isEqualTo(json(result));
  }

  @Test
  void apply_keepsTarget() {
    JsonNode target = json("{'auth':{'type':'basic','user':'u'}}");

    JsonMergePatch.apply(target, json("{'auth':{'user':'v'}}"));

    assertThat(target).isEqualTo(json("{'auth':{'type':'basic','user':'u'}}"));
  }
}

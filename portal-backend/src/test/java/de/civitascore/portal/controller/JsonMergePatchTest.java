package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.civitascore.portal.util.InvalidInputException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class JsonMergePatchTest {

  private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
  private static final UUID ID = UUID.randomUUID();

  private final ObjectMapper objectMapper = new JsonMapper();
  private final JsonMergePatch jsonMergePatch =
      new JsonMergePatch(objectMapper, VALIDATORS.getValidator());

  @AfterAll
  static void closeValidators() {
    VALIDATORS.close();
  }

  @Data
  static class TestInput {
    @NotBlank private String name = "current";
    private Integer count = 1;
    private Instant since = Instant.EPOCH;

    @JsonSetter(nulls = Nulls.FAIL)
    private List<String> tags = List.of("a");

    private String code = "ok";

    public void setCode(String code) {
      if (code.startsWith("!")) {
        throw new RejectedByInputException();
      }
      this.code = code;
    }
  }

  static class RejectedByInputException extends RuntimeException {}

  private JsonNode json(String json) {
    return objectMapper.readTree(json.replace('\'', '"'));
  }

  private TestInput apply(String patch) {
    return jsonMergePatch.apply(new TestInput(), json(patch), ID);
  }

  @Test
  void apply_withUnknownField_rejectsWithItsJsonPointer() {
    assertThatThrownBy(() -> apply("{'name':'x','unknown':null,'other':{'a':1}}"))
        .isInstanceOf(InvalidInputException.class)
        .hasMessage("Unknown field: /unknown, /other");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"{'count':'many'} | /count", "{'since':'yesterday'} | /since"})
  void apply_withWrongType_rejectsWithItsJsonPointer(String patch, String pointer) {
    assertThatThrownBy(() -> apply(patch))
        .isInstanceOf(InvalidInputException.class)
        .hasMessage("Invalid value: " + pointer);
  }

  @Test
  void apply_withNullForNonNullableField_rejects() {
    assertThatThrownBy(() -> apply("{'tags':null}"))
        .isInstanceOf(InvalidInputException.class)
        .hasMessage("Must not be null: /tags");
  }

  @Test
  void apply_withConstraintViolation_rejects() {
    assertThatThrownBy(() -> apply("{'name':' '}"))
        .isInstanceOf(InvalidInputException.class)
        .extracting(e -> ((InvalidInputException) e).getResourceId())
        .isEqualTo(ID);
  }

  @Test
  void apply_withNonObjectRoot_rejects() {
    assertThatThrownBy(() -> apply("['name']"))
        .isInstanceOf(InvalidInputException.class)
        .hasMessage("A merge patch must be a JSON object");
  }

  @Test
  void apply_withOmittedField_keepsItsValue() {
    TestInput patched = apply("{'count':7}");

    assertThat(patched.getCount()).isEqualTo(7);
    assertThat(patched.getName()).isEqualTo("current");
    assertThat(patched.getTags()).containsExactly("a");
  }

  /** Without it, a DTO's own MALFORMED_REQUEST answer would turn into a generic INVALID_INPUT. */
  @Test
  void apply_whenInputRejectsValueItself_propagatesItsException() {
    assertThatThrownBy(() -> apply("{'code':'!bad'}")).isInstanceOf(RejectedByInputException.class);
  }
}

package de.civitascore.portal.model.input.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import de.civitascore.portal.model.input.LayerInputDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link CqlFilterSyntax} / {@link CqlFilterSyntaxValidator}, exercised against
 * {@link LayerInputDTO#getCqlFilter()} via the Jakarta Validation API. Asserts only on violations
 * of the annotation under test, so {@code @CqlFilterSyntax} and {@code @Size} are pinned apart.
 */
@DisplayName("CqlFilterSyntaxValidator")
class CqlFilterSyntaxValidatorTest {

  private static final String INCOMPLETE =
      "CQL filter is incomplete: a value, condition or closing bracket is missing at the end";

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void initValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        "status = 'frei' AND kapazitaet > 50",
        "status = 'frei'",
        "kapazitaet > 50",
        "name LIKE 'Park%'",
        "status IN ('frei', 'belegt')",
        "kapazitaet BETWEEN 10 AND 100",
        "bemerkung IS NULL",
        "zeitstempel AFTER 2026-01-01T00:00:00Z",
        "\"id\" = 5",
        "name = 'it''s'",
        "status = 'frei'\nAND kapazitaet > 50",
        "INTERSECTS(geom, POINT(1 2))",
        "strToUpperCase(name) = 'PARK'",
      })
  @DisplayName("valid ECQL raises no violation")
  void acceptsValidFilter(String cqlFilter) {
    assertThat(violationsOf(cqlFilter, CqlFilterSyntax.class)).isEmpty();
  }

  @Test
  @DisplayName("a bare word compares two columns, so it is valid syntax")
  void acceptsBareWordAsColumn() {
    // `status = frei` reads frei as a column name; whether that column exists is not checked.
    assertThat(violationsOf("status = frei", CqlFilterSyntax.class)).isEmpty();
  }

  @Test
  @DisplayName("an unknown function raises no violation")
  void acceptsUnknownFunction() {
    // GeoServer registers functions GeoTools alone does not know.
    assertThat(violationsOf("noSuchFunction(name) = 1", CqlFilterSyntax.class)).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\n\t"})
  @DisplayName("a missing or blank filter raises no violation")
  void acceptsMissingOrBlankFilter(String cqlFilter) {
    assertThat(violationsOf(cqlFilter, CqlFilterSyntax.class)).isEmpty();
  }

  static Stream<Arguments> invalidFilters() {
    return Stream.of(
        arguments("status = 'frei", "CQL filter is not valid ECQL near line 1, column 15"),
        arguments("kapazitaet >", "CQL filter is not valid ECQL near line 1, column 12"),
        arguments("(status = 'frei' OR kapazitaet > 50", INCOMPLETE),
        arguments("status = 'frei' AND", INCOMPLETE),
        arguments("a = 1;b = 2", "CQL filter is not valid ECQL near line 1, column 6"),
        arguments("id = 5", "CQL filter is not valid ECQL near line 1, column 1"),
        arguments("a = 1\nAND b >", "CQL filter is not valid ECQL near line 2, column 7"),
        arguments("noSuchFunction(name) = 1 AND (", INCOMPLETE),
        arguments("INTERSECTS(geom, LINESTRING(1 1))", "CQL filter is not valid ECQL"));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidFilters")
  @DisplayName("invalid ECQL raises one violation naming the fault")
  void rejectsInvalidFilter(String cqlFilter, String expectedMessage) {
    Set<ConstraintViolation<LayerInputDTO>> violations =
        violationsOf(cqlFilter, CqlFilterSyntax.class);

    assertThat(violations)
        .singleElement()
        .extracting(ConstraintViolation::getMessage)
        .isEqualTo(expectedMessage);
  }

  @Test
  @DisplayName("a string literal echoed by the parser cannot pose as the position")
  void reportsTheParsersPositionNotOneInsideALiteral() {
    assertThat(violationsOf("a = 1 'at line 99, column 99'", CqlFilterSyntax.class))
        .singleElement()
        .extracting(ConstraintViolation::getMessage)
        .isEqualTo("CQL filter is not valid ECQL near line 1, column 7");
  }

  @Test
  @DisplayName("the deepest bracket nesting the cap allows is rejected without overflowing")
  void parsesTheLongestAllowedNestingWithinTheStack() {
    String nested = "(".repeat(CqlFilterSyntaxValidator.MAX_LENGTH);

    assertThat(violationsOf(nested, CqlFilterSyntax.class))
        .singleElement()
        .extracting(ConstraintViolation::getMessage)
        .isEqualTo(INCOMPLETE);
  }

  @Test
  @DisplayName("a filter over the cap is left unparsed to @Size")
  void leavesAnOverlongFilterToSize() {
    String overlong = "(".repeat(CqlFilterSyntaxValidator.MAX_LENGTH + 1);

    assertThat(violationsOf(overlong, CqlFilterSyntax.class)).isEmpty();
    assertThat(violationsOf(overlong, Size.class))
        .singleElement()
        .extracting(ConstraintViolation::getMessage)
        .isEqualTo("CQL filter must be at most 500 characters");
  }

  /**
   * Returns only the violations raised by {@code annotationType} on {@code LayerInputDTO.cqlFilter}
   * for {@code value}, filtering out violations from the sibling constraint.
   */
  private static Set<ConstraintViolation<LayerInputDTO>> violationsOf(
      String value, Class<? extends Annotation> annotationType) {
    return validator.validateValue(LayerInputDTO.class, "cqlFilter", value).stream()
        .filter(v -> v.getConstraintDescriptor().getAnnotation().annotationType() == annotationType)
        .collect(Collectors.toUnmodifiableSet());
  }
}

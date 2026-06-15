package de.civitascore.portal.model.input.validation;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.NamedApiInputDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link NamedApiAllowedSlug} / {@link NamedApiAllowedSlugValidator}, exercised
 * end-to-end against {@link NamedApiInputDTO#getSlug()} via the Jakarta Validation API. Asserts
 * only on violations raised by {@code @NamedApiAllowedSlug} itself (filtering out sibling
 * {@code @NotBlank} / {@code @Size}) so each test pins the validator's contribution without bleed
 * from neighbouring constraints.
 */
@DisplayName("NamedApiAllowedSlugValidator")
class NamedApiAllowedSlugValidatorTest {

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

  @Test
  @DisplayName("NamedApiInputDTO.slug carries the @NamedApiAllowedSlug annotation")
  void slugFieldIsAnnotated() throws NoSuchFieldException {
    // Without this, every shape-violation test below could silently pass if @NamedApiAllowedSlug
    // were ever removed from the field — `validateValue` would return zero violations of this
    // annotation type and the filter would yield an empty set, matching the happy-path shape.
    Field slug = NamedApiInputDTO.class.getDeclaredField("slug");
    assertThat(slug.isAnnotationPresent(NamedApiAllowedSlug.class)).isTrue();
  }

  @Test
  @DisplayName("valid slug raises no @NamedApiAllowedSlug violation")
  void acceptsValidSlug() {
    assertThat(violationsOf("traffic-counter", NamedApiAllowedSlug.class)).isEmpty();
  }

  @Test
  @DisplayName("null slug raises a @NotBlank violation and no @NamedApiAllowedSlug violation")
  void delegatesNullToNotBlank() {
    // @NamedApiAllowedSlug returns true on null per the Jakarta convention so missing-field errors
    // surface exactly once (from @NotBlank). The two-sided assertion guards against a regression
    // that removes @NotBlank: without the field-level annotation, nulls would slip through with
    // no violation at all.
    assertThat(violationsOf(null, NamedApiAllowedSlug.class)).isEmpty();
    assertThat(violationsOf(null, NotBlank.class)).isNotEmpty();
  }

  @ParameterizedTest(name = "[{index}] slug={0}")
  @ValueSource(
      strings = {
        "Traffic", // uppercase
        "traffic_counter", // underscore
        "traffic.counter", // dot
        "-traffic", // leading hyphen
        "traffic-", // trailing hyphen
        "traffic counter", // space
      })
  @DisplayName("malformed slug emits shape-violation message")
  void rejectsMalformedSlugWithShapeMessage(String slug) {
    Set<ConstraintViolation<NamedApiInputDTO>> violations =
        violationsOf(slug, NamedApiAllowedSlug.class);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage())
        .contains("lowercase alphanumeric")
        .doesNotContain("reserved");
  }

  static Stream<String> reservedSlugs() {
    return NamedApiAllowedSlugValidator.RESERVED.stream();
  }

  @ParameterizedTest(name = "[{index}] slug={0}")
  @MethodSource("reservedSlugs")
  @DisplayName("reserved slug emits reserved-violation message naming the slug")
  void rejectsReservedSlugWithReservedMessage(String slug) {
    Set<ConstraintViolation<NamedApiInputDTO>> violations =
        violationsOf(slug, NamedApiAllowedSlug.class);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage())
        .contains("reserved")
        .contains(slug)
        .doesNotContain("lowercase alphanumeric");
  }

  @Test
  @DisplayName("a failed slug emits exactly one @NamedApiAllowedSlug violation")
  void emitsExactlyOneViolationOnFailure() {
    // Pins disableDefaultConstraintViolation() in NamedApiAllowedSlugValidator.replaceMessage.
    // Without it, Jakarta would emit both the default annotation message and the context-built
    // message, giving the API consumer two violations for one rule.
    assertThat(violationsOf("apis", NamedApiAllowedSlug.class)).hasSize(1);
  }

  /**
   * Returns only the violations raised by {@code annotationType} on {@code NamedApiInputDTO.slug}
   * for {@code value}, filtering out violations from sibling constraints. Lets each test target
   * exactly one annotation without bleed from neighbours like {@code @NotBlank} / {@code @Size}.
   */
  private static Set<ConstraintViolation<NamedApiInputDTO>> violationsOf(
      String value, Class<? extends Annotation> annotationType) {
    return validator.validateValue(NamedApiInputDTO.class, "slug", value).stream()
        .filter(v -> v.getConstraintDescriptor().getAnnotation().annotationType() == annotationType)
        .collect(Collectors.toUnmodifiableSet());
  }
}

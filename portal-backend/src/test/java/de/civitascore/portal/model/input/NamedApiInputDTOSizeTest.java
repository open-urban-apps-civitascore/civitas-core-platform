package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code @Size} bounds on {@link NamedApiInputDTO#getName()} (255) and {@link
 * NamedApiInputDTO#getVersion()} (32) so an over-long value is rejected at the API edge with a 400,
 * not carried to the DB column limits as an opaque DATA_INTEGRITY_ERROR / 500.
 */
@DisplayName("NamedApiInputDTO @Size bounds")
class NamedApiInputDTOSizeTest {

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
  @DisplayName("name longer than 255 raises a @Size violation")
  void rejectsOverlongName() {
    assertThat(sizeViolations("name", "a".repeat(256))).isNotEmpty();
  }

  @Test
  @DisplayName("name of exactly 255 raises no @Size violation")
  void acceptsMaxLengthName() {
    assertThat(sizeViolations("name", "a".repeat(255))).isEmpty();
  }

  @Test
  @DisplayName("version longer than 32 raises a @Size violation")
  void rejectsOverlongVersion() {
    assertThat(sizeViolations("version", "v".repeat(33))).isNotEmpty();
  }

  @Test
  @DisplayName("version of exactly 32 raises no @Size violation")
  void acceptsMaxLengthVersion() {
    assertThat(sizeViolations("version", "v".repeat(32))).isEmpty();
  }

  private static Set<ConstraintViolation<NamedApiInputDTO>> sizeViolations(
      String field, String value) {
    return validator.validateValue(NamedApiInputDTO.class, field, value).stream()
        .filter(v -> v.getConstraintDescriptor().getAnnotation().annotationType() == Size.class)
        .collect(Collectors.toUnmodifiableSet());
  }
}

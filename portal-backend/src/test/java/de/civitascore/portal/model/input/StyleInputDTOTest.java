package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StyleInputDTO Validation Tests")
class StyleInputDTOTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private StyleInputDTO valid() {
    StyleInputDTO dto = new StyleInputDTO();
    dto.setName("my-style");
    dto.setSldContent("<StyledLayerDescriptor/>");
    return dto;
  }

  @Test
  @DisplayName("Fully populated DTO should have no violations")
  void fullyPopulatedDtoShouldPass() {
    assertThat(VALIDATOR.validate(valid())).isEmpty();
  }

  @Test
  @DisplayName("Missing name should fail validation")
  void missingNameShouldFail() {
    StyleInputDTO dto = valid();
    dto.setName(null);

    Set<ConstraintViolation<StyleInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
  }

  @Test
  @DisplayName("Blank name should fail validation")
  void blankNameShouldFail() {
    StyleInputDTO dto = valid();
    dto.setName("   ");

    Set<ConstraintViolation<StyleInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
  }

  @Test
  @DisplayName("Missing sldContent should fail validation")
  void missingSldContentShouldFail() {
    StyleInputDTO dto = valid();
    dto.setSldContent(null);

    Set<ConstraintViolation<StyleInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("sldContent");
  }
}

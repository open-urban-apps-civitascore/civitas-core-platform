package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DataStructureInputDTO Validation Tests")
class DataStructureInputDTOTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private DataStructureInputDTO valid() {
    DataStructureInputDTO dto = new DataStructureInputDTO();
    dto.setName("Traffic Sensor Schema");
    dto.setDescription("Description of the data structure");
    return dto;
  }

  @Test
  @DisplayName("Fully populated DTO should have no violations")
  void fullyPopulatedDtoShouldPass() {
    assertThat(VALIDATOR.validate(valid())).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  @DisplayName("Blank or missing description should fail validation")
  void blankDescriptionShouldFail(String description) {
    DataStructureInputDTO dto = valid();
    dto.setDescription(description);

    Set<ConstraintViolation<DataStructureInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("description");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  @DisplayName("Blank or missing name should fail validation")
  void blankNameShouldFail(String name) {
    DataStructureInputDTO dto = valid();
    dto.setName(name);

    Set<ConstraintViolation<DataStructureInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
  }
}

package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataSinkType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DataSinkInputDTO Validation Tests")
class DataSinkInputDTOTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private DataSinkInputDTO valid() {
    DataSinkInputDTO dto = new DataSinkInputDTO();
    dto.setDataSinkType(DataSinkType.FROST);
    dto.setConfiguration(Map.of());
    return dto;
  }

  @Test
  @DisplayName("Fully populated DTO should have no violations")
  void fullyPopulatedDtoShouldPass() {
    assertThat(VALIDATOR.validate(valid())).isEmpty();
  }

  @Test
  @DisplayName("Missing dataSinkType should fail validation")
  void missingDataSinkTypeShouldFail() {
    DataSinkInputDTO dto = valid();
    dto.setDataSinkType(null);

    Set<ConstraintViolation<DataSinkInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("dataSinkType");
  }

  @Test
  @DisplayName("Missing configuration should fail validation")
  void missingConfigurationShouldFail() {
    DataSinkInputDTO dto = valid();
    dto.setConfiguration(null);

    Set<ConstraintViolation<DataSinkInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("configuration");
  }
}

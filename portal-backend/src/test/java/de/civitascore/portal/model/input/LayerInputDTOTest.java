package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LayerInputDTO Validation Tests")
class LayerInputDTOTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private LayerInputDTO valid() {
    LayerInputDTO dto = new LayerInputDTO();
    dto.setDataSinkId(UUID.randomUUID());
    dto.setLayerName("traffic_layer");
    return dto;
  }

  @Test
  @DisplayName("Fully populated DTO should have no violations")
  void fullyPopulatedDtoShouldPass() {
    assertThat(VALIDATOR.validate(valid())).isEmpty();
  }

  @Test
  @DisplayName("Missing dataSinkId should fail validation")
  void missingDataSinkIdShouldFail() {
    LayerInputDTO dto = valid();
    dto.setDataSinkId(null);

    Set<ConstraintViolation<LayerInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("dataSinkId");
  }

  @Test
  @DisplayName("Missing layerName should fail validation")
  void missingLayerNameShouldFail() {
    LayerInputDTO dto = valid();
    dto.setLayerName(null);

    Set<ConstraintViolation<LayerInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("layerName");
  }

  @Test
  @DisplayName("Blank layerName should fail validation")
  void blankLayerNameShouldFail() {
    LayerInputDTO dto = valid();
    dto.setLayerName("   ");

    Set<ConstraintViolation<LayerInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("layerName");
  }

  @Test
  @DisplayName("bboxAutoCalculate should default to true")
  void bboxAutoCalculateShouldDefaultToTrue() {
    assertThat(new LayerInputDTO().isBboxAutoCalculate()).isTrue();
  }
}

package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // Whitespace-only trips both @NotBlank and @Pattern, so assert by property rather than count.
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .allSatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("layerName"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "traffic_layer", "Traffic-Layer", "_leading", "-leading", "abc123"})
  @DisplayName("layerName with only safe characters and no leading digit should pass")
  void safeLayerNameShouldPass(String layerName) {
    LayerInputDTO dto = valid();
    dto.setLayerName(layerName);

    assertThat(VALIDATOR.validate(dto)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"traffic layer", "traffic.layer", "traffic/layer", "trafficäöü", "1layer"})
  @DisplayName("layerName with invalid characters or a leading digit should fail validation")
  void invalidLayerNameShouldFail(String layerName) {
    LayerInputDTO dto = valid();
    dto.setLayerName(layerName);

    Set<ConstraintViolation<LayerInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("layerName");
  }
}

package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DataStructureVersionInputDTO Validation Tests")
class DataStructureVersionInputDTOTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private DataStructureVersionInputDTO valid() {
    DataStructureVersionInputDTO dto = new DataStructureVersionInputDTO();
    dto.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    dto.setVersion("1.0.0");
    return dto;
  }

  @Test
  @DisplayName("Fully populated DTO should have no violations")
  void fullyPopulatedDtoShouldPass() {
    assertThat(VALIDATOR.validate(valid())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.0.0", "0.0.1", "10.20.30"})
  @DisplayName("Three-segment SemVer versions should pass")
  void semverVersionsShouldPass(String version) {
    DataStructureVersionInputDTO dto = valid();
    dto.setVersion(version);

    assertThat(VALIDATOR.validate(dto)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.0", "2", "v1.0.0", "1.0.0-beta", "1.0.", "1..0", "1.0.0 "})
  @DisplayName("Non-SemVer versions should fail validation")
  void nonSemverVersionsShouldFail(String version) {
    DataStructureVersionInputDTO dto = valid();
    dto.setVersion(version);

    Set<ConstraintViolation<DataStructureVersionInputDTO>> violations = VALIDATOR.validate(dto);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("version");
  }
}

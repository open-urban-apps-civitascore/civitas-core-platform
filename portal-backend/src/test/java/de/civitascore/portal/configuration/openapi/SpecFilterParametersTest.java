package de.civitascore.portal.configuration.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.repository.specification.AssignmentSpec;
import de.civitascore.portal.repository.specification.DataSetSpec;
import de.civitascore.portal.repository.specification.DataSourceSpec;
import de.civitascore.portal.repository.specification.UserSpec;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.Set;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.junit.jupiter.api.Test;

class SpecFilterParametersTest {

  @Test
  void uuidAttribute_isDocumentedAsUuid() {
    Parameter roleId = parameter(AssignmentSpec.class, "roleId");

    assertThat(roleId.getIn()).isEqualTo("query");
    assertThat(roleId.getDescription()).isNotBlank();
    assertThat(roleId.getSchema().getTypes()).containsExactly("string");
    assertThat(roleId.getSchema().getFormat()).isEqualTo("uuid");
  }

  @Test
  void separatedInSpec_isDocumentedAsCommaSeparatedUuidArray() {
    Parameter id = parameter(AssignmentSpec.class, "id");

    assertThat(id.getSchema().getTypes()).containsExactly("array");
    assertThat(id.getSchema().getItems().getFormat()).isEqualTo("uuid");
    assertThat(id.getStyle()).isEqualTo(Parameter.StyleEnum.FORM);
    assertThat(id.getExplode()).isFalse();
  }

  @Test
  void temporalAttribute_isDocumentedAsDateTime() {
    Parameter createdAtFrom = parameter(AssignmentSpec.class, "createdAtFrom");

    assertThat(createdAtFrom.getSchema().getTypes()).containsExactly("string");
    assertThat(createdAtFrom.getSchema().getFormat()).isEqualTo("date-time");
  }

  @Test
  void enumAttribute_listsItsConstants() {
    Parameter status = parameter(DataSourceSpec.class, "dataSourceStatus");

    assertThat(status.getSchema().getTypes()).containsExactly("string");
    assertThat(status.getSchema().getEnum()).contains("DRAFT", "AVAILABLE");
  }

  @Test
  void orOverOneParameter_isDocumentedOnce() {
    List<Parameter> parameters = SpecFilterParameters.of(UserSpec.class);

    assertThat(parameters).filteredOn(parameter -> parameter.getName().equals("q")).hasSize(1);
    assertThat(parameter(UserSpec.class, "q").getSchema().getTypes()).containsExactly("string");
  }

  @Test
  void explicitType_overridesThePathType_andDefaultIsDocumented() {
    Parameter includePendingDelete = parameter(DataSetSpec.class, "includePendingDelete");

    assertThat(includePendingDelete.getSchema().getTypes()).isEqualTo(Set.of("boolean"));
    assertThat(includePendingDelete.getSchema().getDefault()).isEqualTo(false);
  }

  @Test
  void parameterWithoutDescription_failsSpecGeneration() {
    assertThatThrownBy(() -> SpecFilterParameters.of(UndescribedSpec.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(UndescribedSpec.class.getName())
        .hasMessageContaining("scopeType");
  }

  private static Parameter parameter(Class<?> specification, String name) {
    return SpecFilterParameters.of(specification).stream()
        .filter(parameter -> parameter.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Spec(path = "scopeType", params = "scopeType", spec = Equal.class)
  interface UndescribedSpec extends BaseSpec<Assignment> {}
}

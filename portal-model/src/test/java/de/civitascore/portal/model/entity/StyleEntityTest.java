package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Style Entity Tests")
class StyleEntityTest {

  @Nested
  @DisplayName("Field assignment")
  class FieldAssignment {

    @Test
    @DisplayName("Should hold all assigned fields")
    void shouldHoldAllFields() {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("ds");

      Style style = new Style();
      style.setDataSet(dataSet);
      style.setName("my-style");
      style.setSldContent("<StyledLayerDescriptor/>");

      assertThat(style.getDataSet()).isSameAs(dataSet);
      assertThat(style.getName()).isEqualTo("my-style");
      assertThat(style.getSldContent()).isEqualTo("<StyledLayerDescriptor/>");
    }
  }
}

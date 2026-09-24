package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Layer Entity Tests")
class LayerEntityTest {

  @Nested
  @DisplayName("Field assignment")
  class FieldAssignment {

    @Test
    @DisplayName("Should hold all required and optional fields")
    void shouldHoldAllFields() {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("ds");

      DataSink dataSink = new DataSink();
      dataSink.setId(UUID.randomUUID());

      Style defaultStyle = new Style();
      defaultStyle.setId(UUID.randomUUID());

      Layer layer = new Layer();
      layer.setDataSet(dataSet);
      layer.setDataSink(dataSink);
      layer.setLayerName("traffic_layer");
      layer.setTitle("Traffic Layer");
      layer.setDescription("Displays traffic data");
      layer.setKeywords(List.of("traffic", "transport"));
      layer.setAttribute(List.of("speed", "count"));
      layer.setGeometryColumnRef("geom");
      layer.setCqlFilter("speed > 50");
      layer.setDefaultStyle(defaultStyle);
      layer.setCrs("EPSG:4326");
      layer.setNativeBoundingBox(Map.of("minx", -180, "miny", -90, "maxx", 180, "maxy", 90));
      layer.setLatLonBoundingBox(Map.of("minx", -180, "miny", -90, "maxx", 180, "maxy", 90));

      assertThat(layer.getDataSet()).isSameAs(dataSet);
      assertThat(layer.getDataSink()).isSameAs(dataSink);
      assertThat(layer.getLayerName()).isEqualTo("traffic_layer");
      assertThat(layer.getKeywords()).containsExactly("traffic", "transport");
      assertThat(layer.getAttribute()).containsExactly("speed", "count");
      assertThat(layer.getDefaultStyle()).isSameAs(defaultStyle);
      assertThat(layer.getNativeBoundingBox()).containsKey("minx");
    }

    @Test
    @DisplayName("alternativeStyles should default to an empty set")
    void alternativeStylesShouldDefaultToEmptySet() {
      assertThat(new Layer().getAlternativeStyles()).isNotNull().isEmpty();
    }
  }
}

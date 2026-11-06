package de.civitascore.portal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerMapperTest {

  private final LayerMapper mapper = new LayerMapperImpl();

  // The portal lists the Layers a removed API takes along by this id.
  @Test
  void toOutput_layerOfAnApi_carriesTheApiId() {
    NamedApi api = NamedApi.builder().slug("map").standard(ApiStandard.OWS).build();
    api.setId(UUID.randomUUID());
    Layer layer = new Layer();
    layer.setNamedApi(api);

    assertThat(mapper.toOutput(layer).getNamedApiId()).isEqualTo(api.getId());
  }
}

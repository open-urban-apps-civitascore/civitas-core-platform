package de.civitascore.portal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.input.DataSetInputDTO;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DataSetMapper Tests")
class DataSetMapperTest {

  private final DataSetMapper mapper = new DataSetMapperImpl();

  @Test
  @DisplayName("DataSetInputDTO.namedApis default must be null (load-bearing PATCH contract)")
  void namedApisDefaultMustBeNull() {
    // The PATCH reconciler in DataSetService.postConvertToEntity treats null as "field omitted,
    // leave entity untouched" and empty list as "clear collection". Defaulting to an empty list
    // would silently wipe saga-populated routeIds on every PATCH that omits namedApis.
    assertThat(new DataSetInputDTO().getNamedApis()).isNull();
  }

  @Test
  @DisplayName("updateEntity does not wipe namedApis when input has no namedApis field")
  void updateEntityPreservesNamedApis() {
    DataSet entity = new DataSet();
    entity.setName("dataset");

    NamedApi traffic = new NamedApi();
    traffic.setName("Traffic");
    traffic.setSlug("traffic");
    traffic.setStandard(ApiStandard.STA);
    NamedApi weather = new NamedApi();
    weather.setName("Weather");
    weather.setSlug("weather");
    weather.setStandard(ApiStandard.WFS);
    entity.setNamedApis(Set.of(traffic, weather));

    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("dataset-renamed");

    mapper.updateEntity(entity, input);

    assertThat(entity.getNamedApis())
        .extracting(NamedApi::getSlug)
        .containsExactlyInAnyOrder("traffic", "weather");
    assertThat(entity.getName()).isEqualTo("dataset-renamed");
  }
}

package de.civitascore.portal.kind;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKindRegistryTest {

  @Test
  void constructor_standardWithoutKind_failsFast() {
    List<ApiKind> withoutCustom = List.of(new OwsApiKind(), new StaApiKind());

    assertThatThrownBy(() -> new ApiKindRegistry(withoutCustom))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CUSTOM");
  }

  @Test
  void constructor_twoKindsForOneStandard_failsFast() {
    List<ApiKind> twice =
        List.of(new OwsApiKind(), new OwsApiKind(), new StaApiKind(), new CustomApiKind());

    assertThatThrownBy(() -> new ApiKindRegistry(twice))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OWS");
  }

  @Test
  void dataSinkKindRegistry_typeWithoutKind_failsFast() {
    List<DataSinkKind> onlyFrost = List.of(new FrostDataSinkKind());

    assertThatThrownBy(() -> new DataSinkKindRegistry(onlyFrost))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("POSTGIS");
  }
}

package de.civitascore.portal.kind;

import java.util.List;

/** The kind registries as the application wires them, for tests without a Spring context. */
public final class TestKinds {

  private TestKinds() {}

  public static ApiKindRegistry apiKindRegistry() {
    return new ApiKindRegistry(List.of(new OwsApiKind(), new StaApiKind(), new CustomApiKind()));
  }

  public static DataSinkKindRegistry dataSinkKindRegistry() {
    return new DataSinkKindRegistry(List.of(new FrostDataSinkKind(), new PostgisDataSinkKind()));
  }
}

package de.civitascore.portal.service.removal;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.List;
import java.util.UUID;

/** What a request would remove from its Dataset, returned when the caller has not confirmed it. */
public record UnconfirmedRemoval(
    List<RemovedDataSink> dataSinks, List<RemovedLayer> layers, List<RemovedApi> apis) {

  /** A provisioned Data sink loses its stored data at the next release. */
  public record RemovedDataSink(UUID id, DataSinkType dataSinkType, boolean provisioned) {}

  public record RemovedLayer(UUID id, String layerName, String title) {}

  public record RemovedApi(UUID id, String slug, String name, ApiStandard standard) {}

  static UnconfirmedRemoval of(DataSinkRemovalPlan plan) {
    return new UnconfirmedRemoval(
        plan.deactivatedDataSinks().stream()
            .map(
                sink ->
                    new RemovedDataSink(sink.getId(), sink.getDataSinkType(), sink.isProvisioned()))
            .toList(),
        plan.layers().stream()
            .map(layer -> new RemovedLayer(layer.getId(), layer.getLayerName(), layer.getTitle()))
            .toList(),
        plan.apis().stream()
            .map(
                api -> new RemovedApi(api.getId(), api.getSlug(), api.getName(), api.getStandard()))
            .toList());
  }
}

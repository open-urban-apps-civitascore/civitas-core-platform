package de.civitascore.portal.service.removal;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSink;
import java.util.List;
import java.util.UUID;

/**
 * What a request would remove from its Dataset, or the stored data it would discard, returned when
 * the caller has not confirmed it.
 */
public record UnconfirmedRemoval(
    List<RemovedDataSink> dataSinks, List<RemovedLayer> layers, List<RemovedApi> apis) {

  /** A provisioned Data sink loses its stored data at the next release. */
  public record RemovedDataSink(UUID id, DataSinkType dataSinkType, boolean provisioned) {

    static RemovedDataSink of(DataSink sink) {
      return new RemovedDataSink(sink.getId(), sink.getDataSinkType(), sink.isProvisioned());
    }
  }

  public record RemovedLayer(UUID id, String layerName, String title) {}

  public record RemovedApi(UUID id, String slug, String name, ApiStandard standard) {}

  /** A change that rebuilds the storage of the Data sink, which stays. */
  public static UnconfirmedRemoval storedDataOf(DataSink sink) {
    return new UnconfirmedRemoval(List.of(RemovedDataSink.of(sink)), List.of(), List.of());
  }

  static UnconfirmedRemoval of(DataSinkRemovalPlan plan) {
    return new UnconfirmedRemoval(
        plan.deactivatedDataSinks().stream().map(RemovedDataSink::of).toList(),
        plan.layers().stream()
            .map(layer -> new RemovedLayer(layer.getId(), layer.getLayerName(), layer.getTitle()))
            .toList(),
        plan.apis().stream()
            .map(
                api -> new RemovedApi(api.getId(), api.getSlug(), api.getName(), api.getStandard()))
            .toList());
  }
}

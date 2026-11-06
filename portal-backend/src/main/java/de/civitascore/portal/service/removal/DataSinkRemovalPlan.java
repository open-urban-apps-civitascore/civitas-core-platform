package de.civitascore.portal.service.removal;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.service.NamedApiRules;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What a {@link DataSinkRemovalRequest} takes with it, computed on the state after the request.
 *
 * <p>A Data sink becomes inactive when the request deletes it, or when it had a Pipeline before and
 * the request removes its last one. Its Layers go with it. An API goes when the request deactivates
 * a Data sink of one of its backing types and no active Data sink of a backing type remains, and
 * its Layers go with it.
 *
 * @param dataSet the Dataset the removal happens in
 * @param deactivatedDataSinks the Data sinks that become inactive
 * @param layers the Layers that are deleted
 * @param apis the APIs that are deleted
 */
record DataSinkRemovalPlan(
    DataSet dataSet, List<DataSink> deactivatedDataSinks, List<Layer> layers, List<NamedApi> apis) {

  static DataSinkRemovalPlan empty(DataSet dataSet) {
    return new DataSinkRemovalPlan(dataSet, List.of(), List.of(), List.of());
  }

  /**
   * Computes the plan.
   *
   * @param activeDataSinks the Dataset's active Data sinks with their Pipelines
   * @param layers the Dataset's Layers
   * @param apis the Dataset's APIs
   */
  static DataSinkRemovalPlan compute(
      DataSet dataSet,
      Collection<DataSink> activeDataSinks,
      Collection<Layer> layers,
      Collection<NamedApi> apis,
      DataSinkRemovalRequest request) {
    List<DataSink> deactivated =
        activeDataSinks.stream().filter(sink -> becomesInactive(sink, request)).toList();
    Set<UUID> deactivatedIds = idsOf(deactivated.stream().map(DataSink::getId));
    Set<DataSinkType> deactivatedTypes =
        deactivated.stream().map(DataSink::getDataSinkType).collect(Collectors.toSet());
    Set<DataSinkType> remainingTypes =
        activeDataSinks.stream()
            .filter(sink -> !deactivatedIds.contains(sink.getId()))
            .map(DataSink::getDataSinkType)
            .collect(Collectors.toSet());

    List<NamedApi> removedApis =
        apis.stream()
            .filter(api -> losesItsBacking(api.getStandard(), deactivatedTypes, remainingTypes))
            .toList();
    Set<UUID> removedApiIds = idsOf(removedApis.stream().map(NamedApi::getId));

    List<Layer> removedLayers =
        layers.stream()
            .filter(
                layer ->
                    deactivatedIds.contains(layer.getDataSink().getId())
                        || (layer.getNamedApi() != null
                            && removedApiIds.contains(layer.getNamedApi().getId())))
            .toList();

    return new DataSinkRemovalPlan(dataSet, deactivated, removedLayers, removedApis);
  }

  private static boolean losesItsBacking(
      ApiStandard standard, Set<DataSinkType> deactivatedTypes, Set<DataSinkType> remainingTypes) {
    return NamedApiRules.backingDataSinkTypes(standard).stream()
            .anyMatch(deactivatedTypes::contains)
        && !NamedApiRules.isBackedBy(standard, remainingTypes);
  }

  private static boolean becomesInactive(DataSink sink, DataSinkRemovalRequest request) {
    if (request.deletedDataSinkIds().contains(sink.getId())) {
      return true;
    }
    Set<Pipeline> pipelines = sink.getPipelines();
    return !pipelines.isEmpty()
        && pipelines.stream()
            .map(Pipeline::getId)
            .allMatch(pipelineId -> request.detaches(pipelineId, sink.getId()));
  }

  private static Set<UUID> idsOf(Stream<UUID> ids) {
    return ids.collect(Collectors.toCollection(HashSet::new));
  }

  boolean isEmpty() {
    return deactivatedDataSinks.isEmpty() && layers.isEmpty() && apis.isEmpty();
  }

  boolean deletesStoredData() {
    return deactivatedDataSinks.stream().anyMatch(DataSink::isProvisioned);
  }
}

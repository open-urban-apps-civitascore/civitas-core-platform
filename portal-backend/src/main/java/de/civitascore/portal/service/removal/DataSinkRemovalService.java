package de.civitascore.portal.service.removal;

import de.civitascore.portal.kind.ApiKindRegistry;
import de.civitascore.portal.model.embedded.DataSinkState;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.output.DataSinkRemovalImpactDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.RemovalConfirmationRequiredException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the removal rules of {@link DataSinkRemovalPlan} for every client: the Pipeline delete,
 * the Pipeline update and the direct Data sink delete run them in their own transaction, and the
 * removal-impact preview shows them without a write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSinkRemovalService {

  private final DataSinkRepository dataSinkRepository;
  private final LayerRepository layerRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final ApiKindRegistry apiKinds;

  /**
   * What the request would take with it, without a write.
   *
   * @throws ResourceNotFoundException if a Pipeline or an active Data sink of the request is not in
   *     the Dataset
   * @throws ResourceInUseException if a Pipeline still links a Data sink the request deletes
   */
  @Transactional(readOnly = true)
  public DataSinkRemovalImpactDTO preview(DataSet dataSet, DataSinkRemovalRequest request) {
    List<DataSink> activeDataSinks = activeDataSinksOf(dataSet);
    requireInDataSet(dataSet, activeDataSinks, request);
    requireUnlinkedWhenDeleted(activeDataSinks, request);
    return plan(dataSet, activeDataSinks, request).toImpact();
  }

  /**
   * Computes what the request takes with it and checks that the request confirms it.
   *
   * @param confirmedRemovalIds the ids the caller confirmed; a superset of the needed ones is fine
   * @throws ResourceInUseException if a Pipeline still links a Data sink the request deletes
   * @throws RemovalConfirmationRequiredException if an id that needs confirmation is not confirmed
   */
  public DataSinkRemovalPlan planConfirmed(
      DataSet dataSet, DataSinkRemovalRequest request, Set<UUID> confirmedRemovalIds) {
    if (request.isEmpty()) {
      return DataSinkRemovalPlan.empty(dataSet);
    }
    List<DataSink> activeDataSinks = activeDataSinksOf(dataSet);
    requireUnlinkedWhenDeleted(activeDataSinks, request);
    DataSinkRemovalPlan plan = plan(dataSet, activeDataSinks, request);
    if (!confirmedRemovalIds.containsAll(plan.needsConfirmation())) {
      throw new RemovalConfirmationRequiredException(plan.toImpact());
    }
    return plan;
  }

  /**
   * Deletes the planned Layers, then deactivates the planned Data sinks: one that was never
   * provisioned is deleted together with its Model Forge configuration, a provisioned one keeps its
   * row as PENDING_UNPROVISION until the next release removes its storage. Last the planned APIs
   * go. Runs in the caller's transaction.
   */
  @Transactional
  public void apply(DataSinkRemovalPlan plan) {
    if (plan.isEmpty()) {
      return;
    }
    plan.layers().forEach(this::deleteLayer);
    plan.deactivatedDataSinks().forEach(this::deactivate);
    plan.dataSet().getNamedApis().removeAll(plan.apis());
    log.info(
        "Removal in dataset {}: data sinks {}, layers {}, APIs {}",
        plan.dataSet().getId(),
        idsOf(plan.deactivatedDataSinks().stream().map(DataSink::getId)),
        idsOf(plan.layers().stream().map(Layer::getId)),
        idsOf(plan.apis().stream().map(NamedApi::getId)));
  }

  /**
   * Deletes the PENDING_UNPROVISION Data sinks of the Dataset with their Model Forge
   * configurations, once a release removed their storage. Runs in the caller's transaction.
   *
   * <p>A configuration Model Forge does not delete is only logged and stays stranded: the storage
   * is already gone, and failing here would leave the release in flight.
   *
   * @return the deleted Data sinks
   */
  @Transactional
  public List<DataSink> deleteUnprovisioned(DataSet dataSet) {
    List<DataSink> pending =
        dataSinkRepository.findByDataSetIdAndState(
            dataSet.getId(), DataSinkState.PENDING_UNPROVISION);
    for (DataSink sink : pending) {
      dataSinkRepository.delete(sink);
      try {
        deleteConfiguration(sink);
      } catch (RuntimeException e) {
        log.error(
            "Could not remove configuration {} of unprovisioned data sink {}, it is now stranded:"
                + " {}",
            Encode.forJava(sink.getConfigurationLogicalUrn()),
            sink.getId(),
            Encode.forJava(e.getMessage()));
      }
    }
    return pending;
  }

  private DataSinkRemovalPlan plan(
      DataSet dataSet, List<DataSink> activeDataSinks, DataSinkRemovalRequest request) {
    return DataSinkRemovalPlan.compute(
        dataSet,
        activeDataSinks,
        layerRepository.findByDataSetId(dataSet.getId()),
        dataSet.getNamedApis(),
        request,
        apiKinds);
  }

  private List<DataSink> activeDataSinksOf(DataSet dataSet) {
    return dataSinkRepository.findByDataSetIdAndState(dataSet.getId(), DataSinkState.ACTIVE);
  }

  private static void requireInDataSet(
      DataSet dataSet, List<DataSink> activeDataSinks, DataSinkRemovalRequest request) {
    Set<UUID> pipelineIds =
        dataSet.getPipelines().stream().map(Pipeline::getId).collect(Collectors.toSet());
    Set<UUID> dataSinkIds =
        activeDataSinks.stream().map(DataSink::getId).collect(Collectors.toSet());
    request.deletedPipelineIds().forEach(id -> requireIn(pipelineIds, Pipeline.class, id));
    request
        .removedLinks()
        .forEach(
            link -> {
              requireIn(pipelineIds, Pipeline.class, link.pipelineId());
              requireIn(dataSinkIds, DataSink.class, link.dataSinkId());
            });
    request.deletedDataSinkIds().forEach(id -> requireIn(dataSinkIds, DataSink.class, id));
  }

  /**
   * A Data sink is deleted directly only once no Pipeline writes into it: the Pipeline's Model
   * Forge definition would keep referencing it.
   */
  private static void requireUnlinkedWhenDeleted(
      List<DataSink> activeDataSinks, DataSinkRemovalRequest request) {
    for (DataSink sink : activeDataSinks) {
      if (!request.deletedDataSinkIds().contains(sink.getId())) {
        continue;
      }
      List<String> linkingPipelineIds =
          sink.getPipelines().stream()
              .map(Pipeline::getId)
              .filter(pipelineId -> !request.detaches(pipelineId, sink.getId()))
              .map(UUID::toString)
              .toList();
      if (!linkingPipelineIds.isEmpty()) {
        throw new ResourceInUseException(
            DataSink.class.getSimpleName(),
            sink.getId(),
            "Cannot delete Data sink " + sink.getId() + ": remove it from its Pipelines first",
            linkingPipelineIds);
      }
    }
  }

  private static void requireIn(Set<UUID> ids, Class<?> type, UUID id) {
    if (!ids.contains(id)) {
      throw new ResourceNotFoundException(type.getSimpleName(), id);
    }
  }

  /**
   * Detaches the Layer from both parents first: their {@code cascade = ALL} collections would
   * persist it again on flush.
   */
  private void deleteLayer(Layer layer) {
    layer.getDataSink().getLayers().remove(layer);
    layer.getDataSet().getLayers().remove(layer);
    layerRepository.delete(layer);
  }

  private void deactivate(DataSink sink) {
    sink.getPipelines().clear();
    if (sink.isProvisioned()) {
      sink.setState(DataSinkState.PENDING_UNPROVISION);
      dataSinkRepository.save(sink);
      return;
    }
    dataSinkRepository.delete(sink);
    deleteConfiguration(sink);
  }

  private void deleteConfiguration(DataSink sink) {
    if (sink.getConfigurationLogicalUrn() != null) {
      modelRegistryGateway.deletePayload(sink.getConfigurationLogicalUrn());
    }
  }

  private static String idsOf(Stream<UUID> ids) {
    return ids.map(UUID::toString).collect(Collectors.joining(",", "[", "]"));
  }
}

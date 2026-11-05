package de.civitascore.portal.service.removal;

import de.civitascore.portal.model.embedded.DataSinkState;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the change of the links between Pipelines and Data sinks and the removal it causes (see
 * {@link DataSinkRemovalPlan}): the Pipeline create, update and delete and the direct Data sink
 * delete {@link #prepare} it before their first write and {@link #apply} it in the same
 * transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSinkRemovalService {

  private final DataSinkRepository dataSinkRepository;
  private final LayerRepository layerRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /**
   * Checks the change and computes what it takes with it on the state after the change. Writes
   * nothing.
   *
   * @param confirmDataLoss whether the caller accepts that stored data, Layers and APIs go
   * @throws InvalidInputException if a requested Data sink id is unknown, not active, of another
   *     Dataset or linked to another Pipeline
   * @throws ResourceInUseException if a Pipeline still links a Data sink the change deletes
   * @throws RemovalNotConfirmedException if the change removes a provisioned Data sink, a Layer or
   *     an API without {@code confirmDataLoss}
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public PreparedDataSinkChange prepare(DataSinkChange change, boolean confirmDataLoss) {
    return switch (change) {
      case DataSinkChange.LinkPipeline(Pipeline pipeline, Set<UUID> dataSinkIds) ->
          prepareLinks(pipeline, dataSinkIds, confirmDataLoss);
      case DataSinkChange.DeletePipeline(Pipeline pipeline) ->
          prepareLinks(pipeline, Set.of(), confirmDataLoss);
      case DataSinkChange.DeleteDataSink(DataSink sink) ->
          PreparedDataSinkChange.removalOnly(
              planConfirmed(
                  sink.getDataSet(),
                  DataSinkRemovalRequest.deleteDataSink(sink.getId()),
                  confirmDataLoss));
    };
  }

  /**
   * Adds and removes the prepared links, then deletes the planned Layers and deactivates the
   * planned Data sinks: one that was never provisioned is deleted, and its Model Forge
   * configuration after the commit; a provisioned one keeps its row as PENDING_UNPROVISION until
   * the next release removes its storage. Last the planned APIs go.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void apply(PreparedDataSinkChange prepared) {
    Pipeline pipeline = prepared.pipeline();
    prepared
        .linksToRemove()
        .forEach(
            sink ->
                sink.getPipelines().removeIf(linked -> linked.getId().equals(pipeline.getId())));
    prepared.linksToAdd().forEach(sink -> sink.getPipelines().add(pipeline));

    DataSinkRemovalPlan plan = prepared.plan();
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
   * Deletes the PENDING_UNPROVISION Data sinks of the Dataset once a release removed their storage,
   * and their Model Forge configurations after the commit.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteUnprovisioned(DataSet dataSet) {
    List<DataSink> pending =
        dataSinkRepository.findByDataSetIdAndState(
            dataSet.getId(), DataSinkState.PENDING_UNPROVISION);
    for (DataSink sink : pending) {
      dataSinkRepository.delete(sink);
      deleteConfigurationAfterCommit(sink);
    }
  }

  private PreparedDataSinkChange prepareLinks(
      Pipeline pipeline, Set<UUID> dataSinkIds, boolean confirmDataLoss) {
    List<DataSink> requested = requireLinkable(pipeline, dataSinkIds);
    List<DataSink> linked = dataSinkRepository.findByPipelinesId(pipeline.getId());
    List<DataSink> linksToAdd =
        requested.stream().filter(sink -> !isLinkedTo(sink, pipeline.getId())).toList();
    List<DataSink> linksToRemove =
        linked.stream().filter(sink -> !dataSinkIds.contains(sink.getId())).toList();
    Set<PipelineDataSinkLink> removedLinks =
        linksToRemove.stream()
            .map(sink -> new PipelineDataSinkLink(pipeline.getId(), sink.getId()))
            .collect(Collectors.toSet());
    DataSinkRemovalPlan plan =
        planConfirmed(
            pipeline.getDataSet(),
            DataSinkRemovalRequest.removeLinks(removedLinks),
            confirmDataLoss);
    return new PreparedDataSinkChange(plan, pipeline, linksToAdd, linksToRemove);
  }

  private List<DataSink> requireLinkable(Pipeline pipeline, Set<UUID> dataSinkIds) {
    if (dataSinkIds.isEmpty()) {
      return List.of();
    }
    List<DataSink> requested =
        dataSinkRepository.findAllById(dataSinkIds).stream().filter(DataSink::isActive).toList();
    if (requested.size() != dataSinkIds.size()) {
      throw new InvalidInputException(
          "Pipeline", "dataSinkIds", "One or more DataSink IDs not found");
    }
    UUID dataSetId = pipeline.getDataSet().getId();
    for (DataSink sink : requested) {
      if (!dataSetId.equals(sink.getDataSet().getId())) {
        throw new InvalidInputException(
            "Pipeline",
            "dataSinkIds",
            "DataSink " + sink.getId() + " belongs to a different DataSet");
      }
      if (sink.getPipelines().stream().anyMatch(other -> !other.getId().equals(pipeline.getId()))) {
        throw new InvalidInputException(
            "Pipeline",
            "dataSinkIds",
            "DataSink " + sink.getId() + " is already attached to another Pipeline");
      }
    }
    return requested;
  }

  private static boolean isLinkedTo(DataSink sink, UUID pipelineId) {
    return sink.getPipelines().stream().anyMatch(pipeline -> pipelineId.equals(pipeline.getId()));
  }

  private DataSinkRemovalPlan planConfirmed(
      DataSet dataSet, DataSinkRemovalRequest request, boolean confirmDataLoss) {
    if (request.isEmpty()) {
      return DataSinkRemovalPlan.empty(dataSet);
    }
    List<DataSink> activeDataSinks = activeDataSinksOf(dataSet);
    requireUnlinkedWhenDeleted(activeDataSinks, request);
    DataSinkRemovalPlan plan = plan(dataSet, activeDataSinks, request);
    if (!confirmDataLoss) {
      requireNoDataLoss(plan);
    }
    return plan;
  }

  private static void requireNoDataLoss(DataSinkRemovalPlan plan) {
    boolean deletesStoredData =
        plan.deactivatedDataSinks().stream().anyMatch(DataSink::isProvisioned);
    if (deletesStoredData || !plan.layers().isEmpty() || !plan.apis().isEmpty()) {
      throw new RemovalNotConfirmedException(plan.dataSet().getId(), UnconfirmedRemoval.of(plan));
    }
  }

  private DataSinkRemovalPlan plan(
      DataSet dataSet, List<DataSink> activeDataSinks, DataSinkRemovalRequest request) {
    return DataSinkRemovalPlan.compute(
        dataSet,
        activeDataSinks,
        layerRepository.findByDataSetId(dataSet.getId()),
        dataSet.getNamedApis(),
        request);
  }

  private List<DataSink> activeDataSinksOf(DataSet dataSet) {
    return dataSinkRepository.findByDataSetIdAndState(dataSet.getId(), DataSinkState.ACTIVE);
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
          sink.getPipelines().stream().map(Pipeline::getId).map(UUID::toString).toList();
      if (!linkingPipelineIds.isEmpty()) {
        throw new ResourceInUseException(
            DataSink.class.getSimpleName(),
            sink.getId(),
            "Cannot delete Data sink " + sink.getId() + ": remove it from its Pipelines first",
            linkingPipelineIds);
      }
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
    deleteConfigurationAfterCommit(sink);
  }

  /**
   * Model Forge is outside the transaction, so a delete before the commit survives a rollback and
   * leaves the restored row without its configuration. After the commit, a configuration Model
   * Forge does not delete is only logged and stays stranded: no row references it any more.
   */
  private void deleteConfigurationAfterCommit(DataSink sink) {
    UUID sinkId = sink.getId();
    String urn = sink.getConfigurationLogicalUrn();
    if (urn == null) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            deleteConfiguration(sinkId, urn);
          }
        });
  }

  private void deleteConfiguration(UUID sinkId, String urn) {
    try {
      modelRegistryGateway.deletePayload(urn);
    } catch (RuntimeException e) {
      log.error(
          "Could not remove configuration {} of removed data sink {}, it is now stranded: {}",
          Encode.forJava(urn),
          sinkId,
          Encode.forJava(e.getMessage()));
    }
  }

  private static String idsOf(Stream<UUID> ids) {
    return ids.map(UUID::toString).collect(Collectors.joining(",", "[", "]"));
  }
}

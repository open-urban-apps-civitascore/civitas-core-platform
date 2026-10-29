package de.civitascore.portal.service.removal;

import de.civitascore.portal.kind.ApiKindRegistry;
import de.civitascore.portal.model.embedded.DataSinkState;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.output.DataSinkRemovalImpactDTO;
import de.civitascore.portal.model.output.DataSinkRemovalImpactDTO.ApiImpact;
import de.civitascore.portal.model.output.DataSinkRemovalImpactDTO.DataSinkImpact;
import de.civitascore.portal.model.output.DataSinkRemovalImpactDTO.LayerImpact;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.InvalidInputException;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the change of the links between Pipelines and Data sinks and the removal it causes (see
 * {@link DataSinkRemovalPlan}): the Pipeline create, update and delete and the direct Data sink
 * delete {@link #prepare} it before their first write and {@link #apply} it in the same
 * transaction, and the removal-impact preview shows it without a write.
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
    return toImpact(plan(dataSet, activeDataSinks, request));
  }

  /**
   * Checks the change, computes what it takes with it on the state after the change, and refuses it
   * unless the caller confirmed that. Writes nothing.
   *
   * @param confirmedRemovalIds the ids the caller confirmed; a superset of the needed ones is fine
   * @throws InvalidInputException if a requested Data sink id is unknown, not active, of another
   *     Dataset or linked to another Pipeline
   * @throws ResourceInUseException if a Pipeline still links a Data sink the change deletes
   * @throws RemovalConfirmationRequiredException if an id that needs confirmation is not confirmed
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public PreparedDataSinkChange prepare(DataSinkChange change, Set<UUID> confirmedRemovalIds) {
    return switch (change) {
      case DataSinkChange.LinkPipeline(Pipeline pipeline, Set<UUID> dataSinkIds) ->
          prepareLinks(pipeline, dataSinkIds, confirmedRemovalIds);
      case DataSinkChange.DeletePipeline(Pipeline pipeline) -> {
        List<DataSink> linked = dataSinkRepository.findByPipelinesId(pipeline.getId());
        DataSinkRemovalPlan plan =
            planConfirmed(
                pipeline.getDataSet(),
                DataSinkRemovalRequest.deletePipeline(pipeline.getId()),
                confirmedRemovalIds);
        yield new PreparedDataSinkChange(plan, pipeline, List.of(), linked);
      }
      case DataSinkChange.DeleteDataSink(DataSink sink) ->
          PreparedDataSinkChange.removalOnly(
              planConfirmed(
                  sink.getDataSet(),
                  DataSinkRemovalRequest.deleteDataSink(sink.getId()),
                  confirmedRemovalIds));
    };
  }

  /**
   * Adds and removes the prepared links, then deletes the planned Layers and deactivates the
   * planned Data sinks: one that was never provisioned is deleted together with its Model Forge
   * configuration, a provisioned one keeps its row as PENDING_UNPROVISION until the next release
   * removes its storage. Last the planned APIs go.
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
   * Deletes the PENDING_UNPROVISION Data sinks of the Dataset with their Model Forge
   * configurations, once a release removed their storage. Runs in the caller's transaction.
   *
   * <p>A configuration Model Forge does not delete is only logged and stays stranded: the storage
   * is already gone, and failing here would leave the release in flight.
   */
  @Transactional
  public void deleteUnprovisioned(DataSet dataSet) {
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
  }

  private PreparedDataSinkChange prepareLinks(
      Pipeline pipeline, Set<UUID> dataSinkIds, Set<UUID> confirmedRemovalIds) {
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
            confirmedRemovalIds);
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
      DataSet dataSet, DataSinkRemovalRequest request, Set<UUID> confirmedRemovalIds) {
    if (request.isEmpty()) {
      return DataSinkRemovalPlan.empty(dataSet);
    }
    List<DataSink> activeDataSinks = activeDataSinksOf(dataSet);
    requireUnlinkedWhenDeleted(activeDataSinks, request);
    DataSinkRemovalPlan plan = plan(dataSet, activeDataSinks, request);
    if (!confirmedRemovalIds.containsAll(plan.needsConfirmation())) {
      throw new RemovalConfirmationRequiredException(toImpact(plan));
    }
    return plan;
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

  private static DataSinkRemovalImpactDTO toImpact(DataSinkRemovalPlan plan) {
    return new DataSinkRemovalImpactDTO(
        plan.deactivatedDataSinks().stream()
            .map(
                sink ->
                    new DataSinkImpact(sink.getId(), sink.getDataSinkType(), sink.isProvisioned()))
            .toList(),
        plan.layers().stream()
            .map(layer -> new LayerImpact(layer.getId(), layer.getLayerName(), layer.getTitle()))
            .toList(),
        plan.apis().stream()
            .map(api -> new ApiImpact(api.getId(), api.getSlug(), api.getName(), api.getStandard()))
            .toList(),
        plan.needsConfirmation().stream().sorted().toList());
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

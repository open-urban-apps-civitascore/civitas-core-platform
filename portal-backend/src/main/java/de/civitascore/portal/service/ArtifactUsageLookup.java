package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.ReferrerReleaseState;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;

/**
 * Answers, for a Data source, a Data structure or a Data structure version, whether anything
 * references it and whether a released entity does. The first is only reported; the second decides
 * the unrelease and which fields a released artifact may still change.
 *
 * <p>Only direct referrers are judged. Releasing an entity requires everything it references to be
 * released, so a DRAFT artifact has no released referrer and is answered without classifying one. A
 * Mapping has no row and no status, so it counts as released when a Pipeline or Dataset that
 * references it does.
 *
 * <p>A Dataset counts as released until its unrelease saga completes, because a failed saga
 * restores it to AVAILABLE.
 *
 * <p>A referrer the platform keeps no row for counts as released: the unrelease it guards then
 * fails closed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactUsageLookup {

  private static final String PIPELINE = "pipeline";
  private static final String DATA_SINK = "datasink";
  private static final String DATA_SOURCE = "datasource";
  private static final String DATA_SET = "dataset";
  private static final String DATA_STRUCTURE = "datastructure";
  private static final String ELEMENT = "element";
  private static final String MAPPING = "mapping";

  private final ModelRegistryGateway modelRegistryGateway;
  private final PipelineRepository pipelineRepository;
  private final DataSourceRepository dataSourceRepository;
  private final DataSinkRepository dataSinkRepository;
  private final DataSetRepository dataSetRepository;
  private final DataStructureRepository dataStructureRepository;

  /**
   * How an artifact is used.
   *
   * @param inUse whether anything references the artifact
   * @param releasedReferrers the released entities among the referrers; empty when none is
   */
  public record ArtifactUsage(boolean inUse, List<ReleasedReferrer> releasedReferrers) {

    public ArtifactUsage {
      releasedReferrers = List.copyOf(releasedReferrers);
    }

    public boolean inUseByReleased() {
      return !releasedReferrers.isEmpty();
    }

    /** The usage of several artifacts taken together; unused when there are none. */
    public static ArtifactUsage anyOf(Collection<ArtifactUsage> usages) {
      return new ArtifactUsage(
          usages.stream().anyMatch(ArtifactUsage::inUse),
          usages.stream().flatMap(usage -> usage.releasedReferrers().stream()).distinct().toList());
    }

    /**
     * Refuses the unrelease of the artifact while a released entity references it.
     *
     * @throws ResourceInUseException if there is a released referrer; its message names what the
     *     first one is
     */
    public void requireNoReleasedReferrer(String resourceType, UUID resourceId) {
      if (!inUseByReleased()) {
        return;
      }
      throw new ResourceInUseException(
          resourceType,
          resourceId,
          "Cannot unrelease "
              + resourceType
              + " because it is referenced by "
              + releasedReferrers.getFirst().kind().description()
              + ".",
          releasedReferrers.stream().map(ReleasedReferrer::reference).toList());
    }
  }

  /**
   * A released entity that references the artifact.
   *
   * @param kind what the referrer is
   * @param reference its id or CORE URN
   */
  public record ReleasedReferrer(ReferrerKind kind, String reference) {}

  /** What a released referrer is. */
  public enum ReferrerKind {
    DATA_SOURCE("a released Data source"),
    PIPELINE("a Pipeline of a released Dataset"),
    DATA_SINK("a Data sink of a released Dataset"),
    MAPPING("a Mapping used by a released Dataset"),
    DATA_SET("a released Dataset"),
    DATA_STRUCTURE("a released Data structure"),
    UNKNOWN("an artifact the platform keeps no record of");

    private final String description;

    ReferrerKind(String description) {
      this.description = description;
    }

    public String description() {
      return description;
    }
  }

  /** How a Data source is used. Its referrers are the Pipelines linking it. */
  public ArtifactUsage of(DataSource dataSource) {
    boolean inUse = pipelineRepository.existsByDataSourcesId(dataSource.getId());
    if (!inUse || dataSource.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      return new ArtifactUsage(inUse, List.of());
    }
    List<ReleasedReferrer> released =
        pipelineRepository.findIdsByDataSourceIdWithReleasedDataSet(dataSource.getId()).stream()
            .map(pipelineId -> new ReleasedReferrer(ReferrerKind.PIPELINE, pipelineId.toString()))
            .toList();
    return new ArtifactUsage(true, released);
  }

  /** How one Data structure version is used. */
  public ArtifactUsage of(DataStructureVersion version) {
    return ofVersion(version.getDataStructure(), version);
  }

  /** How a Data structure is used: through any of its versions. */
  public ArtifactUsage of(DataStructure dataStructure) {
    return ArtifactUsage.anyOf(ofEachVersion(dataStructure).values());
  }

  /** How each version of a Data structure is used, keyed by version id. */
  public Map<UUID, ArtifactUsage> ofEachVersion(DataStructure dataStructure) {
    Map<UUID, ArtifactUsage> usageByVersion = new LinkedHashMap<>();
    dataStructure
        .getDataStructureVersions()
        .forEach(version -> usageByVersion.put(version.getId(), ofVersion(dataStructure, version)));
    return usageByVersion;
  }

  private ArtifactUsage ofVersion(DataStructure dataStructure, DataStructureVersion version) {
    boolean released =
        dataStructure.getDataStructureStatus() == DataStructureStatus.AVAILABLE
            && version.getDataStructureVersionStatus() == DataStructureVersionStatus.AVAILABLE;
    if (!released) {
      return new ArtifactUsage(isReferenced(version), List.of());
    }

    List<String> registryReferrers =
        modelRegistryGateway.referencesTo(version.getModelUrn()).stream().distinct().toList();
    List<ReleasedReferrer> releasedReferrers = new ArrayList<>();
    dataSourceRepository
        .findIdsByDataStructureVersionIdInAndStatus(
            Set.of(version.getId()), DataSourceStatus.AVAILABLE)
        .forEach(
            id ->
                releasedReferrers.add(
                    new ReleasedReferrer(ReferrerKind.DATA_SOURCE, id.toString())));
    releasedReferrers.addAll(releasedAmong(registryReferrers, true));

    boolean inUse =
        !registryReferrers.isEmpty()
            || dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(version.getId()));
    return new ArtifactUsage(inUse, releasedReferrers);
  }

  /** A Data source pins a version by a portal column, which the model registry does not see. */
  private boolean isReferenced(DataStructureVersion version) {
    return dataSourceRepository.existsByDataStructureVersionIdIn(Set.of(version.getId()))
        || modelRegistryGateway.isReferenced(version.getModelUrn());
  }

  /**
   * The released entities among registry referrers, one database query per referrer kind.
   *
   * @param followMappings whether a Mapping referrer is judged by what references it in turn
   */
  private List<ReleasedReferrer> releasedAmong(
      Collection<String> referrerUrns, boolean followMappings) {
    Map<String, List<String>> byType = new LinkedHashMap<>();
    for (String urn : referrerUrns) {
      byType
          .computeIfAbsent(
              Objects.requireNonNullElse(modelRegistryGateway.artifactType(urn), ""),
              type -> new ArrayList<>())
          .add(urn);
    }
    List<ReleasedReferrer> released = new ArrayList<>();
    List<String> structureReferrers = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : byType.entrySet()) {
      List<String> urns = entry.getValue();
      switch (entry.getKey()) {
        case PIPELINE ->
            released.addAll(
                releasedRows(
                    ReferrerKind.PIPELINE,
                    identity(urns),
                    found -> pipelineRepository.findReleaseStatesByModelLogicalUrnIn(found)));
        case DATA_SINK ->
            released.addAll(
                releasedRows(
                    ReferrerKind.DATA_SINK,
                    identity(urns),
                    found ->
                        dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(found)));
        case DATA_SOURCE ->
            released.addAll(
                releasedRows(
                    ReferrerKind.DATA_SOURCE,
                    identity(urns),
                    found ->
                        dataSourceRepository.findReleaseStatesByConfigurationLogicalUrnIn(
                            found, DataSourceStatus.AVAILABLE)));
        case DATA_SET ->
            released.addAll(
                releasedRows(
                    ReferrerKind.DATA_SET,
                    identity(urns),
                    found -> dataSetRepository.findReleaseStatesByManifestLogicalUrnIn(found)));
        case DATA_STRUCTURE, ELEMENT -> structureReferrers.addAll(urns);
        case MAPPING -> released.addAll(followMappings ? releasedMappings(urns) : unknown(urns));
        default -> released.addAll(unknown(urns));
      }
    }
    if (!structureReferrers.isEmpty()) {
      Map<String, Set<String>> hostsByReferrer = new LinkedHashMap<>();
      structureReferrers.forEach(
          urn -> hostsByReferrer.put(urn, modelRegistryGateway.hostModelUrnsOf(urn)));
      released.addAll(
          releasedRows(
              ReferrerKind.DATA_STRUCTURE,
              hostsByReferrer,
              found ->
                  dataStructureRepository.findReleaseStatesByModelLogicalUrnIn(
                      found, DataStructureStatus.AVAILABLE)));
    }
    return released;
  }

  /**
   * The Mappings a released Pipeline or Dataset references. One registry call per Mapping, then one
   * database query per kind across all of them.
   */
  private List<ReleasedReferrer> releasedMappings(List<String> mappingUrns) {
    Map<String, Set<String>> mappingsByUser = new HashMap<>();
    for (String mapping : mappingUrns) {
      for (String user : modelRegistryGateway.referencesTo(mapping)) {
        mappingsByUser.computeIfAbsent(user, key -> new LinkedHashSet<>()).add(mapping);
      }
    }
    Set<String> released = new LinkedHashSet<>();
    for (ReleasedReferrer user : releasedAmong(mappingsByUser.keySet(), false)) {
      released.addAll(mappingsByUser.get(user.reference()));
    }
    return released.stream()
        .map(mapping -> new ReleasedReferrer(ReferrerKind.MAPPING, mapping))
        .toList();
  }

  /**
   * The referrers with a released host row, plus those with no host row at all.
   *
   * @param hostsByReferrer each referrer's URN mapped to the logical URNs its host row may be found
   *     by
   */
  private List<ReleasedReferrer> releasedRows(
      ReferrerKind kind,
      Map<String, Set<String>> hostsByReferrer,
      Function<Collection<String>, List<ReferrerReleaseState>> lookup) {
    Set<String> allHosts = new HashSet<>();
    hostsByReferrer.values().forEach(allHosts::addAll);
    Map<String, Boolean> releasedByHost = new HashMap<>();
    for (ReferrerReleaseState state : lookup.apply(allHosts)) {
      releasedByHost.merge(state.logicalUrn(), state.released(), Boolean::logicalOr);
    }
    List<ReleasedReferrer> released = new ArrayList<>();
    hostsByReferrer.forEach(
        (referrer, hosts) -> {
          Boolean state =
              hosts.stream()
                  .map(releasedByHost::get)
                  .filter(Objects::nonNull)
                  .reduce(Boolean::logicalOr)
                  .orElse(null);
          if (state == null) {
            released.addAll(unknown(List.of(referrer)));
          } else if (state) {
            released.add(new ReleasedReferrer(kind, referrer));
          }
        });
    return released;
  }

  private List<ReleasedReferrer> unknown(List<String> urns) {
    return urns.stream()
        .map(
            urn -> {
              log.warn(
                  "Usage check: referrer {} maps to no platform record and counts as released",
                  Encode.forJava(urn));
              return new ReleasedReferrer(ReferrerKind.UNKNOWN, urn);
            })
        .toList();
  }

  private static Map<String, Set<String>> identity(List<String> urns) {
    Map<String, Set<String>> identity = new LinkedHashMap<>();
    urns.forEach(urn -> identity.put(urn, Set.of(urn)));
    return identity;
  }
}

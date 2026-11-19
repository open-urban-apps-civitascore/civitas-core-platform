package de.civitascore.portal.service;

import static de.civitascore.portal.service.PackagePreparer.DATAPOOL_ID;
import static de.civitascore.portal.service.PackagePreparer.needsDatapool;

import de.civitascore.portal.model.embedded.UseCaseArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.entity.UseCase;
import de.civitascore.portal.model.entity.UseCaseArtifact;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.model.input.UseCaseInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.UseCaseRepository;
import de.civitascore.portal.service.PreparedMember.Copy;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Installs a package on this instance and keeps the record of the install as a use case.
 *
 * <p>For each member, the install creates a copy on this instance. The backend gives each copy its
 * own URN. The record keeps the identity of the member in the package as the origin of the copy.
 * The members of a package reference each other by these identities. The install creates the
 * members in the order of their kinds and binds each reference to the copy of the member that it
 * names: first the Data structures, then the Data sources that read them, then the Datasets, then
 * the Mappings and Data sinks of their data flows, then their named APIs, which need the Data sinks
 * that back them, then their Pipelines, and last the styles and layers of their maps. A package can
 * have no Dataset, one or several, and each member that belongs to a Dataset names it in {@code
 * dataSetRef}. The install does not release what it creates. Each copy stays DRAFT, and the release
 * goes through the endpoint of each artifact.
 *
 * <p>The install makes all changes to the database in one transaction. If one member fails, the
 * install keeps nothing. Model Forge keeps its dependency graph in memory, and a rollback does not
 * restore the graph. For this reason, {@link PackagePreparer} makes its checks before the first
 * change to the registry. Some rules of the platform apply only when the install updates a Dataset,
 * for example that an OWS named API needs a PostGIS Data sink. A package that breaks such a rule
 * fails after the first change to the registry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UseCaseService {

  private final UseCaseRepository useCaseRepository;
  private final DataPoolRepository dataPoolRepository;
  private final DataStructureService dataStructureService;
  private final DataStructureVersionService dataStructureVersionService;
  private final DataSourceService dataSourceService;
  private final DataSetService dataSetService;
  private final MappingService mappingService;
  private final DataSinkService dataSinkService;
  private final PipelineService pipelineService;
  private final StyleService styleService;
  private final LayerService layerService;
  private final ModelRegistryGateway modelRegistryGateway;
  private final PackagePreparer packagePreparer;

  /** Reads the document of a Mapping member into the input that the Mapping API takes. */
  private final ObjectMapper objectMapper;

  /**
   * Installs a package and returns the record of the install.
   *
   * @param input the package to install, and the Data pool for its Data sources and its Dataset
   * @return the record, with one line for each artifact that the install created
   * @throws UniqueConstraintViolationException if the Data pool has an active use case of the
   *     package, or, for a package without a Data pool, if this instance has one
   * @throws org.springframework.dao.DataIntegrityViolationException if an install of the same
   *     package in the same Data pool that runs at the same time commits first
   * @throws InvalidInputException if the package or one of its members fails a check, or if the
   *     platform refuses a member
   */
  @Transactional
  public UseCase install(UseCaseInputDTO input) {
    PackageManifestInputDTO manifest = input.getPackageManifest();
    UUID datapoolId = input.getDatapoolId();
    // A package without Data sources and without a Dataset puts nothing into the Data pool, so its
    // record keeps no Data pool. Such a package has only one active use case on this instance.
    return install(manifest, datapoolId, needsDatapool(manifest.getMembers()) ? datapoolId : null);
  }

  /**
   * Installs a package in a Data pool and returns the record of the install. The record keeps the
   * Data pool also for a package without Data sources and without a Dataset. Thus the Data pool
   * lists the use case, and a user of the Data pool can read and uninstall it.
   *
   * @param datapoolId the Data pool from the path of the request
   * @param input the package to install. If the request names a Data pool, it must be the same.
   * @return the record, with one line for each artifact that the install created
   * @throws ResourceNotFoundException if the Data pool does not exist
   * @throws InvalidInputException if the request names another Data pool, if the package or one of
   *     its members fails a check, or if the platform refuses a member
   * @throws UniqueConstraintViolationException if the Data pool has an active use case of the
   *     package
   */
  @Transactional
  public UseCase installInDatapool(UUID datapoolId, UseCaseInputDTO input) {
    if (input.getDatapoolId() != null && !input.getDatapoolId().equals(datapoolId)) {
      throw new InvalidInputException(
          DATAPOOL_ID,
          input.getDatapoolId(),
          "The request names the Data pool '%s', but the path names the Data pool '%s'."
              .formatted(input.getDatapoolId(), datapoolId));
    }
    requireDatapool(datapoolId);
    return install(input.getPackageManifest(), datapoolId, datapoolId);
  }

  /**
   * Installs a package. The Data pool of the members and the Data pool of the record can be
   * different: a package without Data sources and without a Dataset can have a record without a
   * Data pool.
   */
  private UseCase install(
      PackageManifestInputDTO manifest, UUID datapoolId, UUID useCaseDatapoolId) {
    if (useCaseRepository.existsActive(manifest.getId(), useCaseDatapoolId)) {
      throw alreadyInstalled(manifest.getId(), useCaseDatapoolId);
    }
    List<PreparedMember> members = packagePreparer.prepare(manifest, datapoolId);

    UseCase useCase = new UseCase();
    useCase.setPackageId(manifest.getId());
    useCase.setPackageVersion(manifest.getVersion());
    useCase.setDatapoolId(useCaseDatapoolId);
    claim(useCase);

    Map<String, Copy> copies = new HashMap<>();
    // The checks make sure that each member of a Dataset names a Dataset of the package, and the
    // order of the kinds makes sure that the Datasets exist before their members.
    Map<String, PreparedMember> dataSetMembers = new HashMap<>();
    Set<String> dataSetsWithApis = new LinkedHashSet<>();
    for (PreparedMember member : members) {
      switch (member.kind()) {
        case DATASTRUCTURE -> useCase.addArtifact(installDataStructure(member, copies));
        case DATASOURCE -> useCase.addArtifact(installDataSource(member, datapoolId, copies));
        case DATASET -> {
          useCase.addArtifact(installDataSet(member, datapoolId, copies));
          dataSetMembers.put(key(member.member().getUrn()), member);
        }
        case API -> {
          // A Dataset checks its named APIs as one list, so its first API member adds all of them.
          String dataSet = key(member.member().getDataSetRef());
          if (dataSetsWithApis.add(dataSet)) {
            installNamedApis(
                    dataSetMembers.get(dataSet),
                    dataSetIdOf(member, copies),
                    apisOf(members, dataSet),
                    datapoolId)
                .forEach(useCase::addArtifact);
          }
        }
        case MAPPING ->
            useCase.addArtifact(installMapping(member, dataSetIdOf(member, copies), copies));
        case DATASINK ->
            useCase.addArtifact(installDataSink(member, dataSetIdOf(member, copies), copies));
        case PIPELINE ->
            useCase.addArtifact(installPipeline(member, dataSetIdOf(member, copies), copies));
        case STYLE ->
            useCase.addArtifact(installStyle(member, dataSetIdOf(member, copies), copies));
        case LAYER ->
            useCase.addArtifact(installLayer(member, dataSetIdOf(member, copies), copies));
      }
    }
    UseCase saved = useCaseRepository.save(useCase);
    log.info(
        "Installed package {} as use case {}", Encode.forJava(saved.getPackageId()), saved.getId());
    return saved;
  }

  /**
   * Returns a page of use cases.
   *
   * @param pageable the page and the sort order
   * @return the use cases of the page, each with its lines
   */
  @Transactional(readOnly = true)
  public Page<UseCase> findAll(Pageable pageable) {
    return useCaseRepository.findAll(pageable);
  }

  /**
   * Returns one use case.
   *
   * @param id the id of the use case
   * @return the use case with its lines
   * @throws ResourceNotFoundException if the use case does not exist
   */
  @Transactional(readOnly = true)
  public UseCase findByIdOrThrow(UUID id) {
    return useCaseRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("UseCase", id.toString()));
  }

  /**
   * Returns a page of the use cases of a Data pool.
   *
   * @param datapoolId the Data pool
   * @param pageable the page and the sort order
   * @return the use cases of the page, each with its lines
   * @throws ResourceNotFoundException if the Data pool does not exist
   */
  @Transactional(readOnly = true)
  public Page<UseCase> findAllInDatapool(UUID datapoolId, Pageable pageable) {
    requireDatapool(datapoolId);
    return useCaseRepository.findByDatapoolId(datapoolId, pageable);
  }

  /**
   * Returns one use case of a Data pool. For a use case of another Data pool, the result is the
   * same as for a use case that does not exist.
   *
   * @param datapoolId the Data pool
   * @param id the id of the use case
   * @return the use case with its lines
   * @throws ResourceNotFoundException if the use case does not exist in the Data pool
   */
  @Transactional(readOnly = true)
  public UseCase findInDatapoolOrThrow(UUID datapoolId, UUID id) {
    return useCaseRepository
        .findByIdAndDatapoolId(id, datapoolId)
        .orElseThrow(() -> new ResourceNotFoundException("UseCase", id.toString()));
  }

  private void requireDatapool(UUID datapoolId) {
    if (!dataPoolRepository.existsById(datapoolId)) {
      throw new ResourceNotFoundException("DataPool", datapoolId.toString());
    }
  }

  /**
   * Writes the record before the first artifact. The unique index on the package id and the Data
   * pool allows one active record for each package in each Data pool. A second install of the same
   * package in the same Data pool that runs at the same time waits here until the first install
   * commits. Then the database refuses the record, before the install changes the registry.
   */
  private void claim(UseCase useCase) {
    useCaseRepository.saveAndFlush(useCase);
  }

  private static UniqueConstraintViolationException alreadyInstalled(
      String packageId, UUID datapoolId) {
    return new UniqueConstraintViolationException(
        datapoolId == null
            ? "Package '%s' is already installed on this instance.".formatted(packageId)
            : "Package '%s' is already installed in the Data pool '%s'."
                .formatted(packageId, datapoolId));
  }

  /**
   * Creates the Data structure first, because the backend makes the URN of the model from the id of
   * the Data structure. Then stores the model as the first version of the Data structure. The later
   * members reference the copy by each identity of the member.
   */
  private UseCaseArtifact installDataStructure(PreparedMember prepared, Map<String, Copy> copies) {
    DataStructure structure = dataStructureService.create(prepared.dataStructureInput());
    DataStructureVersion version =
        dataStructureVersionService.create(prepared.dataStructureVersionInput(structure.getId()));

    String logicalUrn = modelRegistryGateway.logicalUrn(version.getModelUrn());
    register(
        prepared,
        new Copy(logicalUrn, version.getModelUrn(), structure.getId(), version.getId()),
        copies);
    return line(
        UseCaseArtifactType.DATA_STRUCTURE,
        prepared,
        structure.getId(),
        logicalUrn,
        version.getModelUrn());
  }

  /**
   * Creates the Data source with the connector of its document. The Data source reads the version
   * of its Data structure copy, and only the Data pool of the request can use it. It stays DRAFT.
   */
  private UseCaseArtifact installDataSource(
      PreparedMember prepared, UUID datapoolId, Map<String, Copy> copies) {
    DataSource source =
        dataSourceService.create(
            prepared.dataSourceInput(datapoolId, reference -> copyOf(reference, copies)));

    register(
        prepared,
        new Copy(
            source.getConfigurationLogicalUrn(),
            source.getConfigurationUrn(),
            source.getId(),
            null),
        copies);
    return line(
        UseCaseArtifactType.DATA_SOURCE,
        prepared,
        source.getId(),
        source.getConfigurationLogicalUrn(),
        source.getConfigurationUrn());
  }

  /**
   * Creates a Dataset without its named APIs. An API of a standard that Data sinks back needs such
   * a Data sink in the Dataset, so {@link #installNamedApis} adds the APIs after the Data sinks.
   * The members of the Dataset find its copy by the identity of the member. The platform does not
   * give the manifest of a Dataset a version, so its line has the logical URN only.
   */
  private UseCaseArtifact installDataSet(
      PreparedMember prepared, UUID datapoolId, Map<String, Copy> copies) {
    DataSet dataSet = dataSetService.create(prepared.dataSetInput(datapoolId, List.of()));
    register(
        prepared, new Copy(dataSet.getManifestLogicalUrn(), null, dataSet.getId(), null), copies);
    return line(
        UseCaseArtifactType.DATA_SET,
        prepared,
        dataSet.getId(),
        dataSet.getManifestLogicalUrn(),
        null);
  }

  /**
   * Adds the named APIs to the Dataset, through the same update as the Dataset API. The Dataset
   * checks its named APIs as one list, so it gets all of them in one update, with the name, the
   * description and the Data pool that the install gave it. The record has one line for each named
   * API.
   */
  private List<UseCaseArtifact> installNamedApis(
      PreparedMember dataSetMember, UUID dataSetId, List<PreparedMember> apis, UUID datapoolId) {
    DataSet dataSet =
        dataSetService.update(dataSetId, dataSetMember.dataSetInput(datapoolId, apis));

    List<UseCaseArtifact> lines = new ArrayList<>();
    for (PreparedMember api : apis) {
      UUID apiId = namedApiId(dataSet, api.slug());
      lines.add(line(UseCaseArtifactType.NAMED_API, api, apiId, null, null));
    }
    return lines;
  }

  /** Returns the id of the named API with the slug. The Dataset keeps the slugs unique. */
  private static UUID namedApiId(DataSet dataSet, String slug) {
    return dataSet.getNamedApis().stream()
        .filter(api -> slug.equals(api.getSlug()))
        .map(NamedApi::getId)
        .findFirst()
        .orElse(null);
  }

  /**
   * Creates the Mapping in the Dataset, through the same service call as the Mapping API. A Mapping
   * has no row in the portal, so its line has no shell id.
   */
  private UseCaseArtifact installMapping(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    ModelRegistryGateway.ModelPin pin =
        mappingService
            .create(
                dataSetId,
                prepared.mappingInput(reference -> copyOf(reference, copies), objectMapper))
            .pin();

    register(prepared, new Copy(pin.logicalUrn(), pin.versionedUrn(), null, null), copies);
    return line(UseCaseArtifactType.MAPPING, prepared, null, pin.logicalUrn(), pin.versionedUrn());
  }

  /** Creates the Data sink in the Dataset. */
  private UseCaseArtifact installDataSink(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    DataSink sink =
        dataSinkService.create(
            prepared.dataSinkInput(dataSetId, reference -> copyOf(reference, copies)));

    register(
        prepared,
        new Copy(sink.getConfigurationLogicalUrn(), sink.getConfigurationUrn(), sink.getId(), null),
        copies);
    return line(
        UseCaseArtifactType.DATA_SINK,
        prepared,
        sink.getId(),
        sink.getConfigurationLogicalUrn(),
        sink.getConfigurationUrn());
  }

  /** Creates the Pipeline in the Dataset. */
  private UseCaseArtifact installPipeline(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    Pipeline pipeline =
        pipelineService.create(
            prepared.pipelineInput(dataSetId, reference -> copyOf(reference, copies)));

    return line(
        UseCaseArtifactType.PIPELINE,
        prepared,
        pipeline.getId(),
        pipeline.getModelLogicalUrn(),
        pipeline.getModelUrn());
  }

  /** Creates the style in the Dataset. A layer references the style by the id of the copy. */
  private UseCaseArtifact installStyle(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    Style style = styleService.create(prepared.styleInput(dataSetId));

    register(prepared, new Copy(null, null, style.getId(), null), copies);
    return line(UseCaseArtifactType.STYLE, prepared, style.getId(), null, null);
  }

  /** Creates the layer in the Dataset, with the ids of the Data sink copy and of the styles. */
  private UseCaseArtifact installLayer(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    Layer layer =
        layerService.create(prepared.layerInput(dataSetId, reference -> copyOf(reference, copies)));

    return line(UseCaseArtifactType.LAYER, prepared, layer.getId(), null, null);
  }

  private Copy copyOf(String reference, Map<String, Copy> copies) {
    return copies.get(key(reference));
  }

  /** Keeps the copy of a member under each identity of the member. */
  private void register(PreparedMember prepared, Copy copy, Map<String, Copy> copies) {
    packagePreparer.identitiesOf(prepared.member()).forEach(identity -> copies.put(identity, copy));
  }

  private static UseCaseArtifact line(
      UseCaseArtifactType type,
      PreparedMember prepared,
      UUID shellId,
      String urn,
      String versionedUrn) {
    UseCaseArtifact artifact = new UseCaseArtifact();
    artifact.setArtifactType(type);
    artifact.setName(prepared.name());
    artifact.setShellId(shellId);
    artifact.setUrn(urn);
    artifact.setVersionedUrn(versionedUrn);
    artifact.setOrigin(prepared.member().getUrn());
    return artifact;
  }

  /** Returns the id of the copy of the Dataset that a member names in {@code dataSetRef}. */
  private UUID dataSetIdOf(PreparedMember member, Map<String, Copy> copies) {
    return copyOf(member.member().getDataSetRef(), copies).shellId();
  }

  /** Returns the API members of one Dataset, by the key of the identity of the Dataset. */
  private List<PreparedMember> apisOf(List<PreparedMember> members, String dataSet) {
    return members.stream()
        .filter(member -> member.kind() == PackageMemberKind.API)
        .filter(member -> dataSet.equals(key(member.member().getDataSetRef())))
        .toList();
  }

  private String key(String identity) {
    return packagePreparer.key(identity);
  }
}

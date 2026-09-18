package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.modelregistry.DataStructureUrns;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Installs one package on this instance and records what it did.
 *
 * <p>Everything happens in one transaction, so a package is installed whole or not at all — and the
 * journal entry exists exactly when the install committed. The install does what an operator would
 * do by hand, in the order the platform enforces: structures first and released, because a source
 * links only a released structure; then sources, released, because a pipeline links only a released
 * source; then the dataset, which stays DRAFT because releasing it rolls out infrastructure and
 * remains the operator's own decision; then the mappings that belong to it.
 *
 * <p>An installation is a copy, not a shared identity. Every artifact a package ships is created
 * under a URN this instance mints, and the URN the member carried in the package is recorded on its
 * journal line as its origin. Identities inside the package only serve to let members refer to one
 * another; every such reference is rewritten to the minted copy before the referring member is
 * stored.
 */
@Service
@RequiredArgsConstructor
public class InstallationService {

  private static final String ID = "$id";
  private static final String DEFS = "$defs";
  private static final String REF = "$ref";
  private static final String SCHEMA = "$schema";
  private static final String STAMPED_ID = "id";
  private static final String TITLE = "title";
  private static final String CONNECTION_TYPE = "connectionType";
  private static final String ELEMENT = "element";
  private static final String SOURCE = "source";
  private static final String TARGET = "target";
  private static final String OPEN_DATA_ACCESS = "openDataAccess";

  private final InstallationRepository installationRepository;
  private final DataStructureService dataStructureService;
  private final DataStructureVersionService dataStructureVersionService;
  private final DataSourceService dataSourceService;
  private final DataSetService dataSetService;
  private final MappingService mappingService;
  private final ModelRegistryGateway modelRegistryGateway;

  /**
   * What one package member became on this instance. Sources reference structures by version id,
   * mappings by versioned URN, the journal by both — so the copy is remembered in every form the
   * later members need.
   */
  private record Minted(String logicalUrn, String versionedUrn, UUID shellId, UUID versionId) {}

  /** Everything one install accumulates while it works through the members. */
  private static final class Run {
    final Installation installation;
    final UUID datapoolId;
    final Map<String, Minted> minted = new LinkedHashMap<>();
    final List<String> structureUrns = new ArrayList<>();
    final List<DataSource> sources = new ArrayList<>();
    final Map<String, DataSet> datasets = new LinkedHashMap<>();

    Run(Installation installation, UUID datapoolId) {
      this.installation = installation;
      this.datapoolId = datapoolId;
    }
  }

  @Transactional
  public Installation install(InstallationInputDTO input) {
    PackageManifestInputDTO manifest = input.getPackageManifest();
    // A policy, not a technical limit: two copies of the same package on one instance serve
    // nobody. Updating means installing the new version and uninstalling the old one.
    if (installationRepository.existsByPackageId(manifest.getId())) {
      throw new UniqueConstraintViolationException(
          ("Package '%s' is already installed on this instance; updating an installation is not"
                  + " supported yet.")
              .formatted(manifest.getId()));
    }
    List<PackageMemberInputDTO> members =
        manifest.getMembers().stream()
            .sorted(Comparator.comparing(PackageMemberInputDTO::getKind))
            .toList();
    requireDatapoolWhereNeeded(members, input.getDatapoolId(), manifest.getId());

    Installation installation = new Installation();
    installation.setPackageId(manifest.getId());
    installation.setPackageVersion(manifest.getVersion());
    Run run = new Run(installation, input.getDatapoolId());

    for (PackageMemberInputDTO member : members) {
      installation.addArtifact(
          switch (member.getKind()) {
            case DATASTRUCTURE -> installDataStructure(member, run);
            case DATASOURCE -> installDataSource(member, run);
            case DATASET -> installDataSet(member, run);
            case MAPPING -> installMapping(member, run);
            default ->
                throw new InvalidInputException(
                    "kind",
                    member.getUrn(),
                    ("Member kind '%s' is not installable yet; this increment covers data"
                            + " structures, data sources, datasets and mappings.")
                        .formatted(member.getKind()));
          });
    }
    linkMembersIntoDatasets(run);
    return installationRepository.save(installation);
  }

  @Transactional(readOnly = true)
  public Page<Installation> findAll(Pageable pageable) {
    return installationRepository.findAllByOrderByCreatedAtDesc(pageable);
  }

  @Transactional(readOnly = true)
  public Installation findByIdOrThrow(UUID id) {
    return installationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Installation", id.toString()));
  }

  /**
   * The datapool is instance knowledge no package can carry, and sources and datasets cannot exist
   * without one — so it is demanded up front, before anything is created.
   */
  private static void requireDatapoolWhereNeeded(
      List<PackageMemberInputDTO> members, UUID datapoolId, String packageId) {
    boolean needsPool =
        members.stream()
            .anyMatch(
                m ->
                    m.getKind() == PackageMemberKind.DATASOURCE
                        || m.getKind() == PackageMemberKind.DATASET);
    if (needsPool && datapoolId == null) {
      throw new InvalidInputException(
          "datapoolId",
          packageId,
          "The package ships data sources or datasets; datapoolId names the datapool they are"
              + " installed into.");
    }
  }

  /**
   * Creates the shell first, because the minted URN derives from the shell's id — exactly as the
   * editor does it — then stores the model under that identity and releases both. The package's own
   * {@code $id}s are replaced, not kept: root and every inline {@code $defs} member get the minted
   * URNs, and the replaced values are remembered so later members can be rewritten to point at the
   * copies.
   */
  private InstalledArtifact installDataStructure(PackageMemberInputDTO member, Run run) {
    String name = displayName(member);

    DataStructureInputDTO structureInput = new DataStructureInputDTO();
    structureInput.setName(name);
    structureInput.setDescription(member.getDescription());
    structureInput.setCreatedFromDataSource(false);
    DataStructure shell = dataStructureService.create(structureInput);

    String localUrn = DataStructureUrns.dataStructure(name, shell.getId());
    Map<String, Object> model = withMintedIdentities(member.getContent(), localUrn, run.minted);

    DataStructureVersionInputDTO versionInput = new DataStructureVersionInputDTO();
    versionInput.setDataStructureId(shell.getId());
    versionInput.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    versionInput.setDescription(member.getDescription());
    versionInput.setModelName(name);
    versionInput.setModel(model);
    DataStructureVersion version = dataStructureVersionService.create(versionInput);

    // Released here, not later: a source links only an AVAILABLE version of an AVAILABLE
    // structure, and the package's sources come next. Version first, then the shell - which
    // checks its own versions collection, so the new version is registered on the managed shell
    // (the version service only sets the owning side) before the shell's release looks at it.
    dataStructureVersionService.release(version.getId());
    shell.getDataStructureVersions().add(version);
    dataStructureService.release(shell.getId());

    String logicalUrn = modelRegistryGateway.logicalUrn(version.getModelUrn());
    run.minted.put(
        key(member.getUrn()),
        new Minted(logicalUrn, version.getModelUrn(), shell.getId(), version.getId()));
    run.structureUrns.add(logicalUrn);
    return line(
        InstalledArtifactType.DATA_STRUCTURE,
        name,
        shell.getId(),
        logicalUrn,
        version.getModelUrn(),
        member.getUrn());
  }

  /**
   * The member's content is the CORE datasource document: {@code connectionType} and, as {@code
   * element}, the package URN of the structure it reads; everything else is connector
   * configuration. The service normalises and encrypts that configuration and validates it against
   * the connector; the install only translates the document into the service's input and binds the
   * structure reference to the copy made earlier.
   */
  private InstalledArtifact installDataSource(PackageMemberInputDTO member, Run run) {
    String name = displayName(member);
    Map<String, Object> content = member.getContent();
    ConnectorType connectorType =
        connectorType(requireString(content, CONNECTION_TYPE, member), member);
    Minted structure =
        resolveReference(requireString(content, ELEMENT, member), ELEMENT, member, run);
    if (structure.versionId() == null) {
      throw new InvalidInputException(
          ELEMENT, member.getUrn(), "'element' must reference a data structure member.");
    }
    Map<String, Object> configuration = new LinkedHashMap<>(content);
    configuration.remove(CONNECTION_TYPE);
    configuration.remove(ELEMENT);
    configuration.remove(SCHEMA);
    configuration.remove(STAMPED_ID);

    DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
    scope.setType(DatapoolScopeType.SPECIFIC);
    scope.setDatapoolIds(List.of(run.datapoolId));

    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName(name);
    input.setDescription(member.getDescription());
    input.setConnectorType(connectorType);
    input.setConfiguration(configuration);
    input.setDataStructureVersionId(structure.versionId());
    input.setDatapoolScope(scope);
    DataSource source = dataSourceService.create(input);
    // A pipeline links only a released source; releasing here is what makes the package's own
    // pipelines installable in the next increment, and what an operator would do by hand.
    dataSourceService.release(source.getId());

    run.minted.put(
        key(member.getUrn()),
        new Minted(
            source.getConfigurationLogicalUrn(),
            source.getConfigurationUrn(),
            source.getId(),
            null));
    run.sources.add(source);
    return line(
        InstalledArtifactType.DATA_SOURCE,
        name,
        source.getId(),
        source.getConfigurationLogicalUrn(),
        source.getConfigurationUrn(),
        member.getUrn());
  }

  /**
   * The dataset is the container the package's mappings (and later sinks and pipelines) belong to.
   * It stays DRAFT: releasing a dataset rolls out infrastructure, and that remains the operator's
   * explicit decision. The first dataset of a package is also named on the installation header, so
   * the install list can show it without reading the lines.
   */
  private InstalledArtifact installDataSet(PackageMemberInputDTO member, Run run) {
    String name = displayName(member);
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName(name);
    input.setDescription(member.getDescription());
    input.setDatapoolId(run.datapoolId);
    if (member.getContent().get(OPEN_DATA_ACCESS) instanceof Boolean openDataAccess) {
      input.setOpenDataAccess(openDataAccess);
    }
    DataSet dataSet = dataSetService.create(input);

    run.datasets.put(key(member.getUrn()), dataSet);
    run.minted.put(
        key(member.getUrn()),
        new Minted(
            dataSet.getManifestLogicalUrn(), dataSet.getManifestUrn(), dataSet.getId(), null));
    if (run.installation.getDataSetId() == null) {
      run.installation.setDataSetId(dataSet.getId());
      run.installation.setDataSetName(dataSet.getName());
    }
    return line(
        InstalledArtifactType.DATA_SET,
        name,
        dataSet.getId(),
        dataSet.getManifestLogicalUrn(),
        dataSet.getManifestUrn(),
        member.getUrn());
  }

  /**
   * The member's content is the CORE mapping document. {@code source} and {@code target} name
   * structures by package URN and are bound to the versioned URN of the copies — the form the
   * mapping compiler requires — before the service stores the mapping under its dataset, mints its
   * URN and links it into the dataset's manifest.
   */
  private InstalledArtifact installMapping(PackageMemberInputDTO member, Run run) {
    String name = displayName(member);
    DataSet dataSet = datasetFor(member, run);
    Map<String, Object> document = new LinkedHashMap<>(member.getContent());
    document.remove(SCHEMA);
    document.remove(STAMPED_ID);
    for (String field : List.of(SOURCE, TARGET)) {
      if (document.get(field) instanceof String reference && !reference.isBlank()) {
        Minted structure = resolveReference(reference, field, member, run);
        if (structure.versionId() == null) {
          throw new InvalidInputException(
              field, member.getUrn(), "'" + field + "' must reference a data structure member.");
        }
        document.put(field, structure.versionedUrn());
      }
    }
    if (!(document.get(TITLE) instanceof String title) || title.isBlank()) {
      document.put(TITLE, name);
    }
    ModelRegistryGateway.ModelPin pin = mappingService.store(dataSet.getId(), null, document);

    run.minted.put(
        key(member.getUrn()), new Minted(pin.logicalUrn(), pin.versionedUrn(), null, null));
    return line(
        InstalledArtifactType.MAPPING,
        name,
        null,
        pin.logicalUrn(),
        pin.versionedUrn(),
        member.getUrn());
  }

  /**
   * A dataset's manifest is the bracket around a use case; a member the manifest does not name
   * shows up as an orphan although it belongs here. Mappings link themselves when stored; the
   * structures and sources are linked into every dataset the package ships.
   */
  private void linkMembersIntoDatasets(Run run) {
    for (DataSet dataSet : run.datasets.values()) {
      run.structureUrns.forEach(urn -> dataSetService.linkMember(dataSet.getId(), urn));
      run.sources.stream()
          .map(DataSource::getConfigurationLogicalUrn)
          .filter(urn -> urn != null && !urn.isBlank())
          .forEach(urn -> dataSetService.linkMember(dataSet.getId(), urn));
    }
  }

  /**
   * The dataset a dataset-bound member belongs to: the one it names, or the package's only one.
   * Members are installed in kind order, so every dataset of the package exists by the time this is
   * asked.
   */
  private DataSet datasetFor(PackageMemberInputDTO member, Run run) {
    if (member.getDataset() != null && !member.getDataset().isBlank()) {
      DataSet named = run.datasets.get(key(member.getDataset()));
      if (named == null) {
        throw new InvalidInputException(
            "dataset",
            member.getUrn(),
            "Member '%s' names dataset '%s', which is not a dataset member of this package."
                .formatted(member.getUrn(), member.getDataset()));
      }
      return named;
    }
    if (run.datasets.size() == 1) {
      return run.datasets.values().iterator().next();
    }
    throw new InvalidInputException(
        "dataset",
        member.getUrn(),
        run.datasets.isEmpty()
            ? "Member '%s' belongs to a dataset, but the package ships none."
                .formatted(member.getUrn())
            : "Member '%s' must name its dataset: the package ships %d."
                .formatted(member.getUrn(), run.datasets.size()));
  }

  /**
   * What a package URN became on this instance. Only members of this package resolve — binding to
   * artifacts installed earlier, by origin, is a later increment — and the order of kinds
   * guarantees that whatever a member may reference has already been installed.
   */
  private Minted resolveReference(
      String reference, String field, PackageMemberInputDTO member, Run run) {
    Minted minted = run.minted.get(key(reference));
    if (minted == null) {
      throw new InvalidInputException(
          field,
          member.getUrn(),
          "Member '%s' references '%s' in '%s', which is not part of this package."
              .formatted(member.getUrn(), reference, field));
    }
    return minted;
  }

  private static ConnectorType connectorType(String connectionType, PackageMemberInputDTO member) {
    try {
      return ConnectorType.valueOf(connectionType.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException(
          CONNECTION_TYPE,
          member.getUrn(),
          "Unknown connectionType '%s'.".formatted(connectionType));
    }
  }

  private static String requireString(
      Map<String, Object> content, String field, PackageMemberInputDTO member) {
    if (content.get(field) instanceof String value && !value.isBlank()) {
      return value;
    }
    throw new InvalidInputException(
        field, member.getUrn(), "Member '%s' needs '%s'.".formatted(member.getUrn(), field));
  }

  /** Package URNs are compared version-free; a package may pin a version, the copy decides it. */
  private String key(String packageUrn) {
    return modelRegistryGateway.logicalUrn(packageUrn);
  }

  /**
   * A copy of the model with the root {@code $id} and each inline {@code $defs} member's {@code
   * $id} replaced by minted URNs. Members that are only a {@code $ref} to something existing are
   * left alone. Only these two levels carry identities the registry would otherwise adopt as its
   * own; deeper {@code $id}s are not part of the CORE data structure contract.
   */
  private Map<String, Object> withMintedIdentities(
      Map<String, Object> content, String localUrn, Map<String, Minted> minted) {
    Map<String, Object> model = new LinkedHashMap<>(content);
    rememberOrigin(model.get(ID), localUrn, minted);
    model.put(ID, localUrn);

    if (model.get(DEFS) instanceof Map<?, ?> defs) {
      Map<String, Object> rewrittenDefs = new LinkedHashMap<>();
      defs.forEach(
          (key, def) -> {
            String memberName = String.valueOf(key);
            if (def instanceof Map<?, ?> schema && !schema.containsKey(REF)) {
              Map<String, Object> copy = new LinkedHashMap<>();
              schema.forEach((k, v) -> copy.put(String.valueOf(k), v));
              String elementUrn = DataStructureUrns.elementForMember(localUrn, memberName);
              rememberOrigin(copy.get(ID), elementUrn, minted);
              copy.put(ID, elementUrn);
              rewrittenDefs.put(memberName, copy);
            } else {
              rewrittenDefs.put(memberName, def);
            }
          });
      model.put(DEFS, rewrittenDefs);
    }
    return model;
  }

  /**
   * Element identities inside a structure are minted with it but have no version of their own the
   * install could pin; they are remembered by logical URN only.
   */
  private void rememberOrigin(Object packageId, String localUrn, Map<String, Minted> minted) {
    if (packageId instanceof String origin && !origin.isBlank()) {
      minted.putIfAbsent(key(origin), new Minted(localUrn, null, null, null));
    }
  }

  /** The member's name, falling back to the document's title — the URN's name segment needs one. */
  private static String displayName(PackageMemberInputDTO member) {
    if (member.getName() != null && !member.getName().isBlank()) {
      return member.getName();
    }
    if (member.getContent().get(TITLE) instanceof String title && !title.isBlank()) {
      return title;
    }
    throw new InvalidInputException(
        "name", member.getUrn(), "A package member needs a name or a document title.");
  }

  private static InstalledArtifact line(
      InstalledArtifactType type,
      String name,
      UUID shellId,
      String urn,
      String versionedUrn,
      String origin) {
    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(type);
    artifact.setName(name);
    artifact.setShellId(shellId);
    artifact.setUrn(urn);
    artifact.setVersionedUrn(versionedUrn);
    artifact.setOrigin(origin);
    artifact.setAction(InstalledArtifactAction.CREATED);
    return artifact;
  }
}

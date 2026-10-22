package de.civitascore.portal.service;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.service.connector.ConnectorValidator;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

/**
 * Installs a package on this instance and keeps a record of the install.
 *
 * <p>For each member, the install creates a copy on this instance. The backend gives each copy its
 * own URN. The record keeps the identity of the member in the package as the origin of the copy.
 * The members of a package reference each other by these identities. The install creates the
 * members in the order of their kinds and binds each reference to the copy of the member that it
 * names: first the Data structures, then the Data sources that read them, then the Dataset with its
 * named APIs, then the Mappings, Data sinks and Pipelines of its data flow, and last the styles and
 * layers of its map. The install does not release what it creates. Each copy stays DRAFT, and the
 * release goes through the endpoint of each artifact.
 *
 * <p>The install makes all changes to the database in one transaction. If one member fails, the
 * install keeps nothing. Model Forge keeps its dependency graph in memory, and a rollback does not
 * restore the graph. For this reason, the install does all its checks before the first change to
 * the registry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationService {

  private static final String ID = "$id";
  private static final String SCHEMA = "$schema";
  private static final String STAMPED_ID = "id";
  private static final String TITLE = "title";
  private static final String DESCRIPTION = "description";
  private static final String NAME = "name";
  private static final String URN = "urn";
  private static final String KIND = "kind";
  private static final String DATAPOOL_ID = "datapoolId";
  private static final String ELEMENT = "element";
  private static final String SLUG = "slug";
  private static final String STANDARD = "standard";
  private static final String VERSION = "version";
  private static final String CONNECTION_TYPE = "connectionType";
  private static final String SOURCE = "source";
  private static final String TARGET = "target";
  private static final String NODES = "nodes";
  private static final String SLD_CONTENT = "sldContent";
  private static final String LAYER_NAME = "layerName";
  private static final String SINK_REF = "sinkRef";
  private static final String DEFAULT_STYLE_REF = "defaultStyleRef";
  private static final String ALTERNATIVE_STYLE_REFS = "alternativeStyleRefs";
  private static final String ATTRIBUTE = "attribute";
  private static final String GEOMETRY_COLUMN_REF = "geometryColumnRef";
  private static final String CQL_FILTER = "cqlFilter";
  private static final String CRS = "crs";
  private static final String NATIVE_BOUNDING_BOX = "nativeBoundingBox";
  private static final String LAT_LON_BOUNDING_BOX = "latLonBoundingBox";

  /**
   * The version token of a reference to the newest version. A Pipeline references its Data sources
   * in this form, as the editor does, and its Mappings and Data sinks by a fixed version.
   */
  private static final String LATEST = ":latest";

  /** The kinds whose copies belong to the Dataset of the package. */
  private static final Set<PackageMemberKind> PART_OF_DATASET =
      EnumSet.of(
          PackageMemberKind.API,
          PackageMemberKind.MAPPING,
          PackageMemberKind.DATASINK,
          PackageMemberKind.PIPELINE,
          PackageMemberKind.STYLE,
          PackageMemberKind.LAYER);

  /**
   * The kinds whose name on the platform comes from the document. GeoServer knows a style and a
   * layer by this name.
   */
  private static final Set<PackageMemberKind> NAMED_BY_DOCUMENT =
      EnumSet.of(PackageMemberKind.STYLE, PackageMemberKind.LAYER);

  /** A field of a Pipeline node that references another member, and the kind of that member. */
  private record NodeReference(String field, PackageMemberKind kind) {}

  /** The fields of a Pipeline node that reference other members. No other field changes. */
  private static final List<NodeReference> NODE_REFERENCES =
      List.of(
          new NodeReference("sourceRef", PackageMemberKind.DATASOURCE),
          new NodeReference("lookupSourceRef", PackageMemberKind.DATASOURCE),
          new NodeReference("mappingRef", PackageMemberKind.MAPPING),
          new NodeReference(SINK_REF, PackageMemberKind.DATASINK));

  private final InstallationRepository installationRepository;
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
  private final ConnectorValidator connectorValidator;

  /**
   * The install makes the inputs of the Dataset, the named APIs, the styles and the layers itself,
   * so no controller validates them. The install applies the same constraints with this validator.
   */
  private final Validator validator;

  /**
   * A member that passed the checks, with the name and the description that its copy gets. Only a
   * Data source member has a connector.
   */
  private record PreparedMember(
      PackageMemberInputDTO member,
      String name,
      String description,
      ConnectorConfiguration connector) {

    PackageMemberKind kind() {
      return member.getKind();
    }
  }

  /**
   * What a member became on this instance. The install keeps the copy in each form that a reference
   * of a later member needs: a Data source references its Data structure by the id of the version,
   * a Mapping and a Data sink by the versioned URN, a Pipeline its Data sources by the logical URN
   * and the id, and a layer its Data sink and its styles by the id.
   */
  private record Copy(String logicalUrn, String versionedUrn, UUID shellId, UUID versionId) {}

  /**
   * Installs a package and returns the record of the install.
   *
   * @param input the package to install, and the Data pool for its Data sources and its Dataset
   * @return the record, with one line for each artifact that the install created
   * @throws UniqueConstraintViolationException if this instance has an installation of the package
   * @throws org.springframework.dao.DataIntegrityViolationException if an install of the same
   *     package that runs at the same time commits first
   * @throws InvalidInputException if the package or one of its members fails a check, or if the
   *     platform refuses a member
   */
  @Transactional
  public Installation install(InstallationInputDTO input) {
    PackageManifestInputDTO manifest = input.getPackageManifest();
    if (installationRepository.existsByPackageId(manifest.getId())) {
      throw alreadyInstalled(manifest.getId());
    }
    UUID datapoolId = input.getDatapoolId();
    List<PreparedMember> members = prepare(manifest, datapoolId);

    Installation installation = new Installation();
    installation.setPackageId(manifest.getId());
    installation.setPackageVersion(manifest.getVersion());
    claim(installation);

    Map<String, Copy> copies = new HashMap<>();
    List<PreparedMember> apis = ofKind(members, PackageMemberKind.API);
    // The checks make sure that a member of a kind that belongs to the Dataset comes only with the
    // Dataset, and the order of the kinds makes sure that the Dataset exists before such a member.
    UUID dataSetId = null;
    for (PreparedMember member : members) {
      switch (member.kind()) {
        case DATASTRUCTURE -> installation.addArtifact(installDataStructure(member, copies));
        case DATASOURCE -> installation.addArtifact(installDataSource(member, datapoolId, copies));
        case DATASET -> {
          List<InstalledArtifact> lines = installDataSet(member, apis, datapoolId);
          dataSetId = lines.getFirst().getShellId();
          lines.forEach(installation::addArtifact);
        }
        case API -> {
          // The Dataset creates its named APIs, see installDataSet.
        }
        case MAPPING -> installation.addArtifact(installMapping(member, dataSetId, copies));
        case DATASINK -> installation.addArtifact(installDataSink(member, dataSetId, copies));
        case PIPELINE -> installation.addArtifact(installPipeline(member, dataSetId, copies));
        case STYLE -> installation.addArtifact(installStyle(member, dataSetId, copies));
        case LAYER -> installation.addArtifact(installLayer(member, dataSetId, copies));
      }
    }
    Installation saved = installationRepository.save(installation);
    log.info(
        "Installed package {} as installation {}",
        Encode.forJava(saved.getPackageId()),
        saved.getId());
    return saved;
  }

  /**
   * Returns a page of installations.
   *
   * @param pageable the page and the sort order
   * @return the installations of the page, each with its lines
   */
  @Transactional(readOnly = true)
  public Page<Installation> findAll(Pageable pageable) {
    return installationRepository.findAll(pageable);
  }

  /**
   * Returns one installation.
   *
   * @param id the id of the installation
   * @return the installation with its lines
   * @throws ResourceNotFoundException if the installation does not exist
   */
  @Transactional(readOnly = true)
  public Installation findByIdOrThrow(UUID id) {
    return installationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Installation", id.toString()));
  }

  /**
   * Writes the record before the first artifact. The unique index on the package id allows one
   * record for each package. A second install of the same package that runs at the same time waits
   * here until the first install commits. Then the database refuses the record, before the install
   * changes the registry.
   */
  private void claim(Installation installation) {
    installationRepository.saveAndFlush(installation);
  }

  private static UniqueConstraintViolationException alreadyInstalled(String packageId) {
    return new UniqueConstraintViolationException(
        "Package '%s' is already installed on this instance.".formatted(packageId));
  }

  /**
   * Checks the package before the install changes something, and returns its members in the order
   * of the install. The kinds come in the order of {@link PackageMemberKind}. The members of one
   * kind keep the order of the request.
   */
  private List<PreparedMember> prepare(PackageManifestInputDTO manifest, UUID datapoolId) {
    List<PackageMemberInputDTO> members =
        manifest.getMembers().stream()
            .sorted(Comparator.comparing(PackageMemberInputDTO::getKind))
            .toList();
    Map<String, PackageMemberKind> kinds = kindsByIdentity(members);
    requireOneDataSetWhereNecessary(members, manifest.getId());
    requireDatapoolWhereNecessary(members, datapoolId, manifest.getId());
    return members.stream().map(member -> prepare(member, kinds, datapoolId)).toList();
  }

  /**
   * Checks one member before the install changes something, and returns it with the name and the
   * description that its copy gets.
   */
  private PreparedMember prepare(
      PackageMemberInputDTO member, Map<String, PackageMemberKind> kinds, UUID datapoolId) {
    String name = requireName(member);
    return switch (member.getKind()) {
      case DATASTRUCTURE -> new PreparedMember(member, name, requireDescription(member), null);
      case DATASOURCE -> {
        String description = requireDescription(member);
        requireReference(member, ELEMENT, PackageMemberKind.DATASTRUCTURE, kinds);
        yield new PreparedMember(member, name, description, requireValidConnector(member));
      }
      case DATASET -> {
        String description = requireDescription(member);
        requireValid(dataSetInput(name, description, datapoolId, List.of()), member);
        yield new PreparedMember(member, name, description, null);
      }
      case API -> {
        requireValid(namedApiInput(member, name), member);
        yield new PreparedMember(member, name, null, null);
      }
      case MAPPING -> {
        checkOptionalReference(member, SOURCE, PackageMemberKind.DATASTRUCTURE, kinds);
        checkOptionalReference(member, TARGET, PackageMemberKind.DATASTRUCTURE, kinds);
        yield new PreparedMember(member, name, null, null);
      }
      case DATASINK -> {
        dataSinkType(member);
        checkOptionalReference(member, ELEMENT, PackageMemberKind.DATASTRUCTURE, kinds);
        yield new PreparedMember(member, name, null, null);
      }
      case PIPELINE -> {
        checkNodeReferences(member, kinds);
        yield new PreparedMember(member, name, optionalDescription(member), null);
      }
      case STYLE -> {
        requireValid(styleInput(member, name), member);
        yield new PreparedMember(member, name, null, null);
      }
      case LAYER -> {
        requireReference(member, SINK_REF, PackageMemberKind.DATASINK, kinds);
        checkOptionalReference(member, DEFAULT_STYLE_REF, PackageMemberKind.STYLE, kinds);
        List<String> alternativeStyles = stringList(member, ALTERNATIVE_STYLE_REFS);
        if (alternativeStyles != null) {
          alternativeStyles.forEach(
              reference ->
                  checkReference(
                      member, ALTERNATIVE_STYLE_REFS, reference, PackageMemberKind.STYLE, kinds));
        }
        requireValidLayerName(layerInput(member, name), member);
        yield new PreparedMember(member, name, null, null);
      }
    };
  }

  /**
   * Returns the kind of each member by its identity in the package. The members reference each
   * other by these identities, so two members must not have the same identity.
   */
  private Map<String, PackageMemberKind> kindsByIdentity(List<PackageMemberInputDTO> members) {
    Map<String, PackageMemberKind> kinds = new HashMap<>();
    for (PackageMemberInputDTO member : members) {
      for (String identity : identitiesOf(member)) {
        if (kinds.putIfAbsent(identity, member.getKind()) != null) {
          throw new InvalidInputException(
              URN,
              member.getUrn(),
              "Two members of the package have the identity '%s'.".formatted(identity));
        }
      }
    }
    return kinds;
  }

  /**
   * Returns the identities of a member in the package, without a version. A Data structure also has
   * the root {@code $id} of its document as an identity, because the other documents of a package
   * can reference the Data structure by it.
   */
  private Set<String> identitiesOf(PackageMemberInputDTO member) {
    Set<String> identities = new LinkedHashSet<>();
    identities.add(key(member.getUrn()));
    if (member.getKind() == PackageMemberKind.DATASTRUCTURE
        && member.getContent().get(ID) instanceof String rootId
        && !rootId.isBlank()) {
      identities.add(key(rootId));
    }
    return identities;
  }

  /**
   * A package has at most one Dataset. The copies of some kinds belong to the Dataset, so a package
   * with a member of such a kind must have its Dataset.
   */
  private static void requireOneDataSetWhereNecessary(
      List<PackageMemberInputDTO> members, String packageId) {
    long dataSets =
        members.stream().filter(member -> member.getKind() == PackageMemberKind.DATASET).count();
    if (dataSets > 1) {
      throw new InvalidInputException(
          KIND,
          packageId,
          "Package '%s' has %d Datasets, but a package can have only one."
              .formatted(packageId, dataSets));
    }
    if (dataSets == 0
        && members.stream().anyMatch(member -> PART_OF_DATASET.contains(member.getKind()))) {
      throw new InvalidInputException(
          KIND,
          packageId,
          "Package '%s' has members that belong to a Dataset, but it has no Dataset."
              .formatted(packageId));
    }
  }

  /**
   * Data sources and Datasets exist only in a Data pool. A package cannot know the Data pools of
   * this instance, so the request names the Data pool.
   */
  private void requireDatapoolWhereNecessary(
      List<PackageMemberInputDTO> members, UUID datapoolId, String packageId) {
    boolean necessary =
        members.stream()
            .anyMatch(
                member ->
                    member.getKind() == PackageMemberKind.DATASOURCE
                        || member.getKind() == PackageMemberKind.DATASET);
    if (!necessary) {
      return;
    }
    if (datapoolId == null) {
      throw new InvalidInputException(
          DATAPOOL_ID,
          packageId,
          ("Package '%s' has Data sources or a Dataset, so the request must name their Data pool"
                  + " in 'datapoolId'.")
              .formatted(packageId));
    }
    if (!dataPoolRepository.existsById(datapoolId)) {
      throw new InvalidInputException(
          DATAPOOL_ID, datapoolId, "The Data pool '%s' does not exist.".formatted(datapoolId));
    }
  }

  /**
   * Returns the name of the copy. The member gives it, or its document gives it: as {@code name}
   * for a named API and a style, as {@code layerName} for a layer, as {@code title} for the other
   * kinds. For a style and a layer, the name in the document comes first, because the platform
   * knows them by it. The record keeps the name, so the name must fit into a line of the record.
   */
  private static String requireName(PackageMemberInputDTO member) {
    String documentField =
        switch (member.getKind()) {
          case API, STYLE -> NAME;
          case LAYER -> LAYER_NAME;
          default -> TITLE;
        };
    String name =
        NAMED_BY_DOCUMENT.contains(member.getKind())
            ? firstWithText(optionalString(member.getContent(), documentField), member.getName())
            : firstWithText(member.getName(), member.getContent().get(documentField));
    if (name == null) {
      throw new InvalidInputException(
          NAME,
          member.getUrn(),
          "Member '%s' needs a name or a document %s.".formatted(member.getUrn(), documentField));
    }
    if (name.length() > InstalledArtifact.NAME_LENGTH) {
      throw new InvalidInputException(
          NAME,
          member.getUrn(),
          "The name of member '%s' must not exceed %d characters."
              .formatted(member.getUrn(), InstalledArtifact.NAME_LENGTH));
    }
    return name;
  }

  /**
   * Returns the description of the copy. The platform requires a description for each Data
   * structure, Data source and Dataset. The member gives it, or its document gives it as {@code
   * description}.
   */
  private static String requireDescription(PackageMemberInputDTO member) {
    String description = optionalDescription(member);
    if (description == null) {
      throw new InvalidInputException(
          DESCRIPTION,
          member.getUrn(),
          "Member '%s' needs a description or a document description.".formatted(member.getUrn()));
    }
    return description;
  }

  /** Returns the description of the member or of its document, or null if neither has one. */
  private static String optionalDescription(PackageMemberInputDTO member) {
    return firstWithText(member.getDescription(), member.getContent().get(DESCRIPTION));
  }

  /** Returns the first value that is a string with text, or null if no value has text. */
  private static String firstWithText(String value, Object fallback) {
    if (value != null && !value.isBlank()) {
      return value;
    }
    if (fallback instanceof String text && !text.isBlank()) {
      return text;
    }
    return null;
  }

  /** Returns the value of a document field that must be a string with text. */
  private static String requireString(PackageMemberInputDTO member, String field) {
    if (member.getContent().get(field) instanceof String value && !value.isBlank()) {
      return value;
    }
    throw new InvalidInputException(
        field, member.getUrn(), "Member '%s' needs '%s'.".formatted(member.getUrn(), field));
  }

  /** Checks a document field that must name a member of the package of the expected kind. */
  private void requireReference(
      PackageMemberInputDTO member,
      String field,
      PackageMemberKind expected,
      Map<String, PackageMemberKind> kinds) {
    checkReference(member, field, requireString(member, field), expected, kinds);
  }

  /**
   * Checks a document field that can be empty. If it has a value, the value must name a member of
   * the package of the expected kind.
   */
  private void checkOptionalReference(
      PackageMemberInputDTO member,
      String field,
      PackageMemberKind expected,
      Map<String, PackageMemberKind> kinds) {
    String reference = optionalString(member.getContent(), field);
    if (reference != null) {
      checkReference(member, field, reference, expected, kinds);
    }
  }

  /**
   * Checks that a reference names a member of the package of the expected kind. The order of the
   * kinds makes sure that the copy of that member exists when the install binds the reference.
   */
  private void checkReference(
      PackageMemberInputDTO member,
      String field,
      String reference,
      PackageMemberKind expected,
      Map<String, PackageMemberKind> kinds) {
    PackageMemberKind kind = kinds.get(key(reference));
    if (kind == null) {
      throw new InvalidInputException(
          field,
          member.getUrn(),
          "Member '%s' references '%s' in '%s', but the package has no member with this identity."
              .formatted(member.getUrn(), reference, field));
    }
    if (kind != expected) {
      throw new InvalidInputException(
          field,
          member.getUrn(),
          "Member '%s' references '%s' in '%s', but this member is not of the kind %s."
              .formatted(member.getUrn(), reference, field, kindName(expected)));
    }
  }

  /** Checks each reference in the nodes of a Pipeline. */
  private void checkNodeReferences(
      PackageMemberInputDTO member, Map<String, PackageMemberKind> kinds) {
    List<?> nodes = requireNodes(member);
    for (int index = 0; index < nodes.size(); index++) {
      if (!(nodes.get(index) instanceof Map<?, ?> node)) {
        continue;
      }
      for (NodeReference reference : NODE_REFERENCES) {
        if (node.get(reference.field()) instanceof String value && !value.isBlank()) {
          String field = "%s[%d].%s".formatted(NODES, index, reference.field());
          checkReference(member, field, value, reference.kind(), kinds);
        }
      }
    }
  }

  /** Returns the nodes of a Pipeline document. */
  private static List<?> requireNodes(PackageMemberInputDTO member) {
    if (member.getContent().get(NODES) instanceof List<?> nodes) {
      return nodes;
    }
    throw new InvalidInputException(
        NODES, member.getUrn(), "Member '%s' needs '%s'.".formatted(member.getUrn(), NODES));
  }

  /** Returns the name of a kind in a request. */
  private static String kindName(PackageMemberKind kind) {
    return kind.name().toLowerCase(Locale.ROOT);
  }

  /**
   * Reads the connector from the document of a Data source member, and checks it as the platform
   * checks the connector of a new Data source.
   */
  private ConnectorConfiguration requireValidConnector(PackageMemberInputDTO member) {
    ConnectorConfiguration connector;
    try {
      connector = ConnectorJson.readDocument(member.getContent());
    } catch (MalformedDataSourceRequestException e) {
      throw new InvalidInputException(
          ConnectorJson.ROOT,
          member.getUrn(),
          "Member '%s': %s".formatted(member.getUrn(), e.getMessage()));
    }
    connectorValidator.validate(connector, ConnectorValidator.Stage.DRAFT).stream()
        .findFirst()
        .ifPresent(
            violation -> {
              throw invalidField(member, violation.path(), violation.message());
            });
    return connector;
  }

  /** Returns the type of a Data sink from the {@code connectionType} of its document. */
  private static DataSinkType dataSinkType(PackageMemberInputDTO member) {
    String type = requireString(member, CONNECTION_TYPE);
    try {
      return DataSinkType.valueOf(type.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException(
          CONNECTION_TYPE,
          member.getUrn(),
          "Member '%s' has the unknown sink type '%s'. The type must be one of %s."
              .formatted(member.getUrn(), type, Arrays.toString(DataSinkType.values())));
    }
  }

  /**
   * Applies the constraints that the endpoint of the input applies. Reports the first violation by
   * field, so that the same package always fails with the same message.
   */
  private <T> void requireValid(T input, PackageMemberInputDTO member) {
    validator.validate(input).stream()
        .min(
            Comparator.comparing(
                (ConstraintViolation<T> violation) -> violation.getPropertyPath().toString()))
        .ifPresent(
            violation -> {
              throw invalidField(
                  member, violation.getPropertyPath().toString(), violation.getMessage());
            });
  }

  /**
   * Applies the constraints on the name of a layer. The other constraints of the layer apply to the
   * ids of the copies, which do not exist before the install.
   */
  private void requireValidLayerName(LayerInputDTO input, PackageMemberInputDTO member) {
    validator.validateProperty(input, LAYER_NAME).stream()
        .findFirst()
        .ifPresent(
            violation -> {
              throw invalidField(member, LAYER_NAME, violation.getMessage());
            });
  }

  private static InvalidInputException invalidField(
      PackageMemberInputDTO member, String field, String problem) {
    return new InvalidInputException(
        field,
        member.getUrn(),
        "Member '%s', field '%s': %s".formatted(member.getUrn(), field, problem));
  }

  /**
   * Creates the Data structure first, because the backend makes the URN of the model from the id of
   * the Data structure. Then stores the model as the first version of the Data structure. The later
   * members reference the copy by each identity of the member.
   */
  private InstalledArtifact installDataStructure(
      PreparedMember prepared, Map<String, Copy> copies) {
    DataStructureInputDTO structureInput = new DataStructureInputDTO();
    structureInput.setName(prepared.name());
    structureInput.setDescription(prepared.description());
    DataStructure structure = dataStructureService.create(structureInput);

    DataStructureVersionInputDTO versionInput = new DataStructureVersionInputDTO();
    versionInput.setDataStructureId(structure.getId());
    versionInput.setDescription(prepared.description());
    versionInput.setModelName(prepared.name());
    versionInput.setModel(withoutKeys(prepared.member().getContent(), ID));
    DataStructureVersion version = dataStructureVersionService.create(versionInput);

    String logicalUrn = modelRegistryGateway.logicalUrn(version.getModelUrn());
    register(
        prepared,
        new Copy(logicalUrn, version.getModelUrn(), structure.getId(), version.getId()),
        copies);
    return line(
        InstalledArtifactType.DATA_STRUCTURE,
        prepared,
        structure.getId(),
        logicalUrn,
        version.getModelUrn());
  }

  /**
   * Creates the Data source with the connector of its document. The Data source reads the version
   * of its Data structure copy, and only the Data pool of the request can use it. It stays DRAFT.
   */
  private InstalledArtifact installDataSource(
      PreparedMember prepared, UUID datapoolId, Map<String, Copy> copies) {
    DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
    scope.setType(DatapoolScopeType.SPECIFIC);
    scope.setDatapoolIds(List.of(datapoolId));

    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName(prepared.name());
    input.setDescription(prepared.description());
    input.setConnector(prepared.connector());
    input.setDataStructureVersionId(copyOf(prepared.member(), ELEMENT, copies).versionId());
    input.setDatapoolScope(scope);
    DataSource source = dataSourceService.create(input);

    register(
        prepared,
        new Copy(
            source.getConfigurationLogicalUrn(),
            source.getConfigurationUrn(),
            source.getId(),
            null),
        copies);
    return line(
        InstalledArtifactType.DATA_SOURCE,
        prepared,
        source.getId(),
        source.getConfigurationLogicalUrn(),
        source.getConfigurationUrn());
  }

  /**
   * Creates the Dataset with its named APIs. The Dataset checks its named APIs as one list, so it
   * gets all of them when the install creates it. The record has one line for the Dataset and one
   * line for each named API. The platform does not give the manifest of a Dataset a version, so the
   * line of the Dataset has the logical URN only.
   */
  private List<InstalledArtifact> installDataSet(
      PreparedMember prepared, List<PreparedMember> apis, UUID datapoolId) {
    DataSet dataSet =
        dataSetService.create(
            dataSetInput(prepared.name(), prepared.description(), datapoolId, apis));

    List<InstalledArtifact> lines = new ArrayList<>();
    lines.add(
        line(
            InstalledArtifactType.DATA_SET,
            prepared,
            dataSet.getId(),
            dataSet.getManifestLogicalUrn(),
            null));
    for (PreparedMember api : apis) {
      UUID apiId = namedApiId(dataSet, requireString(api.member(), SLUG));
      lines.add(line(InstalledArtifactType.NAMED_API, api, apiId, null, null));
    }
    return lines;
  }

  /**
   * Returns the Dataset of the package in the Data pool of the request. A package cannot make its
   * Dataset open data. This is the decision of the operator, so the Dataset keeps the default of
   * the platform.
   */
  private static DataSetInputDTO dataSetInput(
      String name, String description, UUID datapoolId, List<PreparedMember> apis) {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName(name);
    input.setDescription(description);
    input.setDatapoolId(datapoolId);
    if (!apis.isEmpty()) {
      input.setNamedApis(
          apis.stream().map(api -> namedApiInput(api.member(), api.name())).toList());
    }
    return input;
  }

  /**
   * Returns the named API that the document of an API member describes: {@code slug}, {@code
   * standard} ({@code OWS}, {@code STA} or {@code CUSTOM}), and optionally {@code version} and
   * {@code description}. The route and the id belong to the platform, so a package does not give
   * them.
   */
  private static NamedApiInputDTO namedApiInput(PackageMemberInputDTO member, String name) {
    Map<String, Object> content = member.getContent();
    NamedApiInputDTO api = new NamedApiInputDTO();
    api.setName(name);
    api.setSlug(optionalString(content, SLUG));
    api.setStandard(apiStandard(member));
    api.setVersion(optionalString(content, VERSION));
    api.setDescription(optionalString(content, DESCRIPTION));
    return api;
  }

  /** Returns the standard of a named API, or null if the document gives none. */
  private static ApiStandard apiStandard(PackageMemberInputDTO member) {
    String standard = optionalString(member.getContent(), STANDARD);
    if (standard == null) {
      return null;
    }
    try {
      return ApiStandard.valueOf(standard.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException(
          STANDARD,
          member.getUrn(),
          "Member '%s' has the unknown API standard '%s'. The standard must be one of %s."
              .formatted(member.getUrn(), standard, Arrays.toString(ApiStandard.values())));
    }
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
   * Stores the Mapping in the Dataset. Its {@code source} and {@code target} reference the
   * versioned URN of the Data structure copies, which is the form that the mapping compiler reads.
   * Model Forge gives the Mapping its identity, so the document keeps none. A Mapping has no row in
   * the portal, so its line has no shell id.
   */
  private InstalledArtifact installMapping(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    Map<String, Object> document = withoutKeys(prepared.member().getContent(), SCHEMA, STAMPED_ID);
    for (String field : List.of(SOURCE, TARGET)) {
      String reference = optionalString(document, field);
      if (reference != null) {
        document.put(field, copyOf(reference, copies).versionedUrn());
      }
    }
    if (optionalString(document, TITLE) == null) {
      document.put(TITLE, prepared.name());
    }
    ModelRegistryGateway.ModelPin pin = mappingService.store(dataSetId, null, document);

    register(prepared, new Copy(pin.logicalUrn(), pin.versionedUrn(), null, null), copies);
    return line(
        InstalledArtifactType.MAPPING, prepared, null, pin.logicalUrn(), pin.versionedUrn());
  }

  /**
   * Creates the Data sink of the type that {@code connectionType} names. The other fields of the
   * document are the configuration of the sink, without the keys that the registry owns. Its {@code
   * element} references the versioned URN of the Data structure copy. A FROST sink accepts no key
   * but {@code port} and {@code element}.
   */
  private InstalledArtifact installDataSink(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    Map<String, Object> configuration =
        withoutKeys(
            prepared.member().getContent(),
            CONNECTION_TYPE,
            SCHEMA,
            STAMPED_ID,
            TITLE,
            DESCRIPTION);
    String element = optionalString(configuration, ELEMENT);
    if (element != null) {
      configuration.put(ELEMENT, copyOf(element, copies).versionedUrn());
    }

    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setDataSetId(dataSetId);
    input.setDataSinkType(dataSinkType(prepared.member()));
    input.setConfiguration(configuration);
    DataSink sink = dataSinkService.create(input);

    register(
        prepared,
        new Copy(sink.getConfigurationLogicalUrn(), sink.getConfigurationUrn(), sink.getId(), null),
        copies);
    return line(
        InstalledArtifactType.DATA_SINK,
        prepared,
        sink.getId(),
        sink.getConfigurationLogicalUrn(),
        sink.getConfigurationUrn());
  }

  /**
   * Creates the Pipeline with the model of its document. Only the references in its nodes change,
   * because Model Forge refuses a node with a key outside the schema. The Pipeline has no styles:
   * the styles of the editor hold the ids of the instance that wrote them. Each node keeps its
   * {@code x-ui-position}.
   */
  private InstalledArtifact installPipeline(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    Map<String, Object> model =
        withoutKeys(
            prepared.member().getContent(), SCHEMA, STAMPED_ID, ModelRegistryGateway.X_UI_STYLES);
    Set<UUID> sourceIds = new LinkedHashSet<>();
    Set<UUID> sinkIds = new LinkedHashSet<>();
    model.put(
        NODES, withBoundReferences(requireNodes(prepared.member()), copies, sourceIds, sinkIds));

    PipelineInputDTO input = new PipelineInputDTO();
    input.setDataSetId(dataSetId);
    input.setName(prepared.name());
    input.setDescription(prepared.description());
    input.setModel(model);
    input.setDataSourceIds(sourceIds);
    input.setDataSinkIds(sinkIds);
    Pipeline pipeline = pipelineService.create(input);

    return line(
        InstalledArtifactType.PIPELINE,
        prepared,
        pipeline.getId(),
        pipeline.getModelLogicalUrn(),
        pipeline.getModelUrn());
  }

  /**
   * Returns a copy of the nodes with each reference bound to its copy, and collects the ids of the
   * Data sources and Data sinks that the nodes reference. A Data source reference names the newest
   * version, a Mapping and a Data sink reference a fixed version.
   */
  private List<Object> withBoundReferences(
      List<?> nodes, Map<String, Copy> copies, Set<UUID> sourceIds, Set<UUID> sinkIds) {
    List<Object> bound = new ArrayList<>(nodes.size());
    for (Object node : nodes) {
      if (!(node instanceof Map<?, ?> raw)) {
        bound.add(node);
        continue;
      }
      Map<String, Object> boundNode = new LinkedHashMap<>();
      raw.forEach((key, value) -> boundNode.put(String.valueOf(key), value));
      for (NodeReference reference : NODE_REFERENCES) {
        if (boundNode.get(reference.field()) instanceof String value && !value.isBlank()) {
          Copy copy = copyOf(value, copies);
          switch (reference.kind()) {
            case DATASOURCE -> {
              boundNode.put(reference.field(), copy.logicalUrn() + LATEST);
              sourceIds.add(copy.shellId());
            }
            case DATASINK -> {
              boundNode.put(reference.field(), copy.versionedUrn());
              sinkIds.add(copy.shellId());
            }
            default -> boundNode.put(reference.field(), copy.versionedUrn());
          }
        }
      }
      bound.add(boundNode);
    }
    return bound;
  }

  /** Creates the style in the Dataset. A layer references the style by the id of the copy. */
  private InstalledArtifact installStyle(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    StyleInputDTO input = styleInput(prepared.member(), prepared.name());
    input.setDataSetId(dataSetId);
    Style style = styleService.create(input);

    register(prepared, new Copy(null, null, style.getId(), null), copies);
    return line(InstalledArtifactType.STYLE, prepared, style.getId(), null, null);
  }

  /** Returns the style that the document of a style member describes: its name and its SLD. */
  private static StyleInputDTO styleInput(PackageMemberInputDTO member, String name) {
    StyleInputDTO input = new StyleInputDTO();
    input.setName(name);
    input.setSldContent(optionalString(member.getContent(), SLD_CONTENT));
    return input;
  }

  /**
   * Creates the layer in the Dataset, with the ids of the Data sink copy and of the style copies.
   * The layer API has no {@code keywords}, so the install ignores them.
   */
  private InstalledArtifact installLayer(
      PreparedMember prepared, UUID dataSetId, Map<String, Copy> copies) {
    PackageMemberInputDTO member = prepared.member();
    LayerInputDTO input = layerInput(member, prepared.name());
    input.setDataSetId(dataSetId);
    input.setDataSinkId(copyOf(member, SINK_REF, copies).shellId());
    String defaultStyle = optionalString(member.getContent(), DEFAULT_STYLE_REF);
    if (defaultStyle != null) {
      input.setDefaultStyleId(copyOf(defaultStyle, copies).shellId());
    }
    List<String> alternativeStyles = stringList(member, ALTERNATIVE_STYLE_REFS);
    if (alternativeStyles != null) {
      input.setAlternativeStyleIds(
          alternativeStyles.stream()
              .map(reference -> copyOf(reference, copies).shellId())
              .toList());
    }
    Layer layer = layerService.create(input);

    return line(InstalledArtifactType.LAYER, prepared, layer.getId(), null, null);
  }

  /** Returns the layer that the document of a layer member describes, without the ids. */
  private static LayerInputDTO layerInput(PackageMemberInputDTO member, String name) {
    Map<String, Object> content = member.getContent();
    LayerInputDTO input = new LayerInputDTO();
    input.setLayerName(name);
    input.setTitle(optionalString(content, TITLE));
    input.setDescription(optionalString(content, DESCRIPTION));
    input.setAttribute(stringList(member, ATTRIBUTE));
    input.setGeometryColumnRef(optionalString(content, GEOMETRY_COLUMN_REF));
    input.setCqlFilter(optionalString(content, CQL_FILTER));
    input.setCrs(optionalString(content, CRS));
    input.setNativeBoundingBox(optionalMap(member, NATIVE_BOUNDING_BOX));
    input.setLatLonBoundingBox(optionalMap(member, LAT_LON_BOUNDING_BOX));
    return input;
  }

  /**
   * Returns the copy of the member that a document field names. The checks before the first change
   * make sure that the package has this member, and the order of the kinds makes sure that its copy
   * exists.
   */
  private Copy copyOf(PackageMemberInputDTO member, String field, Map<String, Copy> copies) {
    return copyOf(requireString(member, field), copies);
  }

  private Copy copyOf(String reference, Map<String, Copy> copies) {
    return copies.get(key(reference));
  }

  /** Keeps the copy of a member under each identity of the member. */
  private void register(PreparedMember prepared, Copy copy, Map<String, Copy> copies) {
    identitiesOf(prepared.member()).forEach(identity -> copies.put(identity, copy));
  }

  private static InstalledArtifact line(
      InstalledArtifactType type,
      PreparedMember prepared,
      UUID shellId,
      String urn,
      String versionedUrn) {
    InstalledArtifact artifact = new InstalledArtifact();
    artifact.setArtifactType(type);
    artifact.setName(prepared.name());
    artifact.setShellId(shellId);
    artifact.setUrn(urn);
    artifact.setVersionedUrn(versionedUrn);
    artifact.setOrigin(prepared.member().getUrn());
    return artifact;
  }

  private static List<PreparedMember> ofKind(List<PreparedMember> members, PackageMemberKind kind) {
    return members.stream().filter(member -> member.kind() == kind).toList();
  }

  /**
   * Returns a copy of a document without the keys. For a Data structure, the root {@code $id} is
   * the identity of the member in the package, and the record keeps it as the origin. The platform
   * refuses a root {@code $id} that names another Data structure. The backend replaces the {@code
   * $id} of each {@code $defs} member itself, so the other keys stay as they are.
   */
  private static Map<String, Object> withoutKeys(Map<String, Object> content, String... keys) {
    Map<String, Object> copy = new LinkedHashMap<>(content);
    for (String key : keys) {
      copy.remove(key);
    }
    return copy;
  }

  private static String optionalString(Map<String, Object> content, String field) {
    return content.get(field) instanceof String value && !value.isBlank() ? value : null;
  }

  /** Returns a document field that must be a list of strings, or null if the field is empty. */
  private static List<String> stringList(PackageMemberInputDTO member, String field) {
    Object value = member.getContent().get(field);
    if (value == null) {
      return null;
    }
    if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
      return list.stream().map(String.class::cast).toList();
    }
    throw new InvalidInputException(
        field,
        member.getUrn(),
        "Member '%s': '%s' must be a list of strings.".formatted(member.getUrn(), field));
  }

  /** Returns a document field that must be an object, or null if the field is empty. */
  private static Map<String, Object> optionalMap(PackageMemberInputDTO member, String field) {
    Object value = member.getContent().get(field);
    if (value == null) {
      return null;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
      return copy;
    }
    throw new InvalidInputException(
        field,
        member.getUrn(),
        "Member '%s': '%s' must be an object.".formatted(member.getUrn(), field));
  }

  /**
   * Returns an identity of the package without a version. A reference can name a version, but the
   * copy gets a version of this instance.
   */
  private String key(String identity) {
    return modelRegistryGateway.logicalUrn(identity);
  }
}

package de.civitascore.portal.service;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.input.MappingInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.util.InvalidInputException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.databind.ObjectMapper;

/**
 * A member of a package that passed the checks of {@link PackagePreparer}, with the name and the
 * description that its copy gets. Builds the input of the platform service that creates the copy,
 * and binds each reference in the document of the member to the copy of the member that it names.
 * Only a Data source member has a connector.
 */
record PreparedMember(
    PackageMemberInputDTO member,
    String name,
    String description,
    ConnectorConfiguration connector) {

  static final String ID = "$id";
  static final String SCHEMA = "$schema";
  static final String STAMPED_ID = "id";
  static final String TITLE = "title";
  static final String DESCRIPTION = "description";
  static final String NAME = "name";
  static final String ELEMENT = "element";
  static final String SLUG = "slug";
  static final String STANDARD = "standard";
  static final String VERSION = "version";
  static final String CONNECTION_TYPE = "connectionType";
  static final String SOURCE = "source";
  static final String TARGET = "target";
  static final String NODES = "nodes";
  static final String SLD_CONTENT = "sldContent";
  static final String LAYER_NAME = "layerName";
  static final String SINK_REF = "sinkRef";
  static final String DEFAULT_STYLE_REF = "defaultStyleRef";
  static final String ALTERNATIVE_STYLE_REFS = "alternativeStyleRefs";
  static final String ATTRIBUTE = "attribute";
  static final String GEOMETRY_COLUMN_REF = "geometryColumnRef";
  static final String CQL_FILTER = "cqlFilter";
  static final String CRS = "crs";
  static final String NATIVE_BOUNDING_BOX = "nativeBoundingBox";
  static final String LAT_LON_BOUNDING_BOX = "latLonBoundingBox";

  /**
   * The version token of a reference to the newest version. A Pipeline references its Data sources
   * in this form, as the editor does, and its Mappings and Data sinks by a fixed version.
   */
  private static final String LATEST = ":latest";

  /**
   * The kinds whose name on the platform comes from the document. GeoServer knows a style and a
   * layer by this name.
   */
  static final Set<PackageMemberKind> NAMED_BY_DOCUMENT =
      EnumSet.of(PackageMemberKind.STYLE, PackageMemberKind.LAYER);

  /** A field of a Pipeline node that references another member, and the kind of that member. */
  record NodeReference(String field, PackageMemberKind kind) {}

  /** The fields of a Pipeline node that reference other members. No other field changes. */
  static final List<NodeReference> NODE_REFERENCES =
      List.of(
          new NodeReference("sourceRef", PackageMemberKind.DATASOURCE),
          new NodeReference("lookupSourceRef", PackageMemberKind.DATASOURCE),
          new NodeReference("mappingRef", PackageMemberKind.MAPPING),
          new NodeReference(SINK_REF, PackageMemberKind.DATASINK));

  /**
   * What a member became on this instance. The install keeps the copy in each form that a reference
   * of a later member needs: a Data source references its Data structure by the id of the version,
   * a Mapping and a Data sink by the versioned URN, a Pipeline its Data sources by the logical URN
   * and the id, and a layer its Data sink and its styles by the id.
   */
  record Copy(String logicalUrn, String versionedUrn, UUID shellId, UUID versionId) {}

  PackageMemberKind kind() {
    return member.getKind();
  }

  DataStructureInputDTO dataStructureInput() {
    DataStructureInputDTO input = new DataStructureInputDTO();
    input.setName(name);
    input.setDescription(description);
    return input;
  }

  /**
   * Returns the input for the first version of a Data structure copy, without the root {@code $id}.
   * The root {@code $id} is the identity of the member in the package, and the record keeps it as
   * the origin. The platform refuses a root {@code $id} that names another Data structure. The
   * backend replaces the {@code $id} of each {@code $defs} member itself, so the other keys stay as
   * they are.
   */
  DataStructureVersionInputDTO dataStructureVersionInput(UUID dataStructureId) {
    DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
    input.setDataStructureId(dataStructureId);
    input.setDescription(description);
    input.setModelName(name);
    input.setModel(withoutKeys(member.getContent(), ID));
    return input;
  }

  /**
   * Returns the input for a Data source copy with the connector of its document. The Data source
   * reads the version of its Data structure copy, and only the Data pool of the request can use it.
   */
  DataSourceInputDTO dataSourceInput(UUID datapoolId, Function<String, Copy> copies) {
    DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
    scope.setType(DatapoolScopeType.SPECIFIC);
    scope.setDatapoolIds(List.of(datapoolId));

    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName(name);
    input.setDescription(description);
    input.setConnector(connector);
    input.setDataStructureVersionId(copyOf(member, ELEMENT, copies).versionId());
    input.setDatapoolScope(scope);
    return input;
  }

  /**
   * Returns the Dataset of the package in the Data pool of the request. A package cannot make its
   * Dataset open data. This is the decision of the operator, so the Dataset keeps the default of
   * the platform.
   */
  DataSetInputDTO dataSetInput(UUID datapoolId, List<PreparedMember> apis) {
    return dataSetInput(name, description, datapoolId, apis);
  }

  static DataSetInputDTO dataSetInput(
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
  static NamedApiInputDTO namedApiInput(PackageMemberInputDTO member, String name) {
    Map<String, Object> content = member.getContent();
    NamedApiInputDTO api = new NamedApiInputDTO();
    api.setName(name);
    api.setSlug(optionalString(content, SLUG));
    api.setStandard(apiStandard(member));
    api.setVersion(optionalString(content, VERSION));
    api.setDescription(optionalString(content, DESCRIPTION));
    return api;
  }

  String slug() {
    return requireString(member, SLUG);
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

  /**
   * Returns the input for a Mapping copy. Its {@code source} and {@code target} reference the
   * versioned URN of the Data structure copies, which is the form that the mapping compiler reads.
   * Model Forge gives the Mapping its identity, so the document keeps none.
   */
  MappingInputDTO mappingInput(Function<String, Copy> copies, ObjectMapper objectMapper) {
    Map<String, Object> document = withoutKeys(member.getContent(), SCHEMA, STAMPED_ID);
    for (String field : List.of(SOURCE, TARGET)) {
      String reference = optionalString(document, field);
      if (reference != null) {
        document.put(field, copies.apply(reference).versionedUrn());
      }
    }
    if (optionalString(document, TITLE) == null) {
      document.put(TITLE, name);
    }
    return objectMapper.convertValue(document, MappingInputDTO.class);
  }

  /**
   * Returns the input for a Data sink copy of the type that {@code connectionType} names. The other
   * fields of the document are the configuration of the sink, without the keys that the registry
   * owns. Its {@code element} references the versioned URN of the Data structure copy. A FROST sink
   * accepts no key but {@code port} and {@code element}.
   */
  DataSinkInputDTO dataSinkInput(UUID dataSetId, Function<String, Copy> copies) {
    Map<String, Object> configuration =
        withoutKeys(member.getContent(), CONNECTION_TYPE, SCHEMA, STAMPED_ID, TITLE, DESCRIPTION);
    String element = optionalString(configuration, ELEMENT);
    if (element != null) {
      configuration.put(ELEMENT, copies.apply(element).versionedUrn());
    }

    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setDataSetId(dataSetId);
    input.setDataSinkType(dataSinkType(member));
    input.setConfiguration(configuration);
    return input;
  }

  /** Returns the type of a Data sink from the {@code connectionType} of its document. */
  static DataSinkType dataSinkType(PackageMemberInputDTO member) {
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
   * Returns the input for a Pipeline copy with the model of its document. Only the references in
   * its nodes change, because Model Forge refuses a node with a key outside the schema. The
   * Pipeline has no styles: the styles of the editor hold the ids of the instance that wrote them.
   * Each node keeps its {@code x-ui-position}.
   */
  PipelineInputDTO pipelineInput(UUID dataSetId, Function<String, Copy> copies) {
    Map<String, Object> model =
        withoutKeys(member.getContent(), SCHEMA, STAMPED_ID, ModelRegistryGateway.X_UI_STYLES);
    Set<UUID> sourceIds = new LinkedHashSet<>();
    Set<UUID> sinkIds = new LinkedHashSet<>();
    model.put(NODES, withBoundReferences(requireNodes(member), copies, sourceIds, sinkIds));

    PipelineInputDTO input = new PipelineInputDTO();
    input.setDataSetId(dataSetId);
    input.setName(name);
    input.setDescription(description);
    input.setModel(model);
    input.setDataSourceIds(sourceIds);
    input.setDataSinkIds(sinkIds);
    return input;
  }

  /**
   * Returns a copy of the nodes with each reference bound to its copy, and collects the ids of the
   * Data sources and Data sinks that the nodes reference. A Data source reference names the newest
   * version, a Mapping and a Data sink reference a fixed version.
   */
  private static List<Object> withBoundReferences(
      List<?> nodes, Function<String, Copy> copies, Set<UUID> sourceIds, Set<UUID> sinkIds) {
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
          Copy copy = copies.apply(value);
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

  StyleInputDTO styleInput(UUID dataSetId) {
    StyleInputDTO input = styleInput(member, name);
    input.setDataSetId(dataSetId);
    return input;
  }

  /** Returns the style that the document of a style member describes: its name and its SLD. */
  static StyleInputDTO styleInput(PackageMemberInputDTO member, String name) {
    StyleInputDTO input = new StyleInputDTO();
    input.setName(name);
    input.setSldContent(optionalString(member.getContent(), SLD_CONTENT));
    return input;
  }

  /**
   * Returns the input for a layer copy in the Dataset, with the ids of the Data sink copy and of
   * the style copies. The layer API has no {@code keywords}, so the install ignores them.
   */
  LayerInputDTO layerInput(UUID dataSetId, Function<String, Copy> copies) {
    LayerInputDTO input = layerInput(member, name);
    input.setDataSetId(dataSetId);
    input.setDataSinkId(copyOf(member, SINK_REF, copies).shellId());
    String defaultStyle = optionalString(member.getContent(), DEFAULT_STYLE_REF);
    if (defaultStyle != null) {
      input.setDefaultStyleId(copies.apply(defaultStyle).shellId());
    }
    List<String> alternativeStyles = stringList(member, ALTERNATIVE_STYLE_REFS);
    if (alternativeStyles != null) {
      input.setAlternativeStyleIds(
          alternativeStyles.stream().map(reference -> copies.apply(reference).shellId()).toList());
    }
    return input;
  }

  /** Returns the layer that the document of a layer member describes, without the ids. */
  static LayerInputDTO layerInput(PackageMemberInputDTO member, String name) {
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
  private static Copy copyOf(
      PackageMemberInputDTO member, String field, Function<String, Copy> copies) {
    return copies.apply(requireString(member, field));
  }

  /** Returns the nodes of a Pipeline document. */
  static List<?> requireNodes(PackageMemberInputDTO member) {
    if (member.getContent().get(NODES) instanceof List<?> nodes) {
      return nodes;
    }
    throw new InvalidInputException(
        NODES, member.getUrn(), "Member '%s' needs '%s'.".formatted(member.getUrn(), NODES));
  }

  /** Returns the value of a document field that must be a string with text. */
  static String requireString(PackageMemberInputDTO member, String field) {
    if (member.getContent().get(field) instanceof String value && !value.isBlank()) {
      return value;
    }
    throw new InvalidInputException(
        field, member.getUrn(), "Member '%s' needs '%s'.".formatted(member.getUrn(), field));
  }

  static String optionalString(Map<String, Object> content, String field) {
    return content.get(field) instanceof String value && !value.isBlank() ? value : null;
  }

  /** Returns a document field that must be a list of strings, or null if the field is empty. */
  static List<String> stringList(PackageMemberInputDTO member, String field) {
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

  private static Map<String, Object> withoutKeys(Map<String, Object> content, String... keys) {
    Map<String, Object> copy = new LinkedHashMap<>(content);
    for (String key : keys) {
      copy.remove(key);
    }
    return copy;
  }
}

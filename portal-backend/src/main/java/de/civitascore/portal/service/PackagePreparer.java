package de.civitascore.portal.service;

import static de.civitascore.portal.service.PreparedMember.ALTERNATIVE_STYLE_REFS;
import static de.civitascore.portal.service.PreparedMember.DEFAULT_STYLE_REF;
import static de.civitascore.portal.service.PreparedMember.DESCRIPTION;
import static de.civitascore.portal.service.PreparedMember.ELEMENT;
import static de.civitascore.portal.service.PreparedMember.ID;
import static de.civitascore.portal.service.PreparedMember.LAYER_NAME;
import static de.civitascore.portal.service.PreparedMember.NAME;
import static de.civitascore.portal.service.PreparedMember.NAMED_BY_DOCUMENT;
import static de.civitascore.portal.service.PreparedMember.NODES;
import static de.civitascore.portal.service.PreparedMember.NODE_REFERENCES;
import static de.civitascore.portal.service.PreparedMember.SINK_REF;
import static de.civitascore.portal.service.PreparedMember.SOURCE;
import static de.civitascore.portal.service.PreparedMember.TARGET;
import static de.civitascore.portal.service.PreparedMember.TITLE;
import static de.civitascore.portal.service.PreparedMember.dataSetInput;
import static de.civitascore.portal.service.PreparedMember.dataSinkType;
import static de.civitascore.portal.service.PreparedMember.layerInput;
import static de.civitascore.portal.service.PreparedMember.namedApiInput;
import static de.civitascore.portal.service.PreparedMember.optionalString;
import static de.civitascore.portal.service.PreparedMember.requireNodes;
import static de.civitascore.portal.service.PreparedMember.requireString;
import static de.civitascore.portal.service.PreparedMember.stringList;
import static de.civitascore.portal.service.PreparedMember.styleInput;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import de.civitascore.portal.model.entity.UseCaseArtifact;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.service.PreparedMember.NodeReference;
import de.civitascore.portal.service.connector.ConnectorValidator;
import de.civitascore.portal.util.InvalidInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks a package before an install changes anything, and returns its members as {@link
 * PreparedMember} instances in the order of the install.
 *
 * <p>Model Forge keeps its dependency graph in memory, and a rollback does not restore the graph.
 * For this reason, the checks run before the first change to the database or the registry. The
 * install creates the Inputs of the Dataset, the named APIs, the styles and the layers itself, so
 * no controller validates them; this component applies the same constraints with {@link Validator}
 * and {@link ConnectorValidator}.
 */
@Component
@RequiredArgsConstructor
public class PackagePreparer {

  static final String DATAPOOL_ID = "datapoolId";
  private static final String URN = "urn";
  private static final String DATA_SET_REF = "dataSetRef";

  /** The kinds whose copies belong to a Dataset. Their members name it in {@code dataSetRef}. */
  private static final Set<PackageMemberKind> PART_OF_DATASET =
      EnumSet.of(
          PackageMemberKind.API,
          PackageMemberKind.MAPPING,
          PackageMemberKind.DATASINK,
          PackageMemberKind.PIPELINE,
          PackageMemberKind.STYLE,
          PackageMemberKind.LAYER);

  private final DataPoolRepository dataPoolRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final ConnectorValidator connectorValidator;
  private final Validator validator;

  /**
   * Checks the package before the install changes something, and returns its members in the order
   * of the install. The kinds come in the order of {@link PackageMemberKind}. The members of one
   * kind keep the order of the request.
   */
  List<PreparedMember> prepare(PackageManifestInputDTO manifest, UUID datapoolId) {
    List<PackageMemberInputDTO> members =
        manifest.getMembers().stream()
            .sorted(Comparator.comparing(PackageMemberInputDTO::getKind))
            .toList();
    Map<String, PackageMemberKind> kinds = kindsByIdentity(members);
    requireDataSetRefs(members, kinds);
    requireDatapoolWhereNecessary(members, datapoolId, manifest.getId());
    List<PreparedMember> prepared =
        members.stream().map(member -> prepare(member, kinds, datapoolId)).toList();
    // After the checks of each member, so that a reference to a member of the wrong kind gets the
    // message about the kind.
    requireSameDataSet(members);
    return prepared;
  }

  /**
   * Whether the package has a member that exists only in a Data pool: a Data source or a Dataset.
   */
  static boolean needsDatapool(List<PackageMemberInputDTO> members) {
    return members.stream()
        .anyMatch(
            member ->
                member.getKind() == PackageMemberKind.DATASOURCE
                    || member.getKind() == PackageMemberKind.DATASET);
  }

  /**
   * Returns the identities of a member in the package, without a version. A Data structure also has
   * the root {@code $id} of its document as an identity, because the other documents of a package
   * can reference the Data structure by it.
   */
  Set<String> identitiesOf(PackageMemberInputDTO member) {
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
   * Returns an identity of the package without a version. A reference can name a version, but the
   * copy gets a version of this instance.
   */
  String key(String identity) {
    return modelRegistryGateway.logicalUrn(identity);
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
   * A member whose copy belongs to a Dataset names that Dataset in {@code dataSetRef}, also when
   * the package has only one Dataset. The other members belong to no Dataset and name none.
   */
  private void requireDataSetRefs(
      List<PackageMemberInputDTO> members, Map<String, PackageMemberKind> kinds) {
    for (PackageMemberInputDTO member : members) {
      String dataSetRef = member.getDataSetRef();
      boolean namesDataSet = dataSetRef != null && !dataSetRef.isBlank();
      if (PART_OF_DATASET.contains(member.getKind())) {
        if (!namesDataSet) {
          throw new InvalidInputException(
              DATA_SET_REF,
              member.getUrn(),
              "Member '%s' belongs to a Dataset, so it must name the Dataset in '%s'."
                  .formatted(member.getUrn(), DATA_SET_REF));
        }
        checkReference(member, DATA_SET_REF, dataSetRef, PackageMemberKind.DATASET, kinds);
      } else if (namesDataSet) {
        throw new InvalidInputException(
            DATA_SET_REF,
            member.getUrn(),
            ("Member '%s' is of the kind %s, which belongs to no Dataset, so it must not name one"
                    + " in '%s'.")
                .formatted(member.getUrn(), kindName(member.getKind()), DATA_SET_REF));
      }
    }
  }

  /**
   * A Pipeline and a layer use only the Mappings, Data sinks and styles of their own Dataset. Data
   * sources serve all Datasets of the package, so a reference to a Data source is free.
   */
  private void requireSameDataSet(List<PackageMemberInputDTO> members) {
    Map<String, String> dataSetOf = new HashMap<>();
    for (PackageMemberInputDTO member : members) {
      if (PART_OF_DATASET.contains(member.getKind())) {
        dataSetOf.put(key(member.getUrn()), key(member.getDataSetRef()));
      }
    }
    for (PackageMemberInputDTO member : members) {
      if (member.getKind() == PackageMemberKind.PIPELINE) {
        List<?> nodes = requireNodes(member);
        for (int index = 0; index < nodes.size(); index++) {
          if (!(nodes.get(index) instanceof Map<?, ?> node)) {
            continue;
          }
          for (NodeReference reference : NODE_REFERENCES) {
            if (reference.kind() != PackageMemberKind.DATASOURCE
                && node.get(reference.field()) instanceof String value
                && !value.isBlank()) {
              String field = "%s[%d].%s".formatted(NODES, index, reference.field());
              requireSameDataSet(member, field, value, dataSetOf);
            }
          }
        }
      } else if (member.getKind() == PackageMemberKind.LAYER) {
        requireSameDataSet(member, SINK_REF, requireString(member, SINK_REF), dataSetOf);
        String defaultStyle = optionalString(member.getContent(), DEFAULT_STYLE_REF);
        if (defaultStyle != null) {
          requireSameDataSet(member, DEFAULT_STYLE_REF, defaultStyle, dataSetOf);
        }
        List<String> alternativeStyles = stringList(member, ALTERNATIVE_STYLE_REFS);
        if (alternativeStyles != null) {
          alternativeStyles.forEach(
              style -> requireSameDataSet(member, ALTERNATIVE_STYLE_REFS, style, dataSetOf));
        }
      }
    }
  }

  private void requireSameDataSet(
      PackageMemberInputDTO member, String field, String reference, Map<String, String> dataSetOf) {
    String other = dataSetOf.get(key(reference));
    if (other != null && !other.equals(key(member.getDataSetRef()))) {
      throw new InvalidInputException(
          field,
          member.getUrn(),
          "Member '%s' references '%s' in '%s', but that member belongs to another Dataset."
              .formatted(member.getUrn(), reference, field));
    }
  }

  /**
   * Data sources and Datasets exist only in a Data pool. A package cannot know the Data pools of
   * this instance, so the request names the Data pool.
   */
  private void requireDatapoolWhereNecessary(
      List<PackageMemberInputDTO> members, UUID datapoolId, String packageId) {
    if (!needsDatapool(members)) {
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
    if (name.length() > UseCaseArtifact.NAME_LENGTH) {
      throw new InvalidInputException(
          NAME,
          member.getUrn(),
          "The name of member '%s' must not exceed %d characters."
              .formatted(member.getUrn(), UseCaseArtifact.NAME_LENGTH));
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
}

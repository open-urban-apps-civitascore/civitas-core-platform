package de.civitascore.portal.modelregistry;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.ArtifactWriteResult;
import de.civitascore.modelforge.contract.CreateArtifactCommand;
import de.civitascore.modelforge.contract.DependencyClosureView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.SchemaViewQuery;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.portal.util.InvalidInputException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Anti-corruption layer around embedded Model Forge.
 *
 * <p>This is the <b>only</b> portal-backend package that depends on {@code
 * de.civitascore.modelforge.*}; Model Forge types never leak past it. It exposes schema validation,
 * storing/reading/deleting the model of a data structure version (stage 2/3) and the opaque JSON
 * payloads of pipelines, data sinks and data sources (stage 4).
 *
 * <p><b>Styles convention:</b> UI styles (canvas positions, React Flow layout, …) are model
 * content. They ride inside the stored document under the top-level keyword {@value #X_UI_STYLES};
 * Model Forge's schema fidelity preserves unknown {@code x-*} keywords verbatim. The gateway merges
 * the host's separate {@code styles} map into the document on write and splits it off again on
 * read, so host DTOs keep their stable separate {@code model}/{@code styles} fields. The saga path
 * uses {@link #fetchInlinedModel(String)}, which strips the keyword so the event payload stays a
 * pure schema document.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRegistryGateway {

  /** Top-level document keyword carrying the host's UI styles inside the stored content. */
  public static final String X_UI_STYLES = "x-ui-styles";

  private final ModelForge modelForge;

  private final ObjectMapper objectMapper;

  /**
   * The pin a caller stores on the shell to reference a stored content version. Model Forge is the
   * sole version authority, so all three fields come from what the registry actually assigned.
   *
   * @param logicalUrn stable, version-free CORE URN of the artifact (reused across versions)
   * @param versionedUrn the concrete versioned CORE URN of this stored version
   * @param version the SemVer version segment of {@code versionedUrn}
   */
  public record ModelPin(String logicalUrn, String versionedUrn, String version) {}

  /**
   * A document read back from the registry, split into the host's separate views: {@code content}
   * is the stored document without the {@value #X_UI_STYLES} keyword, {@code styles} is that
   * keyword's value (or {@code null} when the document carries none).
   */
  public record RegistryDocument(Map<String, Object> content, Map<String, Object> styles) {}

  /**
   * The artifacts a flow participates in, and the subset of them the registry does not hold.
   *
   * @param artifacts every artifact the walk reached, entry artifact excluded
   * @param unresolved those of {@code artifacts} the registry no longer holds
   */
  public record ArtifactClosure(Set<String> artifacts, Set<String> unresolved) {}

  /**
   * Stores a model (with its styles merged in as {@value #X_UI_STYLES}) in the registry and returns
   * the version pin. Model Forge assigns the version and the (uuid-bearing) URN.
   *
   * <ul>
   *   <li>First store ({@code existingLogicalUrn} empty): the model is imported as a new Element —
   *       Model Forge mints the logical URN from {@code name} and decomposes any {@code $defs}.
   *   <li>Follow-up version ({@code existingLogicalUrn} present): a new version of that same
   *       artifact is stored, bumping the SemVer version.
   * </ul>
   *
   * @param existingLogicalUrn the stored logical URN of a previous version, empty on first store
   * @param name the data structure's display name, used to derive a readable URN on first store
   * @param model the JSON Schema document to store (must be non-empty)
   * @param styles the host's UI styles, merged into the document as {@value #X_UI_STYLES}; null or
   *     empty means the stored document carries no styles
   * @param bump the change class for a follow-up version (ignored on first store)
   * @param bumpFromVersion the version the caller is revising, which {@code bump} counts from; null
   *     counts from the newest version. A data structure's versions are independent lines rather
   *     than one history, so a revision must name its own version or the bump would jump to the
   *     newest line.
   * @return the pin (logical URN, versioned URN, version) the caller persists on the shell
   */
  public ModelPin storeModel(
      Optional<String> existingLogicalUrn,
      String name,
      Map<String, Object> model,
      Map<String, Object> styles,
      VersionBump bump,
      String bumpFromVersion) {
    JsonNode content = mergeStyles(model, styles);
    ArtifactId root;
    if (isDataStructureModel(content, existingLogicalUrn)) {
      // A datastructure model is folded into a single DataStructure artifact by importSchema (which
      // also splits + versions its member Elements), whether new OR re-versioned. It must NOT go
      // through saveArtifact(ELEMENT) — that would store an Element under a :datastructure: URN.
      // importSchema versions the existing logical URN idempotently, applying the change class to
      // the grouping and to every member whose content changed.
      root = importRoot(withTitle(content, name), bump, bumpFromVersion);
    } else if (existingLogicalUrn.isPresent()) {
      root =
          saveVersion(
              existingLogicalUrn.get(), ArtifactKind.ELEMENT, content, bump, null, bumpFromVersion);
    } else {
      root = importRoot(withTitle(content, name), bump, bumpFromVersion);
    }
    return toPin(root);
  }

  /**
   * Whether the model being stored is a DataStructure (as opposed to a plain Element). True when
   * the content's {@code $id} — or the existing logical URN it re-versions — is a {@code
   * :datastructure:} URN. The portal stamps a datastructure URN on a UML model's {@code $id};
   * model-forge then folds the wrapper into one DataStructure artifact holding its shape + member
   * Elements.
   */
  private static boolean isDataStructureModel(
      JsonNode content, Optional<String> existingLogicalUrn) {
    String id = content.path("$id").asText(null);
    if (UrnParser.isUrn(id) && "datastructure".equals(UrnParser.artifactTypeFromUrn(id))) {
      return true;
    }
    return existingLogicalUrn
        .filter(UrnParser::isUrn)
        .map(u -> "datastructure".equals(UrnParser.artifactTypeFromUrn(u)))
        .orElse(false);
  }

  /**
   * Reads a model back through the registry's bundled schema view (dependencies embedded under
   * {@code $defs} — semantically the document the host used to persist inline). Styles are split
   * off the {@value #X_UI_STYLES} keyword.
   *
   * @param urn logical (current version) or versioned (exact version) CORE URN
   * @return the split document, or empty when the artifact does not exist
   */
  public Optional<RegistryDocument> fetchModel(String urn) {
    return modelForge
        .getBundledView(new SchemaViewQuery(new ArtifactId(urn)))
        .map(ArtifactView::content)
        .map(this::split);
  }

  /**
   * Reads a model as a fully inlined schema document (every CORE-URN {@code $ref} recursively
   * inlined) with the {@value #X_UI_STYLES} keyword stripped — the shape the dataset saga ships to
   * the config-adapter ({@code datasinks[].dataStructure}). Unknown {@code x-*} keywords such as
   * {@code x-core-primaryKey} survive verbatim.
   *
   * @param urn logical (current version) or versioned (exact version) CORE URN
   * @return the inlined schema document, or empty when the artifact does not exist
   */
  public Optional<Map<String, Object>> fetchInlinedModel(String urn) {
    return modelForge
        .getInlinedView(new SchemaViewQuery(new ArtifactId(urn)))
        .map(ArtifactView::content)
        .map(this::split)
        .map(RegistryDocument::content);
  }

  /**
   * Deletes the artifact (all versions) behind a logical URN. No-op semantics are Model Forge's.
   */
  public void deleteModel(String logicalUrn) {
    modelForge.deleteArtifact(new ArtifactId(groupingUrnOf(logicalUrn)), true, false);
  }

  /**
   * The DataStructure grouping a model is addressed by. A delete and the in-use answer both
   * describe the grouping with its member Elements; asking an Element alone would report its own
   * grouping as a reference.
   */
  private static String groupingUrnOf(String urn) {
    return urn.replace(":element:", ":datastructure:");
  }

  /**
   * Whether anything outside the model still references it — a DataSource or DataSink carrying one
   * of its Elements, a Mapping naming one as an endpoint, another model associating with one, or
   * membership of a second DataSet. Gates the delete and the unrelease of a DataStructure alike.
   *
   * @param modelUrn versioned or logical CORE URN of the model; null/blank yields {@code false}
   * @return true while at least one reference stands in the way of deleting the model
   */
  public boolean isReferenced(String modelUrn) {
    return !referencesTo(modelUrn).isEmpty();
  }

  /**
   * What stands in the way of deleting the model, named so a caller can go and remove it. Empty
   * when nothing does.
   *
   * @param modelUrn versioned or logical CORE URN of the model; null/blank yields an empty list
   */
  public List<String> referencesTo(String modelUrn) {
    if (modelUrn == null || modelUrn.isBlank()) {
      return List.of();
    }
    return modelForge.deletionBlockers(new ArtifactId(groupingUrnOf(modelUrn)));
  }

  /**
   * The logical (version-free) form of a CORE URN. Exposed so host services can normalize a URN
   * without importing Model Forge's {@code UrnParser} directly — Model Forge stays behind this
   * anti-corruption layer (enforced by {@code ModelForgeBoundaryTest}).
   *
   * @param urn a versioned, logical or {@code :latest} CORE URN
   * @return the logical URN (the first 8 segments, no trailing version)
   */
  public String logicalUrn(String urn) {
    return UrnParser.logicalUrn(urn);
  }

  /**
   * The versioned CORE URNs of a given artifact type that {@code urn} depends on, read from Model
   * Forge's dependency graph. This is the envelope-level way for host orchestration to learn, e.g.,
   * which Mappings a pipeline references — <b>without ever parsing the pipeline's content</b> (the
   * dependency graph is part of the envelope; see the portal-backend integration contract). Empty
   * when {@code urn} is null/blank.
   *
   * @param urn the depending artifact's logical or versioned CORE URN
   * @param artifactType the CORE artifact-type segment to keep (e.g. {@code "mapping"})
   */
  public List<String> dependencyUrnsOfType(String urn, String artifactType) {
    if (urn == null || urn.isBlank()) {
      return List.of();
    }
    return modelForge.dependencies(new DependencyQuery(new ArtifactId(urn))).nodes().stream()
        .map(node -> node.artifactId().value())
        .filter(u -> artifactType.equals(UrnParser.artifactTypeFromUrn(u)))
        .distinct()
        .toList();
  }

  /**
   * A flow's participating closure: every artifact reachable from {@code urn} within {@code
   * maxDepth} reference hops, and which of them the registry no longer holds.
   *
   * <p>Unlike {@link #dependencyUrnsOfType}, which reports direct edges of one type, this walks the
   * graph transitively. The walk is level-bounded and guards a cyclic graph with a visited set, so
   * a mutually referencing pair terminates rather than recursing. The entry artifact is not a
   * member of its own closure.
   *
   * <p>An unresolved reference — one whose target the registry no longer holds — is deliberately
   * kept as an edge, so it is reached by the walk and reported in {@code unresolved} rather than
   * silently missing. The registry answers that for the whole closure at once.
   *
   * @param urn the flow's entry artifact, logical or versioned; null or blank yields an empty
   *     closure
   * @param maxDepth how many hops to walk; zero or negative yields an empty closure
   */
  public ArtifactClosure closure(String urn, int maxDepth) {
    if (urn == null || urn.isBlank() || maxDepth <= 0) {
      return new ArtifactClosure(Set.of(), Set.of());
    }
    DependencyClosureView view =
        modelForge.closure(new DependencyQuery(new ArtifactId(urn), maxDepth));
    return new ArtifactClosure(urns(view.closure()), urns(view.unresolved()));
  }

  private static Set<String> urns(List<ArtifactId> ids) {
    return ids.stream().map(ArtifactId::value).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Stores an opaque JSON payload (pipeline definition, sink/source configuration) as a registry
   * artifact of the given kind and returns the version pin (stage 4).
   *
   * <ul>
   *   <li>First store ({@code existingLogicalUrn} empty): {@code createArtifact} — Model Forge
   *       mints the URN from {@code name} and stamps it into the document's {@code id}.
   *   <li>Follow-up version ({@code existingLogicalUrn} present): {@code saveArtifact} with a MINOR
   *       bump (payload versions are not user-classified).
   * </ul>
   *
   * @param kind the host payload kind, mapped onto the Model Forge artifact kind
   * @param existingLogicalUrn the stored logical URN of a previous version, empty on first store
   * @param name display name the URN is derived from on first store (must not be blank)
   * @param payload the payload document; null is stored as an empty object when styles exist
   * @param styles optional UI styles, merged into the document as {@value #X_UI_STYLES}
   * @return the pin (logical URN, versioned URN, version) the caller persists on the shell
   */
  public ModelPin storePayload(
      PayloadKind kind,
      Optional<String> existingLogicalUrn,
      String name,
      Map<String, Object> payload,
      Map<String, Object> styles) {
    return storePayload(kind, existingLogicalUrn, name, payload, styles, null);
  }

  /**
   * As {@link #storePayload(PayloadKind, Optional, String, Map, Map)}, but additionally links the
   * stored artifact into a DataSet: {@code dataSet} is the CORE URN of the DataSet manifest to add
   * it to. Model Forge maintains the manifest (for a Pipeline it links the whole reference closure
   * too). {@code null} stores without DataSet membership.
   */
  public ModelPin storePayload(
      PayloadKind kind,
      Optional<String> existingLogicalUrn,
      String name,
      Map<String, Object> payload,
      Map<String, Object> styles,
      String dataSet) {
    JsonNode content = mergeStyles(payload, styles);
    ArtifactKind artifactKind = toArtifactKind(kind);
    ArtifactId root =
        existingLogicalUrn
            .map(
                logicalUrn ->
                    saveVersion(
                        logicalUrn, artifactKind, content, VersionBump.MINOR, dataSet, null))
            .orElseGet(
                () -> {
                  ArtifactWriteResult result =
                      modelForge.createArtifact(
                          new CreateArtifactCommand(artifactKind, name, content, dataSet));
                  logDependencies(result.artifactId(), result.dependencies());
                  return result.artifactId();
                });
    return toPin(root);
  }

  /**
   * Creates an (initially empty) DataSet manifest artifact and returns its pin. Members are linked
   * in later by storing them with this DataSet's URN (see the {@code dataSet} parameter of {@link
   * #storePayload}); Model Forge maintains the manifest and validates it against {@code
   * dataset.schema.json}.
   */
  public ModelPin createDataSetManifest(String name) {
    String safeName = name == null || name.isBlank() ? "dataset" : name;
    JsonNode content = mergeStyles(Map.of("title", safeName), null);
    ArtifactWriteResult result =
        modelForge.createArtifact(
            new CreateArtifactCommand(ArtifactKind.DATA_SET, safeName, content));
    return toPin(result.artifactId());
  }

  /** Explicitly adds a member artifact to a DataSet's manifest ({@code dataset-ref} membership). */
  public void linkToDataSet(String dataSetUrn, String memberUrn) {
    try {
      modelForge.linkToDataSet(new ArtifactId(dataSetUrn), new ArtifactId(memberUrn));
    } catch (IllegalArgumentException e) {
      throw rejectedMembership(e);
    }
  }

  /** Explicitly removes an artifact from a DataSet's manifest. */
  public void unlinkFromDataSet(String dataSetUrn, String memberUrn) {
    try {
      modelForge.unlinkFromDataSet(new ArtifactId(dataSetUrn), new ArtifactId(memberUrn));
    } catch (IllegalArgumentException e) {
      throw rejectedMembership(e);
    }
  }

  /**
   * Deletes an artifact under the DataSet-aware deletion policy, taking the artifacts it owns with
   * it when {@code cascade} is set. A reference the registry still holds refuses the delete; there
   * is no way past that refusal here, because deleting past a reference leaves the artifact holding
   * it pointing at nothing and reports success.
   */
  public void deleteArtifact(String logicalUrn, boolean cascade) {
    if (logicalUrn != null && !logicalUrn.isBlank()) {
      modelForge.deleteArtifact(new ArtifactId(logicalUrn), cascade, false);
    }
  }

  /**
   * Reads an opaque payload back as its authored content, split into content and {@value
   * #X_UI_STYLES} styles.
   *
   * @param urn logical (current version) or versioned (exact version) CORE URN
   * @return the split document, or empty when the artifact does not exist
   */
  public Optional<RegistryDocument> fetchPayload(String urn) {
    return modelForge
        .getArtifact(new ArtifactId(urn))
        .map(ArtifactView::content)
        .map(this::splitPayload);
  }

  /**
   * Whether the given content+styles equal the document already pinned by {@code versionedUrn} (the
   * registry's self-description stamps are ignored). Callers use this to skip the registry write on
   * updates that do not change the stored document — otherwise every host-side metadata update
   * (description, name, …) would mint a new artifact version and repin the shell.
   */
  public boolean isUnchanged(
      String versionedUrn, Map<String, Object> content, Map<String, Object> styles) {
    JsonNode merged = comparable(mergeStyles(content, styles));
    return modelForge
        .getArtifact(new ArtifactId(versionedUrn))
        .map(ArtifactView::content)
        .map(ModelRegistryGateway::comparable)
        .map(merged::equals)
        .orElse(false);
  }

  /**
   * Deletes the payload artifact (all versions) behind a logical URN. No-op semantics are Model
   * Forge's.
   */
  public void deletePayload(String logicalUrn) {
    modelForge.deleteArtifact(new ArtifactId(logicalUrn));
  }

  /**
   * Stores a document through the import path, which splits its members and builds the grouping
   * that references them. The root pin is returned, dependency edges are logged.
   *
   * @param bump the change class to apply; it reaches the grouping and every member whose content
   *     changed, and a member that is byte-identical still mints nothing
   */
  private ArtifactId importRoot(JsonNode content, VersionBump bump, String bumpFromVersion) {
    ImportResult result =
        modelForge.importSchema(
            new ImportSchemaCommand(content, toModelForgeBump(bump), null, false, bumpFromVersion));
    logDependencies(result.rootArtifactId(), result.dependencies());
    return result.rootArtifactId();
  }

  /** Stores a follow-up version of an existing artifact; dependency edges are logged. */
  private ArtifactId saveVersion(
      String logicalUrn,
      ArtifactKind kind,
      JsonNode content,
      VersionBump bump,
      String dataSet,
      String bumpFromVersion) {
    ArtifactWriteResult result =
        modelForge.saveArtifact(
            new SaveArtifactCommand(
                new ArtifactId(logicalUrn),
                kind,
                content,
                toModelForgeBump(bump),
                dataSet,
                bumpFromVersion));
    logDependencies(result.artifactId(), result.dependencies());
    return result.artifactId();
  }

  private static ModelPin toPin(ArtifactId root) {
    return new ModelPin(root.logicalUrn(), root.value(), root.version());
  }

  /**
   * Dependency lists returned by registry writes (rel name -> target URNs), logged at debug for
   * diagnosis. Deliberately not mirrored into host tables: Model Forge answers dependency questions
   * itself, and a copy here would be a second record of the same edges to keep in step.
   */
  private static void logDependencies(
      ArtifactId artifactId, Map<String, List<ArtifactId>> dependencies) {
    if (log.isDebugEnabled() && dependencies != null && !dependencies.isEmpty()) {
      log.debug(
          "Registry write {} produced dependency edges: {}",
          artifactId.value(),
          dependencies.entrySet().stream()
              .map(e -> e.getKey() + "=" + e.getValue().stream().map(ArtifactId::value).toList())
              .toList());
    }
  }

  /** Maps the host payload kind onto the Model Forge artifact kind. */
  private static ArtifactKind toArtifactKind(PayloadKind kind) {
    return switch (kind) {
      case PIPELINE -> ArtifactKind.PIPELINE;
      case DATA_SOURCE -> ArtifactKind.DATA_SOURCE;
      case DATA_SINK -> ArtifactKind.DATA_SINK;
      case MAPPING -> ArtifactKind.MAPPING;
      case DATA_SET -> ArtifactKind.DATA_SET;
    };
  }

  /**
   * Maps the host change class onto the Model Forge one. The host only ever asks for a major (a new
   * version) or a minor (an edit of one); a missing class defaults to the minor, the narrower of
   * the two.
   */
  private static de.civitascore.modelforge.contract.VersionBump toModelForgeBump(VersionBump bump) {
    return switch (bump == null ? VersionBump.MINOR : bump) {
      case MAJOR -> de.civitascore.modelforge.contract.VersionBump.MAJOR;
      case MINOR -> de.civitascore.modelforge.contract.VersionBump.MINOR;
    };
  }

  /**
   * Builds the stored document: the content map plus the styles merged in under {@value
   * #X_UI_STYLES}. A null content becomes an empty object; null/empty styles leave the keyword out.
   */
  private JsonNode mergeStyles(Map<String, Object> content, Map<String, Object> styles) {
    ObjectNode document = objectMapper.valueToTree(content == null ? Map.of() : content);
    document.remove(X_UI_STYLES); // the keyword is gateway-owned; never trust inbound copies
    if (styles != null && !styles.isEmpty()) {
      document.set(X_UI_STYLES, objectMapper.valueToTree(styles));
    }
    return document;
  }

  /** Splits a stored document into content and the {@value #X_UI_STYLES} styles map. */
  @SuppressWarnings("unchecked")
  private RegistryDocument split(JsonNode document) {
    Map<String, Object> content =
        new LinkedHashMap<>(objectMapper.convertValue(document, Map.class));
    Object styles = content.remove(X_UI_STYLES);
    return new RegistryDocument(content, styles instanceof Map<?, ?> map ? (Map) map : null);
  }

  /**
   * {@link #split(JsonNode)} variant for opaque payloads: the registry's self-description stamps
   * ({@code $schema}, {@code id}) are registry metadata, not authored content — they are stripped
   * so payloads round-trip verbatim through the host API. Content that is empty after stripping is
   * served as null (a payload that was stored for its styles only).
   */
  private RegistryDocument splitPayload(JsonNode document) {
    RegistryDocument doc = split(document);
    Map<String, Object> content = doc.content();
    content.remove("$schema");
    content.remove("id");
    return new RegistryDocument(content.isEmpty() ? null : content, doc.styles());
  }

  /** A copy of the document without the registry's identity/self-description stamps. */
  private static JsonNode comparable(JsonNode document) {
    ObjectNode copy = (ObjectNode) document.deepCopy();
    copy.remove("$schema");
    copy.remove("id");
    copy.remove("$id");
    return copy;
  }

  /**
   * Backfills the schema {@code title} from the data structure name so the minted URN is readable.
   */
  private static JsonNode withTitle(JsonNode content, String name) {
    if (content instanceof ObjectNode object
        && name != null
        && !name.isBlank()
        && !object.has("title")) {
      object.put("title", name);
    }
    return content;
  }

  /**
   * The registry rejects a URN that names no artifact it can hold membership for. That is a caller
   * mistake, so it is reported as invalid input rather than escaping as a registry-specific type.
   */
  private static InvalidInputException rejectedMembership(IllegalArgumentException cause) {
    return new InvalidInputException("DataSet", "member", cause.getMessage());
  }
}

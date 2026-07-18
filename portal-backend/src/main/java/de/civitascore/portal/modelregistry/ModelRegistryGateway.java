package de.civitascore.portal.modelregistry;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.ArtifactWriteResult;
import de.civitascore.modelforge.contract.CreateArtifactCommand;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.SchemaViewQuery;
import de.civitascore.modelforge.contract.ValidateSchemaCommand;
import de.civitascore.modelforge.contract.ValidationResult;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.urn.UrnParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
   * Validates a model as a JSON Schema via Model Forge.
   *
   * @param model the JSON Schema document; a null or empty model is treated as "nothing to
   *     validate"
   * @return the ERROR-level diagnostic messages, empty when the model is a valid JSON Schema
   */
  public List<String> validateSchema(Map<String, Object> model) {
    if (model == null || model.isEmpty()) {
      return List.of();
    }
    ValidationResult result =
        modelForge.validateSchema(new ValidateSchemaCommand(objectMapper.valueToTree(model)));
    return result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
        .map(
            diagnostic ->
                diagnostic.path() == null || diagnostic.path().isBlank()
                    ? diagnostic.message()
                    : diagnostic.message() + " (" + diagnostic.path() + ")")
        .toList();
  }

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
   * @param bump the requested version bump for a follow-up version (ignored on first store)
   * @return the pin (logical URN, versioned URN, version) the caller persists on the shell
   */
  public ModelPin storeModel(
      Optional<String> existingLogicalUrn,
      String name,
      Map<String, Object> model,
      Map<String, Object> styles,
      VersionBump bump) {
    JsonNode content = mergeStyles(model, styles);
    ArtifactId root;
    if (isDataStructureModel(content, existingLogicalUrn)) {
      // A datastructure model is folded into a single DataStructure artifact by importSchema (which
      // also splits + versions its member Elements), whether new OR re-versioned. It must NOT go
      // through saveArtifact(ELEMENT) — that would store an Element under a :datastructure: URN.
      // importSchema versions the existing logical URN idempotently; the requested bump is not yet
      // threaded through the datastructure import path, so a re-version lands as a registry PATCH.
      root = importRoot(withTitle(content, name));
    } else if (existingLogicalUrn.isPresent()) {
      root = saveVersion(existingLogicalUrn.get(), ArtifactKind.ELEMENT, content, bump);
    } else {
      root = importRoot(withTitle(content, name));
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
    modelForge.deleteArtifact(new ArtifactId(logicalUrn));
  }

  /**
   * Whether any DataSink references the given model (Element) URN. A sink records the reference by
   * carrying the URN in its configuration's top-level {@code element} field; Model Forge tracks
   * that as a {@code datasink-element} dependency edge on store. This is the registry-native
   * replacement for the former {@code data_sinks.configuration ->> 'dataStructureVersionId'} query
   * — the host in-use guard on {@code DataStructureVersion} asks Model Forge who references the
   * version's model.
   *
   * @param modelUrn versioned or logical CORE URN of the model; null/blank yields {@code false}
   * @return true if at least one DataSink artifact depends on the model
   */
  public boolean isReferencedBySink(String modelUrn) {
    if (modelUrn == null || modelUrn.isBlank()) {
      return false;
    }
    return modelForge.dependents(new DependencyQuery(new ArtifactId(modelUrn))).nodes().stream()
        .map(node -> node.artifactId().value())
        .anyMatch(ModelRegistryGateway::isDataSinkUrn);
  }

  /**
   * A CORE URN identifies a DataSink when its type segment (5th, colon-delimited) is "datasink".
   */
  private static boolean isDataSinkUrn(String urn) {
    String[] segments = urn.split(":");
    return segments.length > 4 && "datasink".equals(segments[4]);
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
                    saveVersion(logicalUrn, artifactKind, content, VersionBump.MINOR, dataSet))
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

  /**
   * Deletes a DataSet manifest by its logical URN. Its members are kept (a DataSet groups, it does
   * not own); Model Forge drops the manifest's outgoing {@code dataset-ref} edges so no member
   * dangles.
   */
  public void deleteDataSet(String logicalUrn) {
    if (logicalUrn != null && !logicalUrn.isBlank()) {
      modelForge.deleteArtifact(new ArtifactId(logicalUrn));
    }
  }

  /**
   * CORE URNs of artifacts of the given type ({@code mapping}/{@code pipeline}/{@code datasource}/
   * {@code datasink}/{@code datastructure}) that belong to no DataSet — the "orphans by type"
   * query.
   */
  public List<String> orphanUrns(String artifactType) {
    ArtifactKind kind = artifactKindForType(artifactType);
    if (kind == null) {
      return List.of();
    }
    return modelForge.orphans(kind).stream().map(summary -> summary.artifactId().value()).toList();
  }

  /** Explicitly adds a member artifact to a DataSet's manifest ({@code dataset-ref} membership). */
  public void linkToDataSet(String dataSetUrn, String memberUrn) {
    modelForge.linkToDataSet(new ArtifactId(dataSetUrn), new ArtifactId(memberUrn));
  }

  /** Explicitly removes an artifact from a DataSet's manifest. */
  public void unlinkFromDataSet(String dataSetUrn, String memberUrn) {
    modelForge.unlinkFromDataSet(new ArtifactId(dataSetUrn), new ArtifactId(memberUrn));
  }

  /**
   * Deletes an artifact under the DataSet-aware deletion policy, optionally cascading into members
   * and/or forcing past the blocks (admin repair — may dangle references). Non-force is the safe
   * path.
   */
  public void deleteArtifact(String logicalUrn, boolean cascade, boolean force) {
    if (logicalUrn != null && !logicalUrn.isBlank()) {
      modelForge.deleteArtifact(new ArtifactId(logicalUrn), cascade, force);
    }
  }

  private static ArtifactKind artifactKindForType(String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase(java.util.Locale.ROOT)) {
      case "mapping" -> ArtifactKind.MAPPING;
      case "pipeline" -> ArtifactKind.PIPELINE;
      case "datasource" -> ArtifactKind.DATA_SOURCE;
      case "datasink" -> ArtifactKind.DATA_SINK;
      case "datastructure" -> ArtifactKind.DATA_STRUCTURE;
      case "dataset" -> ArtifactKind.DATA_SET;
      case "element" -> ArtifactKind.ELEMENT;
      default -> null;
    };
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

  /** Imports a new Element; the root pin is returned, dependency edges are logged. */
  private ArtifactId importRoot(JsonNode content) {
    ImportResult result = modelForge.importSchema(new ImportSchemaCommand(content));
    logDependencies(result.rootArtifactId(), result.dependencies());
    return result.rootArtifactId();
  }

  /** Stores a follow-up version of an existing artifact; dependency edges are logged. */
  private ArtifactId saveVersion(
      String logicalUrn, ArtifactKind kind, JsonNode content, VersionBump bump) {
    return saveVersion(logicalUrn, kind, content, bump, null);
  }

  private ArtifactId saveVersion(
      String logicalUrn, ArtifactKind kind, JsonNode content, VersionBump bump, String dataSet) {
    ArtifactWriteResult result =
        modelForge.saveArtifact(
            new SaveArtifactCommand(
                new ArtifactId(logicalUrn), kind, content, toModelForgeBump(bump), dataSet));
    logDependencies(result.artifactId(), result.dependencies());
    return result.artifactId();
  }

  private static ModelPin toPin(ArtifactId root) {
    return new ModelPin(root.logicalUrn(), root.value(), root.version());
  }

  /**
   * Dependency lists returned by registry writes (rel name -> target URNs). Not mirrored into host
   * tables yet — full dependency mirroring is a later work package; log at debug for diagnosis.
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

  /** Maps the host bump onto the Model Forge bump; a missing bump defaults to {@code MINOR}. */
  private static de.civitascore.modelforge.contract.VersionBump toModelForgeBump(VersionBump bump) {
    return switch (bump == null ? VersionBump.MINOR : bump) {
      case MAJOR -> de.civitascore.modelforge.contract.VersionBump.MAJOR;
      case MINOR -> de.civitascore.modelforge.contract.VersionBump.MINOR;
      case PATCH -> de.civitascore.modelforge.contract.VersionBump.PATCH;
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
}

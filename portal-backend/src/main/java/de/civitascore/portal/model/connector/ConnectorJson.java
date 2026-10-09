package de.civitascore.portal.model.connector;

import de.civitascore.portal.model.embedded.ConnectorType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts a {@link ConnectorConfiguration} between its three JSON forms:
 *
 * <ul>
 *   <li>the request form, read strictly: an unknown key at any level is an error;
 *   <li>the response form, with every secret as {@value Secret#PLACEHOLDER};
 *   <li>the registry document, which follows {@code datasource.schema.json}: {@code connectionType}
 *       in lower case instead of {@code connectorType}, every secret encrypted.
 * </ul>
 */
public final class ConnectorJson {

  static final String TYPE_PROPERTY = "connectorType";
  static final String DOCUMENT_TYPE_PROPERTY = "connectionType";

  /** The request member that holds the connector, and the first key of every key path in it. */
  public static final String ROOT = "connector";

  /**
   * Keys of a registry document that the registry and the shared schema own. A stored connector is
   * read without them.
   */
  private static final Set<String> DOCUMENT_KEYS =
      Set.of("$schema", "$id", "id", "title", "description", "element", DOCUMENT_TYPE_PROPERTY);

  private static final JsonMapper STRICT =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private static final JsonMapper LENIENT =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private ConnectorJson() {}

  /**
   * Reads the connector of a request.
   *
   * @throws InvalidConnectorException for an unknown key at any level, an unknown or missing {@code
   *     connectorType}, or a value of the wrong form
   */
  public static ConnectorConfiguration read(JsonNode connector) {
    if (!connector.isObject()) {
      throw new InvalidConnectorException("Invalid value: " + ROOT);
    }
    try {
      return STRICT.treeToValue(connector, ConnectorConfiguration.class);
    } catch (JacksonException e) {
      throw toInvalidConnector(e);
    }
  }

  /**
   * Reads a connector from its registry document. Keys that the registry adds, and keys that an
   * earlier release wrote and this one no longer knows, are ignored.
   *
   * @param document the stored document, or an empty map for a connector type without a stored
   *     document
   * @throws IllegalStateException if the document does not fit the connector type
   */
  public static ConnectorConfiguration readStored(
      ConnectorType type, Map<String, Object> document) {
    ObjectNode tree = LENIENT.valueToTree(document);
    tree.remove(DOCUMENT_KEYS);
    tree.put(TYPE_PROPERTY, type.name());
    try {
      return LENIENT
          .readerFor(ConnectorConfiguration.class)
          .withAttribute(Secret.StorageRead.class, new Secret.StorageRead())
          .readValue(tree);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new IllegalStateException("Stored " + type + " connector cannot be read", e);
    }
  }

  /** The response form, with every secret as {@value Secret#PLACEHOLDER}. */
  public static ObjectNode toMaskedTree(ConnectorConfiguration connector) {
    return STRICT.valueToTree(connector);
  }

  /**
   * The registry document of {@code connector}. A clear-text secret is encrypted, and a secret read
   * from the registry keeps its ciphertext. A {@value Secret#PLACEHOLDER} takes the stored secret
   * at the same place, as {@link IdentifiedElement} defines it for list elements, and is no secret
   * if {@code storedDocument} has none there or belongs to another connector type.
   *
   * @param storedDocument the current registry document, or null if none is stored
   */
  public static Map<String, Object> toDocument(
      ConnectorConfiguration connector,
      Map<String, Object> storedDocument,
      UnaryOperator<String> encrypt) {
    return writeDocument(connector, readStoredDocument(storedDocument), encrypt);
  }

  static Map<String, Object> writeDocument(
      ConnectorConfiguration connector,
      ConnectorConfiguration stored,
      UnaryOperator<String> encrypt) {
    ConnectorConfiguration resolved = PlaceholderResolver.resolve(connector, stored);
    ObjectNode document =
        (ObjectNode)
            STRICT.readTree(
                STRICT
                    .writer()
                    .withAttribute(Secret.StorageWrite.class, new Secret.StorageWrite(encrypt))
                    .writeValueAsString(resolved));
    document.remove(TYPE_PROPERTY);
    document.put(DOCUMENT_TYPE_PROPERTY, connector.connectorType().name().toLowerCase(Locale.ROOT));
    return LENIENT.convertValue(document, new TypeReference<Map<String, Object>>() {});
  }

  /** The connector of a registry document, of the type that the document names. */
  private static ConnectorConfiguration readStoredDocument(Map<String, Object> document) {
    if (document == null
        || !(document.get(DOCUMENT_TYPE_PROPERTY) instanceof String documentType)) {
      return null;
    }
    return Arrays.stream(ConnectorType.values())
        .filter(type -> type.name().toLowerCase(Locale.ROOT).equals(documentType))
        .findFirst()
        .map(type -> readStored(type, document))
        .orElse(null);
  }

  /**
   * Applies a JSON merge patch (RFC 7386) to the response form of {@code current}. At every depth,
   * an object whose type the patch changes is replaced instead of merged, as {@code JsonMergePatch}
   * defines it.
   *
   * @param current the connector before the patch, or null if there is none
   * @param patch the {@code connector} member of the request; a missing node keeps {@code current}
   * @return the patched connector, or null if the patch removes it or there is none
   * @throws InvalidConnectorException if the patched connector cannot be read
   */
  public static ConnectorConfiguration patch(ConnectorConfiguration current, JsonNode patch) {
    if (patch.isNull() || (patch.isMissingNode() && current == null)) {
      return null;
    }
    if (!patch.isMissingNode() && !patch.isObject()) {
      throw new InvalidConnectorException("Invalid value: " + ROOT);
    }
    return read(
        JsonMergePatch.apply(
            current == null ? JsonNodeFactory.instance.objectNode() : toMaskedTree(current),
            patch));
  }

  /**
   * Reads the {@code connector} member of a request body strictly, as {@link #read(JsonNode)} does.
   * The member type carries type information, which this deserializer resolves itself.
   */
  public static final class RequestDeserializer extends ValueDeserializer<ConnectorConfiguration> {
    @Override
    public ConnectorConfiguration deserialize(JsonParser parser, DeserializationContext context) {
      return read(context.readTree(parser));
    }

    @Override
    public Object deserializeWithType(
        JsonParser parser, DeserializationContext context, TypeDeserializer typeDeserializer) {
      return deserialize(parser, context);
    }
  }

  private static InvalidConnectorException toInvalidConnector(JacksonException e) {
    if (e instanceof UnrecognizedPropertyException) {
      return new InvalidConnectorException("Unknown key: " + keyPath(e));
    }
    if (e instanceof InvalidTypeIdException typeId) {
      return new InvalidConnectorException(
          typeId.getTypeId() == null
              ? "Missing " + ROOT + "." + TYPE_PROPERTY
              : "Unknown " + TYPE_PROPERTY + ": " + typeId.getTypeId());
    }
    for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof InvalidConnectorValueException value) {
        return new InvalidConnectorException("Invalid value: " + keyPath(e) + "." + value.key());
      }
    }
    return new InvalidConnectorException("Invalid value: " + keyPath(e));
  }

  private static String keyPath(JacksonException e) {
    StringBuilder path = new StringBuilder(ROOT);
    for (JacksonException.Reference reference : e.getPath()) {
      if (reference.getPropertyName() != null) {
        path.append('.').append(reference.getPropertyName());
      } else if (reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex()).append(']');
      }
    }
    return path.toString();
  }
}

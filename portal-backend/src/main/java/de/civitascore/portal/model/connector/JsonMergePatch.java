package de.civitascore.portal.model.connector;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * A JSON merge patch (RFC 7396) for the tree of a connector, with one deviation: at every depth, a
 * patch object that names another type than the target object replaces the target object, so that
 * no key of the previous type remains. The type is {@value ConnectorJson#TYPE_PROPERTY} at the root
 * and {@value #NESTED_TYPE_PROPERTY} in nested objects.
 */
final class JsonMergePatch {

  static final String NESTED_TYPE_PROPERTY = "type";

  private JsonMergePatch() {}

  /**
   * @param patch the patch; a missing node keeps {@code target}
   * @return the patched tree; {@code target} stays unchanged
   */
  static JsonNode apply(JsonNode target, JsonNode patch) {
    return merge(target, patch, ConnectorJson.TYPE_PROPERTY);
  }

  private static JsonNode merge(JsonNode target, JsonNode patch, String typeProperty) {
    if (!patch.isObject()) {
      return patch.isMissingNode() ? target : patch;
    }
    ObjectNode result =
        target.isObject() && !changesType(target, patch, typeProperty)
            ? (ObjectNode) target.deepCopy()
            : JsonNodeFactory.instance.objectNode();
    for (Map.Entry<String, JsonNode> member : patch.properties()) {
      if (member.getValue().isNull()) {
        result.remove(member.getKey());
      } else {
        result.set(
            member.getKey(),
            merge(result.path(member.getKey()), member.getValue(), NESTED_TYPE_PROPERTY));
      }
    }
    return result;
  }

  private static boolean changesType(JsonNode target, JsonNode patch, String typeProperty) {
    JsonNode targetType = target.get(typeProperty);
    JsonNode patchType = patch.get(typeProperty);
    return targetType != null
        && patchType != null
        && !patchType.isNull()
        && !targetType.equals(patchType);
  }
}

package de.civitascore.portal.controller;

import de.civitascore.portal.util.InvalidInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Applies a strict merge patch to the input DTO of a resource. Only the top-level members merge: a
 * member that the patch contains replaces the field, a nested object or map included, and a {@code
 * null} sets the field to {@code null}. This differs from RFC 7396, which merges nested objects
 * recursively and removes a member on {@code null}.
 */
@Component
@RequiredArgsConstructor
class JsonMergePatch {

  static final String OPERATION_DESCRIPTION =
      "Applies a merge patch to an existing resource. A top-level field that the patch omits keeps"
          + " its value. A field that the patch contains replaces the stored value; a nested object"
          + " replaces the stored object as a whole and does not merge. null clears a field, or"
          + " answers 400 for a field that must have a value. A field that the resource does not"
          + " have answers 400.";

  private final ObjectMapper objectMapper;
  private final Validator validator;

  /**
   * Applies the patch to {@code current} and validates the result. A field that the patch omits
   * keeps its value. The type of {@code current} defines which fields the patch may contain.
   *
   * @param current the current state, updated in place
   * @param patch the merge patch
   * @param id the id of the patched resource, used in the error
   * @return the patched and validated state
   * @throws InvalidInputException if the patch is not a JSON object, contains a field that {@code
   *     current} does not have, sets a field to {@code null} that rejects it, sets a field to a
   *     value of the wrong type, or the result violates a Bean Validation constraint
   */
  <T> T apply(T current, JsonNode patch, UUID id) {
    if (!patch.isObject()) {
      throw invalid(current, id, "A merge patch must be a JSON object");
    }
    UnknownFieldCollector unknownFields = new UnknownFieldCollector();
    T patched;
    try {
      patched = objectMapper.readerForUpdating(current).withHandler(unknownFields).readValue(patch);
    } catch (InvalidNullException e) {
      throw invalid(current, id, "Must not be null: " + jsonPointer(e));
    } catch (JacksonException e) {
      throw rejectedValue(current, id, e);
    }
    if (!unknownFields.pointers.isEmpty()) {
      throw invalid(current, id, "Unknown field: " + String.join(", ", unknownFields.pointers));
    }
    return validated(patched, id);
  }

  /**
   * Jackson wraps an exception that the input throws itself, from a setter, any-setter or custom
   * deserializer; that exception carries its own answer. A mismatched input is Jackson's own
   * rejection, even when it carries the parse error as its cause.
   */
  private static RuntimeException rejectedValue(Object current, UUID id, JacksonException e) {
    if (!(e instanceof MismatchedInputException)
        && e.getCause() instanceof RuntimeException cause
        && !(cause instanceof JacksonException)) {
      return cause;
    }
    return invalid(current, id, "Invalid value: " + jsonPointer(e));
  }

  private <T> T validated(T dto, UUID id) {
    Set<ConstraintViolation<T>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      String message =
          violations.stream()
              .map(ConstraintViolation::getMessage)
              .reduce((a, b) -> a + ";\n" + b)
              .orElse("");
      throw invalid(dto, id, message);
    }
    return dto;
  }

  private static InvalidInputException invalid(Object current, UUID id, String message) {
    return new InvalidInputException(current.getClass().getSimpleName(), id, message);
  }

  private static String jsonPointer(JacksonException e) {
    StringBuilder pointer = new StringBuilder();
    for (JacksonException.Reference reference : e.getPath()) {
      pointer.append('/');
      if (reference.getPropertyName() != null) {
        pointer.append(reference.getPropertyName());
      } else {
        pointer.append(reference.getIndex());
      }
    }
    return pointer.toString();
  }

  private static final class UnknownFieldCollector extends DeserializationProblemHandler {
    private final List<String> pointers = new ArrayList<>();

    @Override
    public boolean handleUnknownProperty(
        DeserializationContext context,
        JsonParser parser,
        ValueDeserializer<?> deserializer,
        Object beanOrClass,
        String propertyName) {
      pointers.add(parser.streamReadContext().pathAsPointer().toString());
      parser.skipChildren();
      return true;
    }
  }
}

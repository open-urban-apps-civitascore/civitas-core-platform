package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Replaces each {@value Secret#PLACEHOLDER} of a connector with the stored secret at the same
 * place: the same record component, and in a list the stored element with the same {@link
 * IdentifiedElement#identity()}. A placeholder without such a stored secret becomes no secret. The
 * rest of the connector is not compared, so a placeholder keeps the stored secret also when the
 * connector's host changes: the right to update a Data source includes the use of its stored
 * credentials.
 */
final class PlaceholderResolver {

  private PlaceholderResolver() {}

  static ConnectorConfiguration resolve(
      ConnectorConfiguration connector, ConnectorConfiguration stored) {
    return (ConnectorConfiguration) resolveValue(connector, stored, ConnectorJson.ROOT, path -> {});
  }

  /**
   * The key paths, e.g. {@code connector.password}, of the placeholders that {@link #resolve} turns
   * into no secret.
   */
  static List<String> unresolvedPlaceholders(
      ConnectorConfiguration connector, ConnectorConfiguration stored) {
    List<String> unresolved = new ArrayList<>();
    resolveValue(connector, stored, ConnectorJson.ROOT, unresolved::add);
    return unresolved;
  }

  private static Object resolveValue(
      Object value, Object stored, String path, Consumer<String> onUnresolved) {
    return switch (value) {
      case Secret secret when secret.isPlaceholder() ->
          resolvePlaceholder(stored, path, onUnresolved);
      case Record record -> resolveRecord(record, stored, path, onUnresolved);
      case List<?> list -> resolveList(list, stored, path, onUnresolved);
      case null, default -> value;
    };
  }

  private static Secret resolvePlaceholder(
      Object stored, String path, Consumer<String> onUnresolved) {
    if (stored instanceof Secret storedSecret && storedSecret.isStored()) {
      return storedSecret;
    }
    onUnresolved.accept(path);
    return null;
  }

  private static Record resolveRecord(
      Record record, Object stored, String path, Consumer<String> onUnresolved) {
    RecordComponent[] components = record.getClass().getRecordComponents();
    Object counterpart = stored != null && stored.getClass() == record.getClass() ? stored : null;
    Object[] values = new Object[components.length];
    boolean changed = false;
    for (int i = 0; i < components.length; i++) {
      Object current = componentValue(record, components[i]);
      values[i] =
          resolveValue(
              current,
              counterpart == null ? null : componentValue(counterpart, components[i]),
              path + "." + jsonKey(components[i]),
              onUnresolved);
      changed |= values[i] != current;
    }
    return changed ? newRecord(record.getClass(), components, values) : record;
  }

  private static List<?> resolveList(
      List<?> list, Object stored, String path, Consumer<String> onUnresolved) {
    Map<Object, Object> storedByIdentity =
        stored instanceof List<?> storedList ? byUniqueIdentity(storedList) : Map.of();
    List<Object> resolved = new ArrayList<>(list.size());
    boolean changed = false;
    for (int i = 0; i < list.size(); i++) {
      Object element = list.get(i);
      Object counterpart =
          element instanceof IdentifiedElement identified && identified.identity() != null
              ? storedByIdentity.get(identified.identity())
              : null;
      Object value = resolveValue(element, counterpart, path + "[" + i + "]", onUnresolved);
      resolved.add(value);
      changed |= value != element;
    }
    return changed ? List.copyOf(resolved) : list;
  }

  /** The stored elements by identity, without an identity that more than one element has. */
  private static Map<Object, Object> byUniqueIdentity(List<?> elements) {
    Map<Object, Object> byIdentity = new HashMap<>();
    Set<Object> ambiguous = new HashSet<>();
    for (Object element : elements) {
      if (element instanceof IdentifiedElement identified && identified.identity() != null) {
        if (byIdentity.putIfAbsent(identified.identity(), element) != null) {
          ambiguous.add(identified.identity());
        }
      }
    }
    byIdentity.keySet().removeAll(ambiguous);
    return byIdentity;
  }

  private static String jsonKey(RecordComponent component) {
    JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
    return property == null || property.value().isEmpty() ? component.getName() : property.value();
  }

  private static Object componentValue(Object record, RecordComponent component) {
    try {
      component.getAccessor().setAccessible(true);
      return component.getAccessor().invoke(record);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Cannot read " + component, e);
    }
  }

  private static Record newRecord(Class<?> type, RecordComponent[] components, Object[] values) {
    Class<?>[] parameterTypes =
        Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
    try {
      Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
      constructor.setAccessible(true);
      return (Record) constructor.newInstance(values);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot create " + type.getName(), e);
    }
  }
}

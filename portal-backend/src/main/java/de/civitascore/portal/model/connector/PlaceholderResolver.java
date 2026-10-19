package de.civitascore.portal.model.connector;

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

/**
 * Replaces each {@value Secret#PLACEHOLDER} of a connector with the stored secret at the same
 * place: the same record component, and in a list the stored element with the same {@link
 * IdentifiedElement#identity()}. A placeholder without such a stored secret becomes no secret.
 */
final class PlaceholderResolver {

  private PlaceholderResolver() {}

  static ConnectorConfiguration resolve(
      ConnectorConfiguration connector, ConnectorConfiguration stored) {
    return (ConnectorConfiguration) resolveValue(connector, stored);
  }

  private static Object resolveValue(Object value, Object stored) {
    return switch (value) {
      case Secret secret when secret.isPlaceholder() ->
          stored instanceof Secret storedSecret && storedSecret.isStored() ? storedSecret : null;
      case Record record -> resolveRecord(record, stored);
      case List<?> list -> resolveList(list, stored);
      case null, default -> value;
    };
  }

  private static Record resolveRecord(Record record, Object stored) {
    RecordComponent[] components = record.getClass().getRecordComponents();
    Object counterpart = stored != null && stored.getClass() == record.getClass() ? stored : null;
    Object[] values = new Object[components.length];
    boolean changed = false;
    for (int i = 0; i < components.length; i++) {
      Object current = componentValue(record, components[i]);
      values[i] =
          resolveValue(
              current, counterpart == null ? null : componentValue(counterpart, components[i]));
      changed |= values[i] != current;
    }
    return changed ? newRecord(record.getClass(), components, values) : record;
  }

  private static List<?> resolveList(List<?> list, Object stored) {
    Map<Object, Object> storedByIdentity =
        stored instanceof List<?> storedList ? byUniqueIdentity(storedList) : Map.of();
    List<Object> resolved = new ArrayList<>(list.size());
    boolean changed = false;
    for (Object element : list) {
      Object counterpart =
          element instanceof IdentifiedElement identified && identified.identity() != null
              ? storedByIdentity.get(identified.identity())
              : null;
      Object value = resolveValue(element, counterpart);
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

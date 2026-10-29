package de.civitascore.portal.kind;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Builds the id index of a kind registry and fails fast on a missing or duplicate id. */
final class KindIndex {

  private KindIndex() {}

  static <I extends Enum<I>, K> Map<I, K> byId(
      Class<I> idType, Collection<K> kinds, Function<K, I> idOf) {
    Map<I, K> index = new EnumMap<>(idType);
    for (K kind : kinds) {
      K previous = index.put(idOf.apply(kind), kind);
      if (previous != null) {
        throw new IllegalStateException(
            "Two kinds for " + idType.getSimpleName() + " " + idOf.apply(kind));
      }
    }
    Set<I> missing = EnumSet.allOf(idType);
    missing.removeAll(index.keySet());
    if (!missing.isEmpty()) {
      throw new IllegalStateException("No kind for " + idType.getSimpleName() + " " + missing);
    }
    return Map.copyOf(index);
  }
}

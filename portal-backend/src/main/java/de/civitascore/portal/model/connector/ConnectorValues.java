package de.civitascore.portal.model.connector;

import java.util.List;

/** Normalizations that every connector configuration applies in its canonical constructor. */
final class ConnectorValues {

  private ConnectorValues() {}

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /**
   * An unmodifiable copy of {@code values}.
   *
   * @throws InvalidConnectorValueException naming {@code key[i]} for a null element
   */
  static List<String> listOf(List<String> values, String key) {
    if (values == null) {
      return null;
    }
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i) == null) {
        throw new InvalidConnectorValueException(key + "[" + i + "]");
      }
    }
    return List.copyOf(values);
  }
}

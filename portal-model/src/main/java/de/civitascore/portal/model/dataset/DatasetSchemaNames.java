package de.civitascore.portal.model.dataset;

import java.util.UUID;

/** Creates stable, readable FROST schema names from a dataset name and UUID. */
public final class DatasetSchemaNames {

  private static final int MAX_PREFIX_LENGTH = 54; // PostgreSQL identifier limit is 63 bytes.

  private DatasetSchemaNames() {}

  public static String fromDataset(String datasetName, UUID datasetId) {
    if (datasetName == null || datasetName.isBlank() || datasetId == null) {
      throw new IllegalArgumentException("Dataset name and id are required");
    }

    StringBuilder normalized = new StringBuilder();
    boolean separator = false;
    for (int index = 0; index < datasetName.length(); index++) {
      char character = datasetName.charAt(index);
      if (character >= 'A' && character <= 'Z') {
        normalized.append((char) (character + ('a' - 'A')));
        separator = false;
      } else if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
        normalized.append(character);
        separator = false;
      } else if (!separator) {
        normalized.append('_');
        separator = true;
      }
    }
    String prefix = normalized.toString().replaceAll("^_+|_+$", "");
    if (prefix.isEmpty()) {
      prefix = "dataset";
    }
    boolean startsWithDigit = Character.isDigit(prefix.charAt(0));
    int maxPrefixLength = startsWithDigit ? MAX_PREFIX_LENGTH - 3 : MAX_PREFIX_LENGTH;
    if (prefix.length() > maxPrefixLength) {
      prefix = prefix.substring(0, maxPrefixLength).replaceAll("_+$", "");
    }
    if (startsWithDigit) {
      prefix = "ds_" + prefix;
    }
    String uuid = datasetId.toString().replace("-", "");
    return prefix + "_" + uuid.substring(uuid.length() - 8);
  }
}

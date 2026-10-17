package de.civitascore.portal.modelregistry;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The identity scheme the portal gives a data structure it creates — the server-side twin of the
 * frontend's {@code utils/urn.ts}, which stamps the same URNs on models saved from the UML editor.
 *
 * <p>Name segment: the display name in PascalCase. Disambiguator: the shell's UUID read as one
 * number and written in base36, last ten digits. Deterministic, so a shell always yields the same
 * URN; member Elements share the shell's disambiguator and differ only in the type and name
 * segments. The two implementations must stay in step — otherwise structures authored in the editor
 * and structures installed from a package would live under different naming rules.
 */
public final class DataStructureUrns {

  private static final String SCOPE = "platform";
  private static final String OWNER = "civitas";
  private static final String DOMAIN = "common";
  private static final String TYPE_DATA_STRUCTURE = "datastructure";
  private static final String TYPE_ELEMENT = "element";
  private static final int DISAMBIGUATOR_LENGTH = 10;
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+");
  private static final Map<Character, String> UMLAUTS =
      Map.of('ä', "ae", 'ö', "oe", 'ü', "ue", 'Ä', "Ae", 'Ö', "Oe", 'Ü', "Ue", 'ß', "ss");

  private DataStructureUrns() {}

  /** The logical (version-free) URN of the data structure behind {@code shellId}. */
  public static String dataStructure(String name, UUID shellId) {
    String normalizedName = toPascalCaseName(name);
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException(
          "Data structure name yields no URN segment: '" + name + "'");
    }
    return "urn:core:%s:%s:%s:%s:%s:%s"
        .formatted(
            SCOPE, OWNER, TYPE_DATA_STRUCTURE, DOMAIN, normalizedName, toDisambiguator(shellId));
  }

  /**
   * The URN of one {@code $defs} member of a data structure: same disambiguator, type segment
   * {@code element}, name segment from the member's name.
   */
  public static String elementForMember(String dataStructureUrn, String memberName) {
    String[] parts = dataStructureUrn.split(":");
    if (parts.length < 8 || !"urn".equals(parts[0]) || !"core".equals(parts[1])) {
      throw new IllegalArgumentException("Not a CORE URN: '" + dataStructureUrn + "'");
    }
    String normalizedName = toPascalCaseName(memberName);
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException("Member name yields no URN segment: '" + memberName + "'");
    }
    parts[4] = TYPE_ELEMENT;
    parts[6] = normalizedName;
    return String.join(":", parts);
  }

  /**
   * Umlauts transliterated, split on any run of non-alphanumerics, every word capitalised: {@code
   * "Lärmkartierung – Hauptverkehrsstraßen"} → {@code "LaermkartierungHauptverkehrsstrassen"}.
   */
  static String toPascalCaseName(String name) {
    if (name == null) {
      return "";
    }
    StringBuilder transliterated = new StringBuilder(name.length());
    for (char c : name.toCharArray()) {
      transliterated.append(UMLAUTS.getOrDefault(c, String.valueOf(c)));
    }
    StringBuilder pascal = new StringBuilder();
    for (String word : NON_ALPHANUMERIC.split(transliterated)) {
      if (!word.isEmpty()) {
        pascal.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return pascal.toString();
  }

  /**
   * The UUID's hex digits as one 128-bit integer in base36, least-significant ten digits,
   * left-padded — the low-order digits spread the whole UUID's entropy across the token.
   */
  static String toDisambiguator(UUID id) {
    String base36 = new BigInteger(id.toString().replace("-", ""), 16).toString(36);
    String tail =
        base36.length() > DISAMBIGUATOR_LENGTH
            ? base36.substring(base36.length() - DISAMBIGUATOR_LENGTH)
            : base36;
    return "0".repeat(DISAMBIGUATOR_LENGTH - tail.length()) + tail;
  }
}

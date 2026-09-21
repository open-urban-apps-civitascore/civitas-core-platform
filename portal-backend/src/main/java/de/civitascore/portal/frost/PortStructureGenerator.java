package de.civitascore.portal.frost;

import de.civitascore.portal.frost.PortStructureModel.Child;
import de.civitascore.portal.frost.PortStructureModel.Field;
import de.civitascore.portal.frost.PortStructureModel.Member;
import de.civitascore.portal.frost.PortStructureModel.PortStructure;
import de.civitascore.portal.frost.PortStructureModel.StaClass;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders a declared port structure as the JSON-Schema model of a Data structure.
 *
 * <p>The output is the same document a modeller's own Data structure carries, so the mapping editor
 * and the deploy engine read it with the components they already have: a wrapper root pointing at
 * the record class, the classes under {@code $defs}, {@code required} for the mandatory fields and
 * {@code x-core-primaryKey} for the reference.
 *
 * <p>The rendering is a pure function of the declaration. The same declaration gives the same bytes
 * — key order follows the declaration, and the printer is pinned — which is what lets the generated
 * files be reviewed in a diff and checked by a test.
 */
public final class PortStructureGenerator {

  private static final String SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";

  /**
   * The identity of a published structure, as a logical CORE URN: {@code
   * urn:core:<scope>:<owner>:<type>:<domain>:<name>:<disambiguator>}.
   *
   * <p>It carries no version. The registry is the version authority and assigns one on store, so a
   * version authored here would only state a guess.
   */
  private static final String ID_TEMPLATE = "urn:core:platform:civitas:datastructure:frost:%s:%s";

  /** The length of the disambiguator segment, as the portal builds it elsewhere. */
  private static final int DISAMBIGUATOR_LENGTH = 10;

  /** The classpath directory the rendered structures are in. */
  public static final String RESOURCE_DIRECTORY = "frost/port-structure/";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PortStructureGenerator() {}

  /** The model document of one port, as the map the registry and the editor read. */
  public static Map<String, Object> model(PortStructure structure) {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("$id", logicalUrn(structure.port()));
    document.put("$schema", SCHEMA_DIALECT);
    document.put("title", structure.port());
    document.put("type", "object");
    // The wrapper root: one property holding a bare reference to the record class. Both the editor
    // and the deploy engine resolve through it, so the record's own fields stay anchored at '$'.
    Map<String, Object> rootProperty = new LinkedHashMap<>();
    rootProperty.put(wrapperName(structure.root()), ref(structure.root()));
    document.put("properties", rootProperty);

    Map<String, Object> defs = new LinkedHashMap<>();
    for (StaClass declared : structure.classes()) {
      defs.put(declared.title(), definition(declared));
    }
    document.put("$defs", defs);
    return document;
  }

  /**
   * The logical CORE URN a port's structure is stored under.
   *
   * <p>The disambiguator keeps equal names apart. A modelled structure takes it from its database
   * identifier; a published one has none, so it is derived from the port label. The derivation is a
   * function of the label alone, which is what makes the identity survive a reinstallation.
   */
  public static String logicalUrn(String port) {
    return ID_TEMPLATE.formatted(port, disambiguator(port));
  }

  private static String disambiguator(String port) {
    byte[] digest;
    try {
      digest = MessageDigest.getInstance("SHA-256").digest(port.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
    // The low-order digits carry the spread; the high-order ones of neighbouring digests often
    // agree. Same reasoning as the portal's own disambiguator.
    String base36 = new BigInteger(1, digest).toString(36);
    return base36.substring(Math.max(0, base36.length() - DISAMBIGUATOR_LENGTH));
  }

  /** The model document of one port, printed the one way this generator prints. */
  public static String render(PortStructure structure) {
    DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
    // Two spaces, LF, and the field separator a JSON file is normally written with. The printer is
    // pinned rather than defaulted, so a Jackson upgrade cannot rewrite every generated file.
    DefaultPrettyPrinter printer =
        new DefaultPrettyPrinter(
            Separators.createDefaultInstance()
                .withObjectNameValueSpacing(Separators.Spacing.AFTER));
    printer.indentObjectsWith(indenter);
    printer.indentArraysWith(indenter);
    return MAPPER.writer().with(printer).writeValueAsString(model(structure)) + "\n";
  }

  /**
   * Where the rendered structure of a port is kept. The generated files are in the repository so
   * that a change to the declaration is visible in a diff, and a test renders them again and
   * compares.
   */
  public static String resourcePath(PortStructure structure) {
    return RESOURCE_DIRECTORY + fileName(structure.port()) + ".json";
  }

  /** The port label as a file name: {@code ThingTree} becomes {@code thing-tree}. */
  static String fileName(String port) {
    return port.replaceAll("(?<=[a-z])(?=[A-Z])", "-").toLowerCase(Locale.ROOT);
  }

  private static Map<String, Object> definition(StaClass declared) {
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("type", "object");
    definition.put("title", declared.title());

    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (Member member : declared.members()) {
      properties.put(member.name(), property(member));
      if (member.required()) {
        required.add(member.name());
      }
    }
    definition.put("properties", properties);
    if (!required.isEmpty()) {
      definition.put("required", required);
    }
    return definition;
  }

  private static Map<String, Object> property(Member member) {
    if (member instanceof Child child) {
      if (!child.many()) {
        return ref(child.type());
      }
      Map<String, Object> collection = new LinkedHashMap<>();
      collection.put("type", "array");
      collection.put("items", ref(child.type()));
      return collection;
    }
    Field field = (Field) member;
    Map<String, Object> property = new LinkedHashMap<>();
    switch (field.type()) {
      case TEXT -> property.put("type", "string");
      case TIMESTAMP -> {
        property.put("type", "string");
        property.put("format", "date-time");
      }
      case JSON -> property.put("type", "object");
      // An empty schema is how JSON Schema says "any value". A declared type here would refuse
      // valid data: a measurement result may be a number, a text, a boolean or an object.
      case ANY -> {
        // no keyword
      }
    }
    if (field.primaryKey()) {
      property.put("x-core-primaryKey", true);
    }
    return property;
  }

  private static Map<String, Object> ref(String className) {
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put("$ref", "#/$defs/" + className);
    return reference;
  }

  /** The single property of the wrapper root, named after the record class. */
  private static String wrapperName(String className) {
    return className.substring(0, 1).toLowerCase(Locale.ROOT) + className.substring(1);
  }
}

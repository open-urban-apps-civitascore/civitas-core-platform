package de.civitascore.portal.frost;

import java.util.List;

/**
 * The vocabulary the port structures are declared in.
 *
 * <p>It is deliberately small: a class holds fields and children, a field carries a type, and a
 * child names another class. Everything a SensorThings entity has beyond that — the self link, the
 * navigation links, the read-only identifier — is not in this vocabulary, so it cannot reach a
 * published structure by accident.
 */
public final class PortStructureModel {

  private PortStructureModel() {}

  /** What a member of a class is. */
  public sealed interface Member permits Field, Child {

    String name();

    boolean required();
  }

  /**
   * A value of the entity.
   *
   * @param primaryKey whether the value is the reference the port upserts the entity on. Only a
   *     field can carry it, which mirrors the rule of the deploy engine and of the editor.
   */
  public record Field(String name, Type type, boolean required, boolean primaryKey)
      implements Member {

    public static Field text(String name, boolean required) {
      return new Field(name, Type.TEXT, required, false);
    }

    /** The reference block's key: a text field the port resolves the entity by. */
    public static Field reference(boolean required) {
      return new Field("reference", Type.TEXT, required, true);
    }

    public static Field timestamp(String name) {
      return new Field(name, Type.TIMESTAMP, false, false);
    }

    /** A free JSON value the platform passes through, for example a GeoJSON geometry. */
    public static Field json(String name, boolean required) {
      return new Field(name, Type.JSON, required, false);
    }

    /** A value of no fixed type, for example a measurement that is a number or a text. */
    public static Field any(String name, boolean required) {
      return new Field(name, Type.ANY, required, false);
    }
  }

  /**
   * Another class under this one: a nested entity, a value object, or the free-attribute bag.
   *
   * @param many whether the member is a collection. The ports of this stage write one member of a
   *     collection; a record that carries more is rejected with a message.
   */
  public record Child(String name, String type, boolean many, boolean required) implements Member {

    public static Child one(String name, String type, boolean required) {
      return new Child(name, type, false, required);
    }

    public static Child many(String name, String type) {
      return new Child(name, type, true, false);
    }
  }

  /** One class of a published structure. */
  public record StaClass(String title, List<Member> members) {

    public static StaClass of(String title, Member... members) {
      return new StaClass(title, List.of(members));
    }
  }

  /**
   * The structure one port publishes.
   *
   * @param port the label of the port, as the sink configuration carries it
   * @param root the class a record of this port is
   * @param classes every class of the structure, the root included
   */
  public record PortStructure(String port, String root, List<StaClass> classes) {}

  /** The JSON types a field may have. */
  public enum Type {
    /** A text. */
    TEXT,
    /** A text holding a point in time, which the editor offers as a date-time port. */
    TIMESTAMP,
    /** A JSON object the platform passes through without reading it. */
    JSON,
    /**
     * No fixed type. SensorThings lets a measurement result be a number, a text, a boolean or an
     * object, and a declared type here would refuse valid data at edit time.
     */
    ANY
  }
}

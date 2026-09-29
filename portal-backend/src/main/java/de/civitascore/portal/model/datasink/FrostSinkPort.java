package de.civitascore.portal.model.datasink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;

/**
 * The write logic of a FROST DataSink. A port declares the data model it expects, the write
 * operation it applies and the references it resolves. The modeller selects it; the platform does
 * not derive it from the Mapping.
 */
@Schema(description = "The write logic of a FROST data sink")
public enum FrostSinkPort {
  /** Upserts a Thing by its own reference. One entity for each record. */
  THINGS("Things"),

  /** Appends a measurement to a Datastream that must exist. One entity for each record. */
  OBSERVATIONS("Observations"),

  /**
   * Upserts a Thing with its Location, Datastream, Sensor and ObservedProperty, and appends one
   * measurement. Six entities for each record.
   */
  THING_TREE("ThingTree");

  private final String label;

  FrostSinkPort(String label) {
    this.label = label;
  }

  /** The name the port carries on the wire and in the processor property. */
  @JsonValue
  public String label() {
    return label;
  }

  /** The labels of the closed set, for a message that has to name the choices. */
  public static List<String> labels() {
    return Arrays.stream(values()).map(FrostSinkPort::label).toList();
  }

  /**
   * The port of a stored value.
   *
   * @throws IllegalArgumentException for a value outside the closed set. An unknown port must not
   *     pass through to the deploy engine, where it would reach a processor property.
   */
  @JsonCreator
  public static FrostSinkPort of(String label) {
    return Arrays.stream(values())
        .filter(port -> port.label.equals(label))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown FROST sink port: " + label));
  }
}

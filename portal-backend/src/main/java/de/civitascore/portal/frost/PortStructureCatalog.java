package de.civitascore.portal.frost;

import static de.civitascore.portal.frost.PortStructureModel.Child.many;
import static de.civitascore.portal.frost.PortStructureModel.Child.one;
import static de.civitascore.portal.frost.PortStructureModel.Field.any;
import static de.civitascore.portal.frost.PortStructureModel.Field.json;
import static de.civitascore.portal.frost.PortStructureModel.Field.reference;
import static de.civitascore.portal.frost.PortStructureModel.Field.text;
import static de.civitascore.portal.frost.PortStructureModel.Field.timestamp;

import de.civitascore.portal.frost.PortStructureModel.PortStructure;
import de.civitascore.portal.frost.PortStructureModel.StaClass;
import de.civitascore.portal.model.datasink.FrostSinkPort;
import java.util.List;
import java.util.Optional;

/**
 * What each port accepts, declared once and reviewed here.
 *
 * <p>This is the source of the published structures. The FROST OpenAPI document is not: the server
 * builds it at run time from its own model and serves it at {@code <serviceRoot>/api}, so there is
 * no document to pin, and the parts that matter here are not in it either. It declares no mandatory
 * fields, it gives {@code properties} the shape of a free object, and it carries the self link, the
 * navigation links and the read-only identifier, which a Mapping author cannot set. Generating from
 * a declaration keeps the structures reproducible and adds no dependency on a document the platform
 * does not control. That FROST still has the fields declared here is checked by a test against a
 * running server, not by the build.
 *
 * <p>Three differences to the SensorThings specification are deliberate:
 *
 * <ul>
 *   <li>Only the writable part. A link is not a field a Mapping writes.
 *   <li>The {@code properties} bag carries the reference block. SensorThings leaves the bag free;
 *       the port needs a key in it to find the entity again.
 *   <li>A reference shows the external key, not the {@code @iot.id}. The Mapping author knows the
 *       identifier of the source system; the port resolves it when it writes.
 * </ul>
 */
public final class PortStructureCatalog {

  private PortStructureCatalog() {}

  /**
   * The bags are classes of their own, so that the reference block is a field with a path and a
   * type instead of a free object the editor cannot show.
   */
  private static final StaClass THING_PROPERTIES = StaClass.of("ThingProperties", reference(true));

  private static final StaClass DATASTREAM_PROPERTIES =
      StaClass.of("DatastreamProperties", reference(true));

  // A Location without a reference of its own takes the reference of its Thing, so the field is
  // optional here although it is the key the port looks the Location up by.
  private static final StaClass LOCATION_PROPERTIES =
      StaClass.of("LocationProperties", reference(false));

  private static final StaClass UNIT_OF_MEASUREMENT =
      StaClass.of(
          "UnitOfMeasurement", text("name", true), text("symbol", true), text("definition", true));

  private static final StaClass FEATURE_OF_INTEREST =
      StaClass.of(
          "FeatureOfInterest",
          text("name", true),
          text("description", true),
          text("encodingType", true),
          json("feature", true),
          json("properties", false));

  private static final StaClass SENSOR =
      StaClass.of(
          "Sensor",
          text("name", true),
          text("description", true),
          text("encodingType", true),
          text("metadata", true),
          json("properties", false));

  private static final StaClass OBSERVED_PROPERTY =
      StaClass.of(
          "ObservedProperty",
          text("name", true),
          text("definition", true),
          text("description", true),
          json("properties", false));

  /**
   * The measurement inside the chain. It carries no reference of its own: the tree appends every
   * measurement, which is what the generated graph it replaces did.
   *
   * <p>Only {@code resultTime} is a point in time. SensorThings lets {@code phenomenonTime} be a
   * period as well and declares {@code validTime} as one, so both stay text: a date-time field
   * would refuse a period at edit time, and there is no transform that makes one out of it.
   */
  private static final StaClass TREE_OBSERVATION =
      StaClass.of(
          "Observation",
          any("result", true),
          text("phenomenonTime", false),
          timestamp("resultTime"),
          json("resultQuality", false),
          text("validTime", false),
          one("FeatureOfInterest", "FeatureOfInterest", false));

  private static final PortStructure THINGS =
      new PortStructure(
          FrostSinkPort.THINGS.label(),
          "Thing",
          List.of(
              StaClass.of(
                  "Thing",
                  text("name", true),
                  text("description", true),
                  one("properties", "ThingProperties", true)),
              THING_PROPERTIES));

  /**
   * The reference block of a standalone measurement. {@code thingReference} and {@code
   * datastreamReference} name the Datastream the measurement belongs to, and the port rejects the
   * record when they resolve nothing. The measurement's own reference decides the write: with one
   * the port updates, without one it appends.
   */
  private static final StaClass OBSERVATION_PARAMETERS =
      StaClass.of(
          "ObservationParameters",
          reference(false),
          text("thingReference", true),
          text("datastreamReference", true));

  private static final PortStructure OBSERVATIONS =
      new PortStructure(
          FrostSinkPort.OBSERVATIONS.label(),
          "Observation",
          List.of(
              StaClass.of(
                  "Observation",
                  any("result", true),
                  text("phenomenonTime", false),
                  timestamp("resultTime"),
                  json("resultQuality", false),
                  text("validTime", false),
                  one("parameters", "ObservationParameters", true),
                  one("FeatureOfInterest", "FeatureOfInterest", false)),
              OBSERVATION_PARAMETERS,
              FEATURE_OF_INTEREST));

  private static final PortStructure THING_TREE =
      new PortStructure(
          FrostSinkPort.THING_TREE.label(),
          "Thing",
          List.of(
              StaClass.of(
                  "Thing",
                  text("name", true),
                  text("description", true),
                  one("properties", "ThingProperties", true),
                  many("Locations", "Location"),
                  many("Datastreams", "Datastream")),
              THING_PROPERTIES,
              StaClass.of(
                  "Location",
                  text("name", true),
                  text("description", true),
                  text("encodingType", true),
                  json("location", true),
                  one("properties", "LocationProperties", false)),
              LOCATION_PROPERTIES,
              StaClass.of(
                  "Datastream",
                  text("name", true),
                  text("description", true),
                  text("observationType", true),
                  one("unitOfMeasurement", "UnitOfMeasurement", true),
                  one("properties", "DatastreamProperties", true),
                  one("Sensor", "Sensor", false),
                  one("ObservedProperty", "ObservedProperty", false),
                  many("Observations", "Observation")),
              DATASTREAM_PROPERTIES,
              UNIT_OF_MEASUREMENT,
              SENSOR,
              OBSERVED_PROPERTY,
              TREE_OBSERVATION,
              FEATURE_OF_INTEREST));

  private static final List<PortStructure> STRUCTURES = List.of(THINGS, OBSERVATIONS, THING_TREE);

  /** Every declared structure, in the order the ports are published in. */
  public static List<PortStructure> all() {
    return STRUCTURES;
  }

  /** The structure of one port, or empty when the label names no port. */
  public static Optional<PortStructure> of(String port) {
    return STRUCTURES.stream().filter(structure -> structure.port().equals(port)).findFirst();
  }
}

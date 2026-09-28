/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.port;

import java.util.function.Supplier;

/**
 * The ports the FROST sink offers. A port declares three items: the data model it expects, the
 * logic it applies and the reference condition it resolves. The user selects one; the platform does
 * not derive it.
 */
public enum SinkPort {
  THINGS(
      "Things",
      "Upserts a Thing by its reference, inside the Dataset's project. One entity per record.",
      ThingsPort::new),

  OBSERVATIONS(
      "Observations",
      "Appends a measurement to a Datastream that must exist, resolved through thingReference and"
          + " datastreamReference. A measurement with a reference of its own is updated instead of"
          + " added. One entity per record.",
      ObservationsPort::new),

  THING_TREE(
      "ThingTree",
      "Upserts a Thing with its Location, its Datastream, the Sensor and the ObservedProperty, and"
          + " appends one measurement. Six entities per record.",
      ThingTreePort::new);

  private final String label;
  private final String description;
  private final Supplier<PortPlanner> planner;

  SinkPort(String label, String description, Supplier<PortPlanner> planner) {
    this.label = label;
    this.description = description;
    this.planner = planner;
  }

  /** The name the port carries in the processor property and in the user interface. */
  public String label() {
    return label;
  }

  /** What the port writes and what it costs. */
  public String description() {
    return description;
  }

  public PortPlanner planner() {
    return planner.get();
  }

  /** The port of a property value. */
  public static SinkPort of(String label) {
    for (SinkPort port : values()) {
      if (port.label.equals(label)) {
        return port;
      }
    }
    throw new IllegalArgumentException("unknown port: " + label);
  }
}

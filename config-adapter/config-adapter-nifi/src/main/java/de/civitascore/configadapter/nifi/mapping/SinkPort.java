/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The write logic of a FROST sink, as the Dataset configuration declares it.
 *
 * <p>A port defines three items: the data model it expects, the write operation it applies and the
 * references it resolves. The modeller selects it. Before the port existed the Mapping decided the
 * behaviour, and the rules were only in the validation code of the sink.
 *
 * <p>The closed set is the whitelist for the value that reaches the processor property. An unknown
 * port is rejected at plan time rather than passed through.
 *
 * <p>It lives beside the SensorThings vocabulary rather than with the sink, because that is the
 * direction the packages already run: the sink reads the mapping's types, and the mapping reads
 * none of the sink's.
 */
public enum SinkPort {
  THINGS("Things", List.of("$.properties.reference")),

  OBSERVATIONS(
      "Observations",
      List.of("$.parameters.thingReference", "$.parameters.datastreamReference", "$.result")),

  THING_TREE("ThingTree", List.of("$.properties.reference"));

  private final String label;
  private final List<String> requiredTargets;

  SinkPort(String label, List<String> requiredTargets) {
    this.label = label;
    this.requiredTargets = List.copyOf(requiredTargets);
  }

  /** The value of the processor property and of the Dataset configuration. */
  public String label() {
    return label;
  }

  /**
   * The target paths a Mapping into this port must write. They are the references the port resolves
   * — without them the lookup matches nothing and the write creates an entity under an empty key.
   */
  public List<String> requiredTargets() {
    return requiredTargets;
  }

  /** The port of a configured value, or empty when the value names none. */
  public static Optional<SinkPort> fromLabel(String label) {
    return Arrays.stream(values()).filter(port -> port.label.equals(label)).findFirst();
  }

  /** The labels of every port, for a message that offers the choice. */
  public static String labels() {
    return String.join(", ", Arrays.stream(values()).map(SinkPort::label).toList());
  }
}

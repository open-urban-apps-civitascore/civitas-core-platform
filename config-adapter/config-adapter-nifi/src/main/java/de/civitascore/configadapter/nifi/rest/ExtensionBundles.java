/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pins the processors of the platform's own NiFi extensions to the bundle version NiFi carries.
 *
 * <p>The flow fragments name a bundle version, and NiFi loads a processor only from exactly that
 * bundle; a flow that names another one deploys as an invalid group. The NAR ships in an image of
 * its own and is versioned on its own, so the version the config-adapter was built with says
 * nothing about the one an operator installed. The installed one is asked for at deploy time.
 */
final class ExtensionBundles {

  /** The bundle group of the platform's own extensions. Third-party bundles are left alone. */
  static final String GROUP = "de.civitas-core";

  private ExtensionBundles() {}

  /** Whether the snapshot uses a processor of the platform's own extensions. */
  static boolean usesOwnBundles(JsonNode snapshot) {
    return !ownProcessors(snapshot.path("flowContents")).isEmpty();
  }

  /**
   * Sets every own processor of the snapshot to the version NiFi carries.
   *
   * @param snapshot the versioned flow snapshot, changed in place
   * @param processorTypes the answer of {@code GET /flow/processor-types}
   * @throws FatalAdapterException when NiFi carries no bundle for a processor, or several versions
   *     of it and none is the one the flow names
   */
  static void pin(JsonNode snapshot, JsonNode processorTypes) throws FatalAdapterException {
    Map<String, SortedSet<String>> installed = new TreeMap<>();
    for (JsonNode type : processorTypes.path("processorTypes")) {
      JsonNode bundle = type.path("bundle");
      if (GROUP.equals(bundle.path("group").asText())) {
        installed
            .computeIfAbsent(key(type.path("type").asText(), bundle), k -> new TreeSet<>())
            .add(bundle.path("version").asText());
      }
    }

    for (JsonNode processor : ownProcessors(snapshot.path("flowContents"))) {
      ObjectNode bundle = (ObjectNode) processor.path("bundle");
      String type = processor.path("type").asText();
      SortedSet<String> versions = installed.getOrDefault(key(type, bundle), new TreeSet<>());
      String wanted = bundle.path("version").asText();
      if (versions.contains(wanted)) {
        continue;
      }
      if (versions.size() != 1) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_FLOW_ERROR,
            versions.isEmpty()
                ? "NiFi carries no "
                    + bundle.path("artifact").asText()
                    + " with "
                    + type
                    + "; install the NAR of the platform's NiFi extensions"
                : "NiFi carries "
                    + bundle.path("artifact").asText()
                    + " in the versions "
                    + versions
                    + " and none is "
                    + wanted
                    + "; keep one of them installed");
      }
      bundle.put("version", versions.first());
    }
  }

  private static String key(String type, JsonNode bundle) {
    return type + "|" + bundle.path("artifact").asText();
  }

  /** The processors of the group and every group below it that come from an own bundle. */
  private static List<JsonNode> ownProcessors(JsonNode group) {
    List<JsonNode> found = new ArrayList<>();
    for (JsonNode processor : group.path("processors")) {
      if (GROUP.equals(processor.path("bundle").path("group").asText())) {
        found.add(processor);
      }
    }
    for (JsonNode child : group.path("processGroups")) {
      found.addAll(ownProcessors(child));
    }
    return found;
  }
}

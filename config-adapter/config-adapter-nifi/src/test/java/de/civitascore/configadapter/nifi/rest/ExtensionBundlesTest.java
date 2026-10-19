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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import org.junit.jupiter.api.Test;

class ExtensionBundlesTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PUT = "de.civitascore.nifi.frost.PutFrostRecord";

  private static JsonNode snapshot(String putVersion) throws Exception {
    return MAPPER.readTree(
        """
        {"flowContents": {
          "processors": [
            {"type": "org.apache.nifi.processors.standard.SplitJson",
             "bundle": {"group": "org.apache.nifi", "artifact": "nifi-standard-nar", "version": "2.9.0"}}
          ],
          "processGroups": [
            {"processors": [
              {"type": "%s",
               "bundle": {"group": "de.civitas-core", "artifact": "nifi-frost-nar", "version": "%s"}}
            ]}
          ]
        }}
        """
            .formatted(PUT, putVersion));
  }

  private static JsonNode installed(String... versions) throws Exception {
    StringBuilder types = new StringBuilder();
    for (String version : versions) {
      if (!types.isEmpty()) {
        types.append(',');
      }
      types.append(
          """
          {"type": "%s", "bundle": {"group": "de.civitas-core", "artifact": "nifi-frost-nar", "version": "%s"}}
          """
              .formatted(PUT, version));
    }
    return MAPPER.readTree("{\"processorTypes\": [" + types + "]}");
  }

  private static String putVersion(JsonNode snapshot) {
    return snapshot
        .path("flowContents")
        .path("processGroups")
        .get(0)
        .path("processors")
        .get(0)
        .path("bundle")
        .path("version")
        .asText();
  }

  @Test
  void pin_takesTheVersionNifiCarries() throws Exception {
    // The fragment names the version the adapter was built with; the NAR image has its own.
    JsonNode snapshot = snapshot("1.0.0");

    ExtensionBundles.pin(snapshot, installed("1.0.0-MR-861-SNAPSHOT"));

    assertEquals("1.0.0-MR-861-SNAPSHOT", putVersion(snapshot));
  }

  @Test
  void pin_keepsTheVersionTheFlowNamesWhenNifiCarriesIt() throws Exception {
    JsonNode snapshot = snapshot("1.1.0");

    ExtensionBundles.pin(snapshot, installed("1.0.0", "1.1.0"));

    assertEquals("1.1.0", putVersion(snapshot));
  }

  @Test
  void pin_withoutTheNar_failsWithAReasonAnOperatorCanActOn() throws Exception {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> ExtensionBundles.pin(snapshot("1.0.0"), installed()));

    assertTrue(ex.getMessage().contains("install the NAR"), ex.getMessage());
  }

  @Test
  void pin_withSeveralOtherVersions_refusesToGuess() throws Exception {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> ExtensionBundles.pin(snapshot("1.0.0"), installed("1.1.0", "1.2.0")));

    assertTrue(ex.getMessage().contains("[1.1.0, 1.2.0]"), ex.getMessage());
  }

  @Test
  void usesOwnBundles_isFalseForAFlowOfThirdPartyProcessorsOnly() throws Exception {
    JsonNode thirdParty =
        MAPPER.readTree(
            """
            {"flowContents": {"processors": [
              {"type": "x", "bundle": {"group": "org.apache.nifi", "artifact": "a", "version": "1"}}
            ]}}
            """);

    assertFalse(ExtensionBundles.usesOwnBundles(thirdParty));
    assertTrue(ExtensionBundles.usesOwnBundles(snapshot("1.0.0")));
  }
}

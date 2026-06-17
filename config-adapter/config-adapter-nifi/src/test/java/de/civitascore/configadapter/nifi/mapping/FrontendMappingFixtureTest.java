/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check against the real {@code mappingConfig} JSON produced by the frontend mapping
 * editor: it must parse and compile to NiFi {@code UpdateRecord} RecordPath properties.
 */
class FrontendMappingFixtureTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void realFrontendMappingCompilesToRecordPath() throws Exception {
    var root =
        mapper.readTree(
            getClass().getClassLoader().getResourceAsStream("fixtures/frontend-mapping.json"));

    MappingConfig mapping = new MappingConfigParser().parse(root);
    assertEquals(
        "urn:core:datastructure:8cc31216-5417-4d0a-abea-dde0659ce00d:501b78f7-f076-46e2-b5cb-748267a75e24",
        mapping.source());

    List<UpdateRecordProperty> props = new RecordPathCompiler().compile(mapping);
    Map<String, UpdateRecordProperty> byPath =
        props.stream().collect(Collectors.toMap(UpdateRecordProperty::recordPath, p -> p));

    // copy shorthand
    assertEquals("/name", byPath.get("/titel").value());
    assertEquals(ReplacementStrategy.RECORD_PATH_VALUE, byPath.get("/titel").strategy());

    // toString over a nested path
    assertEquals("toString(/klasse/offen)", byPath.get("/groups/boolean").value());

    // toDate with pattern
    assertEquals("toDate(/klasse/Stufe, 'yyyy-MM-dd')", byPath.get("/groups/datum").value());

    // concat of two source paths
    assertEquals("concat(/adresse, /klasse/Bezeichnung)", byPath.get("/adresse").value());

    // layout-only "positions" is ignored; exactly four target fields are produced
    assertEquals(4, props.size());
  }
}

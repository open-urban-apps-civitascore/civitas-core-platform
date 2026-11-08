/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.bpmn;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Cross-checks the semantic process definition against its BPMNDI diagram so the two can't drift
 * apart, e.g. after a cherry-pick that reintroduces stale diagram elements. XSD schema validation
 * (see {@link BpmnSchemaValidationTest}) already enforces document-wide id uniqueness, but {@code
 * bpmnElement}/{@code sourceElement}/{@code targetElement} are typed as plain {@code xsd:QName} in
 * the OMG schema, so it never checks that they actually resolve — that's what this test covers.
 */
class BpmnDiagramSyncTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/processes/dataset-create.bpmn",
        "/processes/dataset-update.bpmn",
        "/processes/dataset-delete.bpmn",
        "/processes/dataset-unrelease.bpmn"
      })
  void diagramMatchesProcessDefinition(String resource) throws Exception {
    Document doc = parse(resource);
    Element process = firstElementByLocalName(doc, "process");
    assertNotNull(process, resource + ": expected a <process> element");

    Set<String> flowNodeIds = new HashSet<>();
    Map<String, String[]> sequenceFlows = new HashMap<>(); // flowId -> [sourceRef, targetRef]
    for (Element child : directChildElements(process)) {
      String localName = child.getLocalName();
      String id = child.getAttribute("id");
      if (id.isEmpty()) {
        continue;
      }
      if ("sequenceFlow".equals(localName)) {
        sequenceFlows.put(
            id, new String[] {child.getAttribute("sourceRef"), child.getAttribute("targetRef")});
      } else {
        flowNodeIds.add(id);
      }
    }

    Element plane = firstElementByLocalName(doc, "BPMNPlane");
    assertNotNull(plane, resource + ": expected a bpmndi:BPMNPlane element");

    Map<String, String> shapesByBpmnElement = new HashMap<>(); // flowNodeId -> shapeId
    Map<String, String> shapeIdToBpmnElement = new HashMap<>(); // shapeId -> flowNodeId
    Map<String, Element> edgesByBpmnElement = new HashMap<>(); // sequenceFlowId -> edge element
    List<String> duplicateMappings = new ArrayList<>();

    for (Element child : directChildElements(plane)) {
      String localName = child.getLocalName();
      String bpmnElement = child.getAttribute("bpmnElement");
      if (bpmnElement.isEmpty()) {
        continue;
      }
      if ("BPMNShape".equals(localName)) {
        if (shapesByBpmnElement.put(bpmnElement, child.getAttribute("id")) != null) {
          duplicateMappings.add(
              resource + ": multiple BPMNShape elements reference '" + bpmnElement + "'");
        }
        shapeIdToBpmnElement.put(child.getAttribute("id"), bpmnElement);
      } else if ("BPMNEdge".equals(localName)) {
        if (edgesByBpmnElement.put(bpmnElement, child) != null) {
          duplicateMappings.add(
              resource + ": multiple BPMNEdge elements reference '" + bpmnElement + "'");
        }
      }
    }

    List<String> problems = new ArrayList<>(duplicateMappings);

    for (String flowNodeId : flowNodeIds) {
      if (!shapesByBpmnElement.containsKey(flowNodeId)) {
        problems.add(resource + ": flow node '" + flowNodeId + "' has no BPMNShape");
      }
    }
    for (String shapeTarget : shapesByBpmnElement.keySet()) {
      if (!flowNodeIds.contains(shapeTarget)) {
        problems.add(
            resource + ": BPMNShape references non-existent element '" + shapeTarget + "'");
      }
    }

    for (Map.Entry<String, String[]> flow : sequenceFlows.entrySet()) {
      String flowId = flow.getKey();
      Element edge = edgesByBpmnElement.get(flowId);
      if (edge == null) {
        problems.add(resource + ": sequenceFlow '" + flowId + "' has no BPMNEdge");
        continue;
      }
      // sourceElement/targetElement are optional in the OMG BPMNDI schema (derivable from the
      // sequenceFlow's own sourceRef/targetRef), so only check them when the diagram sets them.
      String expectedSourceRef = flow.getValue()[0];
      String expectedTargetRef = flow.getValue()[1];
      String sourceElementId = edge.getAttribute("sourceElement");
      String targetElementId = edge.getAttribute("targetElement");
      if (!sourceElementId.isEmpty()) {
        String actualSourceRef = shapeIdToBpmnElement.get(sourceElementId);
        if (!expectedSourceRef.equals(actualSourceRef)) {
          problems.add(
              resource
                  + ": BPMNEdge for '"
                  + flowId
                  + "' has sourceElement resolving to '"
                  + actualSourceRef
                  + "', expected '"
                  + expectedSourceRef
                  + "'");
        }
      }
      if (!targetElementId.isEmpty()) {
        String actualTargetRef = shapeIdToBpmnElement.get(targetElementId);
        if (!expectedTargetRef.equals(actualTargetRef)) {
          problems.add(
              resource
                  + ": BPMNEdge for '"
                  + flowId
                  + "' has targetElement resolving to '"
                  + actualTargetRef
                  + "', expected '"
                  + expectedTargetRef
                  + "'");
        }
      }
    }
    for (String edgeTarget : edgesByBpmnElement.keySet()) {
      if (!sequenceFlows.containsKey(edgeTarget)) {
        problems.add(
            resource + ": BPMNEdge references non-existent sequenceFlow '" + edgeTarget + "'");
      }
    }

    assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
  }

  private static Document parse(String resource) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    try (InputStream bpmn = BpmnDiagramSyncTest.class.getResourceAsStream(resource)) {
      assertNotNull(bpmn, "BPMN resource not found on classpath: " + resource);
      return builder.parse(bpmn);
    }
  }

  private static Element firstElementByLocalName(Document doc, String localName) {
    NodeList matches = doc.getElementsByTagNameNS("*", localName);
    return matches.getLength() > 0 ? (Element) matches.item(0) : null;
  }

  private static List<Element> directChildElements(Element parent) {
    List<Element> result = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element element) {
        result.add(element);
      }
    }
    return result;
  }
}

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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Validates the three saga BPMN files against the official OMG BPMN 2.0 XML Schema (bundled under
 * resources/schemas/bpmn). Catches structural spec violations that Flowable's looser internal
 * parser may overlook.
 */
class BpmnSchemaValidationTest {

  private static final String BPMN20_XSD = "/schemas/bpmn/BPMN20.xsd";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/processes/dataset-create.bpmn",
        "/processes/dataset-update.bpmn",
        "/processes/dataset-delete.bpmn",
        "/processes/dataset-unrelease.bpmn"
      })
  void validatesAgainstOmgBpmn20Schema(String resource) throws Exception {
    URL xsdUrl = getClass().getResource(BPMN20_XSD);
    assertNotNull(xsdUrl, "Expected bundled BPMN 2.0 XSD at classpath:" + BPMN20_XSD);

    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = factory.newSchema(xsdUrl);
    Validator validator = schema.newValidator();
    CollectingErrorHandler errors = new CollectingErrorHandler();
    validator.setErrorHandler(errors);

    try (InputStream bpmn = getClass().getResourceAsStream(resource)) {
      assertNotNull(bpmn, "BPMN resource not found on classpath: " + resource);
      validator.validate(new StreamSource(bpmn));
    }

    if (!errors.isEmpty()) {
      fail("BPMN schema validation failed for " + resource + ":\n" + errors.summary());
    }
  }

  private static final class CollectingErrorHandler implements ErrorHandler {
    private final StringBuilder buf = new StringBuilder();
    private int count;

    @Override
    public void warning(SAXParseException e) {
      record("WARNING", e);
    }

    @Override
    public void error(SAXParseException e) {
      record("ERROR", e);
    }

    @Override
    public void fatalError(SAXParseException e) {
      record("FATAL", e);
    }

    private void record(String level, SAXParseException e) {
      count++;
      buf.append("  [")
          .append(level)
          .append("] line ")
          .append(e.getLineNumber())
          .append(":")
          .append(e.getColumnNumber())
          .append(" — ")
          .append(e.getMessage())
          .append('\n');
    }

    boolean isEmpty() {
      return count == 0;
    }

    String summary() {
      return buf.toString();
    }
  }
}

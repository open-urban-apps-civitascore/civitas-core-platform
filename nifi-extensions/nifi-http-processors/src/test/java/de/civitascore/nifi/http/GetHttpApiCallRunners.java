/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.ssl.SSLContextProvider;
import org.apache.nifi.util.LogMessage;
import org.apache.nifi.util.MockComponentLog;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.apache.nifi.web.client.provider.service.StandardWebClientServiceProvider;

/** The test runner of the processor and what the tests read from it. */
final class GetHttpApiCallRunners {

  private static final List<Relationship> RELATIONSHIPS =
      List.of(GetHttpApiCall.SUCCESS, GetHttpApiCall.STOP, GetHttpApiCall.FAILURE);

  static final String STATIONS = "{\"name\":\"Stations\",\"path\":\"/stations\"}";

  static final String STATION_SCHEMA =
      """
      {"$schema": "https://json-schema.org/draft/2020-12/schema",
       "type": "object",
       "required": ["stationid"],
       "properties": {"stationid": {"type": "string"},
                      "name": {"type": "string", "maxLength": 20},
                      "lat": {"type": "number"}}}""";

  private static final String WEB_CLIENT = "web-client";
  private static final String TLS = "tls";

  private GetHttpApiCallRunners() {}

  /**
   * A runner of the processor for the API call {@link #STATIONS}, with a web client service that
   * follows no redirect.
   *
   * @param tls the SSL Context Service of the web client service, or null for none
   */
  static TestRunner runner(GetHttpApiCall processor, String baseUrl, SSLContextProvider tls)
      throws InitializationException {
    TestRunner runner = TestRunners.newTestRunner(processor);
    StandardWebClientServiceProvider webClient = new StandardWebClientServiceProvider();
    runner.addControllerService(WEB_CLIENT, webClient);
    runner.setProperty(webClient, "Redirect Handling Strategy", "IGNORED");
    if (tls != null) {
      runner.addControllerService(TLS, tls);
      runner.enableControllerService(tls);
      runner.setProperty(webClient, "SSL Context Service", TLS);
    }
    runner.enableControllerService(webClient);

    runner.setProperty(GetHttpApiCall.WEB_CLIENT_SERVICE, WEB_CLIENT);
    runner.setProperty(GetHttpApiCall.BASE_URL, baseUrl);
    runner.setProperty(GetHttpApiCall.API_CALL, STATIONS);
    runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, STATION_SCHEMA);
    return runner;
  }

  static Collection<ValidationResult> invalid(TestRunner runner) {
    return runner.validate().stream().filter(result -> !result.isValid()).toList();
  }

  /** Asserts the one empty FlowFile of a failed run of {@link #STATIONS}. */
  static void assertRouted(
      TestRunner runner, Relationship relationship, int status, String message) {
    assertEquals(List.of(relationship.getName() + ": " + message), routed(runner));
    MockFlowFile failed = runner.getFlowFilesForRelationship(relationship).get(0);
    failed.assertAttributeEquals(GetHttpApiCall.REQUEST_PATH, "/stations");
    failed.assertAttributeEquals(GetHttpApiCall.ERROR_STATUS, Integer.toString(status));
    failed.assertContentEquals("");
  }

  /** The relationship and the error message of each FlowFile that the runs transferred. */
  private static List<String> routed(TestRunner runner) {
    List<String> routed = new ArrayList<>();
    for (Relationship relationship : RELATIONSHIPS) {
      for (MockFlowFile flowFile : runner.getFlowFilesForRelationship(relationship)) {
        routed.add(
            relationship.getName() + ": " + flowFile.getAttribute(GetHttpApiCall.ERROR_MESSAGE));
      }
    }
    return routed;
  }

  /** Every log message with its arguments and exceptions, as a bulletin would show it. */
  static List<String> logged(MockComponentLog log) {
    List<LogMessage> messages = new ArrayList<>();
    messages.addAll(log.getTraceMessages());
    messages.addAll(log.getDebugMessages());
    messages.addAll(log.getInfoMessages());
    messages.addAll(log.getWarnMessages());
    messages.addAll(log.getErrorMessages());
    List<String> texts = new ArrayList<>();
    for (LogMessage message : messages) {
      texts.add(message.toString());
      texts.add(String.valueOf(message.getThrowable()));
      for (Object argument : message.getArgs() == null ? new Object[0] : message.getArgs()) {
        texts.add(String.valueOf(argument));
      }
    }
    return texts;
  }

  /** What the log and the FlowFiles of each relationship show, as {@link #logged} does. */
  static List<String> visible(TestRunner runner) {
    List<String> visible = new ArrayList<>(logged(runner.getLogger()));
    for (Relationship relationship : RELATIONSHIPS) {
      for (MockFlowFile flowFile : runner.getFlowFilesForRelationship(relationship)) {
        visible.addAll(flowFile.getAttributes().values());
      }
    }
    assertTrue(visible.size() > 0, "nothing is visible");
    return visible;
  }
}

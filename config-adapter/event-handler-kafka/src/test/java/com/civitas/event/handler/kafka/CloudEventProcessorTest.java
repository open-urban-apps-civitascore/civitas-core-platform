/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.event.handler.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.model.ConfigEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.v1.CloudEventBuilder;
import java.net.URI;
import org.junit.jupiter.api.Test;

class CloudEventProcessorTest {

  @Test
  void handleEventShouldProcessValidCloudEvent() throws Exception {
    ConfigAdapter mockAdapter = mock(ConfigAdapter.class);
    CloudEventProcessor processor = new CloudEventProcessor(mockAdapter);

    String validJson =
        """
        {
          "metadata": {
            "messageId": "test-msg-id",
            "timestamp": "2025-01-01T10:00:00Z",
            "source": "test-source",
            "correlationId": "test-corr-id",
            "resultTopic": "test.result"
          },
          "payload": {
            "targetComponent": "keycloak",
            "targetResource": "realms/test",
            "operation": "CREATE",
            "config": {
              "path": null,
              "value": {"enabled": true}
            }
          }
        }
        """;

    CloudEvent cloudEvent =
        new CloudEventBuilder()
            .withId("test-id")
            .withType("com.civitas.config.v1")
            .withSource(URI.create("/test"))
            .withData("application/json", validJson.getBytes())
            .build();

    processor.handleEvent("test.topic", cloudEvent);

    verify(mockAdapter).processConfigEvent(eq("test.topic"), any(ConfigEvent.class));
  }

  @Test
  void handleEventShouldSkipCloudEventWithNullData() throws Exception {
    ConfigAdapter mockAdapter = mock(ConfigAdapter.class);
    CloudEventProcessor processor = new CloudEventProcessor(mockAdapter);

    CloudEvent cloudEvent =
        new CloudEventBuilder()
            .withId("test-id")
            .withType("com.civitas.config.v1")
            .withSource(URI.create("/test"))
            .build();

    processor.handleEvent("test.topic", cloudEvent);

    verify(mockAdapter, never()).processConfigEvent(anyString(), any(ConfigEvent.class));
  }

  @Test
  void handleEventShouldThrowExceptionForInvalidJson() {
    ConfigAdapter mockAdapter = mock(ConfigAdapter.class);
    CloudEventProcessor processor = new CloudEventProcessor(mockAdapter);

    String invalidJson = "{invalid json}";

    CloudEvent cloudEvent =
        new CloudEventBuilder()
            .withId("test-id")
            .withType("com.civitas.config.v1")
            .withSource(URI.create("/test"))
            .withData("application/json", invalidJson.getBytes())
            .build();

    assertThrows(Exception.class, () -> processor.handleEvent("test.topic", cloudEvent));

    verify(mockAdapter, never()).processConfigEvent(anyString(), any(ConfigEvent.class));
  }

  @Test
  void handleEventShouldThrowExceptionForEmptyData() {
    ConfigAdapter mockAdapter = mock(ConfigAdapter.class);
    CloudEventProcessor processor = new CloudEventProcessor(mockAdapter);

    CloudEvent cloudEvent =
        new CloudEventBuilder()
            .withId("test-id")
            .withType("com.civitas.config.v1")
            .withSource(URI.create("/test"))
            .withData("application/json", "".getBytes())
            .build();

    assertThrows(Exception.class, () -> processor.handleEvent("test.topic", cloudEvent));

    verify(mockAdapter, never()).processConfigEvent(anyString(), any(ConfigEvent.class));
  }

  @Test
  void handleEventShouldThrowExceptionForDataDeserializationError() {
    ConfigAdapter mockAdapter = mock(ConfigAdapter.class);
    CloudEventProcessor processor = new CloudEventProcessor(mockAdapter);

    CloudEventData mockData = mock(CloudEventData.class);
    when(mockData.toBytes()).thenThrow(new RuntimeException("Data conversion error"));

    CloudEvent mockCloudEvent = mock(CloudEvent.class);
    when(mockCloudEvent.getId()).thenReturn("test-id");
    when(mockCloudEvent.getType()).thenReturn("test-type");
    when(mockCloudEvent.getSource()).thenReturn(URI.create("/test"));
    when(mockCloudEvent.getData()).thenReturn(mockData);

    assertThrows(Exception.class, () -> processor.handleEvent("test.topic", mockCloudEvent));

    verify(mockAdapter, never()).processConfigEvent(anyString(), any(ConfigEvent.class));
  }

  @Test
  void handleEventShouldThrowExceptionForProcessingError() {
    ConfigAdapter mockAdapter = mock(ConfigAdapter.class);
    CloudEventProcessor processor = new CloudEventProcessor(mockAdapter);

    String validJson =
        """
        {
          "metadata": {
            "messageId": "test-msg-id",
            "timestamp": "2025-01-01T10:00:00Z",
            "source": "test-source",
            "correlationId": "test-corr-id",
            "resultTopic": "test.result"
          },
          "payload": {
            "targetComponent": "keycloak",
            "targetResource": "realms/test",
            "operation": "CREATE",
            "config": {
              "path": null,
              "value": {"enabled": true}
            }
          }
        }
        """;

    CloudEvent cloudEvent =
        new CloudEventBuilder()
            .withId("test-id")
            .withType("com.civitas.config.v1")
            .withSource(URI.create("/test"))
            .withData("application/json", validJson.getBytes())
            .build();

    doThrow(new RuntimeException("Processing error"))
        .when(mockAdapter)
        .processConfigEvent(anyString(), any(ConfigEvent.class));

    assertThrows(Exception.class, () -> processor.handleEvent("test.topic", cloudEvent));

    verify(mockAdapter).processConfigEvent(eq("test.topic"), any(ConfigEvent.class));
  }
}

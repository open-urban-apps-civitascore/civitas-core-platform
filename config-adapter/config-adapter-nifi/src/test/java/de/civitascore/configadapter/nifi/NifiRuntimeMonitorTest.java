/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import de.civitascore.configadapter.nifi.rest.NifiRestClient.ManagedProcessGroup;
import de.civitascore.configadapter.nifi.rest.NifiRestClient.RuntimeStatus;
import de.civitascore.configadapter.nifi.rest.NifiRestClient.RuntimeStatus.State;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NifiRuntimeMonitorTest {

  private static final String PIPELINE_ID = "0b6f7d8e-5c4b-4a3f-9e2d-1c0b9a8f7e6d";
  private static final Instant BULLETIN_TIME = Instant.parse("2026-10-20T08:00:00Z");
  private static final String CAUSE =
      "API call \"Get stations\": GET /stations failed with status 404";
  private static final RuntimeStatus STOPPED =
      new RuntimeStatus(State.STOPPED, CAUSE, "", BULLETIN_TIME);
  private static final RuntimeStatus FAILING =
      new RuntimeStatus(
          State.ERROR, "Pipeline record dropped", "Pipeline record dropped", BULLETIN_TIME);

  private final NifiRestClient client = mock(NifiRestClient.class);
  private final JsonNode bulletins = new ObjectMapper().createArrayNode();
  private final List<Map<String, Object>> published = new ArrayList<>();
  private NifiRuntimeMonitor monitor;

  @BeforeEach
  void setUp() throws Exception {
    when(client.readBulletins()).thenReturn(bulletins);
    // The stopped group stays in NiFi, so discovery keeps finding it.
    when(client.listManagedProcessGroups())
        .thenReturn(List.of(new ManagedProcessGroup(PIPELINE_ID, "pg-1")));
    // The scheduled poll must not run during a test; each test polls by hand.
    monitor = new NifiRuntimeMonitor(client, Duration.ofHours(1).toMillis());
    monitor.setPublisher(published::add);
    monitor.register(PIPELINE_ID, "ds-1", "pg-1");
  }

  @AfterEach
  void tearDown() {
    monitor.close();
  }

  @Test
  void aBulletinOfTheStopSinkStopsTheGroupAndPublishesStoppedWithTheCause() throws Exception {
    when(client.readRuntimeStatus("pg-1", bulletins)).thenReturn(STOPPED);

    monitor.poll();

    verify(client).stopProcessGroup("pg-1");
    assertEquals(1, published.size());
    Map<String, Object> event = published.getFirst();
    assertEquals("PIPELINE_STATUS_CHANGED", event.get("type"));
    assertEquals("STOPPED", event.get("status"));
    assertEquals("RUNTIME", event.get("source"));
    assertEquals(PIPELINE_ID, event.get("pipelineId"));
    assertEquals("ds-1", event.get("datasetId"));
    assertEquals(CAUSE, event.get("message"));
    assertEquals("", event.get("stacktrace"));
    assertEquals(BULLETIN_TIME.toString(), event.get("occurredAt"));
  }

  @Test
  void aStopAfterAnErrorIsStillPublished() throws Exception {
    when(client.readRuntimeStatus("pg-1", bulletins)).thenReturn(FAILING, STOPPED);

    monitor.poll();
    monitor.poll();

    assertEquals(
        List.of("ERROR", "STOPPED"), published.stream().map(e -> e.get("status")).toList());
  }

  @Test
  void aStoppedPipelineIsNoLongerInspected() throws Exception {
    when(client.readRuntimeStatus("pg-1", bulletins)).thenReturn(STOPPED, FAILING);

    monitor.poll();
    monitor.poll();
    monitor.poll();

    verify(client, times(1)).readRuntimeStatus(anyString(), any());
    assertEquals(1, published.size());
  }

  @Test
  void aStoppedPipelineIsWatchedAgainAfterADeploymentRegistersIt() throws Exception {
    when(client.readRuntimeStatus("pg-1", bulletins)).thenReturn(STOPPED);
    when(client.readRuntimeStatus("pg-2", bulletins)).thenReturn(FAILING);
    monitor.poll();

    when(client.listManagedProcessGroups())
        .thenReturn(List.of(new ManagedProcessGroup(PIPELINE_ID, "pg-2")));
    monitor.register(PIPELINE_ID, "ds-1", "pg-2");
    monitor.poll();

    assertEquals(
        List.of("STOPPED", "ERROR"), published.stream().map(e -> e.get("status")).toList());
  }

  @Test
  void aFailedStopCallPublishesNothingAndTheNextPollTriesAgain() throws Exception {
    when(client.readRuntimeStatus("pg-1", bulletins)).thenReturn(STOPPED);
    doThrow(new RetryableAdapterException(AdapterErrorCode.NIFI_ERROR, "did not stop in time"))
        .doNothing()
        .when(client)
        .stopProcessGroup("pg-1");

    monitor.poll();
    assertTrue(published.isEmpty());

    monitor.poll();

    verify(client, times(2)).stopProcessGroup("pg-1");
    assertEquals(1, published.size());
    assertEquals("STOPPED", published.getFirst().get("status"));
  }

  @Test
  void anErrorOfAnotherProcessorPublishesErrorAndStopsNothing() throws Exception {
    when(client.readRuntimeStatus("pg-1", bulletins)).thenReturn(FAILING);

    monitor.poll();

    verify(client, never()).stopProcessGroup(anyString());
    assertEquals(1, published.size());
    assertEquals("ERROR", published.getFirst().get("status"));
  }
}

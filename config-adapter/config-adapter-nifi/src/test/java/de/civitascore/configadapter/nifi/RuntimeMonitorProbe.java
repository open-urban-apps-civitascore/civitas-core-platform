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

import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link NifiRuntimeMonitor} for the NiFi integration tests of other packages, which poll it by
 * hand and read the events it published.
 */
public final class RuntimeMonitorProbe implements AutoCloseable {

  private final NifiRuntimeMonitor monitor;
  private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

  public RuntimeMonitorProbe(NifiRestClient client) {
    monitor = new NifiRuntimeMonitor(client, Duration.ofHours(1).toMillis());
    monitor.setPublisher(events::add);
  }

  public void register(String pipelineId, String datasetId, String processGroupId) {
    monitor.register(pipelineId, datasetId, processGroupId);
  }

  /** Polls NiFi once and returns every event published so far for the Pipeline. */
  public List<Map<String, Object>> poll(String pipelineId) {
    monitor.poll();
    return events.stream().filter(event -> pipelineId.equals(event.get("pipelineId"))).toList();
  }

  @Override
  public void close() {
    monitor.close();
  }
}

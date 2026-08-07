/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.GroupConfig;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeycloakAdapterTest {

  @Test
  void updateExistingResource_whenResourceIdMissing_shouldRejectBeforeCallingHandler()
      throws Exception {
    KeycloakAdapter adapter = new KeycloakAdapter();
    CapturingHandler handler = configureHandler(adapter, ResourceType.GROUP);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () ->
                adapter.doProcessConfigEvent(
                    Topics.GROUP_UPDATED.toString(), groupUpdateEventWithoutId()));

    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
    assertEquals(0, handler.updateCalls);
  }

  private CapturingHandler configureHandler(KeycloakAdapter adapter, ResourceType type)
      throws Exception {
    CapturingHandler handler = new CapturingHandler();
    Field handlers = KeycloakAdapter.class.getDeclaredField("handlers");
    handlers.setAccessible(true);
    handlers.set(adapter, Map.of(type, handler));
    return handler;
  }

  private ConfigEvent groupUpdateEventWithoutId() {
    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName("Local Data Consumers");
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "test.source",
            UUID.randomUUID().toString(),
            "1.0",
            "result.topic");
    Payload payload =
        new Payload("group", "civitas-core", Operation.UPDATE, new Config("/groups", groupConfig));
    return new ConfigEvent(metadata, payload);
  }

  private static class CapturingHandler implements KeycloakResourceHandler {
    private int updateCalls;

    @Override
    public void create(String realm, ConfigEvent event)
        throws FatalAdapterException, RetryableAdapterException {}

    @Override
    public void update(String realm, String resourceId, ConfigEvent event)
        throws FatalAdapterException, RetryableAdapterException {
      updateCalls++;
    }

    @Override
    public void delete(String realm, String resourceId, ConfigEvent event)
        throws FatalAdapterException, RetryableAdapterException {}
  }
}

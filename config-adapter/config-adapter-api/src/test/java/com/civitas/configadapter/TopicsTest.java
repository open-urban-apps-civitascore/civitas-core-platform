/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for Topics enum */
class TopicsTest {

  @Test
  void getValue_whenBackendTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.api.backend.created", Topics.BACKEND_CREATED.getValue());
    assertEquals("core.civitas.api.backend.updated", Topics.BACKEND_UPDATED.getValue());
    assertEquals("core.civitas.api.backend.deleted", Topics.BACKEND_DELETED.getValue());
  }

  @Test
  void toString_whenCalled_shouldReturnValue() {
    assertEquals("core.civitas.api.backend.created", Topics.BACKEND_CREATED.toString());
    assertEquals("core.civitas.api.route.created", Topics.ROUTE_CREATED.toString());
  }

  @Test
  void getValue_whenRouteTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.api.route.created", Topics.ROUTE_CREATED.getValue());
    assertEquals("core.civitas.api.route.updated", Topics.ROUTE_UPDATED.getValue());
    assertEquals("core.civitas.api.route.deleted", Topics.ROUTE_DELETED.getValue());
  }

  @Test
  void getValue_whenUserTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.idm.user.created", Topics.USER_CREATED.getValue());
    assertEquals("core.civitas.idm.user.updated", Topics.USER_UPDATED.getValue());
    assertEquals("core.civitas.idm.user.deleted", Topics.USER_DELETED.getValue());
    assertEquals("core.civitas.idm.user.locked", Topics.USER_LOCKED.getValue());
    assertEquals("core.civitas.idm.user.unlocked", Topics.USER_UNLOCKED.getValue());
    assertEquals("core.civitas.idm.user.password.changed", Topics.USER_PASSWORD_CHANGED.getValue());
    assertEquals("core.civitas.idm.user.password.reset", Topics.USER_PASSWORD_RESET.getValue());
  }

  @Test
  void getValue_whenRealmTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.idm.realm.created", Topics.REALM_CREATED.getValue());
    assertEquals("core.civitas.idm.realm.updated", Topics.REALM_UPDATED.getValue());
    assertEquals("core.civitas.idm.realm.deleted", Topics.REALM_DELETED.getValue());
  }

  @Test
  void getValue_whenClientTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.idm.client.created", Topics.CLIENT_CREATED.getValue());
    assertEquals("core.civitas.idm.client.updated", Topics.CLIENT_UPDATED.getValue());
    assertEquals("core.civitas.idm.client.deleted", Topics.CLIENT_DELETED.getValue());
  }

  @Test
  void getValue_whenGroupTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.idm.group.created", Topics.GROUP_CREATED.getValue());
    assertEquals("core.civitas.idm.group.updated", Topics.GROUP_UPDATED.getValue());
    assertEquals("core.civitas.idm.group.deleted", Topics.GROUP_DELETED.getValue());
  }

  @Test
  void getValue_whenRoleTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.idm.role.created", Topics.ROLE_CREATED.getValue());
    assertEquals("core.civitas.idm.role.updated", Topics.ROLE_UPDATED.getValue());
    assertEquals("core.civitas.idm.role.deleted", Topics.ROLE_DELETED.getValue());
  }

  @Test
  void getValue_whenThingTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.data.thing.created", Topics.THING_CREATED.getValue());
    assertEquals("core.civitas.data.thing.updated", Topics.THING_UPDATED.getValue());
    assertEquals("core.civitas.data.thing.deleted", Topics.THING_DELETED.getValue());
  }

  @Test
  void getValue_whenLocationTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.data.location.created", Topics.LOCATION_CREATED.getValue());
    assertEquals("core.civitas.data.location.updated", Topics.LOCATION_UPDATED.getValue());
    assertEquals("core.civitas.data.location.deleted", Topics.LOCATION_DELETED.getValue());
  }

  @Test
  void getValue_whenSensorTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.data.sensor.created", Topics.SENSOR_CREATED.getValue());
    assertEquals("core.civitas.data.sensor.updated", Topics.SENSOR_UPDATED.getValue());
    assertEquals("core.civitas.data.sensor.deleted", Topics.SENSOR_DELETED.getValue());
  }

  @Test
  void getValue_whenObservedPropertyTopics_shouldReturnCorrectValues() {
    assertEquals(
        "core.civitas.data.observedproperty.created", Topics.OBSERVED_PROPERTY_CREATED.getValue());
    assertEquals(
        "core.civitas.data.observedproperty.updated", Topics.OBSERVED_PROPERTY_UPDATED.getValue());
    assertEquals(
        "core.civitas.data.observedproperty.deleted", Topics.OBSERVED_PROPERTY_DELETED.getValue());
  }

  @Test
  void getValue_whenDatastreamTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.data.datastream.created", Topics.DATASTREAM_CREATED.getValue());
    assertEquals("core.civitas.data.datastream.updated", Topics.DATASTREAM_UPDATED.getValue());
    assertEquals("core.civitas.data.datastream.deleted", Topics.DATASTREAM_DELETED.getValue());
  }

  @Test
  void getValue_whenFrostProjectTopics_shouldReturnCorrectValues() {
    assertEquals("core.civitas.data.project.created", Topics.FROST_PROJECT_CREATED.getValue());
    assertEquals("core.civitas.data.project.updated", Topics.FROST_PROJECT_UPDATED.getValue());
    assertEquals("core.civitas.data.project.deleted", Topics.FROST_PROJECT_DELETED.getValue());
  }

  @Test
  void isValidTopic_whenValidTopic_shouldReturnTrue() {
    assertTrue(Topics.isValidTopic("core.civitas.api.backend.created"));
    assertTrue(Topics.isValidTopic("core.civitas.api.route.created"));
    assertTrue(Topics.isValidTopic("core.civitas.idm.user.created"));
  }

  @Test
  void isValidTopic_whenUpperCase_shouldReturnTrue() {
    assertTrue(Topics.isValidTopic("CORE.CIVITAS.API.BACKEND.CREATED"));
    assertTrue(Topics.isValidTopic("Core.Civitas.Api.Route.Updated"));
  }

  @Test
  void isValidTopic_whenWhitespace_shouldReturnTrue() {
    assertTrue(Topics.isValidTopic("  core.civitas.api.backend.created  "));
    assertTrue(Topics.isValidTopic("\tcore.civitas.api.route.deleted\n"));
  }

  @Test
  void isValidTopic_whenInvalidTopic_shouldReturnFalse() {
    assertFalse(Topics.isValidTopic("invalid.topic"));
    assertFalse(Topics.isValidTopic("core.civitas.api.unknown"));
    assertFalse(Topics.isValidTopic(""));
  }

  @Test
  void isValidTopic_whenNull_shouldReturnFalse() {
    assertFalse(Topics.isValidTopic(null));
  }

  @Test
  void fromString_whenValidTopic_shouldReturnTopic() {
    Optional<Topics> topic = Topics.fromString("core.civitas.api.backend.created");
    assertTrue(topic.isPresent());
    assertEquals(Topics.BACKEND_CREATED, topic.get());
  }

  @Test
  void fromString_whenUpperCase_shouldReturnTopic() {
    Optional<Topics> topic = Topics.fromString("CORE.CIVITAS.API.ROUTE.CREATED");
    assertTrue(topic.isPresent());
    assertEquals(Topics.ROUTE_CREATED, topic.get());
  }

  @Test
  void fromString_whenWhitespace_shouldReturnTopic() {
    Optional<Topics> topic = Topics.fromString("  core.civitas.api.backend.updated  ");
    assertTrue(topic.isPresent());
    assertEquals(Topics.BACKEND_UPDATED, topic.get());
  }

  @Test
  void fromString_whenInvalidTopic_shouldReturnEmpty() {
    Optional<Topics> topic = Topics.fromString("invalid.topic");
    assertFalse(topic.isPresent());
  }

  @Test
  void fromString_whenNull_shouldReturnEmpty() {
    Optional<Topics> topic = Topics.fromString(null);
    assertFalse(topic.isPresent());
  }

  @Test
  void allTopics_whenAccessed_shouldNotBeNull() {
    assertNotNull(Topics.ALL_TOPICS);
    assertFalse(Topics.ALL_TOPICS.isEmpty());
    assertEquals(Topics.values().length, Topics.ALL_TOPICS.size());
  }

  @Test
  void allTopics_whenAccessed_shouldContainBackendTopics() {
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.api.backend.created"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.api.backend.updated"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.api.backend.deleted"));
  }

  @Test
  void allTopics_whenAccessed_shouldContainRouteTopics() {
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.api.route.created"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.api.route.updated"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.api.route.deleted"));
  }

  @Test
  void allTopics_whenAccessed_shouldContainFrostTopics() {
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.thing.created"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.location.updated"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.sensor.deleted"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.observedproperty.created"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.datastream.updated"));
  }

  @Test
  void allTopics_whenAccessed_shouldContainFrostProjectTopics() {
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.project.created"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.project.updated"));
    assertTrue(Topics.ALL_TOPICS.contains("core.civitas.data.project.deleted"));
  }

  @Test
  void values_whenCounted_shouldReturn43() {
    // User: 7, Realm: 3, Client: 3, Group: 3, Role: 3, Backend: 3, Route: 3,
    // Thing: 3, Location: 3, Sensor: 3, ObservedProperty: 3, Datastream: 3,
    // FROST Project: 3 = 43
    assertEquals(43, Topics.values().length);
  }
}

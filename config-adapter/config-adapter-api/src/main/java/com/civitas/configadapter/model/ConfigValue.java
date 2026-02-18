/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model;

import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.apisix.RouteConfigValue;
import com.civitas.configadapter.model.frost.FrostConfigValue;
import com.civitas.configadapter.model.idm.ClientConfig;
import com.civitas.configadapter.model.idm.GroupConfig;
import com.civitas.configadapter.model.idm.RealmConfig;
import com.civitas.configadapter.model.idm.RoleConfig;
import com.civitas.configadapter.model.idm.UserConfig;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for typed configuration values. Each adapter domain (IDM, APISIX, etc.) provides
 * its own implementation with domain-specific data.
 *
 * <p>This interface serves as a Jackson polymorphism marker ({@code @JsonTypeInfo}) that enables
 * type-safe deserialization of domain-specific configuration values. It intentionally declares no
 * methods — domain-specific behaviour (e.g. {@code toApiMap()}) belongs in the respective base
 * classes like {@link AbstractApiModel}.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RealmConfig.class, name = "realm"),
  @JsonSubTypes.Type(value = UserConfig.class, name = "user"),
  @JsonSubTypes.Type(value = ClientConfig.class, name = "client"),
  @JsonSubTypes.Type(value = RoleConfig.class, name = "role"),
  @JsonSubTypes.Type(value = GroupConfig.class, name = "group"),
  @JsonSubTypes.Type(value = ApisixConfigValue.class, name = "apisix-upstream"),
  @JsonSubTypes.Type(value = RouteConfigValue.class, name = "apisix-route"),
  @JsonSubTypes.Type(value = FrostConfigValue.class, name = "frost-thing"),
  @JsonSubTypes.Type(value = FrostConfigValue.class, name = "frost-location"),
  @JsonSubTypes.Type(value = FrostConfigValue.class, name = "frost-sensor"),
  @JsonSubTypes.Type(value = FrostConfigValue.class, name = "frost-observedproperty"),
  @JsonSubTypes.Type(value = FrostConfigValue.class, name = "frost-datastream"),
  @JsonSubTypes.Type(value = FrostConfigValue.class, name = "frost-project"),
})
public interface ConfigValue {}

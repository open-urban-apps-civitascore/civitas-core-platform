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
package com.civitas.configadapter.model;

import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.idm.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for typed configuration values. Each adapter domain (IDM, APISIX, etc.) provides
 * its own implementation with domain-specific data.
 *
 * <p>This interface enables type-safe configuration handling while maintaining flexibility for
 * different adapter types.
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
})
public interface ConfigValue {}

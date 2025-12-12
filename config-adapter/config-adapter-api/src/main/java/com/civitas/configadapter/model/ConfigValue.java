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
package com.civitas.configadapter.model;

import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.idm.ClientConfig;
import com.civitas.configadapter.model.idm.RealmConfig;
import com.civitas.configadapter.model.idm.RoleConfig;
import com.civitas.configadapter.model.idm.UserConfig;
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
  @JsonSubTypes.Type(value = ApisixConfigValue.class, name = "apisix-upstream"),
})
public interface ConfigValue {}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.civitascore.configadapter.model.apisix.ApisixConfigValue;
import de.civitascore.configadapter.model.apisix.RouteConfigValue;
import de.civitascore.configadapter.model.frost.FrostConfigValue;
import de.civitascore.configadapter.model.idm.ClientConfig;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.configadapter.model.idm.RealmConfig;
import de.civitascore.configadapter.model.idm.RoleConfig;
import de.civitascore.configadapter.model.idm.UserConfig;
import de.civitascore.configadapter.model.redpanda.PipelineConfigValue;

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
  @JsonSubTypes.Type(value = PipelineConfigValue.class, name = "redpanda-pipeline"),
})
public interface ConfigValue {}

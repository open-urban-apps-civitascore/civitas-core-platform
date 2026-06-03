/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.postgis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Configuration for a PostgreSQL schema.
 *
 * <p>CREATE renders {@code CREATE SCHEMA "name"}, optionally with {@code AUTHORIZATION "owner"}
 * when {@link #getOwner()} is set. UPDATE changes the owner ({@code ALTER SCHEMA ... OWNER TO}).
 * DELETE renders {@code DROP SCHEMA "name"} with {@code RESTRICT} by default, or {@code CASCADE}
 * when {@link #isCascade()} is {@code true}.
 *
 * <p>Example JSON:
 *
 * <pre>{@code
 * {
 *   "resourceType": "postgis-schema",
 *   "name": "iot",
 *   "owner": "iot_admin",
 *   "cascade": false
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SchemaConfig implements PostgisConfigValue {

  private String name;
  private String owner;
  private boolean cascade;

  public SchemaConfig() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public boolean isCascade() {
    return cascade;
  }

  public void setCascade(boolean cascade) {
    this.cascade = cascade;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    SchemaConfig that = (SchemaConfig) obj;
    return cascade == that.cascade
        && Objects.equals(this.name, that.name)
        && Objects.equals(this.owner, that.owner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, owner, cascade);
  }

  @Override
  public String toString() {
    return "SchemaConfig[name=" + name + ", owner=" + owner + ", cascade=" + cascade + ']';
  }
}

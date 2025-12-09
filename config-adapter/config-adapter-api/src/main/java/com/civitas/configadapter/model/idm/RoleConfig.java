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
package com.civitas.configadapter.model.idm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for IDM role resources. Represents a role that can be assigned to users or used in
 * authorization policies.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RoleConfig implements IdmConfigValue {

  private String id;
  private String name;
  private String description;
  private Boolean composite;
  private String clientRole;
  private String containerId;
  private Map<String, List<String>> attributes;

  /**
   * Set of realm role names to include in this composite role. This provides a simple interface for
   * developers - just list the role names you want to include. The adapter handles the complex
   * Keycloak Composites structure internally.
   *
   * <p>Example:
   *
   * <pre>
   * roleConfig.setCompositeRoles(Set.of("role1", "role2", "role3"));
   * </pre>
   */
  private Set<String> compositeRoles;

  public RoleConfig() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Boolean getComposite() {
    return composite;
  }

  public void setComposite(Boolean composite) {
    this.composite = composite;
  }

  public String getClientRole() {
    return clientRole;
  }

  public void setClientRole(String clientRole) {
    this.clientRole = clientRole;
  }

  public String getContainerId() {
    return containerId;
  }

  public void setContainerId(String containerId) {
    this.containerId = containerId;
  }

  public Map<String, List<String>> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, List<String>> attributes) {
    this.attributes = attributes;
  }

  public Set<String> getCompositeRoles() {
    return compositeRoles;
  }

  public void setCompositeRoles(Set<String> compositeRoles) {
    this.compositeRoles = compositeRoles;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (RoleConfig) obj;
    return Objects.equals(this.id, that.id)
        && Objects.equals(this.name, that.name)
        && Objects.equals(this.description, that.description)
        && Objects.equals(this.composite, that.composite)
        && Objects.equals(this.clientRole, that.clientRole)
        && Objects.equals(this.containerId, that.containerId)
        && Objects.equals(this.attributes, that.attributes)
        && Objects.equals(this.compositeRoles, that.compositeRoles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, name, description, composite, clientRole, containerId, attributes, compositeRoles);
  }

  @Override
  public String toString() {
    return "RoleConfig["
        + "name="
        + name
        + ", description="
        + description
        + ", composite="
        + composite
        + ']';
  }
}

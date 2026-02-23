/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.idm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for IDM group resources. Represents a group that can contain users and be assigned
 * roles. Groups support hierarchical structures through parent-child relationships.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GroupConfig implements IdmConfigValue {

  private String id;
  private String name;
  private String path;
  private String parentId;
  private Map<String, List<String>> attributes;

  /**
   * Set of realm role names to assign to this group. All users who are members of this group will
   * inherit these roles.
   *
   * <p>Example:
   *
   * <pre>
   * groupConfig.setRealmRoles(Set.of("admin", "user"));
   * </pre>
   */
  private Set<String> realmRoles;

  /**
   * Map of client IDs to lists of client role names to assign to this group. All users who are
   * members of this group will inherit these client roles.
   *
   * <p>Example:
   *
   * <pre>
   * groupConfig.setClientRoles(Map.of("my-client", List.of("client-admin")));
   * </pre>
   */
  private Map<String, List<String>> clientRoles;

  /**
   * Set of subgroup names to create under this group. The adapter will create these as child groups
   * in the group hierarchy.
   */
  private Set<String> subGroups;

  public GroupConfig() {}

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

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public Map<String, List<String>> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, List<String>> attributes) {
    this.attributes = attributes;
  }

  public Set<String> getRealmRoles() {
    return realmRoles;
  }

  public void setRealmRoles(Set<String> realmRoles) {
    this.realmRoles = realmRoles;
  }

  public Map<String, List<String>> getClientRoles() {
    return clientRoles;
  }

  public void setClientRoles(Map<String, List<String>> clientRoles) {
    this.clientRoles = clientRoles;
  }

  public Set<String> getSubGroups() {
    return subGroups;
  }

  public void setSubGroups(Set<String> subGroups) {
    this.subGroups = subGroups;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (GroupConfig) obj;
    return Objects.equals(this.id, that.id)
        && Objects.equals(this.name, that.name)
        && Objects.equals(this.path, that.path)
        && Objects.equals(this.parentId, that.parentId)
        && Objects.equals(this.attributes, that.attributes)
        && Objects.equals(this.realmRoles, that.realmRoles)
        && Objects.equals(this.clientRoles, that.clientRoles)
        && Objects.equals(this.subGroups, that.subGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, path, parentId, attributes, realmRoles, clientRoles, subGroups);
  }

  @Override
  public String toString() {
    return "GroupConfig["
        + "id="
        + id
        + ", name="
        + name
        + ", path="
        + path
        + ", parentId="
        + parentId
        + ']';
  }
}

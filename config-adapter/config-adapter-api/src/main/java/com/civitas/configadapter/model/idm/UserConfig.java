/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.idm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for IDM user resources. Represents a user in the identity management system with
 * their core properties and role assignments.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UserConfig implements IdmConfigValue {

  private String id;
  private String username;
  private String email;
  private String firstName;
  private String lastName;
  private Boolean enabled;
  private Boolean emailVerified;
  private List<String> realmRoles;
  private List<String> groups;
  private List<CredentialConfig> credentials;
  private Map<String, List<String>> attributes;
  private List<String> requiredActions;

  public UserConfig() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(Boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public List<String> getRealmRoles() {
    return realmRoles;
  }

  public void setRealmRoles(List<String> realmRoles) {
    this.realmRoles = realmRoles;
  }

  public List<String> getGroups() {
    return groups;
  }

  public void setGroups(List<String> groups) {
    this.groups = groups;
  }

  public List<CredentialConfig> getCredentials() {
    return credentials;
  }

  public void setCredentials(List<CredentialConfig> credentials) {
    this.credentials = credentials;
  }

  public Map<String, List<String>> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, List<String>> attributes) {
    this.attributes = attributes;
  }

  public List<String> getRequiredActions() {
    return requiredActions;
  }

  public void setRequiredActions(List<String> requiredActions) {
    this.requiredActions = requiredActions;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (UserConfig) obj;
    return Objects.equals(this.id, that.id)
        && Objects.equals(this.username, that.username)
        && Objects.equals(this.email, that.email)
        && Objects.equals(this.firstName, that.firstName)
        && Objects.equals(this.lastName, that.lastName)
        && Objects.equals(this.enabled, that.enabled)
        && Objects.equals(this.emailVerified, that.emailVerified)
        && Objects.equals(this.realmRoles, that.realmRoles)
        && Objects.equals(this.groups, that.groups)
        && Objects.equals(this.credentials, that.credentials)
        && Objects.equals(this.attributes, that.attributes)
        && Objects.equals(this.requiredActions, that.requiredActions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        username,
        email,
        firstName,
        lastName,
        enabled,
        emailVerified,
        realmRoles,
        groups,
        credentials,
        attributes,
        requiredActions);
  }

  @Override
  public String toString() {
    return "UserConfig["
        + "username="
        + username
        + ", email="
        + email
        + ", enabled="
        + enabled
        + ']';
  }

  /** Represents a credential configuration for a user (password, OTP, etc.). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class CredentialConfig {
    private String type;
    private String value;
    private Boolean temporary;

    public CredentialConfig() {}

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public Boolean getTemporary() {
      return temporary;
    }

    public void setTemporary(Boolean temporary) {
      this.temporary = temporary;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (CredentialConfig) obj;
      return Objects.equals(this.type, that.type)
          && Objects.equals(this.value, that.value)
          && Objects.equals(this.temporary, that.temporary);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, value, temporary);
    }

    @Override
    public String toString() {
      return "CredentialConfig[" + "type=" + type + ", temporary=" + temporary + ']';
    }
  }
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.idm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for IDM realm resources. Represents a Keycloak realm with its core properties.
 *
 * <p>A realm manages a set of users, credentials, roles, and groups. A user belongs to and logs
 * into a realm.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RealmConfig implements IdmConfigValue {

  private String realm;
  private String displayName;
  private Boolean enabled;
  private Integer accessTokenLifespan;
  private Integer ssoSessionIdleTimeout;
  private Integer ssoSessionMaxLifespan;
  private Boolean registrationAllowed;
  private Boolean resetPasswordAllowed;
  private Boolean rememberMe;
  private Boolean verifyEmail;
  private Boolean loginWithEmailAllowed;
  private Boolean duplicateEmailsAllowed;
  private String defaultSignatureAlgorithm;
  private Map<String, String> attributes;

  public RealmConfig() {}

  public String getRealm() {
    return realm;
  }

  public void setRealm(String realm) {
    this.realm = realm;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Integer getAccessTokenLifespan() {
    return accessTokenLifespan;
  }

  public void setAccessTokenLifespan(Integer accessTokenLifespan) {
    this.accessTokenLifespan = accessTokenLifespan;
  }

  public Integer getSsoSessionIdleTimeout() {
    return ssoSessionIdleTimeout;
  }

  public void setSsoSessionIdleTimeout(Integer ssoSessionIdleTimeout) {
    this.ssoSessionIdleTimeout = ssoSessionIdleTimeout;
  }

  public Integer getSsoSessionMaxLifespan() {
    return ssoSessionMaxLifespan;
  }

  public void setSsoSessionMaxLifespan(Integer ssoSessionMaxLifespan) {
    this.ssoSessionMaxLifespan = ssoSessionMaxLifespan;
  }

  public Boolean getRegistrationAllowed() {
    return registrationAllowed;
  }

  public void setRegistrationAllowed(Boolean registrationAllowed) {
    this.registrationAllowed = registrationAllowed;
  }

  public Boolean getResetPasswordAllowed() {
    return resetPasswordAllowed;
  }

  public void setResetPasswordAllowed(Boolean resetPasswordAllowed) {
    this.resetPasswordAllowed = resetPasswordAllowed;
  }

  public Boolean getRememberMe() {
    return rememberMe;
  }

  public void setRememberMe(Boolean rememberMe) {
    this.rememberMe = rememberMe;
  }

  public Boolean getVerifyEmail() {
    return verifyEmail;
  }

  public void setVerifyEmail(Boolean verifyEmail) {
    this.verifyEmail = verifyEmail;
  }

  public Boolean getLoginWithEmailAllowed() {
    return loginWithEmailAllowed;
  }

  public void setLoginWithEmailAllowed(Boolean loginWithEmailAllowed) {
    this.loginWithEmailAllowed = loginWithEmailAllowed;
  }

  public Boolean getDuplicateEmailsAllowed() {
    return duplicateEmailsAllowed;
  }

  public void setDuplicateEmailsAllowed(Boolean duplicateEmailsAllowed) {
    this.duplicateEmailsAllowed = duplicateEmailsAllowed;
  }

  public String getDefaultSignatureAlgorithm() {
    return defaultSignatureAlgorithm;
  }

  public void setDefaultSignatureAlgorithm(String defaultSignatureAlgorithm) {
    this.defaultSignatureAlgorithm = defaultSignatureAlgorithm;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (RealmConfig) obj;
    return Objects.equals(this.realm, that.realm)
        && Objects.equals(this.displayName, that.displayName)
        && Objects.equals(this.enabled, that.enabled)
        && Objects.equals(this.accessTokenLifespan, that.accessTokenLifespan)
        && Objects.equals(this.ssoSessionIdleTimeout, that.ssoSessionIdleTimeout)
        && Objects.equals(this.ssoSessionMaxLifespan, that.ssoSessionMaxLifespan)
        && Objects.equals(this.registrationAllowed, that.registrationAllowed)
        && Objects.equals(this.resetPasswordAllowed, that.resetPasswordAllowed)
        && Objects.equals(this.rememberMe, that.rememberMe)
        && Objects.equals(this.verifyEmail, that.verifyEmail)
        && Objects.equals(this.loginWithEmailAllowed, that.loginWithEmailAllowed)
        && Objects.equals(this.duplicateEmailsAllowed, that.duplicateEmailsAllowed)
        && Objects.equals(this.defaultSignatureAlgorithm, that.defaultSignatureAlgorithm)
        && Objects.equals(this.attributes, that.attributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        realm,
        displayName,
        enabled,
        accessTokenLifespan,
        ssoSessionIdleTimeout,
        ssoSessionMaxLifespan,
        registrationAllowed,
        resetPasswordAllowed,
        rememberMe,
        verifyEmail,
        loginWithEmailAllowed,
        duplicateEmailsAllowed,
        defaultSignatureAlgorithm,
        attributes);
  }

  @Override
  public String toString() {
    return "RealmConfig["
        + "realm="
        + realm
        + ", displayName="
        + displayName
        + ", enabled="
        + enabled
        + ']';
  }
}

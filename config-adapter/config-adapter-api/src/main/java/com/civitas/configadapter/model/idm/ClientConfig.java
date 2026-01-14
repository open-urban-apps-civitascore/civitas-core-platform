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
 * Configuration for IDM client resources. Represents an OAuth2/OIDC client application that can
 * request authentication and authorization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ClientConfig implements IdmConfigValue {

  private String id;
  private String clientId;
  private String name;
  private String description;
  private String secret;
  private Boolean enabled;
  private Boolean publicClient;
  private Boolean bearerOnly;
  private Boolean standardFlowEnabled;
  private Boolean implicitFlowEnabled;
  private Boolean directAccessGrantsEnabled;
  private Boolean serviceAccountsEnabled;
  private String protocol;
  private List<String> redirectUris;
  private List<String> webOrigins;
  private String baseUrl;
  private String rootUrl;
  private String adminUrl;
  private Map<String, String> attributes;
  private List<String> defaultClientScopes;
  private List<String> optionalClientScopes;

  public ClientConfig() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
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

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getPublicClient() {
    return publicClient;
  }

  public void setPublicClient(Boolean publicClient) {
    this.publicClient = publicClient;
  }

  public Boolean getBearerOnly() {
    return bearerOnly;
  }

  public void setBearerOnly(Boolean bearerOnly) {
    this.bearerOnly = bearerOnly;
  }

  public Boolean getStandardFlowEnabled() {
    return standardFlowEnabled;
  }

  public void setStandardFlowEnabled(Boolean standardFlowEnabled) {
    this.standardFlowEnabled = standardFlowEnabled;
  }

  public Boolean getImplicitFlowEnabled() {
    return implicitFlowEnabled;
  }

  public void setImplicitFlowEnabled(Boolean implicitFlowEnabled) {
    this.implicitFlowEnabled = implicitFlowEnabled;
  }

  public Boolean getDirectAccessGrantsEnabled() {
    return directAccessGrantsEnabled;
  }

  public void setDirectAccessGrantsEnabled(Boolean directAccessGrantsEnabled) {
    this.directAccessGrantsEnabled = directAccessGrantsEnabled;
  }

  public Boolean getServiceAccountsEnabled() {
    return serviceAccountsEnabled;
  }

  public void setServiceAccountsEnabled(Boolean serviceAccountsEnabled) {
    this.serviceAccountsEnabled = serviceAccountsEnabled;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public List<String> getRedirectUris() {
    return redirectUris;
  }

  public void setRedirectUris(List<String> redirectUris) {
    this.redirectUris = redirectUris;
  }

  public List<String> getWebOrigins() {
    return webOrigins;
  }

  public void setWebOrigins(List<String> webOrigins) {
    this.webOrigins = webOrigins;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getRootUrl() {
    return rootUrl;
  }

  public void setRootUrl(String rootUrl) {
    this.rootUrl = rootUrl;
  }

  public String getAdminUrl() {
    return adminUrl;
  }

  public void setAdminUrl(String adminUrl) {
    this.adminUrl = adminUrl;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes;
  }

  public List<String> getDefaultClientScopes() {
    return defaultClientScopes;
  }

  public void setDefaultClientScopes(List<String> defaultClientScopes) {
    this.defaultClientScopes = defaultClientScopes;
  }

  public List<String> getOptionalClientScopes() {
    return optionalClientScopes;
  }

  public void setOptionalClientScopes(List<String> optionalClientScopes) {
    this.optionalClientScopes = optionalClientScopes;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ClientConfig) obj;
    return Objects.equals(this.id, that.id)
        && Objects.equals(this.clientId, that.clientId)
        && Objects.equals(this.name, that.name)
        && Objects.equals(this.description, that.description)
        && Objects.equals(this.secret, that.secret)
        && Objects.equals(this.enabled, that.enabled)
        && Objects.equals(this.publicClient, that.publicClient)
        && Objects.equals(this.bearerOnly, that.bearerOnly)
        && Objects.equals(this.standardFlowEnabled, that.standardFlowEnabled)
        && Objects.equals(this.implicitFlowEnabled, that.implicitFlowEnabled)
        && Objects.equals(this.directAccessGrantsEnabled, that.directAccessGrantsEnabled)
        && Objects.equals(this.serviceAccountsEnabled, that.serviceAccountsEnabled)
        && Objects.equals(this.protocol, that.protocol)
        && Objects.equals(this.redirectUris, that.redirectUris)
        && Objects.equals(this.webOrigins, that.webOrigins)
        && Objects.equals(this.baseUrl, that.baseUrl)
        && Objects.equals(this.rootUrl, that.rootUrl)
        && Objects.equals(this.adminUrl, that.adminUrl)
        && Objects.equals(this.attributes, that.attributes)
        && Objects.equals(this.defaultClientScopes, that.defaultClientScopes)
        && Objects.equals(this.optionalClientScopes, that.optionalClientScopes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        clientId,
        name,
        description,
        secret,
        enabled,
        publicClient,
        bearerOnly,
        standardFlowEnabled,
        implicitFlowEnabled,
        directAccessGrantsEnabled,
        serviceAccountsEnabled,
        protocol,
        redirectUris,
        webOrigins,
        baseUrl,
        rootUrl,
        adminUrl,
        attributes,
        defaultClientScopes,
        optionalClientScopes);
  }

  @Override
  public String toString() {
    return "ClientConfig["
        + "clientId="
        + clientId
        + ", name="
        + name
        + ", enabled="
        + enabled
        + ']';
  }
}

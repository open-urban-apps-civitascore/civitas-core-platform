/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.apisix;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * TLS configuration for an APISIX upstream connection. Specifies the SNI, certificate verification,
 * and optional client certificate for mutual TLS.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * {
 *   "sni": "backend.example.com",
 *   "verify": true,
 *   "client_cert": "-----BEGIN CERTIFICATE-----\n...",
 *   "client_key": "-----BEGIN RSA PRIVATE KEY-----\n...",
 *   "client_cert_id": "ssl-cert-ref-id"
 * }
 * }</pre>
 *
 * @see <a href="https://apisix.apache.org/docs/apisix/admin-api/#upstream">APISIX Upstream API</a>
 */
public final class UpstreamTls extends AbstractApiModel {

  private String sni;
  private Boolean verify;

  @JsonProperty("client_cert")
  private String clientCert;

  @JsonProperty("client_key")
  private String clientKey;

  @JsonProperty("client_cert_id")
  private String clientCertId;

  public UpstreamTls() {}

  public String getSni() {
    return sni;
  }

  public void setSni(String sni) {
    this.sni = sni;
  }

  public Boolean getVerify() {
    return verify;
  }

  public void setVerify(Boolean verify) {
    this.verify = verify;
  }

  public String getClientCert() {
    return clientCert;
  }

  public void setClientCert(String clientCert) {
    this.clientCert = clientCert;
  }

  public String getClientKey() {
    return clientKey;
  }

  public void setClientKey(String clientKey) {
    this.clientKey = clientKey;
  }

  public String getClientCertId() {
    return clientCertId;
  }

  public void setClientCertId(String clientCertId) {
    this.clientCertId = clientCertId;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (sni != null) map.put("sni", sni);
    if (verify != null) map.put("verify", verify);
    if (clientCert != null) map.put("client_cert", clientCert);
    if (clientKey != null) map.put("client_key", clientKey);
    if (clientCertId != null) map.put("client_cert_id", clientCertId);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (UpstreamTls) obj;
    return Objects.equals(this.sni, that.sni)
        && Objects.equals(this.verify, that.verify)
        && Objects.equals(this.clientCert, that.clientCert)
        && Objects.equals(this.clientKey, that.clientKey)
        && Objects.equals(this.clientCertId, that.clientCertId)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(sni, verify, clientCert, clientKey, clientCertId, additionalProperties());
  }

  @Override
  public String toString() {
    return "UpstreamTls["
        + "sni="
        + sni
        + ", verify="
        + verify
        + ", clientCert="
        + (clientCert != null ? "[PRESENT]" : "null")
        + ", clientKey="
        + (clientKey != null ? "[PRESENT]" : "null")
        + ", clientCertId="
        + clientCertId
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}

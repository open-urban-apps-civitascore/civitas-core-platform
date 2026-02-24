/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Basic authentication credentials for HTTP client output. */
public final class BasicAuth extends AbstractApiModel {

  private String username;
  private String password;

  public BasicAuth() {}

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (username != null) map.put("username", username);
    if (password != null) map.put("password", password);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (BasicAuth) obj;
    return Objects.equals(this.username, that.username)
        && Objects.equals(this.password, that.password)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password, additionalProperties());
  }

  @Override
  public String toString() {
    return "BasicAuth["
        + "username="
        + username
        + ", password="
        + (password != null ? "[PRESENT]" : "null")
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}

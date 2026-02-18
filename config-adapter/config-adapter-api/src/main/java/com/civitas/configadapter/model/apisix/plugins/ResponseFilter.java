/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.apisix.plugins;

import com.civitas.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A response body filter that replaces text matching a regex pattern. */
public final class ResponseFilter extends AbstractApiModel {

  private String regex;
  private String replace;

  public ResponseFilter() {}

  public String getRegex() {
    return regex;
  }

  public void setRegex(String regex) {
    this.regex = regex;
  }

  public String getReplace() {
    return replace;
  }

  public void setReplace(String replace) {
    this.replace = replace;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (regex != null) map.put("regex", regex);
    if (replace != null) map.put("replace", replace);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ResponseFilter) obj;
    return Objects.equals(this.regex, that.regex)
        && Objects.equals(this.replace, that.replace)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(regex, replace, additionalProperties());
  }

  @Override
  public String toString() {
    return "ResponseFilter[regex=" + regex + ", replace=" + replace + ']';
  }
}

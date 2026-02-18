/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.apisix.plugins;

import com.civitas.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Header rewriting configuration shared between proxy-rewrite and response-rewrite plugins.
 *
 * <p>Supports structured header operations:
 *
 * <ul>
 *   <li>set - Headers to set (overwrite if exists)
 *   <li>add - Headers to add (append)
 *   <li>remove - Headers to remove
 * </ul>
 */
public final class RewriteHeaders extends AbstractApiModel {

  private Map<String, String> set;
  private Map<String, String> add;
  private List<String> remove;

  public RewriteHeaders() {}

  public Map<String, String> getSet() {
    return set;
  }

  public void setSet(Map<String, String> set) {
    this.set = set;
  }

  public Map<String, String> getAdd() {
    return add;
  }

  public void setAdd(Map<String, String> add) {
    this.add = add;
  }

  public List<String> getRemove() {
    return remove;
  }

  public void setRemove(List<String> remove) {
    this.remove = remove;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (set != null) map.put("set", set);
    if (add != null) map.put("add", add);
    if (remove != null) map.put("remove", remove);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (RewriteHeaders) obj;
    return Objects.equals(this.set, that.set)
        && Objects.equals(this.add, that.add)
        && Objects.equals(this.remove, that.remove)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(set, add, remove, additionalProperties());
  }

  @Override
  public String toString() {
    return "RewriteHeaders[set=" + set + ", add=" + add + ", remove=" + remove + ']';
  }
}

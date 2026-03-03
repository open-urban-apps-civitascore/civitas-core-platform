/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.redpanda;

import de.civitascore.configadapter.model.AbstractApiModel;
import de.civitascore.configadapter.util.StringUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A single case within a switch output, with a condition and a recursive output definition. */
public final class SwitchCase extends AbstractApiModel {

  private static final String KEY_CHECK = "check";
  private static final String KEY_OUTPUT = "output";
  private static final int TOSTRING_MAX_LENGTH = 50;

  private String check;
  private PipelineOutput output;

  public SwitchCase() {}

  public String getCheck() {
    return check;
  }

  public void setCheck(String check) {
    this.check = check;
  }

  public PipelineOutput getOutput() {
    return output;
  }

  public void setOutput(PipelineOutput output) {
    this.output = output;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (check != null) map.put(KEY_CHECK, check);
    if (output != null) map.put(KEY_OUTPUT, output.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (SwitchCase) obj;
    return Objects.equals(this.check, that.check)
        && Objects.equals(this.output, that.output)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(check, output, additionalProperties());
  }

  @Override
  public String toString() {
    return "SwitchCase["
        + "check="
        + StringUtils.truncate(check, TOSTRING_MAX_LENGTH)
        + ", output="
        + output
        + ']';
  }
}

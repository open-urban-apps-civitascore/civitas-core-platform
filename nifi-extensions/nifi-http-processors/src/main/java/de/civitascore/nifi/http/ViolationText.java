/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import com.networknt.schema.JsonNodePath;
import com.networknt.schema.ValidationMessage;
import java.util.Set;

/**
 * Describes a violation of the Record Schema without a value of the response, because some messages
 * of the validator quote the value.
 */
final class ViolationText {

  /** Keywords whose subschema applies to a member of the instance that the schema does not name. */
  private static final Set<String> UNNAMED_MEMBER_KEYWORDS =
      Set.of(
          "additionalProperties",
          "unevaluatedProperties",
          "items",
          "additionalItems",
          "unevaluatedItems",
          "contains");

  /** Keywords followed by a pattern or an index whose subschema applies to one member. */
  private static final Set<String> MATCHED_MEMBER_KEYWORDS =
      Set.of("patternProperties", "prefixItems");

  /** Keywords followed by a property name whose subschema applies to the instance itself. */
  private static final Set<String> NAMED_SELF_KEYWORDS = Set.of("dependentSchemas", "dependencies");

  private ViolationText() {}

  /** The pointer inside the record and the keyword; for {@code required} the property too. */
  static String describe(ValidationMessage message) {
    StringBuilder text = new StringBuilder();
    String pointer = pointer(message.getInstanceLocation(), message.getEvaluationPath());
    if (!pointer.isEmpty()) {
      text.append(pointer).append(' ');
    }
    text.append(message.getType());
    if ("required".equals(message.getType()) && message.getProperty() != null) {
      text.append(' ').append(message.getProperty());
    }
    return text.toString();
  }

  /**
   * The JSON Pointer of RFC 6901 for the location inside the record. A property name that no {@code
   * properties} of the schema declares is {@code *}, because it comes from the response.
   */
  private static String pointer(JsonNodePath location, JsonNodePath evaluationPath) {
    boolean[] declared = declaredProperties(location, evaluationPath);
    StringBuilder pointer = new StringBuilder();
    for (int i = 0; i < declared.length; i++) {
      Object token = location.getElement(i);
      pointer.append('/');
      if (declared[i] || token instanceof Integer) {
        pointer.append(String.valueOf(token).replace("~", "~0").replace("/", "~1"));
      } else {
        pointer.append('*');
      }
    }
    return pointer.toString();
  }

  /** Walks the evaluation path along the location, one member of the instance at a time. */
  private static boolean[] declaredProperties(JsonNodePath location, JsonNodePath evaluationPath) {
    boolean[] declared = new boolean[location.getNameCount()];
    int member = 0;
    int step = 0;
    while (step < evaluationPath.getNameCount() && member < declared.length) {
      String keyword = String.valueOf(evaluationPath.getElement(step));
      if ("properties".equals(keyword) && step + 1 < evaluationPath.getNameCount()) {
        step++;
        declared[member] = evaluationPath.getElement(step).equals(location.getElement(member));
        member++;
      } else if (MATCHED_MEMBER_KEYWORDS.contains(keyword)) {
        step++;
        member++;
      } else if (UNNAMED_MEMBER_KEYWORDS.contains(keyword)) {
        member++;
      } else if (NAMED_SELF_KEYWORDS.contains(keyword)) {
        step++;
      }
      step++;
    }
    return declared;
  }
}

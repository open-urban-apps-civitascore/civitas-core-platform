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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;

/**
 * The rules of the {@link GetHttpApiCall} properties that the portal backend also applies. No
 * explanation names a value, because a value can be a credential.
 */
final class PropertyRules {

  static final Validator BASE_URL =
      (subject, input, context) ->
          result(
              subject,
              urlViolation(input, Set.of("http", "https"), "must use the scheme http or https"));

  static final Validator TOKEN_URL =
      (subject, input, context) ->
          result(subject, urlViolation(input, Set.of("https"), "must use https"));

  static final Validator HEADER_VALUE =
      (subject, input, context) -> result(subject, headerValueViolation(input));

  static final Validator USERNAME =
      (subject, input, context) -> result(subject, usernameViolation(input));

  private PropertyRules() {}

  /**
   * The rules of the authentication that depend on more than one property.
   *
   * @param apiCall the API call, empty when it breaks a rule of its own
   */
  static List<ValidationResult> authenticationViolations(
      ValidationContext context, Optional<ApiCall> apiCall) {
    AuthenticationType type =
        context
            .getProperty(GetHttpApiCall.AUTHENTICATION_TYPE)
            .asAllowableValue(AuthenticationType.class);
    List<ValidationResult> results = new ArrayList<>();
    String scheme = URI.create(context.getProperty(GetHttpApiCall.BASE_URL).getValue()).getScheme();
    if (type != AuthenticationType.NONE && "http".equalsIgnoreCase(scheme)) {
      results.add(
          result(
              GetHttpApiCall.BASE_URL.getDisplayName(),
              "must use https when the request authenticates"));
    }
    if (type == AuthenticationType.API_TOKEN) {
      results.addAll(apiTokenViolations(context, apiCall));
    }
    return results;
  }

  private static List<ValidationResult> apiTokenViolations(
      ValidationContext context, Optional<ApiCall> apiCall) {
    String name = context.getProperty(GetHttpApiCall.API_TOKEN_NAME).getValue();
    String nameSubject = GetHttpApiCall.API_TOKEN_NAME.getDisplayName();
    List<ValidationResult> results = new ArrayList<>();
    switch (context
        .getProperty(GetHttpApiCall.API_TOKEN_LOCATION)
        .asAllowableValue(ParameterLocation.class)) {
      case HEADER -> {
        ApiCallParser.headerNameViolation(name)
            .ifPresent(violation -> results.add(result(nameSubject, violation)));
        if (!ApiCallParser.isFieldValue(
            context.getProperty(GetHttpApiCall.API_TOKEN_VALUE).getValue())) {
          results.add(
              result(
                  GetHttpApiCall.API_TOKEN_VALUE.getDisplayName(),
                  ApiCallParser.FIELD_VALUE_VIOLATION));
        }
        if (sends(apiCall, name, ParameterLocation.HEADER)) {
          results.add(
              result(
                  nameSubject,
                  "must differ from the name of each header of the API call, pagination"
                      + " included"));
        }
      }
      case QUERY -> {
        if (sends(apiCall, name, ParameterLocation.QUERY)) {
          results.add(
              result(
                  nameSubject,
                  "must differ from the name of each query parameter of the API call, pagination"
                      + " included"));
        }
      }
    }
    return results;
  }

  private static boolean sends(Optional<ApiCall> apiCall, String name, ParameterLocation location) {
    return apiCall.isPresent() && PaginationNames.sentBy(apiCall.get()).sends(name, location);
  }

  /** The explanations name no value: a user info in the URL would be a credential. */
  private static String urlViolation(String input, Set<String> schemes, String schemeViolation) {
    URI uri;
    try {
      uri = new URI(input);
    } catch (URISyntaxException e) {
      return "is not a URL";
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!schemes.contains(scheme)) {
      return schemeViolation;
    }
    if (uri.getRawUserInfo() != null) {
      return "must not contain user info";
    }
    if (uri.getHost() == null) {
      return "must name a host";
    }
    if (uri.getRawQuery() != null) {
      return "must not contain a query";
    }
    if (uri.getRawFragment() != null) {
      return "must not contain a fragment";
    }
    return null;
  }

  private static String headerValueViolation(String input) {
    if (input == null || input.isEmpty()) {
      return "must not be empty";
    }
    return ApiCallParser.isFieldValue(input) ? null : ApiCallParser.FIELD_VALUE_VIOLATION;
  }

  private static String usernameViolation(String input) {
    if (input == null || input.isEmpty()) {
      return "must not be empty";
    }
    if (input.indexOf(':') >= 0) {
      return "must not contain a colon";
    }
    return null;
  }

  static ValidationResult result(String subject, String violation) {
    return new ValidationResult.Builder()
        .subject(subject)
        .valid(violation == null)
        .explanation(violation)
        .build();
  }
}

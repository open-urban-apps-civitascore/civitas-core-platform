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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.civitascore.nifi.http.ApiCall.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the {@code API Call} JSON and checks the rules the portal backend applies to it.
 *
 * <p>The processor checks them again because it builds the request and the credentials from them: a
 * header that the configuration sets could replace the authentication, and a line break in a header
 * value could start a second header. The header rules also reject what the JDK HTTP client behind
 * the web client service refuses, so that such a header makes the processor invalid instead of
 * failing every run.
 */
final class ApiCallParser {

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9._~:/-]*");
  private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
  private static final Set<String> CREDENTIAL_HEADERS =
      Set.of("authorization", "proxy-authorization", "cookie");
  private static final Set<String> CLIENT_HEADERS =
      Set.of("connection", "content-length", "expect", "host", "upgrade");

  /** The explanation for a header value that {@link #isFieldValue} refuses. */
  static final String FIELD_VALUE_VIOLATION =
      "must hold only printable ISO-8859-1 characters, spaces and tabs";

  private ApiCallParser() {}

  /**
   * Reads the JSON of the {@code API Call} property.
   *
   * @throws InvalidApiCallException with one violation for each broken rule
   */
  static ApiCall parse(String json) throws InvalidApiCallException {
    List<String> violations = new ArrayList<>();
    Optional<JsonApiCall> read = read(json, violations);
    if (read.isEmpty()) {
      throw new InvalidApiCallException(violations);
    }
    JsonApiCall call = read.get();
    notBlank(call.name(), "name", violations);
    HttpMethod method = method(call.method(), violations);
    if (call.path() == null || !PATH.matcher(call.path()).matches()) {
      violations.add("path must start with / and hold only A-Z, a-z, 0-9 and . _ ~ : / -");
    }
    List<Parameter> queryParameters =
        parameters(call.queryParameters(), "queryParameters", false, violations);
    List<Parameter> headers = parameters(call.headers(), "headers", true, violations);
    Optional<Pagination> pagination =
        PaginationParser.parse(call.pagination(), queryParameters, headers, violations);

    if (!violations.isEmpty()) {
      throw new InvalidApiCallException(violations);
    }
    return new ApiCall(
        call.name(), method, call.path(), queryParameters, headers, pagination.orElseThrow());
  }

  /** Reads the JSON shape, or adds the one violation that stops the reading. */
  private static Optional<JsonApiCall> read(String json, List<String> violations) {
    JsonNode tree;
    try {
      tree = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      violations.add("API Call is not valid JSON");
      return Optional.empty();
    }
    if (!tree.isObject()) {
      violations.add("API Call must be a JSON object");
      return Optional.empty();
    }
    try {
      return Optional.of(MAPPER.treeToValue(tree, JsonApiCall.class));
    } catch (JsonProcessingException e) {
      violations.add(shapeViolation(e));
      return Optional.empty();
    }
  }

  private static HttpMethod method(String method, List<String> violations) {
    if (method == null || HttpMethod.GET.name().equals(method)) {
      return HttpMethod.GET;
    }
    violations.add("method supports only GET");
    return null;
  }

  private static List<Parameter> parameters(
      List<JsonParameter> parameters, String key, boolean header, List<String> violations) {
    if (parameters == null) {
      return List.of();
    }
    List<Parameter> checked = new ArrayList<>(parameters.size());
    for (int index = 0; index < parameters.size(); index++) {
      String path = key + "[" + index + "]";
      JsonParameter parameter = parameters.get(index);
      if (parameter == null) {
        violations.add(path + " must not be null");
        continue;
      }
      String value = parameter.value() == null ? "" : parameter.value();
      if (notBlank(parameter.name(), path + ".name", violations) && header) {
        checkHeaderName(parameter.name(), path + ".name", violations);
        if (!isFieldValue(value)) {
          violations.add(path + ".value " + FIELD_VALUE_VIOLATION);
        }
      }
      checked.add(new Parameter(parameter.name(), value));
    }
    return checked;
  }

  /**
   * @param keyPath the key path of the name, which each violation starts with
   */
  static void checkHeaderName(String name, String keyPath, List<String> violations) {
    if (CREDENTIAL_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
      violations.add(keyPath + " must not be Authorization, Proxy-Authorization or Cookie");
    } else {
      headerNameViolation(name).ifPresent(violation -> violations.add(keyPath + " " + violation));
    }
  }

  /**
   * Why the JDK HTTP client behind the web client service does not send a header of this name, or
   * empty when it does.
   */
  static Optional<String> headerNameViolation(String name) {
    if (CLIENT_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
      return Optional.of("names a header the HTTP client sets itself");
    }
    if (!HEADER_NAME.matcher(name).matches()) {
      return Optional.of("is not a valid header name");
    }
    return Optional.empty();
  }

  /** Whether the text holds only the field-value characters of RFC 9110, section 5.5. */
  static boolean isFieldValue(String value) {
    return value.chars().allMatch(ApiCallParser::isFieldValueCharacter);
  }

  private static boolean isFieldValueCharacter(int c) {
    return c == '\t' || (c >= ' ' && c <= '~') || (c >= 0x80 && c <= 0xFF);
  }

  static boolean notBlank(String text, String path, List<String> violations) {
    if (text == null || text.isBlank()) {
      violations.add(path + " must not be blank");
      return false;
    }
    return true;
  }

  /** Names the key path of a JSON that does not fit the shape. Jackson's message holds values. */
  private static String shapeViolation(JsonProcessingException e) {
    String path = e instanceof JsonMappingException mapping ? keyPath(mapping.getPath()) : "";
    if (path.isEmpty()) {
      return "API Call does not have the shape of an API call";
    }
    return e instanceof UnrecognizedPropertyException
        ? path + " is not a known key"
        : path + " has the wrong type";
  }

  private static String keyPath(List<JsonMappingException.Reference> references) {
    StringBuilder path = new StringBuilder();
    for (JsonMappingException.Reference reference : references) {
      if (reference.getFieldName() != null) {
        if (!path.isEmpty()) {
          path.append('.');
        }
        path.append(reference.getFieldName());
      } else {
        path.append('[').append(reference.getIndex()).append(']');
      }
    }
    return path.toString();
  }

  /** The JSON shape, before the rules are checked. */
  record JsonApiCall(
      String name,
      String method,
      String path,
      List<JsonParameter> queryParameters,
      List<JsonParameter> headers,
      PaginationParser.JsonPagination pagination) {}

  record JsonParameter(String name, String value) {}
}

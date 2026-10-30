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

import de.civitascore.nifi.http.ApiCall.Parameter;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reads the {@code pagination} of the {@code API Call} JSON and checks the portal backend rules.
 */
final class PaginationParser {

  private static final String PAGE_NUMBER = "PAGE_NUMBER";
  private static final String CURSOR = "CURSOR";
  private static final String LIMIT_OFFSET = "LIMIT_OFFSET";
  private static final String PAGE_PARAMETER = "pagination.pageParameter";
  private static final String PAGE_SIZE_PARAMETER = "pagination.pageSizeParameter";
  private static final String CURSOR_PATH = "pagination.cursorPath";
  private static final String CURSOR_PARAMETER = "pagination.cursorParameter";
  private static final String CURSOR_IN = "pagination.cursorIn";
  private static final String LIMIT_PARAMETER = "pagination.limitParameter";
  private static final String LIMIT = "pagination.limit";
  private static final String OFFSET_PARAMETER = "pagination.offsetParameter";
  private static final String COUNT_PATH = "pagination.countPath";

  private PaginationParser() {}

  /**
   * @param queryParameters the query parameters of the API call, which the query parameter names of
   *     the pagination must not repeat
   * @param headers the headers of the API call, which the header names of the pagination must not
   *     repeat
   * @param violations receives one violation for each broken rule
   * @return {@link SinglePage} when the pagination is absent, empty when it breaks a rule
   */
  static Optional<Pagination> parse(
      JsonPagination pagination,
      List<Parameter> queryParameters,
      List<Parameter> headers,
      List<String> violations) {
    if (pagination == null) {
      return Optional.of(new SinglePage());
    }
    PaginationNames names = new PaginationNames(queryParameters, headers);
    return switch (pagination.type()) {
      case PAGE_NUMBER -> pageNumber(pagination, names, violations);
      case CURSOR -> cursor(pagination, names, violations);
      case LIMIT_OFFSET -> limitOffset(pagination, names, violations);
      case null, default -> {
        violations.add(
            "pagination.type must be " + PAGE_NUMBER + ", " + CURSOR + " or " + LIMIT_OFFSET);
        yield Optional.empty();
      }
    };
  }

  private static Optional<Pagination> pageNumber(
      JsonPagination pagination, PaginationNames names, List<String> violations) {
    int before = violations.size();
    addRequiredQueryName(pagination.pageParameter(), PAGE_PARAMETER, names, violations);
    addRequiredQueryName(pagination.pageSizeParameter(), PAGE_SIZE_PARAMETER, names, violations);
    names.check(violations);
    int startPage = pagination.startPage() == null ? 0 : pagination.startPage();
    checkPageNumbers(pagination, startPage, violations);
    if (violations.size() > before) {
      return Optional.empty();
    }
    return Optional.of(
        new PageNumberPagination(
            pagination.pageParameter(),
            pagination.pageSizeParameter(),
            pagination.pageSize(),
            startPage,
            pagination.endPage() == null
                ? OptionalInt.empty()
                : OptionalInt.of(pagination.endPage())));
  }

  private static Optional<Pagination> cursor(
      JsonPagination pagination, PaginationNames names, List<String> violations) {
    int before = violations.size();
    Optional<JsonPath> cursorPath = jsonPath(pagination.cursorPath(), CURSOR_PATH, violations);
    boolean hasCursorParameter =
        ApiCallParser.notBlank(pagination.cursorParameter(), CURSOR_PARAMETER, violations);
    Optional<ParameterLocation> cursorIn = location(pagination.cursorIn(), violations);
    if (hasCursorParameter && cursorIn.isPresent()) {
      if (cursorIn.get() == ParameterLocation.HEADER) {
        ApiCallParser.checkHeaderName(pagination.cursorParameter(), CURSOR_PARAMETER, violations);
      }
      names.add(CURSOR_PARAMETER, pagination.cursorParameter(), cursorIn.get());
    }
    Optional<Limit> limit = limit(pagination, names, violations);
    names.check(violations);
    if (violations.size() > before) {
      return Optional.empty();
    }
    return Optional.of(
        new CursorPagination(
            cursorPath.orElseThrow(), pagination.cursorParameter(), cursorIn.orElseThrow(), limit));
  }

  private static Optional<Pagination> limitOffset(
      JsonPagination pagination, PaginationNames names, List<String> violations) {
    int before = violations.size();
    Optional<Limit> limit = limit(pagination, names, violations);
    addRequiredQueryName(pagination.offsetParameter(), OFFSET_PARAMETER, names, violations);
    names.check(violations);
    int offset = pagination.offset() == null ? 0 : pagination.offset();
    if (offset < 0) {
      violations.add("pagination.offset must be at least 0");
    }
    String countPathText = blankToNull(pagination.countPath());
    Optional<JsonPath> countPath =
        countPathText == null ? Optional.empty() : jsonPath(countPathText, COUNT_PATH, violations);
    if (violations.size() > before) {
      return Optional.empty();
    }
    return Optional.of(
        new LimitOffsetPagination(pagination.offsetParameter(), offset, limit, countPath));
  }

  private static Optional<JsonPath> jsonPath(String text, String keyPath, List<String> violations) {
    if (!ApiCallParser.notBlank(text, keyPath, violations)) {
      return Optional.empty();
    }
    Optional<JsonPath> path = JsonPath.parse(text);
    if (path.isEmpty()) {
      violations.add(keyPath + " must start with $ and hold only member names and array indexes");
    }
    return path;
  }

  private static Optional<ParameterLocation> location(String text, List<String> violations) {
    for (ParameterLocation location : ParameterLocation.values()) {
      if (location.name().equals(text)) {
        return Optional.of(location);
      }
    }
    violations.add(CURSOR_IN + " must be QUERY or HEADER");
    return Optional.empty();
  }

  /**
   * A limit and its parameter come together, or the request sends no limit. The limit parameter
   * goes into {@code names} as a query name.
   */
  private static Optional<Limit> limit(
      JsonPagination pagination, PaginationNames names, List<String> violations) {
    String limitParameter = blankToNull(pagination.limitParameter());
    if (limitParameter != null) {
      names.add(LIMIT_PARAMETER, limitParameter, ParameterLocation.QUERY);
    }
    Integer limit = pagination.limit();
    if (limit == null) {
      if (limitParameter != null) {
        violations.add(LIMIT + " is required with " + LIMIT_PARAMETER);
      }
      return Optional.empty();
    }
    int before = violations.size();
    if (limitParameter == null) {
      violations.add(LIMIT_PARAMETER + " is required with " + LIMIT);
    }
    if (limit <= 0) {
      violations.add(LIMIT + " must be at least 1");
    }
    return violations.size() > before
        ? Optional.empty()
        : Optional.of(new Limit(limitParameter, limit));
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text;
  }

  private static void addRequiredQueryName(
      String name, String keyPath, PaginationNames names, List<String> violations) {
    if (ApiCallParser.notBlank(name, keyPath, violations)) {
      names.add(keyPath, name, ParameterLocation.QUERY);
    }
  }

  private static void checkPageNumbers(
      JsonPagination pagination, int startPage, List<String> violations) {
    if (pagination.pageSize() == null || pagination.pageSize() < 1) {
      violations.add("pagination.pageSize must be at least 1");
    }
    if (startPage < 0) {
      violations.add("pagination.startPage must be at least 0");
    }
    if (pagination.endPage() != null && pagination.endPage() < startPage) {
      violations.add("pagination.endPage must be at least pagination.startPage");
    }
  }

  /**
   * The JSON shape of each type, before the rules are checked. A type ignores the keys of another
   * type.
   *
   * @param cursorIn a text, so that a wrong value is one violation among the others
   */
  record JsonPagination(
      String type,
      String pageParameter,
      String pageSizeParameter,
      Integer pageSize,
      Integer startPage,
      Integer endPage,
      String cursorPath,
      String cursorParameter,
      String cursorIn,
      String limitParameter,
      Integer limit,
      String offsetParameter,
      Integer offset,
      String countPath) {}
}

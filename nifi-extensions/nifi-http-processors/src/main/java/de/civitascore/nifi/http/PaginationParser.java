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
import java.util.OptionalInt;

/**
 * Reads the {@code pagination} of the {@code API Call} JSON and checks the portal backend rules.
 */
final class PaginationParser {

  private static final String PAGE_NUMBER = "PAGE_NUMBER";
  private static final String PAGE_PARAMETER = "pagination.pageParameter";
  private static final String PAGE_SIZE_PARAMETER = "pagination.pageSizeParameter";

  private PaginationParser() {}

  /**
   * Reads the pagination of an API call.
   *
   * @param queryParameters the query parameters of the API call, which the parameter names of the
   *     pagination must not repeat
   * @param violations receives one violation for each broken rule
   * @return {@link SinglePage} when the pagination is absent, null when it breaks a rule
   */
  static Pagination parse(
      JsonPagination pagination, List<Parameter> queryParameters, List<String> violations) {
    if (pagination == null) {
      return new SinglePage();
    }
    if (!PAGE_NUMBER.equals(pagination.type())) {
      violations.add("pagination.type must be " + PAGE_NUMBER);
      return null;
    }
    int before = violations.size();
    checkParameterNames(pagination, queryParameters, violations);
    int startPage = pagination.startPage() == null ? 0 : pagination.startPage();
    checkNumbers(pagination, startPage, violations);
    if (violations.size() > before) {
      return null;
    }
    return new PageNumberPagination(
        pagination.pageParameter(),
        pagination.pageSizeParameter(),
        pagination.pageSize(),
        startPage,
        pagination.endPage() == null ? OptionalInt.empty() : OptionalInt.of(pagination.endPage()));
  }

  /** A request must never send one name with two values, so the names must not collide. */
  private static void checkParameterNames(
      JsonPagination pagination, List<Parameter> queryParameters, List<String> violations) {
    String page = pagination.pageParameter();
    String pageSize = pagination.pageSizeParameter();
    boolean hasPage = ApiCallParser.notBlank(page, PAGE_PARAMETER, violations);
    boolean hasPageSize = ApiCallParser.notBlank(pageSize, PAGE_SIZE_PARAMETER, violations);
    if (hasPage) {
      checkNoQueryParameterNamed(page, PAGE_PARAMETER, queryParameters, violations);
    }
    if (hasPageSize) {
      checkNoQueryParameterNamed(pageSize, PAGE_SIZE_PARAMETER, queryParameters, violations);
    }
    if (hasPage && hasPageSize && page.equals(pageSize)) {
      violations.add(PAGE_SIZE_PARAMETER + " must differ from " + PAGE_PARAMETER);
    }
  }

  /** HTTP query parameter names are case-sensitive, so the comparison is too. */
  private static void checkNoQueryParameterNamed(
      String name, String path, List<Parameter> queryParameters, List<String> violations) {
    if (queryParameters.stream().anyMatch(parameter -> name.equals(parameter.name()))) {
      violations.add(path + " must differ from the name of each query parameter");
    }
  }

  private static void checkNumbers(
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

  /** The JSON shape, before the rules are checked. */
  record JsonPagination(
      String type,
      String pageParameter,
      String pageSizeParameter,
      Integer pageSize,
      Integer startPage,
      Integer endPage) {}
}

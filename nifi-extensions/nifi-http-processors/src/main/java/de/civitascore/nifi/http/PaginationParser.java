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
  private static final String PAGE_PARAMETER = "pagination.pageParameter";
  private static final String PAGE_SIZE_PARAMETER = "pagination.pageSizeParameter";

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
      case null, default -> {
        violations.add("pagination.type must be " + PAGE_NUMBER);
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

  /** The JSON shape, before the rules are checked. */
  record JsonPagination(
      String type,
      String pageParameter,
      String pageSizeParameter,
      Integer pageSize,
      Integer startPage,
      Integer endPage) {}
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import de.civitascore.nifi.http.ApiCall.Parameter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LimitOffsetPaginationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final URI REQUEST_URL = URI.create("https://api.example.org/items");
  private static final JsonPath COUNT_PATH = new JsonPath("$.meta.count");
  private static final Limit LIMIT = new Limit("limit", 20);

  @Test
  void constructor_withANegativeStartOffset_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LimitOffsetPagination("offset", -1, Optional.empty(), Optional.empty()));
  }

  @Test
  void first_sendsTheLimitAndThenTheStartOffset() {
    Page first = withLimit(80, Optional.empty()).first();

    assertEquals(
        List.of(new Parameter("limit", "20"), new Parameter("offset", "80")),
        first.queryParameters());
    assertEquals(List.of(), first.headers());
    assertEquals(Optional.of("offset 80"), first.title());
    assertEquals(0, first.index());
  }

  @Test
  void first_withoutALimit_sendsTheOffsetAlone() {
    Page first = withoutLimit(0, Optional.empty()).first();

    assertEquals(List.of(new Parameter("offset", "0")), first.queryParameters());
  }

  @Test
  void shape_withoutACountPath_isARecordArray() {
    assertEquals(new ResponseShape.RecordArray(), withLimit(0, Optional.empty()).shape());
  }

  @Test
  void shape_withACountPath_isARecordObjectThatReadsTheCountPath() {
    assertEquals(
        new ResponseShape.RecordObject(List.of(COUNT_PATH)),
        withLimit(0, Optional.of(COUNT_PATH)).shape());
  }

  @Test
  void next_withALimit_addsTheLimitToTheOffset() {
    LimitOffsetPagination pagination = withLimit(80, Optional.empty());

    Page next = nextPage(pagination, pagination.first(), arrayOf(20));

    assertEquals(
        List.of(new Parameter("limit", "20"), new Parameter("offset", "100")),
        next.queryParameters());
    assertEquals(Optional.of("offset 100"), next.title());
    assertEquals(1, next.index());
  }

  @Test
  void next_withoutALimit_addsTheRecordCount() {
    LimitOffsetPagination pagination = withoutLimit(80, Optional.empty());

    Page second = nextPage(pagination, pagination.first(), arrayOf(7));
    Page third = nextPage(pagination, second, arrayOf(3));

    assertEquals(List.of(new Parameter("offset", "87")), second.queryParameters());
    assertEquals(List.of(new Parameter("offset", "90")), third.queryParameters());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 19})
  void next_withALimit_endsAtFewerRecordsThanTheLimit(int records) {
    LimitOffsetPagination pagination = withLimit(0, Optional.empty());

    Pagination.Progress progress =
        pagination.next(pagination.first(), REQUEST_URL, arrayOf(records));

    assertEquals(new Pagination.Progress.Accepted(records, Optional.empty()), progress);
  }

  @Test
  void next_withMoreRecordsThanTheLimit_rejects() {
    LimitOffsetPagination pagination = withLimit(0, Optional.empty());

    Pagination.Progress progress = pagination.next(pagination.first(), REQUEST_URL, arrayOf(21));

    assertEquals(
        new Pagination.Progress.Rejected("the response holds more records than the limit"),
        progress);
  }

  @Test
  void next_withoutALimit_endsAtNoRecords() {
    LimitOffsetPagination pagination = withoutLimit(0, Optional.empty());

    Pagination.Progress progress = pagination.next(pagination.first(), REQUEST_URL, arrayOf(0));

    assertEquals(new Pagination.Progress.Accepted(0, Optional.empty()), progress);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "5                   | 5",
        "5.0                 | 5",
        "0                   | 0",
        "3000000000          | 3000000000",
        "'[\"a\", \"b\"]'    | 2",
        "[]                  | 0",
        "null                | 0"
      })
  void next_withACountPath_takesTheCountFromAWholeNumberAnArrayOrNull(String value, long count)
      throws JsonProcessingException {
    LimitOffsetPagination pagination = withoutLimit(10, Optional.of(COUNT_PATH));

    Pagination.Progress progress =
        pagination.next(pagination.first(), REQUEST_URL, countOf(MAPPER.readTree(value)));

    Pagination.Progress.Accepted accepted =
        assertInstanceOf(Pagination.Progress.Accepted.class, progress);
    assertEquals(count, accepted.records());
    assertEquals(
        count == 0
            ? Optional.empty()
            : Optional.of(List.of(new Parameter("offset", Long.toString(10 + count)))),
        accepted.next().map(Page::queryParameters));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"-1", "1.5", "\"3\"", "true", "{}", "absent", "1e19", "12345678901234567890"})
  void next_withACountPathValueThatIsNotACount_rejectsWithoutTheValue(String value)
      throws JsonProcessingException {
    LimitOffsetPagination pagination = withLimit(0, Optional.of(COUNT_PATH));
    JsonNode count = "absent".equals(value) ? MissingNode.getInstance() : MAPPER.readTree(value);

    Pagination.Progress progress = pagination.next(pagination.first(), REQUEST_URL, countOf(count));

    assertEquals(
        new Pagination.Progress.Rejected("the value at $.meta.count is not a record count"),
        progress);
  }

  @Test
  void next_fromTheLargestStartOffset_countsPastTheIntRange() {
    LimitOffsetPagination pagination =
        new LimitOffsetPagination(
            "offset", Integer.MAX_VALUE, Optional.of(new Limit("limit", 2)), Optional.empty());

    Page next = nextPage(pagination, pagination.first(), arrayOf(2));

    assertEquals(new Parameter("offset", "2147483649"), next.queryParameters().get(1));
  }

  @Test
  void next_withACountThatCarriesTheOffsetPastTheLongRange_rejects() {
    LimitOffsetPagination pagination = withoutLimit(1, Optional.of(COUNT_PATH));

    Pagination.Progress progress =
        pagination.next(pagination.first(), REQUEST_URL, countOf(LongNode.valueOf(Long.MAX_VALUE)));

    assertEquals(
        new Pagination.Progress.Rejected("the next offset would be larger than " + Long.MAX_VALUE),
        progress);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void isPastTheEnd_isTrueFor400Or404AfterTheFirstRequestOnly(int status) {
    LimitOffsetPagination pagination = withLimit(0, Optional.empty());
    Page second = nextPage(pagination, pagination.first(), arrayOf(20));

    assertTrue(pagination.isPastTheEnd(second, status));
    assertFalse(pagination.isPastTheEnd(pagination.first(), status));
    assertFalse(pagination.isPastTheEnd(second, 500));
  }

  private static LimitOffsetPagination withLimit(int startOffset, Optional<JsonPath> countPath) {
    return new LimitOffsetPagination("offset", startOffset, Optional.of(LIMIT), countPath);
  }

  private static LimitOffsetPagination withoutLimit(int startOffset, Optional<JsonPath> countPath) {
    return new LimitOffsetPagination("offset", startOffset, Optional.empty(), countPath);
  }

  private static PageContent arrayOf(int records) {
    return new PageContent(records, Map.of(), Map.of());
  }

  private static PageContent countOf(JsonNode count) {
    return new PageContent(1, Map.of(COUNT_PATH, count), Map.of());
  }

  private static Page nextPage(LimitOffsetPagination pagination, Page page, PageContent content) {
    Pagination.Progress.Accepted accepted =
        assertInstanceOf(
            Pagination.Progress.Accepted.class, pagination.next(page, REQUEST_URL, content));
    return accepted.next().orElseThrow();
  }
}

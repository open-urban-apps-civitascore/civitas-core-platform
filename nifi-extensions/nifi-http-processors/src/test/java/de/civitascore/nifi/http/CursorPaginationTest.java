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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import de.civitascore.nifi.http.ApiCall.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CursorPaginationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonPath CURSOR_PATH = new JsonPath("$.next_cursor");
  private static final Limit LIMIT = new Limit("limit", 20);

  @Test
  void limit_withASizeBelow1_throws() {
    assertThrows(IllegalArgumentException.class, () -> new Limit("limit", 0));
  }

  @Test
  void first_sendsTheLimitAndNoCursor() {
    Page first = inQuery(Optional.of(LIMIT)).first();

    assertEquals(List.of(new Parameter("limit", "20")), first.queryParameters());
    assertEquals(List.of(), first.headers());
  }

  @Test
  void first_withoutALimit_sendsNoParameter() {
    Page first = inQuery(Optional.empty()).first();

    assertEquals(List.of(), first.queryParameters());
    assertEquals(List.of(), first.headers());
  }

  @Test
  void shape_isARecordObjectThatReadsTheCursorPath() {
    assertEquals(
        new ResponseShape.RecordObject(List.of(CURSOR_PATH)), inQuery(Optional.empty()).shape());
  }

  @Test
  void next_withAStringCursorInTheQuery_sendsItAfterTheLimit() {
    CursorPagination pagination = inQuery(Optional.of(LIMIT));

    Page next = nextPage(pagination, pagination.first(), new TextNode("abc 123"));

    assertEquals(
        List.of(new Parameter("limit", "20"), new Parameter("cursor", "abc 123")),
        next.queryParameters());
    assertEquals(List.of(), next.headers());
  }

  @Test
  void next_withACursorInAHeader_sendsItAsAHeaderOnly() {
    CursorPagination pagination =
        new CursorPagination(CURSOR_PATH, "X-Cursor", ParameterLocation.HEADER, Optional.of(LIMIT));

    Page next = nextPage(pagination, pagination.first(), new TextNode("abc123"));

    assertEquals(List.of(new Parameter("limit", "20")), next.queryParameters());
    assertEquals(List.of(new Parameter("X-Cursor", "abc123")), next.headers());
  }

  @ParameterizedTest
  @ValueSource(strings = {"42", "-7", "12345678901234567890", "1.50", "1e2", "-0"})
  void next_withANumberCursor_sendsItsJsonText(String number) throws JsonProcessingException {
    CursorPagination pagination = inQuery(Optional.empty());
    PageContent content =
        new PageContent(
            1, Map.of(CURSOR_PATH, MAPPER.readTree(number)), Map.of(CURSOR_PATH, number));

    Page next = nextPage(pagination, pagination.first(), content);

    assertEquals(List.of(new Parameter("cursor", number)), next.queryParameters());
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"\"", "absent"})
  void next_withACursorThatIsAbsentNullOrEmpty_endsTheRun(String value)
      throws JsonProcessingException {
    CursorPagination pagination = inQuery(Optional.empty());
    JsonNode cursor = "absent".equals(value) ? MissingNode.getInstance() : MAPPER.readTree(value);

    Pagination.Progress turn = pagination.next(pagination.first(), content(cursor));

    assertEquals(new Pagination.Progress.Accepted(1, Optional.empty()), turn);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"{\"c\":\"v-61b0\"} | object", "[\"v-61b0\"] | array", "true | boolean"})
  void next_withACursorThatIsAnObjectAnArrayOrABoolean_rejectsWithoutTheValue(
      String value, String type) throws JsonProcessingException {
    CursorPagination pagination = inQuery(Optional.empty());

    Pagination.Progress turn = pagination.next(pagination.first(), content(MAPPER.readTree(value)));

    assertEquals(
        new Pagination.Progress.Rejected(
            "the value at $.next_cursor is a JSON " + type + ", not a string or a number"),
        turn);
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\rb", "a\nb", "a\tb", "a\u0000b", "a\u0085b", "a\u007fb", "a€b"})
  void next_withARefusedCharacterInAHeaderCursor_rejectsWithoutTheCursor(String cursor) {
    CursorPagination pagination =
        new CursorPagination(CURSOR_PATH, "X-Cursor", ParameterLocation.HEADER, Optional.empty());

    Pagination.Progress turn = pagination.next(pagination.first(), content(new TextNode(cursor)));

    Pagination.Progress.Rejected rejected =
        assertInstanceOf(Pagination.Progress.Rejected.class, turn);
    assertEquals(
        "the value at $.next_cursor holds a character that a header value must not hold",
        rejected.reason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\nb", "a€b"})
  void next_withACharacterOutsideAHeaderValueInAQueryCursor_sendsIt(String cursor) {
    CursorPagination pagination = inQuery(Optional.empty());

    Page next = nextPage(pagination, pagination.first(), new TextNode(cursor));

    assertEquals(List.of(new Parameter("cursor", cursor)), next.queryParameters());
  }

  @Test
  void next_withAnAccentInAHeaderCursor_sendsIt() {
    CursorPagination pagination =
        new CursorPagination(CURSOR_PATH, "X-Cursor", ParameterLocation.HEADER, Optional.empty());

    Page next = nextPage(pagination, pagination.first(), new TextNode("café 1"));

    assertEquals(List.of(new Parameter("X-Cursor", "café 1")), next.headers());
  }

  @Test
  void next_namesEachPageByItsRequestNumber() {
    CursorPagination pagination = inQuery(Optional.empty());

    Page first = pagination.first();
    Page second = nextPage(pagination, first, new TextNode("a"));
    Page third = nextPage(pagination, second, new TextNode("b"));

    assertEquals(
        List.of(Optional.of("request 1"), Optional.of("request 2"), Optional.of("request 3")),
        List.of(first.title(), second.title(), third.title()));
    assertEquals(List.of(0L, 1L, 2L), List.of(first.index(), second.index(), third.index()));
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void isPastTheEnd_isFalseFor400And404(int status) {
    CursorPagination pagination = inQuery(Optional.empty());
    Page second = nextPage(pagination, pagination.first(), new TextNode("a"));

    assertFalse(pagination.isPastTheEnd(second, status));
  }

  private static CursorPagination inQuery(Optional<Limit> limit) {
    return new CursorPagination(CURSOR_PATH, "cursor", ParameterLocation.QUERY, limit);
  }

  private static PageContent content(JsonNode cursor) {
    return new PageContent(1, Map.of(CURSOR_PATH, cursor), Map.of());
  }

  private static Page nextPage(CursorPagination pagination, Page page, JsonNode cursor) {
    return nextPage(pagination, page, content(cursor));
  }

  private static Page nextPage(CursorPagination pagination, Page page, PageContent content) {
    Pagination.Progress.Accepted accepted =
        assertInstanceOf(Pagination.Progress.Accepted.class, pagination.next(page, content));
    return accepted.next().orElseThrow();
  }
}

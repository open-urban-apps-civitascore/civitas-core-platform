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

import static de.civitascore.nifi.http.GetHttpApiCallRunners.assertRouted;
import static de.civitascore.nifi.http.GetHttpApiCallRunners.invalid;
import static de.civitascore.nifi.http.GetHttpApiCallRunners.visible;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.nifi.http.ApiEndpoint.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GetHttpApiCallEntriesPathTest {

  private static final String A1 = "{\"stationid\":\"a1\"}";
  private static final String A2 = "{\"stationid\":\"a2\"}";
  private static final String A3 = "{\"stationid\":\"a3\"}";

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "{\"meta\":{\"n\":2},\"data\": [ {\"stationid\":\"a1\",\"lat\":1.50},"
            + " {\"stationid\":\"a2\"} ] } | [ {\"stationid\":\"a1\",\"lat\":1.50},"
            + " {\"stationid\":\"a2\"} ]",
        "{\"data\":{\"stationid\":\"a1\",\"lat\":1e2}} | {\"stationid\":\"a1\",\"lat\":1e2}",
        "﻿{\"data\":[{\"stationid\":\"a1\"}]} | [{\"stationid\":\"a1\"}]",
        "{\"meta\":\"ä€😀\",\"data\":[{\"stationid\":\"ü\"}]} | [{\"stationid\":\"ü\"}]",
        "{\"data\":[]} | []",
        "{\"data\":null} | []",
      })
  void onTrigger_withoutPagination_deliversTheValueAtThePathAsTheResponseWritesIt(
      String response, String delivered) throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, response)) {
      TestRunner runner = runner(endpoint, "$.data", null);

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      runner
          .getFlowFilesForRelationship(GetHttpApiCall.SUCCESS)
          .get(0)
          .assertContentEquals(delivered);
    }
  }

  @Test
  void onTrigger_withAQuotedPathIntoAnArray_deliversTheValueAtThePath()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(200, "[{\"result set\":{\"items\":[" + A1 + "]}}]")) {
      TestRunner runner = runner(endpoint, "$[0]['result set'].items", null);

      runner.run();

      runner
          .getFlowFilesForRelationship(GetHttpApiCall.SUCCESS)
          .get(0)
          .assertContentEquals("[" + A1 + "]");
    }
  }

  /** The Record Schema requires {@code stationid}, which the wrapping object does not have. */
  @Test
  void onTrigger_whenAnEntryViolatesTheDataStructure_routesTheRunToStop()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(200, "{\"data\":[" + A1 + ",{\"name\":\"x\"}]}")) {
      TestRunner runner = runner(endpoint, "$.data", null);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 2 records at $.data violate the Data structure: record 1:"
              + " required stationid");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", " null\n"})
  void onTrigger_whenTheResponseIsOnlyNull_createsNoFlowFile(String body)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint, "$.data", null);

      runner.run();

      runner.assertTransferCount(GetHttpApiCall.SUCCESS, 0);
      runner.assertTransferCount(GetHttpApiCall.STOP, 0);
      runner.assertTransferCount(GetHttpApiCall.FAILURE, 0);
      assertTrue(runner.getLogger().getErrorMessages().isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"7", "\"val-1f3e\"", "true", "null", "[]", "[{\"stationid\":\"a0\"}]"})
  void onTrigger_whenAnEntryIsNotAnObjectAndTheSchemaAllowsIt_routesTheRunToStop(String element)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(200, "{\"data\":[" + element + "," + A1 + "]}")) {
      TestRunner runner = runner(endpoint, "$.data", null);
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, "{\"required\": [\"stationid\"]}");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 2 records at $.data violate the Data structure: record 0:"
              + " type");
      assertTrue(visible(runner).stream().noneMatch(text -> text.contains("val-1f3e")));
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "{\"meta\":{}} | the response has no value at $.data",
        "{\"data\":{\"stationid\":\"a1\"}} | the response has no value at $.items",
        "[] | the response has no value at $.data",
        "'\"s-7d1e\"' | the response has no value at $.data",
        "{\"data\":[],\"data\":[]} | the response holds more than one value at $.data",
        "{\"data\":null,\"data\":null} | the response holds more than one value at $.data",
        "{\"data\":[{\"stationid\":\"a1\"} | the response is not JSON",
        "{\"data\":42} | the value at $.data is a JSON number, not an object or an array",
        "{\"data\":\"s-7d1e\"} | the value at $.data is a JSON string, not an object or an array",
        "{\"data\":true} | the value at $.data is a JSON boolean, not an object or an array",
      })
  void onTrigger_withoutEntriesAtThePath_routesTheRunToStopWithoutTheValue(
      String response, String reason) throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, response)) {
      TestRunner runner = runner(endpoint, reason.contains("$.items") ? "$.items" : "$.data", null);

      runner.run();

      assertRouted(runner, GetHttpApiCall.STOP, 200, "API call \"Stations\": " + reason);
      assertTrue(visible(runner).stream().noneMatch(text -> text.contains("s-7d1e")));
    }
  }

  @Test
  void onTrigger_withPageNumberPagination_countsTheEntries() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(wrapped("[" + A1 + "," + A2 + "]")),
            Response.json(wrapped("[" + A3 + "]")))) {
      TestRunner runner =
          runner(
              endpoint,
              "$.data",
              "{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\","
                  + "\"pageSizeParameter\":\"size\",\"pageSize\":2}");

      runner.run();

      assertDelivered(runner, "[" + A1 + "," + A2 + "]", "[" + A3 + "]");
      assertEquals(2, endpoint.requests().size());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "null"})
  void onTrigger_withPageNumberPaginationAndNoEntries_endsTheRunWithTheEarlierPages(String entries)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(wrapped("[" + A1 + "," + A2 + "]")), Response.json(wrapped(entries)))) {
      TestRunner runner =
          runner(
              endpoint,
              "$.data",
              "{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\","
                  + "\"pageSizeParameter\":\"size\",\"pageSize\":2}");

      runner.run();

      assertDelivered(runner, "[" + A1 + "," + A2 + "]");
      assertEquals(2, endpoint.requests().size());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "null"})
  void onTrigger_withLimitOffsetPaginationWithoutACountPath_advancesByTheEntries(String last)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(wrapped("[" + A1 + "," + A2 + "]")),
            Response.json(wrapped(A3)),
            Response.json(wrapped(last)))) {
      TestRunner runner =
          runner(endpoint, "$.data", "{\"type\":\"LIMIT_OFFSET\",\"offsetParameter\":\"offset\"}");

      runner.run();

      assertEquals(List.of("offset=0", "offset=2", "offset=3"), rawQueries(endpoint));
      assertDelivered(runner, "[" + A1 + "," + A2 + "]", A3);
    }
  }

  @Test
  void onTrigger_withLimitOffsetPaginationAndACountPath_advancesByTheCountAtTheTop()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"meta\":{\"count\":2},\"data\":[" + A1 + "]}"),
            Response.json("{\"meta\":{\"count\":0},\"data\":[]}"))) {
      TestRunner runner =
          runner(
              endpoint,
              "$.data",
              "{\"type\":\"LIMIT_OFFSET\",\"offsetParameter\":\"offset\","
                  + "\"countPath\":\"$.meta.count\"}");

      runner.run();

      assertEquals(List.of("offset=0", "offset=2"), rawQueries(endpoint));
      assertDelivered(runner, "[" + A1 + "]");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"$.data", "$['data']"})
  void onTrigger_withACountPathThatLeadsToTheEntries_countsTheEntries(String countPath)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(wrapped(A1)), Response.json(wrapped("null")))) {
      TestRunner runner =
          runner(
              endpoint,
              "$.data",
              "{\"type\":\"LIMIT_OFFSET\",\"offsetParameter\":\"offset\",\"countPath\":\""
                  + countPath
                  + "\"}");

      runner.run();

      assertEquals(List.of("offset=0", "offset=1"), rawQueries(endpoint));
      assertDelivered(runner, A1);
    }
  }

  @Test
  void onTrigger_withCursorPagination_readsTheCursorFromTheTop() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"data\":[" + A1 + "],\"next_cursor\":\"c2\"}"),
            Response.json("{\"next_cursor\":null,\"data\":" + A2 + "}"))) {
      TestRunner runner =
          runner(
              endpoint,
              "$.data",
              "{\"type\":\"CURSOR\",\"cursorPath\":\"$.next_cursor\","
                  + "\"cursorParameter\":\"cursor\",\"cursorIn\":\"QUERY\"}");

      runner.run();

      assertEquals("cursor=c2", rawQueries(endpoint).get(1));
      assertDelivered(runner, "[" + A1 + "]", A2);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.50", "1e2", "-0"})
  void onTrigger_withANumberCursorAtTheTop_sendsItsJsonText(String cursor)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"next\":" + cursor + ",\"data\":[" + A1 + "]}"),
            Response.json("{\"data\":[]}"))) {
      TestRunner runner =
          runner(
              endpoint,
              "$.data",
              "{\"type\":\"CURSOR\",\"cursorPath\":\"$.next\","
                  + "\"cursorParameter\":\"cursor\",\"cursorIn\":\"QUERY\"}");

      runner.run();

      assertEquals("cursor=" + cursor.replace("+", "%2B"), rawQueries(endpoint).get(1));
    }
  }

  @Test
  void onTrigger_withACursorInsideAnEntry_readsTheCursorFromTheEntry()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"data\":[{\"stationid\":\"a1\",\"next\":7}]}"),
            Response.json("{\"data\":[]}"))) {
      TestRunner runner =
          runner(
              endpoint,
              "$.data",
              "{\"type\":\"CURSOR\",\"cursorPath\":\"$.data[0].next\","
                  + "\"cursorParameter\":\"cursor\",\"cursorIn\":\"QUERY\"}");

      runner.run();

      assertEquals("cursor=7", rawQueries(endpoint).get(1));
    }
  }

  @Test
  void onTrigger_withNextLinkPagination_readsTheLinkFromTheTop() throws Exception {
    AtomicReference<String> apiUrl = new AtomicReference<>();
    try (ApiEndpoint endpoint =
        ApiEndpoint.startHttp(
            index ->
                Response.json(
                    index == 0
                        ? "{\"value\":["
                            + A1
                            + "],\"@odata.nextLink\":\""
                            + apiUrl.get()
                            + "/stations?skip=1\"}"
                        : "{\"value\":[]}"))) {
      apiUrl.set(endpoint.baseUrl());
      TestRunner runner =
          runner(
              endpoint,
              "$.value",
              "{\"type\":\"NEXT_LINK\"," + "\"nextLinkPath\":\"$['@odata.nextLink']\"}");

      runner.run();

      assertEquals("skip=1", rawQueries(endpoint).get(1));
      assertDelivered(runner, "[" + A1 + "]");
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "$.data | {\"type\":\"CURSOR\",\"cursorPath\":\"$.data\",\"cursorParameter\":\"c\","
            + "\"cursorIn\":\"QUERY\"} | pagination.cursorPath",
        "$.page.data | {\"type\":\"CURSOR\",\"cursorPath\":\"$['page']\",\"cursorParameter\":\"c\","
            + "\"cursorIn\":\"QUERY\"} | pagination.cursorPath",
        "$.value | {\"type\":\"NEXT_LINK\",\"nextLinkPath\":\"$.value\"} | pagination.nextLinkPath",
        "$.page.data | {\"type\":\"LIMIT_OFFSET\",\"offsetParameter\":\"offset\","
            + "\"countPath\":\"$.page\"} | pagination.countPath",
      })
  void validate_withAPaginationPathAtOrAroundTheEntries_namesTheKey(
      String entriesPath, String pagination, String keyPath) throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "{}")) {
      TestRunner runner = runner(endpoint, entriesPath, pagination);

      runner.assertNotValid();
      assertTrue(
          invalid(runner).stream()
              .anyMatch(
                  result ->
                      result
                          .getExplanation()
                          .contains(
                              keyPath
                                  + " must not lead to the value at entriesPath or to a value"
                                  + " around it")),
          () -> invalid(runner).toString());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void validate_withABlankEntriesPath_isValid(String entriesPath) throws InitializationException {
    TestRunner runner =
        GetHttpApiCallRunners.runner(new GetHttpApiCall(), "http://localhost", null);
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\":\"Stations\",\"path\":\"/stations\",\"entriesPath\":\"" + entriesPath + "\"}");

    runner.assertValid();
  }

  @ParameterizedTest
  @ValueSource(strings = {"$..data", "data", "$.data[?(@.x)]", "$"})
  void validate_withAnInvalidEntriesPath_namesTheKey(String entriesPath)
      throws InitializationException {
    TestRunner runner =
        GetHttpApiCallRunners.runner(new GetHttpApiCall(), "http://localhost", null);
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\":\"Stations\",\"path\":\"/stations\",\"entriesPath\":\"" + entriesPath + "\"}");

    runner.assertNotValid();
    assertTrue(
        invalid(runner).stream()
            .anyMatch(result -> result.getExplanation().contains("entriesPath must start with $")),
        () -> invalid(runner).toString());
  }

  private static TestRunner runner(ApiEndpoint endpoint, String entriesPath, String pagination)
      throws InitializationException {
    TestRunner runner =
        GetHttpApiCallRunners.runner(new GetHttpApiCall(), endpoint.baseUrl(), null);
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\":\"Stations\",\"path\":\"/stations\",\"entriesPath\":\""
            + entriesPath
            + "\",\"pagination\":"
            + pagination
            + "}");
    return runner;
  }

  /** A response that holds the entries under {@code data}, next to a value that is no entry. */
  private static String wrapped(String entries) {
    return "{\"data\":" + entries + ",\"meta\":{\"source\":\"test\"}}";
  }

  private static void assertDelivered(TestRunner runner, String... contents) {
    runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, contents.length);
    List<MockFlowFile> delivered = runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS);
    for (int i = 0; i < contents.length; i++) {
      delivered.get(i).assertContentEquals(contents[i]);
    }
  }

  private static List<String> rawQueries(ApiEndpoint endpoint) {
    return endpoint.requests().stream().map(ApiEndpoint.Request::rawQuery).toList();
  }
}

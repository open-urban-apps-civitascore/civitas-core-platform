/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Shared OkHttp/Jackson glue for adapters that speak JSON over {@link okhttp3.OkHttpClient}:
 * building a JSON {@link RequestBody} via the shared {@link PayloadConverter} mapper, executing a
 * {@link Request}, and reading a JSON or plain-text {@link Response} body back.
 */
public final class OkHttpJson {

  public static final MediaType JSON = MediaType.get("application/json");

  private OkHttpJson() {}

  /**
   * Builds a URL for a path relative to {@code baseUrl}, tolerating an optional leading slash on
   * {@code path} (stripped before {@link HttpUrl.Builder#addPathSegments}, which otherwise treats
   * it as an empty leading segment).
   */
  public static HttpUrl url(String baseUrl, String path) {
    return HttpUrl.get(baseUrl).newBuilder().addPathSegments(path.replaceFirst("^/", "")).build();
  }

  /** A request builder pre-set to accept JSON responses. */
  public static Request.Builder jsonRequest(HttpUrl url) {
    return new Request.Builder().url(url).header("Accept", "application/json");
  }

  /** Executes a request, wrapping OkHttp's checked {@link IOException} as unchecked. */
  public static Response execute(OkHttpClient client, Request request) {
    try {
      return client.newCall(request).execute();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Serializes a value to a JSON request body via the shared, pre-configured mapper. */
  public static RequestBody jsonBody(Object value) throws JsonProcessingException {
    return RequestBody.create(PayloadConverter.objectMapper().writeValueAsString(value), JSON);
  }

  /**
   * Like {@link #jsonBody(Object)}, wrapping a serialization failure as {@link
   * UncheckedIOException} for callers whose surrounding method declares no checked exception.
   */
  public static RequestBody jsonBodyUnchecked(Object value) {
    try {
      return jsonBody(value);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Reads a JSON object response body as a map, via the shared, pre-configured mapper. */
  public static Map<String, Object> readJsonMap(Response response) {
    try {
      return PayloadConverter.readMap(response.body().bytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Reads the response body for an error message; an unreadable body degrades to empty. */
  public static String readBody(Response response) {
    try {
      return response.body().string();
    } catch (IOException e) {
      return "";
    }
  }
}

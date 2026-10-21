/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static de.civitascore.configadapter.connector.ConnectorValues.objectOrEmpty;
import static de.civitascore.configadapter.connector.ConnectorValues.trimmedString;

import de.civitascore.configadapter.connector.ConnectorValues;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Runs the named API call of an HTTP connector once and returns its status code and body. A
 * redirect is not followed, and the body is read up to the preview limit.
 */
final class HttpProber implements ConnectionProber {

  /** The connect timeout of the pipeline's web client. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /** Read past the preview limit, longer than any credential, so the cut cannot split one. */
  private static final int MASK_MARGIN_BYTES = 1024;

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  /** Content encodings whose body the preview can show. */
  private static final Set<String> DECODABLE_ENCODINGS =
      Set.of("identity", "gzip", "x-gzip", "deflate");

  private final DestinationCheck guard;
  private final Duration timeout;

  HttpProber(DestinationCheck guard, Duration timeout) {
    this.guard = guard;
    this.timeout = timeout;
  }

  @Override
  public String connectionType() {
    return "http";
  }

  @Override
  @SuppressWarnings("PMD.CloseResource") // shutdownNow() in finally; close() waits for a hang
  public ProbeResult probe(Map<String, Object> connector, String apiCall, Cancellation cancellation)
      throws ConnectionTestFailure {
    Map<String, Object> call = apiCall(connector, apiCall);
    String baseUrl = trimmedString(connector.get("baseUrl"));
    URI uri = uri(baseUrl, call);
    guard.check(uri.getHost());
    Map<String, Object> authentication = objectOrEmpty(connector.get("authentication"));
    boolean basic = "BASIC".equals(authentication.get("type"));
    if (basic && !"https".equalsIgnoreCase(uri.getScheme())) {
      throw ConnectionTestFailure.invalid("Basic authentication needs an https base URL.");
    }
    String credential = basic ? credential(authentication) : null;
    HttpRequest request = request(uri, call, credential);

    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();
    cancellation.register(client::shutdownNow);
    try {
      CompletableFuture<HttpResponse<InputStream>> exchange =
          client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
      HttpResponse<InputStream> response = exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return result(response, credential);
    } catch (ExecutionException e) {
      throw new ConnectionTestFailure(
          "The API did not answer: " + ConnectionTestFailure.describe(e.getCause()), e);
    } catch (TimeoutException e) {
      throw ConnectionTestFailure.timeout("The API did not answer in time.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ConnectionTestFailure.timeout("The test was stopped.", e);
    } finally {
      // close() would wait for an exchange that hangs; the answer is read by now.
      client.shutdownNow();
    }
  }

  private static Map<String, Object> apiCall(Map<String, Object> connector, String name)
      throws ConnectionTestFailure {
    if (name == null || name.isBlank()) {
      throw ConnectionTestFailure.invalid("The test names no API call.");
    }
    if (connector.get("apiCalls") instanceof List<?> calls) {
      for (Object call : calls) {
        if (call instanceof Map<?, ?> candidate && name.equals(candidate.get("name"))) {
          return objectOrEmpty(candidate);
        }
      }
    }
    throw ConnectionTestFailure.invalid("The connector has no API call named '" + name + "'.");
  }

  /**
   * Base URL without a trailing {@code /}, then the path, then the query.
   *
   * @throws ConnectionTestFailure if the result is not an http or https URL with a host
   */
  static URI uri(String baseUrl, Map<String, Object> call) throws ConnectionTestFailure {
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String query = query(call.get("queryParameters"));
    URI uri;
    try {
      uri =
          URI.create(base + trimmedString(call.get("path")) + (query.isEmpty() ? "" : "?" + query));
    } catch (IllegalArgumentException e) {
      throw ConnectionTestFailure.invalid("The base URL and path do not form a valid URL.", e);
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
      throw ConnectionTestFailure.invalid("The base URL must be an http or https URL with a host.");
    }
    if (uri.getRawUserInfo() != null) {
      throw ConnectionTestFailure.invalid("The base URL must not contain credentials.");
    }
    return uri;
  }

  /** The base64 {@code user:password} of a basic authentication. */
  private static String credential(Map<String, Object> basic) {
    String credential = basic.get("username") + ":" + basic.get("password");
    return Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
  }

  private HttpRequest request(URI uri, Map<String, Object> call, String credential)
      throws ConnectionTestFailure {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET().timeout(timeout);
    try {
      for (Map<String, Object> header : parameters(call.get("headers"))) {
        request.header(String.valueOf(header.get("name")), valueOf(header));
      }
      if (credential != null) {
        request.header("Authorization", "Basic " + credential);
      }
      return request.build();
    } catch (IllegalArgumentException e) {
      // The JDK HTTP client puts the header value into this message, so it is not passed on.
      throw ConnectionTestFailure.invalid("The API call has a header that cannot be sent.", e);
    }
  }

  /** An API that echoes the request would return the basic credential, which is a secret. */
  static String maskCredential(String text, String credential) {
    return credential == null ? text : text.replace(credential, ResultText.MASK);
  }

  private static ProbeResult result(HttpResponse<InputStream> response, String credential)
      throws ConnectionTestFailure {
    int status = response.statusCode();
    boolean success = status >= 200 && status < 300;
    String message = "The API answered with HTTP " + status + ".";
    if (status >= 300 && status < 400) {
      message += " The pipeline does not follow redirects.";
    }
    Body body = read(response);
    if (success && body.bytes() > 0) {
      String unreadable = unreadableByPipeline(body);
      if (unreadable != null) {
        success = false;
        message += " " + unreadable;
      } else if (JsonBody.classify(body.text(), body.truncated()) == JsonBody.NO_RECORDS) {
        message +=
            " The pipeline reads no record from it: it reads JSON objects, alone or in an array.";
      }
    }
    if (body.text() != null) {
      return new ProbeResult(
          success,
          message,
          maskCredential(body.text(), credential),
          body.truncated(),
          null,
          status);
    }
    String preview =
        body.bytes() == 0
            ? null
            : "("
                + (body.truncated() ? "more than " : "")
                + body.bytes()
                + " bytes "
                + body.kind()
                + ", not shown)";
    return new ProbeResult(success, message, preview, false, null, status);
  }

  /** Why the pipeline cannot read a body, or null if it can. */
  private static String unreadableByPipeline(Body body) {
    if (!"identity".equals(body.encoding())) {
      return "The body is compressed ("
          + body.encoding()
          + "), which the pipeline does not unpack.";
    }
    if (body.text() == null
        || JsonBody.classify(body.text(), body.truncated()) == JsonBody.NOT_JSON) {
      return "The body is not JSON; the pipeline reads JSON only.";
    }
    return null;
  }

  /**
   * Reads beyond the preview limit, to tell whether the body is longer and to keep a secret that
   * crosses the limit whole for masking. The limit applies to the decompressed bytes.
   */
  private static Body read(HttpResponse<InputStream> response) throws ConnectionTestFailure {
    String contentType =
        response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
    String encoding =
        response
            .headers()
            .firstValue("Content-Encoding")
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .orElse("identity");
    boolean decodable = DECODABLE_ENCODINGS.contains(encoding);
    byte[] bytes;
    try (InputStream body = response.body()) {
      bytes =
          (decodable ? decoded(body, encoding) : body)
              .readNBytes(ResultText.MAX_PREVIEW_BYTES + MASK_MARGIN_BYTES);
    } catch (IOException e) {
      throw new ConnectionTestFailure(
          "The API answered with HTTP "
              + response.statusCode()
              + ", but the body could not be read: "
              + ConnectionTestFailure.describe(e),
          e);
    }
    boolean truncated = bytes.length > ResultText.MAX_PREVIEW_BYTES;
    int length = Math.min(bytes.length, ResultText.MAX_PREVIEW_BYTES);
    if (!decodable) {
      return new Body(null, length, truncated, "encoded as " + encoding, encoding);
    }
    if (length == 0 || !isText(contentType, bytes)) {
      return new Body(
          null,
          length,
          truncated,
          "of " + (contentType.isEmpty() ? "binary data" : contentType),
          encoding);
    }
    return new Body(
        new String(bytes, charset(contentType)), length, truncated, "of " + contentType, encoding);
  }

  private static InputStream decoded(InputStream body, String encoding) throws IOException {
    return switch (encoding) {
      case "gzip", "x-gzip" -> new GZIPInputStream(body);
      case "deflate" -> new InflaterInputStream(body);
      default -> body;
    };
  }

  private static boolean isText(String contentType, byte[] bytes) {
    if (contentType.isEmpty()) {
      for (byte b : bytes) {
        if (b == 0) {
          return false;
        }
      }
      return true;
    }
    return contentType.startsWith("text/")
        || contentType.contains("json")
        || contentType.contains("xml")
        || contentType.contains("csv")
        || contentType.contains("javascript")
        || contentType.contains("yaml");
  }

  private static Charset charset(String contentType) {
    for (String part : contentType.split(";")) {
      String trimmed = part.trim();
      if (trimmed.startsWith("charset=")) {
        String name = trimmed.substring("charset=".length()).replace("\"", "");
        try {
          return Charset.forName(name);
        } catch (IllegalArgumentException e) {
          return StandardCharsets.UTF_8;
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  /** The parameters in their order; an entry that is not an object is skipped. */
  private static List<Map<String, Object>> parameters(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().filter(Map.class::isInstance).map(ConnectorValues::objectOrEmpty).toList();
  }

  private static String valueOf(Map<String, Object> parameter) {
    return Optional.ofNullable(parameter.get("value")).map(String::valueOf).orElse("");
  }

  /** Joins the parameters in their order, each name and value percent-encoded byte for byte. */
  private static String query(Object parameters) {
    StringBuilder query = new StringBuilder();
    for (Map<String, Object> parameter : parameters(parameters)) {
      if (!query.isEmpty()) {
        query.append('&');
      }
      encode(String.valueOf(parameter.get("name")), query);
      query.append('=');
      encode(valueOf(parameter), query);
    }
    return query.toString();
  }

  /** Percent-encodes all but the unreserved characters of RFC 3986, so a space is {@code %20}. */
  private static void encode(String text, StringBuilder out) {
    for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xFF;
      if (unreserved(c)) {
        out.append((char) c);
      } else {
        out.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
      }
    }
  }

  private static boolean unreserved(int c) {
    return (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '.'
        || c == '_'
        || c == '~';
  }

  /**
   * The start of a response body.
   *
   * @param text the body as text, or null if it is not text
   * @param bytes the number of bytes kept
   * @param truncated whether the body is longer than the bytes kept
   * @param kind what the bytes are, e.g. "of application/zip" or "encoded as br"
   * @param encoding the content encoding the server sent, {@code identity} if none
   */
  private record Body(String text, int bytes, boolean truncated, String kind, String encoding) {}
}

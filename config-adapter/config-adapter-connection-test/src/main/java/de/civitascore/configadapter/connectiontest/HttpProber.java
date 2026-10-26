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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  public ProbeResult probe(
      Map<String, Object> connector, String apiCall, boolean applyWhere, Cancellation cancellation)
      throws ConnectionTestFailure {
    Map<String, Object> authentication = objectOrEmpty(connector.get("authentication"));
    Authentication type = Authentication.read(authentication);
    Map<String, Object> call = apiCall(connector, apiCall);
    ParameterLocation apiTokenIn =
        type == Authentication.API_TOKEN ? apiTokenIn(authentication) : null;
    if (apiTokenIn == ParameterLocation.QUERY) {
      call = withQueryParameter(call, apiToken(authentication));
    }
    URI uri = uri(trimmedString(connector.get("baseUrl")), call);
    guard.check(uri.getHost());
    if (type != Authentication.NONE && !"https".equalsIgnoreCase(uri.getScheme())) {
      throw ConnectionTestFailure.invalid("Authentication needs an https base URL.");
    }
    URI tokenUri = type == Authentication.OAUTH2 ? tokenUri(authentication) : null;

    HttpClient client = client(type, authentication);
    cancellation.register(client::shutdownNow);
    long deadline = System.nanoTime() + timeout.toNanos();
    try {
      String credential = type == Authentication.BASIC ? credential(authentication) : null;
      Map.Entry<String, String> header =
          switch (type) {
            case NONE, CLIENT_CERTIFICATE -> null;
            case BASIC -> Map.entry("Authorization", "Basic " + credential);
            case BEARER_TOKEN ->
                Map.entry("Authorization", "Bearer " + required(authentication, "token"));
            case API_TOKEN ->
                apiTokenIn == ParameterLocation.HEADER ? apiToken(authentication) : null;
            case OAUTH2 ->
                Map.entry(
                    "Authorization",
                    "Bearer " + accessToken(client, tokenUri, authentication, deadline));
          };
      HttpResponse<InputStream> response =
          send(client, request(uri, call, header), "The API", deadline);
      return result(response, credential);
    } finally {
      // close() would wait for an exchange that hangs; the answer is read by now.
      client.shutdownNow();
    }
  }

  private URI tokenUri(Map<String, Object> oauth2) throws ConnectionTestFailure {
    URI tokenUri = ClientCredentialsGrant.tokenUri(required(oauth2, "tokenUrl"));
    guard.check(tokenUri.getHost());
    return tokenUri;
  }

  private static HttpClient client(Authentication type, Map<String, Object> authentication)
      throws ConnectionTestFailure {
    HttpClient.Builder builder =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(HttpClient.Builder.NO_PROXY);
    if (type == Authentication.CLIENT_CERTIFICATE) {
      builder.sslContext(
          ClientCertificate.tls(
              required(authentication, "certificate"), required(authentication, "privateKey")));
    }
    return builder.build();
  }

  /**
   * Sends a request and waits for its answer until {@code deadline}.
   *
   * @param peer who answers, as the subject of a failure message
   */
  private static HttpResponse<InputStream> send(
      HttpClient client, HttpRequest request, String peer, long deadline)
      throws ConnectionTestFailure {
    try {
      return client
          .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
          .get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (ExecutionException e) {
      throw failure(peer, e);
    } catch (TimeoutException e) {
      throw ConnectionTestFailure.timeout(peer + " did not answer in time.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ConnectionTestFailure.timeout("The test was stopped.", e);
    }
  }

  private String accessToken(
      HttpClient client, URI tokenUri, Map<String, Object> oauth2, long deadline)
      throws ConnectionTestFailure {
    HttpRequest request =
        ClientCredentialsGrant.request(
            tokenUri,
            required(oauth2, "clientId"),
            required(oauth2, "clientSecret"),
            trimmedString(oauth2.get("scope")),
            timeout);
    HttpResponse<InputStream> response = send(client, request, "The token endpoint", deadline);
    byte[] body;
    try (InputStream in = response.body()) {
      body = in.readNBytes(ClientCredentialsGrant.MAX_RESPONSE_BYTES);
    } catch (IOException e) {
      throw new ConnectionTestFailure(
          "The token endpoint answered with HTTP "
              + response.statusCode()
              + ", but the body could not be read: "
              + ConnectionTestFailure.describe(e),
          e);
    }
    return ClientCredentialsGrant.accessToken(response.statusCode(), body);
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

  /**
   * A value of the authentication.
   *
   * @throws ConnectionTestFailure if it is missing or blank
   */
  private static String required(Map<String, Object> authentication, String key)
      throws ConnectionTestFailure {
    Object value = authentication.get(key);
    if (trimmedString(value).isEmpty()) {
      throw ConnectionTestFailure.invalid("The authentication has no '" + key + "'.");
    }
    return String.valueOf(value);
  }

  private static ParameterLocation apiTokenIn(Map<String, Object> apiToken)
      throws ConnectionTestFailure {
    try {
      return ParameterLocation.valueOf(trimmedString(apiToken.get("in")));
    } catch (IllegalArgumentException e) {
      throw ConnectionTestFailure.invalid(
          "The API token must be sent in a QUERY parameter or a HEADER.", e);
    }
  }

  private static Map.Entry<String, String> apiToken(Map<String, Object> apiToken)
      throws ConnectionTestFailure {
    return Map.entry(required(apiToken, "name"), required(apiToken, "value"));
  }

  private static Map<String, Object> withQueryParameter(
      Map<String, Object> call, Map.Entry<String, String> parameter) {
    List<Map<String, Object>> parameters = new ArrayList<>(parameters(call.get("queryParameters")));
    parameters.add(
        Map.<String, Object>of("name", parameter.getKey(), "value", parameter.getValue()));
    Map<String, Object> extended = new HashMap<>(call);
    extended.put("queryParameters", parameters);
    return extended;
  }

  /** The base64 {@code user:password} of a basic authentication. */
  private static String credential(Map<String, Object> basic) {
    String credential = basic.get("username") + ":" + basic.get("password");
    return Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
  }

  private HttpRequest request(URI uri, Map<String, Object> call, Map.Entry<String, String> header)
      throws ConnectionTestFailure {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET().timeout(timeout);
    try {
      for (Map<String, Object> parameter : parameters(call.get("headers"))) {
        request.header(String.valueOf(parameter.get("name")), valueOf(parameter));
      }
    } catch (IllegalArgumentException e) {
      // The JDK HTTP client puts the header value into this message, so it is not passed on.
      throw ConnectionTestFailure.invalid("The API call has a header that cannot be sent.", e);
    }
    if (header != null) {
      try {
        request.header(header.getKey(), header.getValue());
      } catch (IllegalArgumentException e) {
        throw ConnectionTestFailure.invalid("The authentication cannot be sent as a header.", e);
      }
    }
    return request.build();
  }

  /** The client's failure, with the reason TIMEOUT when the client timed out. */
  static ConnectionTestFailure failure(String peer, ExecutionException failed) {
    String message = peer + " did not answer: " + ConnectionTestFailure.describe(failed.getCause());
    return ConnectionTestFailure.timedOut(failed)
        ? ConnectionTestFailure.timeout(message, failed)
        : new ConnectionTestFailure(message, failed);
  }

  /**
   * An API that echoes the request would return the basic credential, which is a secret. JSON may
   * write its {@code /} as {@code \/}, as PHP's {@code json_encode} does by default.
   */
  static String maskCredential(String text, String credential) {
    if (credential == null) {
      return text;
    }
    return text.replace(credential, ResultText.MASK)
        .replace(credential.replace("/", "\\/"), ResultText.MASK);
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
    if (success && body.head().length > 0) {
      String unreadable = unreadableByPipeline(body);
      if (unreadable != null) {
        success = false;
        message += " " + unreadable;
      } else if (JsonBody.classify(body.head(), body.truncated()) == JsonBody.NO_RECORDS) {
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
        body.head().length == 0
            ? null
            : "("
                + (body.truncated() ? "more than " : "")
                + body.head().length
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
    if (JsonBody.classify(body.head(), body.truncated()) == JsonBody.NOT_JSON) {
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
    byte[] head = Arrays.copyOf(bytes, Math.min(bytes.length, ResultText.MAX_PREVIEW_BYTES));
    if (!decodable) {
      return new Body(null, head, truncated, "encoded as " + encoding, encoding);
    }
    if (head.length == 0 || !isText(contentType, head)) {
      return new Body(
          null,
          head,
          truncated,
          "of " + (contentType.isEmpty() ? "binary data" : contentType),
          encoding);
    }
    return new Body(
        new String(bytes, charset(contentType)), head, truncated, "of " + contentType, encoding);
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
  static void encode(String text, StringBuilder out) {
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
   * @param head the first bytes of the body, unpacked if it is compressed
   * @param truncated whether the body is longer than {@code head}
   * @param kind what the bytes are, e.g. "of application/zip" or "encoded as br"
   * @param encoding the content encoding the server sent, {@code identity} if none
   */
  private record Body(String text, byte[] head, boolean truncated, String kind, String encoding) {}

  /** The {@code type} of a connector's authentication. */
  private enum Authentication {
    NONE,
    BASIC,
    BEARER_TOKEN,
    API_TOKEN,
    OAUTH2,
    CLIENT_CERTIFICATE;

    /** Absent is NONE. */
    static Authentication read(Map<String, Object> authentication) throws ConnectionTestFailure {
      String type = trimmedString(authentication.get("type"));
      try {
        return type.isEmpty() ? NONE : valueOf(type);
      } catch (IllegalArgumentException e) {
        throw ConnectionTestFailure.invalid(
            "Authentication type '" + type + "' cannot be tested.", e);
      }
    }
  }

  private enum ParameterLocation {
    QUERY,
    HEADER
  }
}

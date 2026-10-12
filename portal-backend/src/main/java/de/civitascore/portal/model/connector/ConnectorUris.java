package de.civitascore.portal.model.connector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parses the URLs of a connector configuration and removes the credentials they carry. */
final class ConnectorUris {

  private static final String JDBC_PREFIX = "jdbc:";
  private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");

  private ConnectorUris() {}

  /**
   * The broker URLs without user info.
   *
   * @throws InvalidConnectorValueException naming {@code key[i]} for a URL without scheme or host
   */
  static List<String> withoutCredentials(List<String> urls, String key) {
    if (urls == null) {
      return null;
    }
    List<String> cleaned = new ArrayList<>(urls.size());
    for (int i = 0; i < urls.size(); i++) {
      String elementKey = key + "[" + i + "]";
      URI uri = parse(urls.get(i), elementKey);
      if (uri.getScheme() == null || uri.getHost() == null) {
        throw new InvalidConnectorValueException(elementKey);
      }
      cleaned.add(withoutUserInfo(uri, elementKey).toString());
    }
    return List.copyOf(cleaned);
  }

  /**
   * The DSN without user info and without a {@code jdbc:} prefix, because the config-adapter builds
   * the JDBC URL from a {@code postgres://} DSN. A blank DSN is null.
   *
   * @throws InvalidConnectorValueException naming {@code key} for a DSN without host
   */
  static String dsnWithoutCredentials(String dsn, String key) {
    if (dsn == null || dsn.isBlank()) {
      return null;
    }
    String withoutPrefix =
        dsn.toLowerCase(Locale.ROOT).startsWith(JDBC_PREFIX)
            ? dsn.substring(JDBC_PREFIX.length())
            : dsn;
    URI uri = parse(withoutPrefix, key);
    if (uri.getHost() == null) {
      throw new InvalidConnectorValueException(key);
    }
    return withoutUserInfo(uri, key).toString();
  }

  /**
   * The API host URL without user info and without a trailing {@code /}. A blank URL is null.
   *
   * @throws InvalidConnectorValueException naming {@code key} for a URL without host, with a scheme
   *     other than {@code http} or {@code https}, or with a query or fragment
   */
  static String httpBaseUrl(String url, String key) {
    if (url == null || url.isBlank()) {
      return null;
    }
    URI uri = parse(url, key);
    if (uri.getHost() == null
        || !HTTP_SCHEMES.contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null) {
      throw new InvalidConnectorValueException(key);
    }
    String cleaned = withoutUserInfo(uri, key).toString();
    int end = cleaned.length();
    while (cleaned.charAt(end - 1) == '/') {
      end--;
    }
    return cleaned.substring(0, end);
  }

  private static URI parse(String value, String key) {
    if (value == null) {
      throw new InvalidConnectorValueException(key);
    }
    try {
      return new URI(value);
    } catch (URISyntaxException e) {
      throw new InvalidConnectorValueException(key, e);
    }
  }

  private static URI withoutUserInfo(URI uri, String key) {
    if (uri.getUserInfo() == null) {
      return uri;
    }
    try {
      return new URI(
          uri.getScheme(),
          null,
          uri.getHost(),
          uri.getPort(),
          uri.getPath(),
          uri.getQuery(),
          uri.getFragment());
    } catch (URISyntaxException e) {
      throw new InvalidConnectorValueException(key, e);
    }
  }
}

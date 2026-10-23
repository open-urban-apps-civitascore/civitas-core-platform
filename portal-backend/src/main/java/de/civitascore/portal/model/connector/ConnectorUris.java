package de.civitascore.portal.model.connector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Parses the URLs of a connector configuration and removes the credentials they carry. */
public final class ConnectorUris {

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
   * @throws InvalidConnectorValueException naming {@code key} for a DSN without host or with a
   *     fragment
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
    // The connection test and the pipeline read the host after the last '@', also behind a '#'.
    if (uri.getHost() == null || uri.getRawFragment() != null) {
      throw new InvalidConnectorValueException(key);
    }
    return withoutUserInfo(uri, key).toString();
  }

  /**
   * The API host URL as {@link #httpUrl} cleans it, and without a trailing {@code /}.
   *
   * @throws InvalidConnectorValueException as {@link #httpUrl} does
   */
  static String httpBaseUrl(String url, String key) {
    String cleaned = httpUrl(url, key);
    if (cleaned == null) {
      return null;
    }
    int end = cleaned.length();
    while (cleaned.charAt(end - 1) == '/') {
      end--;
    }
    return cleaned.substring(0, end);
  }

  /**
   * The http or https URL without user info. A blank URL is null.
   *
   * @throws InvalidConnectorValueException naming {@code key} for a URL without host, with a scheme
   *     other than {@code http} or {@code https}, or with a query or fragment
   */
  static String httpUrl(String url, String key) {
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
    return withoutUserInfo(uri, key).toString();
  }

  /**
   * Every address the connector connects to, in its order. Throws for an unknown connector type, so
   * that it cannot keep a stored secret.
   */
  public static List<String> addresses(ConnectorConfiguration connector) {
    List<String> addresses =
        switch (connector) {
          case SqlConnectorConfiguration sql -> sql.dsn() == null ? null : List.of(sql.dsn());
          case MqttConnectorConfiguration mqtt -> mqtt.urls();
          case HttpConnectorConfiguration http ->
              Stream.of(
                      http.baseUrl(),
                      http.authentication() instanceof HttpConnectorConfiguration.OAuth2 oauth2
                          ? oauth2.tokenUrl()
                          : null)
                  .filter(Objects::nonNull)
                  .toList();
          default ->
              throw new IllegalArgumentException(
                  "No addresses of " + connector.getClass().getSimpleName());
        };
    return addresses == null ? List.of() : addresses;
  }

  /**
   * Scheme, host, port and {@code sslmode} of every address. A stored secret follows the connector
   * only to the same destinations.
   */
  static List<String> destinations(ConnectorConfiguration connector) {
    return addresses(connector).stream()
        .map(address -> origin(address) + " sslmode=" + sslmode(address))
        .toList();
  }

  /**
   * An unparsable URL or a multi-host authority stands for itself and equals only the same text.
   */
  private static String origin(String url) {
    try {
      URI uri = new URI(url);
      if (uri.getHost() == null) {
        return uri.getScheme() + "://" + uri.getRawAuthority();
      }
      return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
    } catch (URISyntaxException e) {
      return url;
    }
  }

  /** The {@code sslmode} parameter of an address, or null if it has none. */
  private static String sslmode(String dsn) {
    try {
      String query = new URI(dsn).getRawQuery();
      if (query == null) {
        return null;
      }
      for (String parameter : query.split("&")) {
        int equals = parameter.indexOf('=');
        String name = equals < 0 ? parameter : parameter.substring(0, equals);
        if ("sslmode".equalsIgnoreCase(name)) {
          return equals < 0 ? "" : parameter.substring(equals + 1);
        }
      }
      return null;
    } catch (URISyntaxException e) {
      return null;
    }
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
    if (uri.getRawUserInfo() == null) {
      return uri;
    }
    String authority = uri.getRawAuthority();
    StringBuilder cleaned =
        new StringBuilder(uri.getScheme())
            .append("://")
            .append(authority, authority.lastIndexOf('@') + 1, authority.length())
            .append(uri.getRawPath());
    if (uri.getRawQuery() != null) {
      cleaned.append('?').append(uri.getRawQuery());
    }
    if (uri.getRawFragment() != null) {
      cleaned.append('#').append(uri.getRawFragment());
    }
    return parse(cleaned.toString(), key);
  }
}

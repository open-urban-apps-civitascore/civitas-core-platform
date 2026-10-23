package de.civitascore.portal.model.connector;

/** Certificates for tests of the client certificate authentication. */
public final class TestCertificates {

  public static final String SELF_SIGNED =
      """
      -----BEGIN CERTIFICATE-----
      MIIBkDCCATegAwIBAgIUOylf3oXUqQP6TNT7sYtc7g1ByaQwCgYIKoZIzj0EAwIw
      HTEbMBkGA1UEAwwSY2xpZW50LmV4YW1wbGUub3JnMCAXDTI2MTAyMzE0NTI0MFoY
      DzIxMjYwOTI5MTQ1MjQwWjAdMRswGQYDVQQDDBJjbGllbnQuZXhhbXBsZS5vcmcw
      WTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASHzbM9xN778Neeayxtcvf7wbqOkv4O
      KvCbNLEYpcE/yMA7yVEa4KMb6Ms6baGOq18t2UeT3L4mzvAKK73BMAAHo1MwUTAd
      BgNVHQ4EFgQUQ0ZjJfwlFZQuHWHhxsixvRNW2CQwHwYDVR0jBBgwFoAUQ0ZjJfwl
      FZQuHWHhxsixvRNW2CQwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNHADBE
      AiAhIY5L5SeakrxCWkDxl//xwPq5jxbfpSquJvuS8e0xAAIgSnWdgrODCGLkNnw5
      0bJNZP/v2YgYsotwmNh/jzTKk30=
      -----END CERTIFICATE-----
      """;

  private TestCertificates() {}
}

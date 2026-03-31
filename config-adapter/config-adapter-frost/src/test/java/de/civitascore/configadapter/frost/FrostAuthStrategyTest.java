/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.client.Invocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FrostAuthStrategyTest {

  @Nested
  @DisplayName("basicAuth")
  class BasicAuth {

    @Test
    @DisplayName("sets Authorization header with Base64-encoded credentials")
    void shouldSetBasicAuthHeader() {
      Invocation.Builder builder = mock(Invocation.Builder.class);
      when(builder.header("Authorization", "Basic dXNlcjpwYXNz")).thenReturn(builder);

      FrostAuthStrategy strategy = FrostAuthStrategy.basicAuth("user", "pass");
      Invocation.Builder result = strategy.apply(builder);

      verify(builder).header("Authorization", "Basic dXNlcjpwYXNz");
      assertSame(builder, result);
    }

    @Test
    @DisplayName("treats null password as empty string")
    void shouldTreatNullPasswordAsEmpty() {
      Invocation.Builder builder = mock(Invocation.Builder.class);
      when(builder.header("Authorization", "Basic dXNlcjo=")).thenReturn(builder);

      FrostAuthStrategy strategy = FrostAuthStrategy.basicAuth("user", null);
      strategy.apply(builder);

      verify(builder).header("Authorization", "Basic dXNlcjo=");
    }

    @Test
    @DisplayName("pre-computes credentials so encoding happens once, not per request")
    void shouldReuseEncodedCredentials() {
      Invocation.Builder builder1 = mock(Invocation.Builder.class);
      Invocation.Builder builder2 = mock(Invocation.Builder.class);
      String expectedHeader = "Basic dXNlcjpwYXNz";
      when(builder1.header("Authorization", expectedHeader)).thenReturn(builder1);
      when(builder2.header("Authorization", expectedHeader)).thenReturn(builder2);

      FrostAuthStrategy strategy = FrostAuthStrategy.basicAuth("user", "pass");
      strategy.apply(builder1);
      strategy.apply(builder2);

      verify(builder1).header("Authorization", expectedHeader);
      verify(builder2).header("Authorization", expectedHeader);
    }

    @Test
    @DisplayName("throws on null username")
    void shouldThrowOnNullUsername() {
      assertThrows(NullPointerException.class, () -> FrostAuthStrategy.basicAuth(null, "pass"));
    }
  }

  @Nested
  @DisplayName("apiKey")
  class ApiKey {

    @Test
    @DisplayName("sets the configured header with API key value")
    void shouldSetApiKeyHeader() {
      Invocation.Builder builder = mock(Invocation.Builder.class);
      when(builder.header("X-API-Key", "my-key")).thenReturn(builder);

      FrostAuthStrategy strategy = FrostAuthStrategy.apiKey("X-API-Key", "my-key");
      Invocation.Builder result = strategy.apply(builder);

      verify(builder).header("X-API-Key", "my-key");
      assertSame(builder, result);
    }

    @Test
    @DisplayName("throws on null header name")
    void shouldThrowOnNullHeaderName() {
      assertThrows(NullPointerException.class, () -> FrostAuthStrategy.apiKey(null, "key"));
    }

    @Test
    @DisplayName("throws on null key")
    void shouldThrowOnNullKey() {
      assertThrows(NullPointerException.class, () -> FrostAuthStrategy.apiKey("X-API-Key", null));
    }
  }

  @Nested
  @DisplayName("create (factory)")
  class Create {

    @Test
    @DisplayName("selects Basic Auth when username is configured")
    void shouldSelectBasicAuthWhenUsernamePresent() {
      Invocation.Builder builder = mock(Invocation.Builder.class);
      when(builder.header("Authorization", "Basic dXNlcjpwYXNz")).thenReturn(builder);

      FrostAuthStrategy strategy = FrostAuthStrategy.create("user", "pass", "X-API-Key", "my-key");
      strategy.apply(builder);

      verify(builder).header("Authorization", "Basic dXNlcjpwYXNz");
    }

    @Test
    @DisplayName("falls back to API key when username is null")
    void shouldFallBackToApiKeyWhenUsernameNull() {
      Invocation.Builder builder = mock(Invocation.Builder.class);
      when(builder.header("X-API-Key", "my-key")).thenReturn(builder);

      FrostAuthStrategy strategy = FrostAuthStrategy.create(null, null, "X-API-Key", "my-key");
      strategy.apply(builder);

      verify(builder).header("X-API-Key", "my-key");
    }

    @Test
    @DisplayName("falls back to API key when username is blank")
    void shouldFallBackToApiKeyWhenUsernameBlank() {
      Invocation.Builder builder = mock(Invocation.Builder.class);
      when(builder.header("X-API-Key", "my-key")).thenReturn(builder);

      FrostAuthStrategy strategy = FrostAuthStrategy.create("  ", null, "X-API-Key", "my-key");
      strategy.apply(builder);

      verify(builder).header("X-API-Key", "my-key");
    }

    @Test
    @DisplayName("throws when neither username nor API key is configured")
    void shouldThrowWhenNoAuthConfigured() {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> FrostAuthStrategy.create(null, null, "X-API-Key", null));

      assertEquals(
          "FROST authentication not configured: provide either basic.auth.username or api.key",
          ex.getMessage());
    }

    @Test
    @DisplayName("throws when username is blank and API key is blank")
    void shouldThrowWhenUsernameBlankAndApiKeyBlank() {
      assertThrows(
          IllegalArgumentException.class,
          () -> FrostAuthStrategy.create("  ", null, "X-API-Key", "  "));
    }
  }
}

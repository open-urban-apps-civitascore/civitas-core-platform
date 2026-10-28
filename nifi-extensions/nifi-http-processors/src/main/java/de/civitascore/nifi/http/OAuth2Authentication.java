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

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;

/**
 * Sends an access token of the client credentials grant as {@code Authorization: Bearer}.
 *
 * <p>The access token belongs to the Data source, not to one request: it is kept in memory for the
 * following requests and runs until shortly before its {@code expires_in} ends. A token without a
 * usable {@code expires_in} serves the current run only. The token is never in the NiFi state,
 * which NiFi writes to disk; it goes when NiFi stops the processor. Not thread-safe: the processor
 * runs one run at a time.
 */
final class OAuth2Authentication implements Authentication {

  /** How long before the end of {@code expires_in} a kept token is no longer used. */
  private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

  private final ClientCredentialsGrant grant;
  private final InstantSource clock;

  private Optional<Token> token = Optional.empty();

  /** Whether the token was requested for the request now in progress, so a 401 cannot renew it. */
  private boolean requestedForThisRequest;

  OAuth2Authentication(ClientCredentialsGrant grant, InstantSource clock) {
    this.grant = grant;
    this.clock = clock;
  }

  @Override
  public String method() {
    return "OAuth2";
  }

  @Override
  public void startRun() {
    if (token.isPresent() && token.get().lifetime().isEmpty()) {
      token = Optional.empty();
    }
  }

  @Override
  public Optional<ApiCallOutcome.Failure> prepare(ApiCall call) {
    Instant now = clock.instant();
    if (token.isPresent() && token.get().usableAt(now)) {
      requestedForThisRequest = false;
      return Optional.empty();
    }
    return switch (grant.request(call.label())) {
      case ClientCredentialsGrant.Grant.Refused refused -> {
        token = Optional.empty();
        yield Optional.of(refused.failure());
      }
      case ClientCredentialsGrant.Grant.Issued issued -> {
        token = Optional.of(new Token(issued.accessToken(), now, lifetime(issued.expiresIn())));
        requestedForThisRequest = true;
        yield Optional.empty();
      }
    };
  }

  @Override
  public HttpRequestHeadersSpec headers(HttpRequestHeadersSpec request) {
    return request.header("Authorization", "Bearer " + token.orElseThrow().value());
  }

  @Override
  public boolean renewAfterUnauthorized() {
    if (requestedForThisRequest) {
      return false;
    }
    token = Optional.empty();
    return true;
  }

  /** How long a token is kept, or empty for the current run only. */
  private static Optional<Duration> lifetime(OptionalLong expiresIn) {
    if (expiresIn.isEmpty() || expiresIn.getAsLong() <= EXPIRY_MARGIN.toSeconds()) {
      return Optional.empty();
    }
    return Optional.of(Duration.ofSeconds(expiresIn.getAsLong()).minus(EXPIRY_MARGIN));
  }

  /** A kept access token. A class, not a record, so that no generated {@code toString} names it. */
  private static final class Token {

    private final String value;
    private final Instant issuedAt;
    private final Optional<Duration> lifetime;

    /**
     * @param issuedAt the time just before the token request
     */
    Token(String value, Instant issuedAt, Optional<Duration> lifetime) {
      this.value = value;
      this.issuedAt = issuedAt;
      this.lifetime = lifetime;
    }

    String value() {
      return value;
    }

    Optional<Duration> lifetime() {
      return lifetime;
    }

    boolean usableAt(Instant now) {
      return lifetime.isEmpty() || Duration.between(issuedAt, now).compareTo(lifetime.get()) < 0;
    }
  }
}

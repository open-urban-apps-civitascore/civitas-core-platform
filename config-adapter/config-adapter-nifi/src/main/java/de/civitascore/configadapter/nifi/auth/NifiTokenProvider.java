/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.auth;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;

/**
 * Supplies the bearer token the {@code NifiRestClient} sends to NiFi. Isolating token acquisition
 * behind this interface keeps the REST client free of any authentication mechanism: it asks for a
 * token, and — when NiFi rejects one with HTTP 401 — asks for a fresh one. NiFi 2.x validates these
 * tokens against its configured OpenID Connect provider, so the production implementation obtains
 * them from that provider via the client-credentials grant ({@link
 * OidcClientCredentialsTokenProvider}).
 */
public interface NifiTokenProvider {

  /**
   * Returns a currently-valid bearer token, fetching (and caching) one if necessary.
   *
   * @return the bearer token
   * @throws FatalAdapterException on a non-retryable error (e.g. rejected credentials)
   * @throws RetryableAdapterException on a transient error (network/5xx)
   */
  String getToken() throws FatalAdapterException, RetryableAdapterException;

  /**
   * Discards any cached token and obtains a fresh one. Called when NiFi answers 401 (the previous
   * token expired mid-saga).
   *
   * @return the new bearer token
   * @throws FatalAdapterException on a non-retryable error (e.g. rejected credentials)
   * @throws RetryableAdapterException on a transient error (network/5xx)
   */
  String refreshToken() throws FatalAdapterException, RetryableAdapterException;
}

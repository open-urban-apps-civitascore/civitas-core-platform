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

import java.net.URI;
import java.time.Duration;
import java.time.InstantSource;
import org.apache.nifi.context.PropertyContext;
import org.apache.nifi.web.client.api.WebClientService;

/**
 * The values of the {@code Authentication Type} property. The names are the authentication types of
 * the portal.
 */
enum AuthenticationType {
  NONE,
  BASIC,
  BEARER_TOKEN,
  API_TOKEN,
  OAUTH2,
  CLIENT_CERTIFICATE;

  /**
   * Reads the credentials of this type from the properties. Call it once for each start of the
   * processor, because the instance keeps what the following runs reuse, such as an access token.
   *
   * @param webClient the web client service of the API requests, which also sends a token request
   */
  Authentication authentication(
      PropertyContext context,
      WebClientService webClient,
      Duration bodyReadTimeout,
      InstantSource clock) {
    return switch (this) {
      case NONE -> new NoAuthentication();
      case BASIC ->
          new BasicAuthentication(
              context.getProperty(GetHttpApiCall.BASIC_AUTH_USERNAME).getValue(),
              context.getProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD).getValue());
      case BEARER_TOKEN ->
          new BearerTokenAuthentication(
              context.getProperty(GetHttpApiCall.BEARER_TOKEN).getValue());
      case API_TOKEN ->
          new ApiTokenAuthentication(
              context.getProperty(GetHttpApiCall.API_TOKEN_NAME).getValue(),
              context.getProperty(GetHttpApiCall.API_TOKEN_VALUE).getValue(),
              context
                  .getProperty(GetHttpApiCall.API_TOKEN_LOCATION)
                  .asAllowableValue(ParameterLocation.class));
      case OAUTH2 ->
          new OAuth2Authentication(
              new ClientCredentialsGrant(
                  webClient,
                  URI.create(context.getProperty(GetHttpApiCall.OAUTH2_TOKEN_URL).getValue()),
                  context.getProperty(GetHttpApiCall.OAUTH2_CLIENT_ID).getValue(),
                  context.getProperty(GetHttpApiCall.OAUTH2_CLIENT_SECRET).getValue(),
                  context.getProperty(GetHttpApiCall.OAUTH2_SCOPE).getValue(),
                  bodyReadTimeout),
              clock);
      case CLIENT_CERTIFICATE -> new ClientCertificateAuthentication();
    };
  }
}

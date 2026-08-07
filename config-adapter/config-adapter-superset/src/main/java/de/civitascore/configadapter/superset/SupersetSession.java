/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.superset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a Superset API session: login (access token) and CSRF token. Both tokens are held in
 * memory and refreshed via {@link #authenticate()}. Thread-safe for concurrent re-auth.
 */
class SupersetSession {

  private static final Logger logger = LoggerFactory.getLogger(SupersetSession.class);

  private final Client client;
  private final String baseUrl;
  private final String username;
  private final String password;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private volatile String accessToken;
  private volatile String csrfToken;
  private volatile String sessionCookie;

  SupersetSession(Client client, String baseUrl, String username, String password) {
    this.client = client;
    this.baseUrl = baseUrl;
    this.username = username;
    this.password = password;
  }

  /**
   * Authenticates against Superset: fetches an access token then a CSRF token. Replaces any
   * previously held tokens. Thread-safe.
   */
  synchronized void authenticate() throws FatalAdapterException, RetryableAdapterException {
    fetchAccessToken();
    fetchCsrfToken();
    logger.info("Superset session established for {}", Encode.forJava(baseUrl));
  }

  private void fetchAccessToken() throws FatalAdapterException, RetryableAdapterException {
    Map<String, String> credentials =
        Map.of("username", username, "password", password, "provider", "db", "refresh", "true");
    Response response = null;
    try {
      response =
          client
              .target(baseUrl)
              .path("/api/v1/security/login")
              .request(MediaType.APPLICATION_JSON)
              .post(Entity.json(credentials));
      int status = response.getStatus();
      if (status == 200) {
        JsonNode root = objectMapper.readTree(response.readEntity(String.class));
        this.accessToken = root.path("access_token").asText();
      } else if (status >= 500) {
        throw new RetryableAdapterException(
            AdapterErrorCode.SERVICE_UNAVAILABLE, "superset", status);
      } else {
        throw new FatalAdapterException(
            AdapterErrorCode.SUPERSET_AUTH_ERROR, "Login returned HTTP " + status);
      }
    } catch (ProcessingException e) {
      throw new RetryableAdapterException(
          AdapterErrorCode.NETWORK_ERROR, e, "superset", e.getMessage());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (Exception e) {
      throw new FatalAdapterException(AdapterErrorCode.SUPERSET_AUTH_ERROR, e.getMessage());
    } finally {
      closeQuietly(response);
    }
  }

  private void fetchCsrfToken() throws FatalAdapterException, RetryableAdapterException {
    Response response = null;
    try {
      response =
          client
              .target(baseUrl)
              .path("/api/v1/security/csrf_token/")
              .request(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + accessToken)
              .get();
      int status = response.getStatus();
      if (status == 200) {
        JsonNode root = objectMapper.readTree(response.readEntity(String.class));
        this.csrfToken = root.path("result").asText();
        jakarta.ws.rs.core.NewCookie cookie = response.getCookies().get("session");
        if (cookie != null) {
          this.sessionCookie = "session=" + cookie.getValue();
        }
      } else if (status >= 500) {
        throw new RetryableAdapterException(
            AdapterErrorCode.SERVICE_UNAVAILABLE, "superset", status);
      } else {
        throw new FatalAdapterException(
            AdapterErrorCode.SUPERSET_AUTH_ERROR, "CSRF fetch returned HTTP " + status);
      }
    } catch (ProcessingException e) {
      throw new RetryableAdapterException(
          AdapterErrorCode.NETWORK_ERROR, e, "superset", e.getMessage());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (Exception e) {
      throw new FatalAdapterException(AdapterErrorCode.SUPERSET_AUTH_ERROR, e.getMessage());
    } finally {
      closeQuietly(response);
    }
  }

  String getAccessToken() {
    return accessToken;
  }

  String getCsrfToken() {
    return csrfToken;
  }

  String getSessionCookie() {
    return sessionCookie;
  }

  boolean isAuthenticated() {
    return accessToken != null && csrfToken != null;
  }

  void invalidate() {
    this.accessToken = null;
    this.csrfToken = null;
    this.sessionCookie = null;
  }

  private static void closeQuietly(Response response) {
    if (response != null) {
      try {
        response.close();
      } catch (Exception ignored) {
      }
    }
  }
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import java.util.Map;
import java.util.Objects;

/**
 * FROST Basic Auth credentials bound to every NiFi {@code InvokeHTTP} processor of a FROST sink.
 * The password is pushed only after upload and never enters the versioned flow snapshot. Missing
 * credentials intentionally leave the processors unauthenticated for the local development FROST.
 */
public final class FrostSinkAuth {

  public static final String BASIC_AUTH_USERNAME = "basicAuthUsername";
  public static final String BASIC_AUTH_PASSWORD = "basicAuthPassword";

  private final String basicAuthUsername;
  private final String basicAuthPassword;
  private final CredentialResolver credentialResolver;
  private final Map<String, Object> properties;

  private FrostSinkAuth(String basicAuthUsername, String basicAuthPassword) {
    this.basicAuthUsername = basicAuthUsername;
    this.basicAuthPassword = basicAuthPassword;
    this.credentialResolver = null;
    this.properties = Map.of();
  }

  private FrostSinkAuth(CredentialResolver credentialResolver, Map<String, Object> properties) {
    this.basicAuthUsername = null;
    this.basicAuthPassword = null;
    this.credentialResolver = Objects.requireNonNull(credentialResolver, "credentialResolver");
    this.properties = Map.copyOf(properties);
  }

  /**
   * Resolves the configured Basic Auth credentials. An empty configuration intentionally disables
   * authentication, as used by the local development FROST deployment.
   */
  private static FrostSinkAuth resolve(Map<String, Object> credentials) {
    String basicAuthUsername = stringValue(credentials.get(BASIC_AUTH_USERNAME));
    String basicAuthPassword = stringValue(credentials.get(BASIC_AUTH_PASSWORD));
    boolean hasBasicAuth = basicAuthUsername != null && !basicAuthUsername.isBlank();
    if (!hasBasicAuth) {
      return new FrostSinkAuth((String) null, null);
    }
    if (basicAuthPassword == null || basicAuthPassword.isBlank()) {
      throw new IllegalArgumentException(
          "FROST Basic Auth password must be configured when a username is set");
    }
    return new FrostSinkAuth(basicAuthUsername, basicAuthPassword);
  }

  /** Creates Basic authentication for directly constructed stages. */
  public static FrostSinkAuth basicAuth(String username, String password) {
    return resolve(Map.of(BASIC_AUTH_USERNAME, username, BASIC_AUTH_PASSWORD, password));
  }

  /**
   * Resolves possibly encrypted FROST credentials through the shared deploy-time credential
   * resolver. This keeps {@code ENC(...)} handling consistent with the other NiFi stages.
   */
  public static FrostSinkAuth fromProperties(
      CredentialResolver credentialResolver, Map<String, Object> properties) {
    return new FrostSinkAuth(credentialResolver, properties);
  }

  private static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /** Adds the non-secret build property and the post-upload secret patch. */
  void bind(PlanContext out) throws FatalAdapterException {
    Objects.requireNonNull(out, "out");
    if (credentialResolver != null) {
      Map<String, Object> decrypted = credentialResolver.decrypt(properties);
      resolve(decrypted).bind(out);
      return;
    }
    if (basicAuthUsername != null) {
      out.putSinkProperty(FrostSinkStage.FROST_BASIC_AUTH_USERNAME, basicAuthUsername);
      out.putSensitive(FrostSinkStage.FROST_HTTP_PROCESSOR, "Request Password", basicAuthPassword);
    }
  }
}

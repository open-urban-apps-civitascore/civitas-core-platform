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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** A TLS context that presents a client certificate and trusts the JVM's default CAs. */
final class ClientCertificate {

  private static final Pattern PKCS8_PEM =
      Pattern.compile("-----BEGIN PRIVATE KEY-----([A-Za-z0-9+/=\\s]+)-----END PRIVATE KEY-----");

  private static final List<String> KEY_ALGORITHMS = List.of("RSA", "EC");

  private static final String KEY_FORMAT =
      "The private key must be an unencrypted PKCS#8 PEM key ('BEGIN PRIVATE KEY') of type RSA or"
          + " EC. 'openssl pkcs8 -topk8 -nocrypt' converts other formats.";

  private static final char[] IN_MEMORY_PASSWORD = new char[0];

  private ClientCertificate() {}

  /**
   * @param certificate the PEM certificate, optionally followed by its chain
   * @param privateKey the PEM private key of the certificate
   * @throws ConnectionTestFailure if either cannot be read
   */
  static SSLContext tls(String certificate, String privateKey) throws ConnectionTestFailure {
    Certificate[] chain = chain(certificate);
    PrivateKey key = privateKey(privateKey);
    try {
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(null, null);
      store.setKeyEntry("client", key, IN_MEMORY_PASSWORD, chain);
      KeyManagerFactory keys =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keys.init(store, IN_MEMORY_PASSWORD);
      SSLContext tls = SSLContext.getInstance("TLS");
      tls.init(keys.getKeyManagers(), null, null);
      return tls;
    } catch (GeneralSecurityException | IOException e) {
      throw ConnectionTestFailure.invalid(
          "The client certificate cannot be used with its private key.", e);
    }
  }

  private static Certificate[] chain(String pem) throws ConnectionTestFailure {
    Collection<? extends Certificate> chain;
    try {
      chain =
          CertificateFactory.getInstance("X.509")
              .generateCertificates(
                  new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    } catch (CertificateException e) {
      throw ConnectionTestFailure.invalid(
          "The client certificate must be one or more PEM certificates ('BEGIN CERTIFICATE').", e);
    }
    if (chain.isEmpty()) {
      throw ConnectionTestFailure.invalid(
          "The client certificate must be one or more PEM certificates ('BEGIN CERTIFICATE').");
    }
    return chain.toArray(new Certificate[0]);
  }

  private static PrivateKey privateKey(String pem) throws ConnectionTestFailure {
    Matcher block = PKCS8_PEM.matcher(pem);
    if (!block.find()) {
      throw ConnectionTestFailure.invalid(KEY_FORMAT);
    }
    PKCS8EncodedKeySpec spec;
    try {
      spec = new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(block.group(1)));
    } catch (IllegalArgumentException e) {
      throw ConnectionTestFailure.invalid(KEY_FORMAT, e);
    }
    for (String algorithm : KEY_ALGORITHMS) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(spec);
      } catch (InvalidKeySpecException ignored) {
        // The JDK reads the algorithm of a PKCS#8 key only by trying a key factory.
      } catch (GeneralSecurityException e) {
        throw ConnectionTestFailure.invalid(KEY_FORMAT, e);
      }
    }
    throw ConnectionTestFailure.invalid(KEY_FORMAT);
  }
}

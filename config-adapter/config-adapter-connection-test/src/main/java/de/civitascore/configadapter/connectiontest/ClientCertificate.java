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
import java.util.Iterator;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** A TLS context that presents a client certificate and trusts the JVM's default CAs. */
final class ClientCertificate {

  private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
  private static final String PKCS8_FOOTER = "-----END PRIVATE KEY-----";
  private static final String PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
  private static final String PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----";

  private static final List<String> PKCS8_ALGORITHMS = List.of("RSA", "EC", "Ed25519");

  private static final String KEY_FORMAT =
      "The private key must be an unencrypted PEM key that starts on the first line: PKCS#8 ('BEGIN"
          + " PRIVATE KEY') of type RSA, EC or Ed25519, or PKCS#1 ('BEGIN RSA PRIVATE KEY').";

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

  /**
   * Reads the key as the pipeline's {@code PEMEncodedSSLContextProvider} does, so that the test
   * accepts exactly the keys the pipeline accepts.
   */
  private static PrivateKey privateKey(String pem) throws ConnectionTestFailure {
    Iterator<String> lines = pem.lines().iterator();
    String header = lines.hasNext() ? lines.next() : "";
    try {
      if (PKCS8_HEADER.equals(header)) {
        return pkcs8(Base64.getDecoder().decode(payload(lines, PKCS8_FOOTER)));
      }
      if (PKCS1_HEADER.equals(header)) {
        return (PrivateKey)
            KeyFactory.getInstance("RSA")
                .translateKey(
                    new Pkcs1RsaKey(Base64.getDecoder().decode(payload(lines, PKCS1_FOOTER))));
      }
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      throw ConnectionTestFailure.invalid(KEY_FORMAT, e);
    }
    throw ConnectionTestFailure.invalid(KEY_FORMAT);
  }

  /** The lines up to the footer, or to the end without one. */
  private static String payload(Iterator<String> lines, String footer) {
    StringBuilder payload = new StringBuilder();
    while (lines.hasNext()) {
      String line = lines.next();
      if (footer.equals(line)) {
        break;
      }
      payload.append(line);
    }
    return payload.toString();
  }

  private static PrivateKey pkcs8(byte[] der) throws GeneralSecurityException {
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
    for (String algorithm : PKCS8_ALGORITHMS) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(spec);
      } catch (InvalidKeySpecException ignored) {
        // The JDK reads the algorithm of a PKCS#8 key only by trying a key factory.
      }
    }
    throw new InvalidKeySpecException("No key factory reads the key");
  }

  /** The JDK's RSA key factory translates a key in the PKCS#1 format into an RSA private key. */
  private static final class Pkcs1RsaKey implements PrivateKey {

    private static final long serialVersionUID = 1L;

    private final byte[] encoded;

    Pkcs1RsaKey(byte[] encoded) {
      this.encoded = encoded.clone();
    }

    @Override
    public String getAlgorithm() {
      return "RSA";
    }

    @Override
    public String getFormat() {
      return "PKCS#1";
    }

    @Override
    public byte[] getEncoded() {
      return encoded.clone();
    }
  }
}

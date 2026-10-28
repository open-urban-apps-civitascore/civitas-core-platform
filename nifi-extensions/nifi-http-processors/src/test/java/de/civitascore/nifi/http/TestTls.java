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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.ssl.SSLContextProvider;

/**
 * Self-signed certificates for 127.0.0.1 and for a client, made with the keytool of the running
 * JDK, so that no key is in the repository. The server side serves the server certificate; the
 * client side trusts it and nothing else, and can present the client certificate.
 */
final class TestTls {

  private static final char[] PASSWORD = "test-keystore".toCharArray();

  private final SSLContext server;
  private final SSLContext mutualServer;
  private final SSLContext untrustedServer;
  private final SSLContext client;
  private final X509TrustManager trustManager;
  private final X509ExtendedKeyManager clientKeyManager;
  private final List<Certificate> clientChain;

  private TestTls(KeyStore serverKeys, KeyStore clientKeys) throws GeneralSecurityException {
    TrustManagerFactory trust = trust(serverKeys);
    trustManager = (X509TrustManager) trust.getTrustManagers()[0];
    server = SSLContext.getInstance("TLS");
    server.init(keys(serverKeys).getKeyManagers(), null, null);
    mutualServer = SSLContext.getInstance("TLS");
    mutualServer.init(
        keys(serverKeys).getKeyManagers(), trust(clientKeys).getTrustManagers(), null);
    untrustedServer = SSLContext.getInstance("TLS");
    untrustedServer.init(keys(clientKeys).getKeyManagers(), null, null);
    client = SSLContext.getInstance("TLS");
    client.init(null, trust.getTrustManagers(), null);
    clientKeyManager = (X509ExtendedKeyManager) keys(clientKeys).getKeyManagers()[0];
    clientChain = List.of(clientKeys.getCertificateChain(clientKeys.aliases().nextElement()));
  }

  static TestTls create(Path directory) throws IOException, GeneralSecurityException {
    return new TestTls(
        generate(directory.resolve("api.p12"), "CN=127.0.0.1", "san=ip:127.0.0.1"),
        generate(directory.resolve("client.p12"), "CN=GetHttpApiCall test client", null));
  }

  private static KeyStore generate(Path keyStoreFile, String distinguishedName, String extension)
      throws IOException, GeneralSecurityException {
    String password = new String(PASSWORD);
    List<String> command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair",
                "-alias",
                "key",
                "-keyalg",
                "EC",
                "-groupname",
                "secp256r1",
                "-dname",
                distinguishedName,
                "-validity",
                "1",
                "-storetype",
                "PKCS12",
                "-keystore",
                keyStoreFile.toString(),
                "-storepass",
                password,
                "-keypass",
                password));
    if (extension != null) {
      command.addAll(List.of("-ext", extension));
    }
    Process keytool = new ProcessBuilder(command).redirectErrorStream(true).start();
    try {
      if (keytool.waitFor() != 0) {
        throw new IOException(new String(keytool.getInputStream().readAllBytes()));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, PASSWORD);
    }
    return keyStore;
  }

  private static KeyManagerFactory keys(KeyStore keyStore) throws GeneralSecurityException {
    KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(keyStore, PASSWORD);
    return keys;
  }

  private static TrustManagerFactory trust(KeyStore keyStore) throws GeneralSecurityException {
    TrustManagerFactory trust =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(keyStore);
    return trust;
  }

  /** Serves the server certificate and trusts the default CAs, so no client certificate. */
  SSLContext server() {
    return server;
  }

  /** Serves the server certificate and trusts the client certificate. */
  SSLContext mutualServer() {
    return mutualServer;
  }

  /** Serves the client certificate, which the client side does not trust as a server. */
  SSLContext untrustedServer() {
    return untrustedServer;
  }

  /** The SSL Context Service the web client service takes, without a client certificate. */
  SSLContextProvider clientProvider() {
    return new Provider(client, trustManager, Optional.empty());
  }

  /** The SSL Context Service the web client service takes, with the client certificate. */
  SSLContextProvider clientCertificateProvider() {
    return new Provider(client, trustManager, Optional.of(clientKeyManager));
  }

  List<Certificate> clientChain() {
    return clientChain;
  }

  private static final class Provider extends AbstractControllerService
      implements SSLContextProvider {

    private final SSLContext context;
    private final X509TrustManager trustManager;
    private final Optional<X509ExtendedKeyManager> keyManager;

    Provider(
        SSLContext context,
        X509TrustManager trustManager,
        Optional<X509ExtendedKeyManager> keyManager) {
      this.context = context;
      this.trustManager = trustManager;
      this.keyManager = keyManager;
    }

    @Override
    public SSLContext createContext() {
      return context;
    }

    @Override
    public Optional<X509ExtendedKeyManager> createKeyManager() {
      return keyManager;
    }

    @Override
    public X509TrustManager createTrustManager() {
      return trustManager;
    }
  }
}

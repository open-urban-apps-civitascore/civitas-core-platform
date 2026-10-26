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
 * A self-signed certificate for 127.0.0.1, made with the keytool of the running JDK, so that no key
 * is in the repository. The server side serves it; the client side trusts it and nothing else.
 */
final class TestTls {

  private static final char[] PASSWORD = "test-keystore".toCharArray();

  private final SSLContext server;
  private final SSLContext client;
  private final X509TrustManager trustManager;

  private TestTls(KeyStore keyStore) throws GeneralSecurityException {
    KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(keyStore, PASSWORD);
    TrustManagerFactory trust =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(keyStore);
    trustManager = (X509TrustManager) trust.getTrustManagers()[0];
    server = SSLContext.getInstance("TLS");
    server.init(keys.getKeyManagers(), null, null);
    client = SSLContext.getInstance("TLS");
    client.init(null, trust.getTrustManagers(), null);
  }

  static TestTls create(Path directory) throws IOException, GeneralSecurityException {
    Path keyStoreFile = directory.resolve("api.p12");
    String password = new String(PASSWORD);
    Process keytool =
        new ProcessBuilder(
                List.of(
                    Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair",
                    "-alias",
                    "api",
                    "-keyalg",
                    "EC",
                    "-groupname",
                    "secp256r1",
                    "-dname",
                    "CN=127.0.0.1",
                    "-ext",
                    "san=ip:127.0.0.1",
                    "-validity",
                    "1",
                    "-storetype",
                    "PKCS12",
                    "-keystore",
                    keyStoreFile.toString(),
                    "-storepass",
                    password,
                    "-keypass",
                    password))
            .redirectErrorStream(true)
            .start();
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
    return new TestTls(keyStore);
  }

  SSLContext server() {
    return server;
  }

  /** The SSL Context Service the web client service takes. */
  SSLContextProvider clientProvider() {
    return new Provider(client, trustManager);
  }

  private static final class Provider extends AbstractControllerService
      implements SSLContextProvider {

    private final SSLContext context;
    private final X509TrustManager trustManager;

    Provider(SSLContext context, X509TrustManager trustManager) {
      this.context = context;
      this.trustManager = trustManager;
    }

    @Override
    public SSLContext createContext() {
      return context;
    }

    @Override
    public Optional<X509ExtendedKeyManager> createKeyManager() {
      return Optional.empty();
    }

    @Override
    public X509TrustManager createTrustManager() {
      return trustManager;
    }
  }
}

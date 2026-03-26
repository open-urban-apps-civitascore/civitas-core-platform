package de.civitascore.portal.configuration;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import java.security.GeneralSecurityException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Configures credential encryption for data source connector secrets. Uses the platform master key
 * to derive an AES encryption key, producing {@code ENC(...)} wrapped ciphertext that the
 * config-adapter can decrypt.
 */
@Configuration
@EnableConfigurationProperties(CivitasProperties.class)
public class EncryptionConfig {

  public static final String ENC_PREFIX = "ENC(";
  public static final String ENC_SUFFIX = ")";

  public static final String CREDENTIAL_CONTEXT = "portal-backend:datasource-connector";

  /**
   * Creates a {@link TextEncryptor} that wraps encrypted credentials in {@code ENC(...)} format.
   * Decryption is intentionally unsupported as it is handled by the config-adapter.
   *
   * @param props the CIVITAS properties containing the master key
   * @return a one-way text encryptor for credential encryption
   * @throws GeneralSecurityException if the master key is invalid
   */
  @Bean
  public TextEncryptor textEncryptor(CivitasProperties props) throws GeneralSecurityException {
    byte[] masterKey;
    byte[] stretchedKey;

    try {
      masterKey = CryptoKeyLoader.hexStringToBytes(props.masterKey());
      stretchedKey = CryptoKeyLoader.stretchMasterKey(masterKey);
    } catch (GeneralSecurityException e) {
      throw new GeneralSecurityException(
          "Failed to initialize encryption: CIVITAS_MASTER_KEY must be a valid 64-character hex"
              + " string",
          e);
    }

    return new TextEncryptor() {
      @Override
      public String encrypt(String text) {
        try {
          return ENC_PREFIX
              + CredentialEncryptor.encrypt(text, stretchedKey, CREDENTIAL_CONTEXT)
              + ENC_SUFFIX;
        } catch (GeneralSecurityException e) {
          throw new IllegalStateException("Credential encryption failed", e);
        }
      }

      @Override
      public String decrypt(String encryptedText) {
        throw new UnsupportedOperationException(
            "Decryption is handled by the config-adapter, not the portal-backend");
      }
    };
  }
}

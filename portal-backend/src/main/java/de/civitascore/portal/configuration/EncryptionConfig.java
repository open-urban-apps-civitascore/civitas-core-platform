package de.civitascore.portal.configuration;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import java.security.GeneralSecurityException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
@EnableConfigurationProperties(CivitasProperties.class)
public class EncryptionConfig {

  public static final String ENC_PREFIX = "ENC(";
  public static final String ENC_SUFFIX = ")";

  public static final String CREDENTIAL_CONTEXT = "portal-backend:datasource-connector";

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

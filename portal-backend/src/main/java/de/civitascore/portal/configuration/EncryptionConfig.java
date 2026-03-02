package de.civitascore.portal.configuration;

import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
public class EncryptionConfig {

  public static final String ENC_PREFIX = "ENC(";
  public static final String ENC_SUFFIX = ")";

  @Bean
  public TextEncryptor textEncryptor(
      @Value("${app.encryption.key}") String masterKeyHex,
      @Value("${app.encryption.salt}") String masterSaltHex) {
    byte[] masterKey = CredentialDecryptor.hexStringToBytes(masterKeyHex);
    byte[] salt = CredentialDecryptor.hexStringToBytes(masterSaltHex);

    return new TextEncryptor() {
      @Override
      public String encrypt(String text) {
        try {
          return ENC_PREFIX + CredentialEncryptor.encrypt(text, masterKey, salt) + ENC_SUFFIX;
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

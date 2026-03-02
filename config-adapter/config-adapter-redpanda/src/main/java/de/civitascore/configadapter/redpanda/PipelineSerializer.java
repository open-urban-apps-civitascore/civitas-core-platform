/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import java.io.Closeable;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

/**
 * Converts pipeline configuration maps to YAML, decrypting any {@code ENC(...)} credential values
 * before serialization. Extracted from {@link RedpandaConnectClient} to separate serialization
 * concerns from HTTP transport.
 *
 * <p>Implements {@link Closeable} to zero the stretched key material when no longer needed.
 */
class PipelineSerializer implements Closeable {

  private final byte[] stretchedKey;
  private final ObjectMapper yamlMapper;
  private final Object lock = new Object();
  private boolean closed;

  PipelineSerializer(byte[] stretchedKey) {
    this.stretchedKey = Arrays.copyOf(stretchedKey, stretchedKey.length);
    this.yamlMapper =
        new ObjectMapper(
            new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
  }

  /**
   * Converts pipeline data to YAML, decrypting any {@code ENC(...)} values.
   *
   * @param pipelineData the pipeline definition as a Map
   * @param credentialContext a non-empty context string for per-credential key isolation (e.g.
   *     pipeline ID)
   * @return the YAML string representation
   * @throws FatalAdapterException if decryption fails or master key is missing for encrypted values
   * @throws IllegalStateException if this serializer has been closed
   */
  String toYaml(Map<String, Object> pipelineData, String credentialContext)
      throws FatalAdapterException {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("PipelineSerializer has been closed");
      }
      if (stretchedKey.length == 0 && CredentialDecryptor.containsEncryptedValues(pipelineData)) {
        throw new FatalAdapterException(
            AdapterErrorCode.REDPANDA_DECRYPTION_ERROR,
            "Master key not configured — set CIVITAS_MASTER_KEY environment variable"
                + " to decrypt ENC(...) credentials");
      }
      try {
        Map<String, Object> decrypted =
            CredentialDecryptor.decryptMapValues(pipelineData, stretchedKey, credentialContext);
        return yamlMapper.writeValueAsString(decrypted);
      } catch (GeneralSecurityException e) {
        throw new FatalAdapterException(
            AdapterErrorCode.REDPANDA_DECRYPTION_ERROR, e, e.getMessage());
      } catch (JsonProcessingException e) {
        throw new FatalAdapterException(
            AdapterErrorCode.REDPANDA_PIPELINE_ERROR, e, e.getMessage());
      }
    }
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (!closed) {
        Arrays.fill(stretchedKey, (byte) 0);
        closed = true;
      }
    }
  }
}

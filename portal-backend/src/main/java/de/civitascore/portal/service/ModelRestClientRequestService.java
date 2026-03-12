package de.civitascore.portal.service;

import de.civitascore.portal.configuration.ModelAtlasConfig;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRestClientRequestService {

  private static final MediaType UML = MediaType.parseMediaType("application/uml");

  private final ModelAtlasConfig modelAtlasConfig;
  private final RestClient.Builder restClientBuilder;

  private String getBaseUrl() {
    return String.format("%s/atlas/rest", modelAtlasConfig.getBaseUrl());
  }

  private String getUploadEndpoint(String nsUri) {
    return UriComponentsBuilder.fromPath("/{scope}/schema/stages/{stage}")
        .queryParam("nsUri", nsUri)
        .queryParam("overwrite", true)
        .buildAndExpand(modelAtlasConfig.getScope(), modelAtlasConfig.getStage())
        .encode()
        .toUriString();
  }

  private String getDownloadEndpoint(String nsUri) {
    return UriComponentsBuilder.fromPath("/{scope}/schema/stages/{stage}/content")
        .queryParam("nsUri", nsUri)
        .buildAndExpand(modelAtlasConfig.getScope(), modelAtlasConfig.getStage())
        .encode()
        .toUriString();
  }

  /**
   * Upload a model file to the external Model Atlas service.
   *
   * @param modelFile the file to upload
   * @param nsUri the namespace URI of the model
   * @return response from the external service
   */
  public String uploadModelFile(MultipartFile modelFile, String nsUri) {
    try {
      RestClient restClient = restClientBuilder.baseUrl(getBaseUrl()).build();

      return restClient
          .post()
          .uri(getUploadEndpoint(nsUri))
          .contentType(UML)
          .accept(MediaType.APPLICATION_JSON)
          .body(modelFile.getResource())
          .retrieve()
          .body(String.class);
    } catch (ResourceAccessException e) {
      log.error("Timeout uploading model file to Model Atlas: nsUri={}", nsUri, e);
      throw new ExternalSystemTimeoutException("Model Atlas did not respond in time", e);
    } catch (RestClientException e) {
      log.error("Failed to upload model file to Model Atlas: nsUri={}", nsUri, e);
      throw new ExternalSystemRejectionException("Failed to upload model file to Model Atlas", e);
    }
  }

  /**
   * Upload a model as a string to the external Model Atlas service.
   *
   * @param modelContent the stringified XML model content to upload
   * @param nsUri the namespace URI of the model
   * @return response from the external service
   */
  public String uploadModelString(String modelContent, String nsUri) {
    try {
      RestClient restClient = restClientBuilder.baseUrl(getBaseUrl()).build();

      return restClient
          .post()
          .uri(getUploadEndpoint(nsUri))
          .contentType(UML)
          .accept(MediaType.APPLICATION_JSON)
          .body(modelContent)
          .retrieve()
          .body(String.class);
    } catch (ResourceAccessException e) {
      log.error("Timeout uploading model string to Model Atlas: nsUri={}", nsUri, e);
      throw new ExternalSystemTimeoutException("Model Atlas did not respond in time", e);
    } catch (RestClientException e) {
      log.error("Failed to upload model string to Model Atlas: nsUri={}", nsUri, e);
      throw new ExternalSystemRejectionException("Failed to upload model string to Model Atlas", e);
    }
  }

  /**
   * Download a model file from the external Model Atlas service.
   *
   * @param nsUri the namespace URI of the model to download
   * @param acceptHeader the desired response format
   * @return response from the external service in the requested format
   */
  public String downloadModelFile(String nsUri, String acceptHeader) {
    try {
      RestClient restClient = restClientBuilder.baseUrl(getBaseUrl()).build();

      return restClient
          .get()
          .uri(getDownloadEndpoint(nsUri))
          .accept(MediaType.parseMediaType(acceptHeader))
          .retrieve()
          .body(String.class);
    } catch (ResourceAccessException e) {
      log.error("Timeout downloading model file from Model Atlas: nsUri={}", nsUri, e);
      throw new ExternalSystemTimeoutException("Model Atlas did not respond in time", e);
    } catch (RestClientException e) {
      log.error("Failed to download model file from Model Atlas: nsUri={}", nsUri, e);
      throw new ExternalSystemRejectionException(
          "Failed to download model file from Model Atlas", e);
    }
  }
}

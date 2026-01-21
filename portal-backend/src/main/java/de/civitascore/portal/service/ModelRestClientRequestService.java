package de.civitascore.portal.service;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import de.civitascore.portal.configuration.ModelAtlasConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRestClientRequestService {

  private final ModelAtlasConfig modelAtlasConfig;
  private final RestClient.Builder restClientBuilder;

  /**
   * Upload a model file to the external Model Atlas service.
   *
   * @param modelFile the file to upload
   * @return response from the external service
   */
  public String uploadModelFile(MultipartFile modelFile, String nsUri) {
    String baseUrl =
        String.format(
            "%s:%d/atlas/rest", modelAtlasConfig.getBaseUrl(), modelAtlasConfig.getPort());

    // Param `overwrite` is set to true to allow updating existing models
    String endpoint =
        String.format(
            "/%s/schema/stages/%s?nsUri=%s&overwrite=true",
            modelAtlasConfig.getScope(), modelAtlasConfig.getStage(), encode(nsUri, UTF_8));

    try {
      RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();

      return restClient
          .post()
          .uri(endpoint)
          .contentType(MediaType.parseMediaType("application/uml"))
          .body(modelFile.getResource())
          .retrieve()
          .body(String.class);

    } catch (Exception e) {
      log.error("Failed to upload model file to Model Atlas", e);
      throw new RuntimeException("Failed to upload model file to Model Atlas", e);
    }
  }

  /**
   * Download a model file from the external Model Atlas service.
   *
   * @param nsUri the namespace URI of the model to download
   * @param acceptHeader the desired response format (e.g., application/json, application/xml)
   * @return response from the external service in the requested format
   */
  public String downloadModelFile(String nsUri, String acceptHeader) {
    String baseUrl =
        String.format(
            "%s:%d/atlas/rest", modelAtlasConfig.getBaseUrl(), modelAtlasConfig.getPort());

    String endpoint =
        String.format(
            "/%s/schema/stages/%s/content?nsUri=%s",
            modelAtlasConfig.getScope(), modelAtlasConfig.getStage(), encode(nsUri, UTF_8));

    try {
      RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();

      return restClient
          .get()
          .uri(endpoint)
          .accept(MediaType.parseMediaType(acceptHeader))
          .retrieve()
          .body(String.class);

    } catch (Exception e) {
      log.error("Failed to download model file from Model Atlas", e);
      throw new RuntimeException("Failed to download model file from Model Atlas", e);
    }
  }
}

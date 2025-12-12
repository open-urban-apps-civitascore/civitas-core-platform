package de.civitascore.portal.service;

import de.civitascore.portal.configuration.ModelAtlasConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import static java.net.URLEncoder.encode;

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
        String.format("%s:%d", modelAtlasConfig.getBaseUrl(), modelAtlasConfig.getPort());

    String endpoint = String.format("/%s/schema/stages/%s?nsUri=%s", modelAtlasConfig.getScope(), modelAtlasConfig.getStage(), encode(nsUri, java.nio.charset.StandardCharsets.UTF_8));

    try {
      RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();

      return restClient
          .post()
          .uri(endpoint)
          .contentType(MediaType.APPLICATION_XML)
          .body(modelFile.getResource())
          .retrieve()
          .body(String.class);

    } catch (Exception e) {
      log.error("Failed to upload model file to Model Atlas", e);
      throw new RuntimeException("Failed to upload model file to Model Atlas", e);
    }
  }
}

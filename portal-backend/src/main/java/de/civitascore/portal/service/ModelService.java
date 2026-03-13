package de.civitascore.portal.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

  private final ModelRestClientRequestService modelRestClientRequestService;

  /**
   * Process and forward the model file to the external Model Atlas service.
   *
   * @param modelFile the uploaded model file
   * @return response from the external service
   */
  public String uploadModel(MultipartFile modelFile, String nsUri) {
    log.info("Processing model file upload: {}", Encode.forJava(nsUri));
    return modelRestClientRequestService.uploadModelFile(modelFile, nsUri);
  }

  /**
   * Process and forward the model string to the external Model Atlas service.
   *
   * @param modelContent the stringified XML model content
   * @param nsUri the namespace URI of the model
   * @return response from the external service
   */
  public String uploadModelString(String modelContent, String nsUri) {
    log.info("Processing model string upload: {}", Encode.forJava(nsUri));
    return modelRestClientRequestService.uploadModelString(modelContent, nsUri);
  }

  /**
   * Delete a model from the external Model Atlas service.
   *
   * @param nsUri the namespace URI of the model to delete
   */
  public void deleteModel(String nsUri) {
    log.info("Processing model deletion: {}", Encode.forJava(nsUri));
    modelRestClientRequestService.deleteModel(nsUri);
  }

  /**
   * Process and forward the model file download request to the external Model Atlas service.
   *
   * @param nsUri the namespace URI of the model to download
   * @param acceptHeader the desired response format
   * @return response from the external service in the requested format
   */
  public String downloadModel(@NotNull String nsUri, String acceptHeader) {
    log.debug(
        "Processing model file download for nsUri: {} with accept header: {}",
        Encode.forJava(nsUri),
        acceptHeader);
    return modelRestClientRequestService.downloadModelFile(nsUri, acceptHeader);
  }
}

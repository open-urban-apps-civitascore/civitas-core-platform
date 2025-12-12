package de.civitascore.portal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    log.info("Processing model file upload: {}", modelFile.getOriginalFilename());
    return modelRestClientRequestService.uploadModelFile(modelFile, nsUri);
  }
}

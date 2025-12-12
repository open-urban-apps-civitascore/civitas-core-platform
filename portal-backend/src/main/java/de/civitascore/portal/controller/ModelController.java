package de.civitascore.portal.controller;

import de.civitascore.portal.model.input.ModelInputDTO;
import de.civitascore.portal.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@Tag(name = "Models", description = "Model management endpoints")
public class ModelController {

  private final ModelService modelService;

  /**
   * Upload a model file to the Model Atlas service.
   *
   * @param modelInputDTO contains the model file to upload
   * @return response from the external service
   */
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Upload a model file",
      description = "Upload a model file to the external Model Atlas service")
  public ResponseEntity<String> uploadModel(@Valid @ModelAttribute ModelInputDTO modelInputDTO) {
    String response = modelService.uploadModel(modelInputDTO.getModelFile(), modelInputDTO.getNsUri());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}

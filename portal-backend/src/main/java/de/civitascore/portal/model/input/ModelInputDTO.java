package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelInputDTO {

  @NotNull(message = "Model file is required") private MultipartFile modelFile;

  @NotBlank(message = "nsUri may not be blank") private String nsUri;
}

package de.civitascore.portal.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

class ProblemTypeTest {

  /** Fails when renaming a constant silently changes a URN that portal-frontend reads. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          NOT_FOUND                     | 404
          INVALID_INPUT                 | 400
          MALFORMED_REQUEST             | 400
          VALIDATION_FAILED             | 400
          DATASET_NOT_EDITABLE          | 400
          MODEL_ROOT_MISSING            | 400
          MODEL_REFERENCE_NOT_AVAILABLE | 400
          MODEL_MEMBER_UNREACHABLE      | 400
          NOT_NULL_VIOLATION            | 400
          ENTITY_VALIDATION_FAILED      | 400
          UNAUTHORIZED                  | 401
          INVALID_TOKEN                 | 401
          ACCESS_DENIED                 | 403
          FORBIDDEN                     | 403
          UNIQUE_CONSTRAINT_VIOLATION   | 409
          FOREIGN_KEY_VIOLATION         | 409
          RESOURCE_IN_USE               | 409
          SAGA_IN_FLIGHT                | 409
          MODEL_MISSING                 | 409
          DATASOURCE_SCOPE_VIOLATION    | 422
          PIPELINE_CLOSURE_INVALID      | 422
          TOO_MANY_REQUESTS             | 429
          DATA_INTEGRITY_ERROR          | 500
          PERSISTENCE_ERROR             | 500
          EXTERNAL_SYSTEM_ERROR         | 502
          REGISTRY_UNAVAILABLE          | 503
          EXTERNAL_SYSTEM_TIMEOUT       | 504
          """)
  void catalog_containsEveryPublishedUrn_withItsStatus(String code, int status) {
    ProblemType type = ProblemType.valueOf(code);

    assertThat(type.type()).hasToString("urn:civitas:error:" + code);
    assertThat(type.status().value()).isEqualTo(status);
  }

  @Test
  void problemDetail_carriesTypeTitleStatusDetailAndInstance() {
    ProblemDetail problemDetail =
        ProblemType.RESOURCE_IN_USE.problemDetail("in use", URI.create("/v1/datasets"));

    assertThat(problemDetail.getType()).hasToString("urn:civitas:error:RESOURCE_IN_USE");
    assertThat(problemDetail.getTitle()).isEqualTo("Conflict");
    assertThat(problemDetail.getStatus()).isEqualTo(409);
    assertThat(problemDetail.getDetail()).isEqualTo("in use");
    assertThat(problemDetail.getInstance()).hasToString("/v1/datasets");
  }

  @ParameterizedTest
  @CsvSource({
    "400, INVALID_INPUT",
    "404, NOT_FOUND",
    "405, METHOD_NOT_ALLOWED",
    "406, NOT_ACCEPTABLE",
    "413, PAYLOAD_TOO_LARGE",
    "415, UNSUPPORTED_MEDIA_TYPE",
    "500, INTERNAL_ERROR",
    "501, INTERNAL_ERROR",
    "503, SERVICE_UNAVAILABLE"
  })
  void forFrameworkStatus_mapsStatusToCatalogType(int status, ProblemType expected) {
    assertThat(ProblemType.forFrameworkStatus(HttpStatusCode.valueOf(status))).contains(expected);
  }

  @Test
  void forFrameworkStatus_withStatusNoFrameworkErrorProduces_isEmpty() {
    assertThat(ProblemType.forFrameworkStatus(HttpStatus.I_AM_A_TEAPOT)).isEmpty();
  }
}

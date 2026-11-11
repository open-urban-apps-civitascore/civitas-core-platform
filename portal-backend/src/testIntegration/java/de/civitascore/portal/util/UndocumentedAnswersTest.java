package de.civitascore.portal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class UndocumentedAnswersTest {

  private static final String NOT_FOUND = "urn:civitas:error:NOT_FOUND";
  private static final String SAGA_IN_FLIGHT = "urn:civitas:error:SAGA_IN_FLIGHT";

  private static final JsonNode SPEC =
      new JsonMapper()
          .readTree(
              """
              {
                "paths": {
                  "/datasets/{id}": {
                    "get": {
                      "responses": {
                        "200": {},
                        "404": {"$ref": "#/components/responses/NotFound"}
                      }
                    }
                  },
                  "/datasets/mine": {
                    "get": {"responses": {"200": {}}}
                  }
                },
                "components": {
                  "responses": {
                    "NotFound": {
                      "content": {
                        "application/problem+json": {
                          "examples": {
                            "NOT_FOUND": {"$ref": "#/components/examples/NOT_FOUND"}
                          }
                        }
                      }
                    }
                  },
                  "examples": {
                    "NOT_FOUND": {"value": {"type": "urn:civitas:error:NOT_FOUND"}}
                  }
                }
              }
              """);

  @Test
  void literalSegment_beatsTemplateVariable() {
    List<String> report =
        UndocumentedAnswers.report(
            SPEC, List.of(new RecordedAnswer("GET", "/v1/datasets/mine", 404, NOT_FOUND)));

    assertThat(report)
        .containsExactly("GET /datasets/mine 404 " + NOT_FOUND + ": status not documented");
  }

  @Test
  void undocumentedStatus_isReported() {
    List<String> report =
        UndocumentedAnswers.report(
            SPEC, List.of(new RecordedAnswer("GET", "/v1/datasets/42", 409, SAGA_IN_FLIGHT)));

    assertThat(report)
        .containsExactly("GET /datasets/{id} 409 " + SAGA_IN_FLIGHT + ": status not documented");
  }

  @Test
  void problemTypeNotAmongExamplesOfItsStatus_isReported() {
    List<String> report =
        UndocumentedAnswers.report(
            SPEC, List.of(new RecordedAnswer("GET", "/v1/datasets/42", 404, SAGA_IN_FLIGHT)));

    assertThat(report)
        .containsExactly(
            "GET /datasets/{id} 404 "
                + SAGA_IN_FLIGHT
                + ": problem type not documented for this status");
  }

  @Test
  void documentedStatusAndType_isNotReported() {
    assertThat(
            UndocumentedAnswers.report(
                SPEC,
                List.of(
                    new RecordedAnswer("GET", "/v1/datasets/42", 404, NOT_FOUND),
                    new RecordedAnswer("GET", "/v1/datasets/42", 200, null))))
        .isEmpty();
  }

  @Test
  void unknownRoute404_isNotReported() {
    assertThat(
            UndocumentedAnswers.report(
                SPEC,
                List.of(
                    new RecordedAnswer("GET", "/v1/unknown", 404, NOT_FOUND),
                    new RecordedAnswer("DELETE", "/v1/datasets/42", 405, null))))
        .isEmpty();
  }

  @Test
  void unknownRoute401_isNotReported() {
    assertThat(
            UndocumentedAnswers.report(
                SPEC,
                List.of(
                    new RecordedAnswer(
                        "GET", "/v1/unknown", 401, "urn:civitas:error:UNAUTHORIZED"))))
        .isEmpty();
  }

  @Test
  void answerWithoutOperation_isReported() {
    assertThat(
            UndocumentedAnswers.report(
                SPEC, List.of(new RecordedAnswer("DELETE", "/v1/datasets/42", 500, null))))
        .containsExactly("DELETE /v1/datasets/42 500 -: no operation");
  }
}

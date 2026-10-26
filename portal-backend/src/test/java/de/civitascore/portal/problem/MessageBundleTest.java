package de.civitascore.portal.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

class MessageBundleTest {

  private static final String FREE_FORM_DETAIL = "{0}";

  @Test
  void everyCatalogType_hasTitleAndDetail() {
    assertThat(ProblemType.values())
        .allSatisfy(
            type -> {
              assertThat(message("problem." + type.name() + ".title")).as(type.name()).isNotBlank();
              assertThat(message("problem." + type.name() + ".detail"))
                  .as(type.name())
                  .isNotBlank();
            });
  }

  @Test
  void everyFreeFormType_hasAnExampleDetail() {
    List<ProblemType> freeForm =
        Arrays.stream(ProblemType.values())
            .filter(type -> FREE_FORM_DETAIL.equals(message("problem." + type.name() + ".detail")))
            .toList();

    assertThat(freeForm)
        .isNotEmpty()
        .allSatisfy(
            type ->
                assertThat(message("problem." + type.name() + ".example"))
                    .as(type.name())
                    .isNotBlank());
  }

  /** Fails when a Spring upgrade adds a framework error whose detail would echo client input. */
  @Test
  void everyFrameworkExceptionOfResponseEntityExceptionHandler_hasDetail() throws Exception {
    Class<? extends Throwable>[] frameworkExceptions =
        ResponseEntityExceptionHandler.class
            .getMethod("handleException", Exception.class, WebRequest.class)
            .getAnnotation(ExceptionHandler.class)
            .value();

    assertThat(frameworkExceptions)
        .isNotEmpty()
        .allSatisfy(
            exception ->
                assertThat(message("problemDetail." + exception.getName()))
                    .as(exception.getName())
                    .isNotBlank());
  }

  @Test
  void titles_areUniqueAcrossTheCatalog() {
    List<String> titles =
        Arrays.stream(ProblemType.values())
            .map(type -> message("problem." + type.name() + ".title"))
            .toList();

    assertThat(titles).doesNotHaveDuplicates();
  }

  private static String message(String code) {
    return TestProblemDetails.MESSAGES.getMessage(code, null, null, Locale.ROOT);
  }
}

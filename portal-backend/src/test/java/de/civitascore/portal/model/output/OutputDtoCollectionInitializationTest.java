package de.civitascore.portal.model.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Verifies that every OutputDTO class initializes its Collection fields (List, Set) with empty
 * collections instead of null. This catches new OutputDTOs that forget to initialize their
 * collection fields.
 */
class OutputDtoCollectionInitializationTest {

  private static final String OUTPUT_DTO_PACKAGE = "de.civitascore.portal.model.output";

  @TestFactory
  List<DynamicTest> allOutputDtoCollectionFieldsShouldBeInitialized()
      throws ClassNotFoundException {
    Set<Class<?>> outputDtoClasses = findOutputDtoClasses();

    assertThat(outputDtoClasses).as("Should find OutputDTO classes on the classpath").isNotEmpty();

    List<DynamicTest> tests = new ArrayList<>();

    for (Class<?> dtoClass : outputDtoClasses) {
      for (Field field : dtoClass.getDeclaredFields()) {
        if (Collection.class.isAssignableFrom(field.getType())) {
          tests.add(
              DynamicTest.dynamicTest(
                  dtoClass.getSimpleName() + "." + field.getName() + " should not be null",
                  () -> {
                    Object instance = dtoClass.getDeclaredConstructor().newInstance();
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    assertThat(value)
                        .as(
                            "%s.%s should be initialized with a collection, not null",
                            dtoClass.getSimpleName(), field.getName())
                        .isNotNull();
                  }));
        }
      }
    }

    assertThat(tests).as("Should find at least one collection field to test").isNotEmpty();
    return tests;
  }

  private Set<Class<?>> findOutputDtoClasses() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(
        new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*OutputDTO$")));

    Set<BeanDefinition> candidates = scanner.findCandidateComponents(OUTPUT_DTO_PACKAGE);

    Set<Class<?>> classes =
        candidates.stream()
            .map(
                bd -> {
                  try {
                    return Class.forName(bd.getBeanClassName());
                  } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.toSet());
    return classes;
  }
}

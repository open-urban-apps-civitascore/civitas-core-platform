package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.DataSetNotEditableException;
import de.civitascore.portal.util.SagaInFlightException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Drives a real create, update and delete through each sub-entity's service and asserts the guard
 * rejects each one. The guard's own unit tests prove it decides correctly; these prove every
 * service actually calls it, which is what breaks when a hook override is added or edited without
 * one.
 *
 * <p>Entities come from the classpath and services from the context, so a sub-entity added later
 * fails {@link #everySubEntityHasAFixture()} until it is registered here — the point at which
 * whoever added it has to decide whether the guard really applies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Sub-entity guard wiring")
class SubEntityGuardWiringIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private ApplicationContext applicationContext;
  @Autowired private PortalTestDataFactory portalData;
  @Autowired private DataSetRepository dataSetRepository;

  private record Fixture(UUID id, BaseInputDTO input) {}

  private final Map<Class<?>, BiFunction<PortalTestDataFactory, DataSet, Fixture>> fixtures =
      registerFixtures();

  private static Map<Class<?>, BiFunction<PortalTestDataFactory, DataSet, Fixture>>
      registerFixtures() {
    Map<Class<?>, BiFunction<PortalTestDataFactory, DataSet, Fixture>> registry =
        new LinkedHashMap<>();

    registry.put(
        Pipeline.class,
        (data, dataSet) -> {
          Pipeline pipeline = data.pipeline(dataSet);
          PipelineInputDTO input = new PipelineInputDTO();
          input.setName(pipeline.getName());
          input.setDataSetId(dataSet.getId());
          return new Fixture(pipeline.getId(), input);
        });

    registry.put(
        DataSink.class,
        (data, dataSet) -> {
          DataSink sink = data.dataSink(dataSet);
          DataSinkInputDTO input = new DataSinkInputDTO();
          input.setDataSinkType(DataSinkType.FROST);
          input.setConfiguration(Map.of());
          input.setDataSetId(dataSet.getId());
          return new Fixture(sink.getId(), input);
        });

    registry.put(
        Style.class,
        (data, dataSet) -> {
          Style style = data.style(dataSet);
          StyleInputDTO input = new StyleInputDTO();
          input.setName(style.getName());
          input.setSldContent("<StyledLayerDescriptor version=\"1.1.0\"/>");
          input.setDataSetId(dataSet.getId());
          return new Fixture(style.getId(), input);
        });

    registry.put(
        Layer.class,
        (data, dataSet) -> {
          Layer layer = data.layer(dataSet, data.dataSink(dataSet));
          LayerInputDTO input = new LayerInputDTO();
          input.setLayerName(layer.getLayerName());
          input.setDataSinkId(layer.getDataSink().getId());
          input.setDataSetId(dataSet.getId());
          return new Fixture(layer.getId(), input);
        });

    return registry;
  }

  static Stream<Class<?>> subEntityTypes() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(DataSetOwnedEntity.class));

    return scanner.findCandidateComponents(DataSet.class.getPackageName()).stream()
        .map(SubEntityGuardWiringIntegrationTest::loadClass)
        .sorted(Comparator.comparing(Class::getSimpleName));
  }

  private static Class<?> loadClass(BeanDefinition definition) {
    try {
      return Class.forName(definition.getBeanClassName());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  @SuppressWarnings("unchecked")
  private BaseService<DataSetOwnedEntity, BaseInputDTO> serviceFor(Class<?> entityType) {
    return (BaseService<DataSetOwnedEntity, BaseInputDTO>)
        Stream.of(applicationContext.getBeanNamesForType(BaseService.class))
            .map(name -> applicationContext.getBean(name, BaseService.class))
            .filter(
                service -> {
                  ResolvableType resolved =
                      ResolvableType.forClass(BaseService.class, service.getClass());
                  return entityType.equals(resolved.getGeneric(0).resolve());
                })
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("No BaseService found for " + entityType.getSimpleName()));
  }

  private DataSet dataSetWith(DataSetStatus status, PendingSagaType pendingSagaType) {
    DataSet dataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));
    dataSet.setDataSetStatus(status);
    dataSet.setPendingSagaType(pendingSagaType);
    return dataSetRepository.save(dataSet);
  }

  private Fixture fixtureFor(Class<?> entityType, DataSet dataSet) {
    return fixtures.get(entityType).apply(portalData, dataSet);
  }

  @Test
  @DisplayName("The registered fixtures are exactly the sub-entities on the classpath")
  void everySubEntityHasAFixture() {
    assertThat(subEntityTypes())
        .as(
            "An entity extending DataSetOwnedEntity is claimed to follow the dataset lifecycle. "
                + "Register a fixture so this sweep proves its service enforces that, or move the "
                + "entity off the superclass if it is editable independently of its dataset.")
        .containsExactlyInAnyOrderElementsOf(fixtures.keySet());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("update() is rejected when the parent DataSet is READY")
  void updateRejectedWhenParentIsReady(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);
    draft.setDataSetStatus(DataSetStatus.READY);
    dataSetRepository.save(draft);

    assertThatThrownBy(() -> serviceFor(entityType).update(fixture.id(), fixture.input()))
        .as("%s must consult the guard on update", entityType.getSimpleName())
        .isInstanceOf(DataSetNotEditableException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("update() is rejected when the parent DataSet is AVAILABLE")
  void updateRejectedWhenParentIsAvailable(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);
    draft.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSetRepository.save(draft);

    assertThatThrownBy(() -> serviceFor(entityType).update(fixture.id(), fixture.input()))
        .isInstanceOf(DataSetNotEditableException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("update() is rejected while a saga is in flight on a DRAFT parent")
  void updateRejectedWhileSagaInFlight(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);
    draft.setPendingSagaType(PendingSagaType.UNRELEASE);
    dataSetRepository.save(draft);

    assertThatThrownBy(() -> serviceFor(entityType).update(fixture.id(), fixture.input()))
        .isInstanceOf(SagaInFlightException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("create() is rejected when the parent DataSet is READY")
  void createRejectedWhenParentIsReady(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);
    draft.setDataSetStatus(DataSetStatus.READY);
    dataSetRepository.save(draft);

    assertThatThrownBy(() -> serviceFor(entityType).create(fixture.input()))
        .as("%s must consult the guard on create", entityType.getSimpleName())
        .isInstanceOf(DataSetNotEditableException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("create() is rejected while a saga is in flight on a DRAFT parent")
  void createRejectedWhileSagaInFlight(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);
    draft.setPendingSagaType(PendingSagaType.UNRELEASE);
    dataSetRepository.save(draft);

    assertThatThrownBy(() -> serviceFor(entityType).create(fixture.input()))
        .isInstanceOf(SagaInFlightException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("deleteById() is rejected when the parent DataSet is READY")
  void deleteRejectedWhenParentIsReady(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);
    UUID id = fixture.id();
    draft.setDataSetStatus(DataSetStatus.READY);
    dataSetRepository.save(draft);

    assertThatThrownBy(() -> serviceFor(entityType).deleteById(id))
        .as("%s must consult the guard on delete", entityType.getSimpleName())
        .isInstanceOf(DataSetNotEditableException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("deleteById() is rejected while a saga is in flight on a DRAFT parent")
  void deleteRejectedWhileSagaInFlight(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);
    UUID id = fixture.id();
    draft.setPendingSagaType(PendingSagaType.UNRELEASE);
    dataSetRepository.save(draft);

    assertThatThrownBy(() -> serviceFor(entityType).deleteById(id))
        .isInstanceOf(SagaInFlightException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("subEntityTypes")
  @DisplayName("update() is accepted on a DRAFT parent with no saga in flight")
  void updateAcceptedOnDraftParent(Class<?> entityType) {
    DataSet draft = dataSetWith(DataSetStatus.DRAFT, null);
    Fixture fixture = fixtureFor(entityType, draft);

    assertThatCode(() -> serviceFor(entityType).update(fixture.id(), fixture.input()))
        .as("The guard must not reject a DRAFT parent")
        .doesNotThrowAnyException();
  }
}

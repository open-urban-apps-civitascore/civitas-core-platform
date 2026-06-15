package de.civitascore.portal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DashboardSource;
import de.civitascore.portal.model.entity.DataSet;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.TransientPropertyValueException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("Dashboard Binding Persistence Tests")
class DashboardBindingPersistenceIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private DashboardRepository dashboardRepository;
  @Autowired private DashboardSourceRepository dashboardSourceRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private EntityManager entityManager;

  @AfterEach
  void cleanup() {
    dashboardRepository.deleteAllInBatch();
    dashboardSourceRepository.deleteAllInBatch();
    dataSetRepository.deleteAllInBatch();
  }

  private DataSet newDataSet() {
    DataSet ds = new DataSet();
    ds.setName("dataset-" + UUID.randomUUID().toString().substring(0, 8));
    return dataSetRepository.saveAndFlush(ds);
  }

  private Dashboard newDashboard(DataSet dataset) {
    Dashboard dashboard = new Dashboard();
    dashboard.setExternalDashboardId("dash-" + UUID.randomUUID().toString().substring(0, 8));
    dashboard.setName("Dashboard");
    dashboard.setDataset(dataset);
    return dashboard;
  }

  private DashboardSource newSource() {
    DashboardSource source = new DashboardSource();
    source.setExternalSourceId("src-" + UUID.randomUUID().toString().substring(0, 8));
    source.setMappedDatasetId(UUID.randomUUID());
    source.setPhysicalSourceIdentifier("orders");
    return source;
  }

  @Test
  @DisplayName("Dashboard persists with active defaulting to true")
  void dashboardPersistsWithDefaults() {
    UUID id = dashboardRepository.saveAndFlush(newDashboard(newDataSet())).getId();
    entityManager.clear();

    Dashboard loaded = dashboardRepository.findById(id).orElseThrow();
    assertThat(loaded.isActive()).isTrue();
  }

  @Test
  @DisplayName("A dashboard requires a dataset (the FK is mandatory)")
  void dashboardRequiresDataset() {
    Dashboard dashboard = newDashboard(null);

    assertThatThrownBy(() -> dashboardRepository.saveAndFlush(dashboard))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("dataset");
  }

  @Test
  @Transactional
  @DisplayName("Binding a persisted dashboard source is stored and reloaded")
  void bindingIsPersistedAndReloaded() {
    DashboardSource source = dashboardSourceRepository.saveAndFlush(newSource());

    Dashboard dashboard = newDashboard(newDataSet());
    dashboard.setSources(Set.of(source));
    UUID id = dashboardRepository.saveAndFlush(dashboard).getId();

    entityManager.clear();

    Dashboard loaded = dashboardRepository.findById(id).orElseThrow();
    assertThat(loaded.getSources())
        .extracting(DashboardSource::getId)
        .containsExactly(source.getId());
  }

  @Test
  @DisplayName("Cannot bind a non-existent (transient) dashboard source")
  void cannotBindNonExistentSource() {
    Dashboard dashboard = newDashboard(newDataSet());
    dashboard.setSources(Set.of(newSource()));

    // The binding has no cascade, so Hibernate rejects the transient source before flushing
    // rather than letting the DB FK trip.
    assertThatThrownBy(() -> dashboardRepository.saveAndFlush(dashboard))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(TransientPropertyValueException.class);
  }

  @Test
  @DisplayName("Duplicate external dashboard id is rejected by the unique constraint")
  void duplicateExternalDashboardIdIsRejected() {
    DataSet ds = newDataSet();
    Dashboard first = newDashboard(ds);
    first.setExternalDashboardId("dash-fixed");
    dashboardRepository.saveAndFlush(first);

    Dashboard second = newDashboard(ds);
    second.setExternalDashboardId("dash-fixed");

    assertThatThrownBy(() -> dashboardRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_dashboards_external_dashboard_id");
  }

  @Test
  @DisplayName("Duplicate external source id is rejected by the unique constraint")
  void duplicateExternalSourceIdIsRejected() {
    DashboardSource first = newSource();
    first.setExternalSourceId("src-fixed");
    dashboardSourceRepository.saveAndFlush(first);

    DashboardSource second = newSource();
    second.setExternalSourceId("src-fixed");

    assertThatThrownBy(() -> dashboardSourceRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_dashboard_sources_external_source_id");
  }

  @Test
  @DisplayName("findByDatasetId returns the dashboards of a platform dataset")
  void findByDatasetIdReturnsDashboards() {
    DataSet dsA = newDataSet();
    DataSet dsB = newDataSet();
    Dashboard a1 = newDashboard(dsA);
    Dashboard a2 = newDashboard(dsA);
    Dashboard b1 = newDashboard(dsB);
    dashboardRepository.saveAllAndFlush(List.of(a1, a2, b1));
    entityManager.clear();

    assertThat(dashboardRepository.findByDatasetId(dsA.getId()))
        .extracting(Dashboard::getExternalDashboardId)
        .containsExactlyInAnyOrder(a1.getExternalDashboardId(), a2.getExternalDashboardId());
  }

  @Test
  @DisplayName("findByMappedDatasetId returns sources mapped to a CIVITAS dataset")
  void findByMappedDatasetIdReturnsMappedSources() {
    UUID datasetId = UUID.randomUUID();
    DashboardSource a = newSource();
    a.setMappedDatasetId(datasetId);
    DashboardSource b = newSource();
    b.setMappedDatasetId(datasetId);
    DashboardSource other = newSource();
    dashboardSourceRepository.saveAllAndFlush(List.of(a, b, other));
    entityManager.clear();

    assertThat(dashboardSourceRepository.findByMappedDatasetId(datasetId))
        .extracting(DashboardSource::getExternalSourceId)
        .containsExactlyInAnyOrder(a.getExternalSourceId(), b.getExternalSourceId());
  }

  @Test
  @DisplayName("Deleting a dashboard removes its binding rows but keeps the bound source")
  void deletingDashboardCascadesBindingsButKeepsSource() {
    DashboardSource source = dashboardSourceRepository.saveAndFlush(newSource());
    Dashboard dashboard = newDashboard(newDataSet());
    dashboard.setSources(Set.of(source));
    UUID dashboardId = dashboardRepository.saveAndFlush(dashboard).getId();

    dashboardRepository.deleteById(dashboardId);
    dashboardRepository.flush();
    entityManager.clear();

    assertThat(dashboardRepository.findById(dashboardId)).isEmpty();
    assertThat(dashboardSourceRepository.findById(source.getId())).isPresent();
  }

  @Test
  @DisplayName("Deleting a bound source cascades the binding row but keeps the dashboard")
  void deletingSourceCascadesBindingButKeepsDashboard() {
    DashboardSource source = dashboardSourceRepository.saveAndFlush(newSource());
    Dashboard dashboard = newDashboard(newDataSet());
    dashboard.setSources(Set.of(source));
    UUID dashboardId = dashboardRepository.saveAndFlush(dashboard).getId();
    UUID sourceId = source.getId();

    dashboardSourceRepository.deleteById(sourceId);
    dashboardSourceRepository.flush();
    entityManager.clear();

    assertThat(dashboardSourceRepository.findById(sourceId)).isEmpty();
    assertThat(dashboardRepository.findById(dashboardId)).isPresent();
    Number bindingCount =
        (Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM dashboard_dashboard_sources"
                        + " WHERE dashboard_source_id = :id")
                .setParameter("id", sourceId)
                .getSingleResult();
    assertThat(bindingCount.longValue()).isZero();
  }

  @Test
  @DisplayName("Dashboard lookups by external dashboard id resolve and existence checks work")
  void dashboardExternalIdLookups() {
    Dashboard dashboard = newDashboard(newDataSet());
    dashboard.setExternalDashboardId("dash-lookup");
    dashboardRepository.saveAndFlush(dashboard);
    entityManager.clear();

    assertThat(dashboardRepository.findByExternalDashboardId("dash-lookup")).isPresent();
    assertThat(dashboardRepository.findByExternalDashboardId("missing")).isEmpty();
    assertThat(dashboardRepository.existsByExternalDashboardId("dash-lookup")).isTrue();
    assertThat(dashboardRepository.existsByExternalDashboardId("missing")).isFalse();
  }

  @Test
  @DisplayName("Source lookups by external source id resolve and existence checks work")
  void sourceExternalIdLookups() {
    DashboardSource source = newSource();
    source.setExternalSourceId("src-lookup");
    dashboardSourceRepository.saveAndFlush(source);
    entityManager.clear();

    assertThat(dashboardSourceRepository.findByExternalSourceId("src-lookup")).isPresent();
    assertThat(dashboardSourceRepository.findByExternalSourceId("missing")).isEmpty();
    assertThat(dashboardSourceRepository.existsByExternalSourceId("src-lookup")).isTrue();
    assertThat(dashboardSourceRepository.existsByExternalSourceId("missing")).isFalse();
  }

  @Test
  @DisplayName("A null mapped dataset id is rejected (the authorization anchor is mandatory)")
  void nullMappedDatasetIdIsRejected() {
    DashboardSource source = newSource();
    source.setMappedDatasetId(null);

    assertThatThrownBy(() -> dashboardSourceRepository.saveAndFlush(source))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("mappedDatasetId");
  }

  @Test
  @DisplayName("A source persists with a mappedDatasetId that references no existing dataset")
  void sourceMappedDatasetIdHasNoForeignKey() {
    DashboardSource source = newSource();
    source.setMappedDatasetId(UUID.randomUUID());

    UUID id = dashboardSourceRepository.saveAndFlush(source).getId();
    entityManager.clear();

    assertThat(dashboardSourceRepository.findById(id)).isPresent();
  }

  @Test
  @Transactional
  @DisplayName("A source bound to two dashboards survives the deletion of one of them")
  void sharedSourceSurvivesDeletionOfOneDashboard() {
    DashboardSource source = dashboardSourceRepository.saveAndFlush(newSource());
    Dashboard kept = newDashboard(newDataSet());
    Dashboard removed = newDashboard(newDataSet());
    kept.setSources(Set.of(source));
    removed.setSources(Set.of(source));
    UUID keptId = dashboardRepository.saveAndFlush(kept).getId();
    UUID removedId = dashboardRepository.saveAndFlush(removed).getId();

    dashboardRepository.deleteById(removedId);
    dashboardRepository.flush();
    entityManager.clear();

    assertThat(dashboardRepository.findById(removedId)).isEmpty();
    assertThat(dashboardSourceRepository.findById(source.getId())).isPresent();
    assertThat(dashboardRepository.findById(keptId).orElseThrow().getSources())
        .extracting(DashboardSource::getId)
        .containsExactly(source.getId());
  }

  @Test
  @DisplayName("Several dashboards may await engine confirmation with a null external id at once")
  void multipleDashboardsMayHaveNullExternalId() {
    Dashboard first = newDashboard(newDataSet());
    first.setExternalDashboardId(null);
    Dashboard second = newDashboard(newDataSet());
    second.setExternalDashboardId(null);

    dashboardRepository.saveAndFlush(first);
    assertThatCode(() -> dashboardRepository.saveAndFlush(second)).doesNotThrowAnyException();
  }
}

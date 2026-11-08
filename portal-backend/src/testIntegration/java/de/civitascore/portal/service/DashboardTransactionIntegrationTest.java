package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DashboardRepository;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DashboardTransactionIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private DashboardService dashboardService;
  @Autowired private DashboardRepository dashboardRepository;
  @Autowired private ModelRegistryGateway modelRegistryGateway;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    portalData.cleanAll();
  }

  @Test
  void create_outerTransactionRollsBack_dashboardAndModelForgeArtifactAreAbsent() {
    DataSet dataSet = portalData.dataSet();
    DashboardInputDTO input = new DashboardInputDTO();
    input.setDataSetId(dataSet.getId());
    input.setName("Rollback check");
    AtomicReference<Dashboard> created = new AtomicReference<>();

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          created.set(dashboardService.create(input));
                          throw new IllegalStateException("force rollback");
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("force rollback");

    assertThat(created.get()).isNotNull();
    assertThat(dashboardRepository.findById(created.get().getId())).isEmpty();
    assertThat(modelRegistryGateway.fetchPayload(created.get().getModelUrn())).isEmpty();
  }
}

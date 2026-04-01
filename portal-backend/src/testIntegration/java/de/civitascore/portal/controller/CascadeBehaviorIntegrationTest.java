package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.service.DataSpaceService;
import de.civitascore.portal.service.GroupService;
import de.civitascore.portal.util.ResourceInUseException;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests verifying JPA cascade behavior when parent entities are deleted. Each test
 * creates a parent entity with at least one child, deletes the parent via the repository, flushes
 * the EntityManager, and checks whether the child was cascade-deleted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Cascade Behavior Tests")
class CascadeBehaviorIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private DistributionRepository distributionRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataStructureRepository dataStructureRepository;
  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private DataSpaceRepository dataSpaceRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupService groupService;
  @Autowired private DataSpaceService dataSpaceService;

  private static String uniqueName(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @AfterEach
  void cleanup() {
    assignmentRepository.deleteAll();
    pipelineRepository.deleteAll();
    distributionRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataStructureVersionRepository.deleteAll();
    dataStructureRepository.deleteAll();
    dataSourceRepository.deleteAll();
    dataSpaceRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
  }

  // ---------------------------------------------------------------------------
  // Helper methods
  // ---------------------------------------------------------------------------

  private DataSet createDataSet() {
    DataSet ds = new DataSet();
    ds.setName(uniqueName("dataset"));
    ds.setDescription("cascade test dataset");
    return dataSetRepository.save(ds);
  }

  private Pipeline createPipeline(DataSet dataSet) {
    Pipeline p = new Pipeline();
    p.setName(uniqueName("pipeline"));
    p.setDataSet(dataSet);
    p.setVersion(1L);
    return pipelineRepository.save(p);
  }

  private Distribution createDistribution(DataSet dataSet) {
    Distribution d = new Distribution();
    d.setDataSet(dataSet);
    d.setAccessUrl("http://example.com/" + UUID.randomUUID().toString().substring(0, 8));
    return distributionRepository.save(d);
  }

  private Group createGroup() {
    Group g = new Group();
    g.setName(uniqueName("group"));
    return groupRepository.save(g);
  }

  private Role createRole(RoleType roleType) {
    Role r = new Role();
    r.setName(uniqueName("role"));
    r.setRoleType(roleType);
    return roleRepository.save(r);
  }

  private DataStructure createDataStructure() {
    DataStructure ds = new DataStructure();
    ds.setName(uniqueName("datastruct"));
    return dataStructureRepository.save(ds);
  }

  private DataStructureVersion createDataStructureVersion(DataStructure dataStructure) {
    DataStructureVersion dsv = new DataStructureVersion();
    dsv.setDataStructure(dataStructure);
    dsv.setVersion(uniqueName("v"));
    dsv.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    dsv.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    return dataStructureVersionRepository.save(dsv);
  }

  private DataSource createDataSource() {
    DataSource ds = new DataSource();
    ds.setName(uniqueName("datasource"));
    return dataSourceRepository.save(ds);
  }

  private DataSpace createDataSpace() {
    DataSpace ds = new DataSpace();
    ds.setName(uniqueName("dataspace"));
    return dataSpaceRepository.save(ds);
  }

  private Assignment createUnscopedAssignment(Group group, Role role) {
    Assignment a = new Assignment();
    a.setGroup(group);
    a.setRole(role);
    return assignmentRepository.save(a);
  }

  private Assignment createScopedAssignment(
      Group group, Role role, ScopeType scopeType, Object scopeEntity) {
    Assignment a = new Assignment();
    a.setGroup(group);
    a.setRole(role);
    a.setScopeType(scopeType);
    switch (scopeType) {
      case DATASET -> a.setDataset((DataSet) scopeEntity);
      case DATASOURCE -> a.setDataSource((DataSource) scopeEntity);
      case DATASTRUCTURE -> a.setDataStructure((DataStructure) scopeEntity);
      case DATASPACE -> a.setDataSpace((DataSpace) scopeEntity);
      default -> throw new IllegalArgumentException("Unsupported scope type: " + scopeType);
    }
    return assignmentRepository.save(a);
  }

  // ---------------------------------------------------------------------------
  // DataSet cascades
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DataSet cascades")
  class DataSetCascades {

    @Test
    @Transactional
    @DisplayName("Deleting DataSet should cascade-delete its Pipelines")
    void deletingDataSet_shouldCascadeDeletePipelines() {
      DataSet dataSet = createDataSet();
      Pipeline pipeline = createPipeline(dataSet);
      UUID pipelineId = pipeline.getId();

      entityManager.flush();
      entityManager.clear();

      dataSetRepository.deleteById(dataSet.getId());
      entityManager.flush();

      assertThat(pipelineRepository.findById(pipelineId)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Deleting DataSet should cascade-delete its Distributions")
    void deletingDataSet_shouldCascadeDeleteDistributions() {
      DataSet dataSet = createDataSet();
      Distribution distribution = createDistribution(dataSet);
      UUID distributionId = distribution.getId();

      entityManager.flush();
      entityManager.clear();

      dataSetRepository.deleteById(dataSet.getId());
      entityManager.flush();

      assertThat(distributionRepository.findById(distributionId)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Deleting DataSet should cascade-delete its scoped Assignments")
    void deletingDataSet_shouldCascadeDeleteAssignments() {
      DataSet dataSet = createDataSet();
      Group group = createGroup();
      Role role = createRole(RoleType.DATA);
      Assignment assignment = createScopedAssignment(group, role, ScopeType.DATASET, dataSet);
      UUID assignmentId = assignment.getId();

      entityManager.flush();
      entityManager.clear();

      dataSetRepository.deleteById(dataSet.getId());
      entityManager.flush();

      assertThat(assignmentRepository.findById(assignmentId)).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // Group cascades
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Group cascades")
  class GroupCascades {

    @Test
    @Transactional
    @DisplayName("Deleting Group with children should be prevented")
    void deletingGroup_withChildren_shouldThrowResourceInUseException() {
      Group parent = createGroup();
      Group child = new Group();
      child.setName(uniqueName("child-group"));
      child.setParentGroup(parent);
      groupRepository.save(child);

      entityManager.flush();
      entityManager.clear();

      UUID parentId = parent.getId();
      assertThatThrownBy(() -> groupService.deleteById(parentId))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("child groups");
    }

    @Test
    @Transactional
    @DisplayName("Deleting Group should cascade-delete its Assignments")
    void deletingGroup_shouldCascadeDeleteAssignments() {
      Group group = createGroup();
      Role role = createRole(RoleType.SYSTEM);
      Assignment assignment = createUnscopedAssignment(group, role);
      UUID assignmentId = assignment.getId();

      entityManager.flush();
      entityManager.clear();

      groupRepository.deleteById(group.getId());
      entityManager.flush();

      assertThat(assignmentRepository.findById(assignmentId)).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // DataStructure cascades
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DataStructure cascades")
  class DataStructureCascades {

    @Test
    @Transactional
    @DisplayName("Deleting DataStructure should cascade-delete its DataStructureVersions")
    void deletingDataStructure_shouldCascadeDeleteVersions() {
      DataStructure dataStructure = createDataStructure();
      DataStructureVersion version = createDataStructureVersion(dataStructure);
      UUID versionId = version.getId();

      entityManager.flush();
      entityManager.clear();

      dataStructureRepository.deleteById(dataStructure.getId());
      entityManager.flush();

      assertThat(dataStructureVersionRepository.findById(versionId)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Deleting DataStructure should cascade-delete its scoped Assignments")
    void deletingDataStructure_shouldCascadeDeleteAssignments() {
      DataStructure dataStructure = createDataStructure();
      Group group = createGroup();
      Role role = createRole(RoleType.DATA);
      Assignment assignment =
          createScopedAssignment(group, role, ScopeType.DATASTRUCTURE, dataStructure);
      UUID assignmentId = assignment.getId();

      entityManager.flush();
      entityManager.clear();

      dataStructureRepository.deleteById(dataStructure.getId());
      entityManager.flush();

      assertThat(assignmentRepository.findById(assignmentId)).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // DataSource cascades
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DataSource cascades")
  class DataSourceCascades {

    @Test
    @Transactional
    @DisplayName("Deleting DataSource should cascade-delete its scoped Assignments")
    void deletingDataSource_shouldCascadeDeleteAssignments() {
      DataSource dataSource = createDataSource();
      Group group = createGroup();
      Role role = createRole(RoleType.DATA);
      Assignment assignment = createScopedAssignment(group, role, ScopeType.DATASOURCE, dataSource);
      UUID assignmentId = assignment.getId();

      entityManager.flush();
      entityManager.clear();

      dataSourceRepository.deleteById(dataSource.getId());
      entityManager.flush();

      assertThat(assignmentRepository.findById(assignmentId)).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // DataSpace cascades
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DataSpace cascades")
  class DataSpaceCascades {

    @Test
    @Transactional
    @DisplayName("Deleting DataSpace with children should be prevented")
    void deletingDataSpace_withChildren_shouldThrowResourceInUseException() {
      DataSpace parent = createDataSpace();
      DataSpace child = new DataSpace();
      child.setName(uniqueName("child-dataspace"));
      child.setParentDataSpace(parent);
      dataSpaceRepository.save(child);

      entityManager.flush();
      entityManager.clear();

      UUID parentId = parent.getId();
      assertThatThrownBy(() -> dataSpaceService.deleteById(parentId))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("child data spaces");
    }
  }
}

package de.civitascore.portal.config;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Activity;
import de.civitascore.portal.model.entity.Agent;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSetSeries;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Resource;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.ActivityRepository;
import de.civitascore.portal.repository.AgentRepository;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSetSeriesRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.ResourceRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.UserRepository;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

/**
 * General-purpose test data factory for portal integration tests.
 *
 * <p>Each entity has a no-arg method returning a persisted entity with sensible defaults, and an
 * overload accepting a {@link Consumer} to customize the Lombok builder before persistence.
 *
 * <p>Entities with required parent references ({@link Pipeline}, {@link DataStructureVersion}) take
 * the parent as an explicit first parameter. {@link Assignment} uses dedicated overloads per scope
 * type.
 */
@TestComponent
public class PortalTestDataFactory {

  private static final AtomicLong SEQ = new AtomicLong();

  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataStructureRepository dataStructureRepository;
  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private CatalogRepository catalogRepository;
  @Autowired private DataSpaceRepository dataSpaceRepository;
  @Autowired private DistributionRepository distributionRepository;
  @Autowired private ResourceRepository resourceRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private ActivityRepository activityRepository;
  @Autowired private DataSetSeriesRepository dataSetSeriesRepository;

  private static long nextSeq() {
    return SEQ.incrementAndGet();
  }

  // ---------------------------------------------------------------------------
  // User
  // ---------------------------------------------------------------------------

  public User user() {
    return user(b -> {});
  }

  public User user(Consumer<User.UserBuilder<?, ?>> customizer) {
    long seq = nextSeq();
    var builder =
        User.builder()
            .firstName("First" + seq)
            .lastName("Last" + seq)
            .email("user-" + seq + "@test.local")
            .active(true);
    customizer.accept(builder);
    return userRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // Group
  // ---------------------------------------------------------------------------

  public Group group() {
    return group(b -> {});
  }

  public Group group(Consumer<Group.GroupBuilder<?, ?>> customizer) {
    var builder = Group.builder().name("group-" + nextSeq());
    customizer.accept(builder);
    return groupRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // Role
  // ---------------------------------------------------------------------------

  public Role role() {
    return role(b -> {});
  }

  public Role role(Consumer<Role.RoleBuilder<?, ?>> customizer) {
    var builder = Role.builder().name("role-" + nextSeq()).roleType(RoleType.DATA);
    customizer.accept(builder);
    return roleRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // Assignment
  // ---------------------------------------------------------------------------

  /** Creates a tenant-scoped assignment (no scope entity). */
  public Assignment assignment(Group group, Role role) {
    Assignment a = Assignment.builder().group(group).role(role).scopeType(ScopeType.TENANT).build();
    return assignmentRepository.save(a);
  }

  public Assignment assignment(Group group, Role role, DataSet scope) {
    Assignment a = Assignment.builder().group(group).role(role).build();
    a.setScope(scope);
    return assignmentRepository.save(a);
  }

  public Assignment assignment(Group group, Role role, DataSource scope) {
    Assignment a = Assignment.builder().group(group).role(role).build();
    a.setScope(scope);
    return assignmentRepository.save(a);
  }

  public Assignment assignment(Group group, Role role, DataStructure scope) {
    Assignment a = Assignment.builder().group(group).role(role).build();
    a.setScope(scope);
    return assignmentRepository.save(a);
  }

  public Assignment assignment(Group group, Role role, DataSpace scope) {
    Assignment a = Assignment.builder().group(group).role(role).build();
    a.setScope(scope);
    return assignmentRepository.save(a);
  }

  public Assignment assignment(Group group, Role role, Catalog scope) {
    Assignment a = Assignment.builder().group(group).role(role).build();
    a.setScope(scope);
    return assignmentRepository.save(a);
  }

  // ---------------------------------------------------------------------------
  // DataSet
  // ---------------------------------------------------------------------------

  public DataSet dataSet() {
    return dataSet(b -> {});
  }

  public DataSet dataSet(Consumer<DataSet.DataSetBuilder<?, ?>> customizer) {
    var builder =
        DataSet.builder()
            .name("dataset-" + nextSeq())
            .dataSetStatus(DataSetStatus.DRAFT)
            .openDataAccess(false);
    customizer.accept(builder);
    return dataSetRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // DataSource
  // ---------------------------------------------------------------------------

  public DataSource dataSource() {
    return dataSource(b -> {});
  }

  public DataSource dataSource(Consumer<DataSource.DataSourceBuilder<?, ?>> customizer) {
    var builder =
        DataSource.builder()
            .name("datasource-" + nextSeq())
            .dataSourceStatus(DataSourceStatus.DRAFT);
    customizer.accept(builder);
    return dataSourceRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // DataStructure
  // ---------------------------------------------------------------------------

  public DataStructure dataStructure() {
    return dataStructure(b -> {});
  }

  public DataStructure dataStructure(
      Consumer<DataStructure.DataStructureBuilder<?, ?>> customizer) {
    var builder =
        DataStructure.builder()
            .name("datastructure-" + nextSeq())
            .dataStructureStatus(DataStructureStatus.DRAFT);
    customizer.accept(builder);
    return dataStructureRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // DataStructureVersion (required parent: DataStructure)
  // ---------------------------------------------------------------------------

  public DataStructureVersion dataStructureVersion(DataStructure dataStructure) {
    return dataStructureVersion(dataStructure, b -> {});
  }

  public DataStructureVersion dataStructureVersion(
      DataStructure dataStructure,
      Consumer<DataStructureVersion.DataStructureVersionBuilder<?, ?>> customizer) {
    long seq = nextSeq();
    var builder =
        DataStructureVersion.builder()
            .version("1.0." + seq)
            .dataStructureVersionSource(DataStructureVersionSource.OWN)
            .dataStructureVersionStatus(DataStructureVersionStatus.DRAFT)
            .dataStructure(dataStructure);
    customizer.accept(builder);
    return dataStructureVersionRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // Pipeline (required parent: DataSet)
  // ---------------------------------------------------------------------------

  public Pipeline pipeline(DataSet dataSet) {
    return pipeline(dataSet, b -> {});
  }

  public Pipeline pipeline(DataSet dataSet, Consumer<Pipeline.PipelineBuilder<?, ?>> customizer) {
    var builder = Pipeline.builder().name("pipeline-" + nextSeq()).dataSet(dataSet);
    customizer.accept(builder);
    Pipeline saved = pipelineRepository.save(builder.build());
    dataSet.getPipelines().add(saved);
    dataSetRepository.save(dataSet);
    return saved;
  }

  // ---------------------------------------------------------------------------
  // Catalog
  // ---------------------------------------------------------------------------

  public Catalog catalog() {
    return catalog(b -> {});
  }

  public Catalog catalog(Consumer<Catalog.CatalogBuilder<?, ?>> customizer) {
    var builder = Catalog.builder().name("catalog-" + nextSeq());
    customizer.accept(builder);
    return catalogRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // DataSpace
  // ---------------------------------------------------------------------------

  public DataSpace dataSpace() {
    return dataSpace(b -> {});
  }

  public DataSpace dataSpace(Consumer<DataSpace.DataSpaceBuilder<?, ?>> customizer) {
    var builder = DataSpace.builder().name("dataspace-" + nextSeq());
    customizer.accept(builder);
    return dataSpaceRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // Distribution
  // ---------------------------------------------------------------------------

  public Distribution distribution() {
    return distribution(b -> {});
  }

  public Distribution distribution(Consumer<Distribution.DistributionBuilder<?, ?>> customizer) {
    var builder = Distribution.builder();
    customizer.accept(builder);
    return distributionRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // Resource
  // ---------------------------------------------------------------------------

  public Resource resource() {
    return resourceRepository.save(Resource.builder().build());
  }

  // ---------------------------------------------------------------------------
  // Agent
  // ---------------------------------------------------------------------------

  public Agent agent() {
    return agent(b -> {});
  }

  public Agent agent(Consumer<Agent.AgentBuilder<?, ?>> customizer) {
    var builder = Agent.builder().name("agent-" + nextSeq());
    customizer.accept(builder);
    return agentRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // Activity
  // ---------------------------------------------------------------------------

  public Activity activity() {
    return activity(b -> {});
  }

  public Activity activity(Consumer<Activity.ActivityBuilder<?, ?>> customizer) {
    var builder = Activity.builder().name("activity-" + nextSeq());
    customizer.accept(builder);
    return activityRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // DataSetSeries
  // ---------------------------------------------------------------------------

  public DataSetSeries dataSetSeries() {
    return dataSetSeries(b -> {});
  }

  public DataSetSeries dataSetSeries(
      Consumer<DataSetSeries.DataSetSeriesBuilder<?, ?>> customizer) {
    var builder = DataSetSeries.builder().name("series-" + nextSeq());
    customizer.accept(builder);
    return dataSetSeriesRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // cleanAll — deletes all entities in FK-safe order
  // ---------------------------------------------------------------------------

  /**
   * Deletes all test entities in the correct order (respecting FK and join-table constraints).
   *
   * <p>ManyToMany join tables are owned by: Catalog (catalog_datasets, catalog_children), DataSet
   * (dataset_dataspaces, dataset_agents), Pipeline (pipeline_data_sources), Activity
   * (activity_agents), Group (group_members), Role (role_permissions). The owning side must be
   * deleted before the inverse side.
   */
  public void cleanAll() {
    assignmentRepository.deleteAll();
    distributionRepository.deleteAll();
    pipelineRepository.deleteAll();
    catalogRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataSourceRepository.deleteAll();
    dataStructureVersionRepository.deleteAll();
    dataStructureRepository.deleteAll();
    activityRepository.deleteAll();
    agentRepository.deleteAll();
    resourceRepository.deleteAll();
    dataSetSeriesRepository.deleteAll();
    dataSpaceRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
    userRepository.deleteAll();
  }
}

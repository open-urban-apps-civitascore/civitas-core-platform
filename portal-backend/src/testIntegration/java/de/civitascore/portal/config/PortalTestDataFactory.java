package de.civitascore.portal.config;

import de.civitascore.portal.model.embedded.ConnectorType;
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
import de.civitascore.portal.model.entity.Permission;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

/**
 * General-purpose test data factory for portal integration tests.
 *
 * <p>Provides builder-pattern creation methods for all 16 portal entities. Each builder pre-fills
 * sensible defaults (unique names via an {@link AtomicLong} counter, required fields populated) and
 * exposes {@code with*()} methods for customization. The terminal {@code build()} method persists
 * the entity via the corresponding repository and returns it.
 *
 * <p>Registered as a Spring {@code @TestComponent} so it can be injected via {@code @Autowired} in
 * any integration test that imports it (e.g. via {@link BaseKeycloakIntegrationTest}).
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

  public UserBuilder user() {
    return new UserBuilder();
  }

  public class UserBuilder {
    private final User entity = new User();

    private UserBuilder() {
      long seq = nextSeq();
      entity.setFirstName("First" + seq);
      entity.setLastName("Last" + seq);
      entity.setEmail("user-" + seq + "@test.local");
      entity.setActive(true);
    }

    public UserBuilder withFirstName(String firstName) {
      entity.setFirstName(firstName);
      return this;
    }

    public UserBuilder withLastName(String lastName) {
      entity.setLastName(lastName);
      return this;
    }

    public UserBuilder withEmail(String email) {
      entity.setEmail(email);
      return this;
    }

    public UserBuilder withActive(boolean active) {
      entity.setActive(active);
      return this;
    }

    public UserBuilder withPhone(String phone) {
      entity.setPhone(phone);
      return this;
    }

    public UserBuilder withExternalId(String externalId) {
      entity.setExternalId(externalId);
      return this;
    }

    public User build() {
      return userRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // Group
  // ---------------------------------------------------------------------------

  public GroupBuilder group() {
    return new GroupBuilder();
  }

  public class GroupBuilder {
    private final Group entity = new Group();

    private GroupBuilder() {
      entity.setName("group-" + nextSeq());
    }

    public GroupBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public GroupBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public GroupBuilder withContactUser(User contactUser) {
      entity.setContactUser(contactUser);
      return this;
    }

    public GroupBuilder withParentGroup(Group parentGroup) {
      entity.setParentGroup(parentGroup);
      return this;
    }

    public GroupBuilder withMembers(Set<User> members) {
      entity.setMembers(members);
      return this;
    }

    public Group build() {
      return groupRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // Role
  // ---------------------------------------------------------------------------

  public RoleBuilder role() {
    return new RoleBuilder();
  }

  public class RoleBuilder {
    private final Role entity = new Role();

    private RoleBuilder() {
      entity.setName("role-" + nextSeq());
      entity.setRoleType(RoleType.DATA);
    }

    public RoleBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public RoleBuilder withRoleType(RoleType roleType) {
      entity.setRoleType(roleType);
      return this;
    }

    public RoleBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public RoleBuilder withPermissions(Set<Permission> permissions) {
      entity.setPermissions(permissions);
      return this;
    }

    public Role build() {
      return roleRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // Assignment
  // ---------------------------------------------------------------------------

  public AssignmentBuilder assignment() {
    return new AssignmentBuilder();
  }

  public class AssignmentBuilder {
    private final Assignment entity = new Assignment();

    private AssignmentBuilder() {}

    public AssignmentBuilder withGroup(Group group) {
      entity.setGroup(group);
      return this;
    }

    public AssignmentBuilder withRole(Role role) {
      entity.setRole(role);
      return this;
    }

    public AssignmentBuilder withScopeType(ScopeType scopeType) {
      entity.setScopeType(scopeType);
      return this;
    }

    public AssignmentBuilder withScope(DataSet dataSet) {
      entity.setScope(dataSet);
      return this;
    }

    public AssignmentBuilder withScope(DataSource dataSource) {
      entity.setScope(dataSource);
      return this;
    }

    public AssignmentBuilder withScope(DataStructure dataStructure) {
      entity.setScope(dataStructure);
      return this;
    }

    public AssignmentBuilder withScope(DataSpace dataSpace) {
      entity.setScope(dataSpace);
      return this;
    }

    public AssignmentBuilder withScope(Catalog catalog) {
      entity.setScope(catalog);
      return this;
    }

    public AssignmentBuilder withTenantScope() {
      entity.setScopeType(ScopeType.TENANT);
      return this;
    }

    public Assignment build() {
      return assignmentRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // DataSet
  // ---------------------------------------------------------------------------

  public DataSetBuilder dataSet() {
    return new DataSetBuilder();
  }

  public class DataSetBuilder {
    private final DataSet entity = new DataSet();

    private DataSetBuilder() {
      entity.setName("dataset-" + nextSeq());
      entity.setDataSetStatus(DataSetStatus.DRAFT);
      entity.setOpenDataAccess(false);
    }

    public DataSetBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public DataSetBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public DataSetBuilder withStatus(DataSetStatus status) {
      entity.setDataSetStatus(status);
      return this;
    }

    public DataSetBuilder withOpenDataAccess(boolean openDataAccess) {
      entity.setOpenDataAccess(openDataAccess);
      return this;
    }

    public DataSetBuilder withOwner(User owner) {
      entity.setOwner(owner);
      return this;
    }

    public DataSetBuilder withDataSetSeries(DataSetSeries dataSetSeries) {
      entity.setDataSetSeries(dataSetSeries);
      return this;
    }

    public DataSet build() {
      return dataSetRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // DataSource
  // ---------------------------------------------------------------------------

  public DataSourceBuilder dataSource() {
    return new DataSourceBuilder();
  }

  public class DataSourceBuilder {
    private final DataSource entity = new DataSource();

    private DataSourceBuilder() {
      entity.setName("datasource-" + nextSeq());
      entity.setDataSourceStatus(DataSourceStatus.DRAFT);
    }

    public DataSourceBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public DataSourceBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public DataSourceBuilder withStatus(DataSourceStatus status) {
      entity.setDataSourceStatus(status);
      return this;
    }

    public DataSourceBuilder withConnectorType(ConnectorType connectorType) {
      entity.setConnectorType(connectorType);
      return this;
    }

    public DataSourceBuilder withConfiguration(Map<String, Object> configuration) {
      entity.setConfiguration(configuration);
      return this;
    }

    public DataSourceBuilder withDataStructureVersion(DataStructureVersion dsv) {
      entity.setDataStructureVersion(dsv);
      return this;
    }

    public DataSource build() {
      return dataSourceRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // DataStructure
  // ---------------------------------------------------------------------------

  public DataStructureBuilder dataStructure() {
    return new DataStructureBuilder();
  }

  public class DataStructureBuilder {
    private final DataStructure entity = new DataStructure();

    private DataStructureBuilder() {
      entity.setName("datastructure-" + nextSeq());
      entity.setDataStructureStatus(DataStructureStatus.DRAFT);
    }

    public DataStructureBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public DataStructureBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public DataStructureBuilder withStatus(DataStructureStatus status) {
      entity.setDataStructureStatus(status);
      return this;
    }

    public DataStructure build() {
      return dataStructureRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // DataStructureVersion
  // ---------------------------------------------------------------------------

  public DataStructureVersionBuilder dataStructureVersion() {
    return new DataStructureVersionBuilder();
  }

  public class DataStructureVersionBuilder {
    private final DataStructureVersion entity = new DataStructureVersion();

    private DataStructureVersionBuilder() {
      long seq = nextSeq();
      entity.setVersion("1.0." + seq);
      entity.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      entity.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    }

    public DataStructureVersionBuilder withVersion(String version) {
      entity.setVersion(version);
      return this;
    }

    public DataStructureVersionBuilder withSource(DataStructureVersionSource source) {
      entity.setDataStructureVersionSource(source);
      return this;
    }

    public DataStructureVersionBuilder withStatus(DataStructureVersionStatus status) {
      entity.setDataStructureVersionStatus(status);
      return this;
    }

    public DataStructureVersionBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public DataStructureVersionBuilder withDataStructure(DataStructure dataStructure) {
      entity.setDataStructure(dataStructure);
      return this;
    }

    public DataStructureVersionBuilder withModelAtlasUri(String modelAtlasUri) {
      entity.setModelAtlasUri(modelAtlasUri);
      return this;
    }

    public DataStructureVersionBuilder withModelName(String modelName) {
      entity.setModelName(modelName);
      return this;
    }

    public DataStructureVersionBuilder withStyles(Map<String, Object> styles) {
      entity.setStyles(styles);
      return this;
    }

    public DataStructureVersion build() {
      return dataStructureVersionRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // Pipeline
  // ---------------------------------------------------------------------------

  public PipelineBuilder pipeline() {
    return new PipelineBuilder();
  }

  public class PipelineBuilder {
    private final Pipeline entity = new Pipeline();

    private PipelineBuilder() {
      entity.setName("pipeline-" + nextSeq());
    }

    public PipelineBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public PipelineBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public PipelineBuilder withDataSet(DataSet dataSet) {
      entity.setDataSet(dataSet);
      return this;
    }

    public PipelineBuilder withDataSources(Set<DataSource> dataSources) {
      entity.setDataSources(dataSources);
      return this;
    }

    public PipelineBuilder withStyles(Map<String, Object> styles) {
      entity.setStyles(styles);
      return this;
    }

    public PipelineBuilder withModel(Map<String, Object> model) {
      entity.setModel(model);
      return this;
    }

    public PipelineBuilder withApis(List<String> apis) {
      entity.setApis(apis);
      return this;
    }

    public PipelineBuilder withPersistences(List<Long> persistences) {
      entity.setPersistences(persistences);
      return this;
    }

    public Pipeline build() {
      Pipeline saved = pipelineRepository.save(entity);
      DataSet ds = entity.getDataSet();
      if (ds != null) {
        ds.getPipelines().add(saved);
        dataSetRepository.save(ds);
      }
      return saved;
    }
  }

  // ---------------------------------------------------------------------------
  // Catalog
  // ---------------------------------------------------------------------------

  public CatalogBuilder catalog() {
    return new CatalogBuilder();
  }

  public class CatalogBuilder {
    private final Catalog entity = new Catalog();

    private CatalogBuilder() {
      entity.setName("catalog-" + nextSeq());
    }

    public CatalogBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public CatalogBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public Catalog build() {
      return catalogRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // DataSpace
  // ---------------------------------------------------------------------------

  public DataSpaceBuilder dataSpace() {
    return new DataSpaceBuilder();
  }

  public class DataSpaceBuilder {
    private final DataSpace entity = new DataSpace();

    private DataSpaceBuilder() {
      entity.setName("dataspace-" + nextSeq());
    }

    public DataSpaceBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public DataSpaceBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public DataSpaceBuilder withOwner(User owner) {
      entity.setOwner(owner);
      return this;
    }

    public DataSpaceBuilder withParentDataSpace(DataSpace parentDataSpace) {
      entity.setParentDataSpace(parentDataSpace);
      return this;
    }

    public DataSpace build() {
      return dataSpaceRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // Distribution
  // ---------------------------------------------------------------------------

  public DistributionBuilder distribution() {
    return new DistributionBuilder();
  }

  public class DistributionBuilder {
    private final Distribution entity = new Distribution();

    private DistributionBuilder() {}

    public DistributionBuilder withAccessUrl(String accessUrl) {
      entity.setAccessUrl(accessUrl);
      return this;
    }

    public DistributionBuilder withResource(Resource resource) {
      entity.setResource(resource);
      return this;
    }

    public DistributionBuilder withDataSet(DataSet dataSet) {
      entity.setDataSet(dataSet);
      return this;
    }

    public DistributionBuilder withActivity(Activity activity) {
      entity.setActivity(activity);
      return this;
    }

    public Distribution build() {
      return distributionRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // Resource
  // ---------------------------------------------------------------------------

  public ResourceBuilder resource() {
    return new ResourceBuilder();
  }

  public class ResourceBuilder {
    private ResourceBuilder() {}

    public Resource build() {
      return resourceRepository.save(new Resource());
    }
  }

  // ---------------------------------------------------------------------------
  // Agent
  // ---------------------------------------------------------------------------

  public AgentBuilder agent() {
    return new AgentBuilder();
  }

  public class AgentBuilder {
    private final Agent entity = new Agent();

    private AgentBuilder() {
      entity.setName("agent-" + nextSeq());
    }

    public AgentBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public Agent build() {
      return agentRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // Activity
  // ---------------------------------------------------------------------------

  public ActivityBuilder activity() {
    return new ActivityBuilder();
  }

  public class ActivityBuilder {
    private final Activity entity = new Activity();

    private ActivityBuilder() {
      entity.setName("activity-" + nextSeq());
    }

    public ActivityBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public ActivityBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public Activity build() {
      return activityRepository.save(entity);
    }
  }

  // ---------------------------------------------------------------------------
  // DataSetSeries
  // ---------------------------------------------------------------------------

  public DataSetSeriesBuilder dataSetSeries() {
    return new DataSetSeriesBuilder();
  }

  public class DataSetSeriesBuilder {
    private final DataSetSeries entity = new DataSetSeries();

    private DataSetSeriesBuilder() {
      entity.setName("series-" + nextSeq());
    }

    public DataSetSeriesBuilder withName(String name) {
      entity.setName(name);
      return this;
    }

    public DataSetSeriesBuilder withDescription(String description) {
      entity.setDescription(description);
      return this;
    }

    public DataSetSeries build() {
      return dataSetSeriesRepository.save(entity);
    }
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
    dataStructureVersionRepository.deleteAll();
    catalogRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataSourceRepository.deleteAll();
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

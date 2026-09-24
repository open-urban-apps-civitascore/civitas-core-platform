package de.civitascore.portal.config;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.modelregistry.VersionBump;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.StyleRepository;
import de.civitascore.portal.repository.UserRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataStructureRepository dataStructureRepository;
  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;
  @Autowired private LayerRepository layerRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private StyleRepository styleRepository;
  @Autowired private DataPoolRepository dataPoolRepository;
  @Autowired private ModelRegistryGateway modelRegistryGateway;

  private static long nextSeq() {
    return SEQ.incrementAndGet();
  }

  // ---------------------------------------------------------------------------
  // Model Forge registry content (models and payloads live in the registry, not
  // in host columns — the attach* helpers store the content and mirror the pin)
  // ---------------------------------------------------------------------------

  /**
   * Stores the given JSON Schema as the version's model in the Model Forge registry and mirrors the
   * assigned pin (URN + version) onto the persisted shell — what the service layer does on
   * create/update.
   */
  public DataStructureVersion attachModel(DataStructureVersion version, Map<String, Object> model) {
    return attachModel(version, model, null);
  }

  /** Variant of {@link #attachModel(DataStructureVersion, Map)} carrying UI styles. */
  public DataStructureVersion attachModel(
      DataStructureVersion version, Map<String, Object> model, Map<String, Object> styles) {
    DataStructure parent = version.getDataStructure();
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storeModel(
            Optional.ofNullable(parent.getModelLogicalUrn()),
            parent.getName(),
            model,
            styles,
            // A version's first model starts its own major line and has no number to count from —
            // the builder's placeholder version is not one the registry holds; a replacement
            // advances the minor inside that line.
            version.getModelUrn() == null ? VersionBump.MAJOR : VersionBump.MINOR,
            version.getModelUrn() == null ? null : version.getVersion());
    if (parent.getModelLogicalUrn() == null) {
      parent.setModelLogicalUrn(pin.logicalUrn());
      dataStructureRepository.save(parent);
    }
    version.setModelUrn(pin.versionedUrn());
    version.setVersion(pin.version());
    return dataStructureVersionRepository.save(version);
  }

  /**
   * Stores the pipeline definition (plus optional React Flow layout) in the registry and mirrors
   * the assigned pin onto the persisted shell.
   */
  public Pipeline attachPipelineDefinition(
      Pipeline pipeline, Map<String, Object> model, Map<String, Object> styles) {
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.PIPELINE,
            Optional.ofNullable(pipeline.getModelLogicalUrn()),
            pipeline.getName(),
            model,
            styles);
    if (pipeline.getModelLogicalUrn() == null) {
      pipeline.setModelLogicalUrn(pin.logicalUrn());
    }
    pipeline.setModelUrn(pin.versionedUrn());
    return pipelineRepository.save(pipeline);
  }

  /**
   * Stores the sink configuration in the registry and mirrors the assigned pin onto the persisted
   * shell.
   */
  public DataSink attachSinkConfiguration(DataSink sink, Map<String, Object> configuration) {
    // Mirror DataSinkService: the stored CORE payload carries connectionType (derived from the sink
    // type); Model Forge stamps $schema + id on write and validates against datasink.schema.json.
    Map<String, Object> payload = new LinkedHashMap<>(configuration);
    if (sink.getDataSinkType() != null) {
      payload.put("connectionType", sink.getDataSinkType().name().toLowerCase(Locale.ROOT));
    }
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.DATA_SINK,
            Optional.ofNullable(sink.getConfigurationLogicalUrn()),
            payload.get("tableName") instanceof String tableName ? tableName : "datasink",
            payload,
            null);
    if (sink.getConfigurationLogicalUrn() == null) {
      sink.setConfigurationLogicalUrn(pin.logicalUrn());
    }
    sink.setConfigurationUrn(pin.versionedUrn());
    return dataSinkRepository.save(sink);
  }

  /**
   * Stores the connector configuration in the registry and mirrors the assigned pin onto the
   * persisted shell.
   */
  public DataSource attachSourceConfiguration(
      DataSource dataSource, Map<String, Object> configuration) {
    // Mirror DataSourceService: the stored CORE payload carries connectionType (derived from the
    // connector type); Model Forge stamps $schema + id on write and validates against
    // datasource.schema.json.
    Map<String, Object> payload = new LinkedHashMap<>(configuration);
    if (dataSource.getConnectorType() != null) {
      payload.put("connectionType", dataSource.getConnectorType().name().toLowerCase(Locale.ROOT));
    }
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.DATA_SOURCE,
            Optional.ofNullable(dataSource.getConfigurationLogicalUrn()),
            dataSource.getName() != null ? dataSource.getName() : "datasource",
            payload,
            null);
    if (dataSource.getConfigurationLogicalUrn() == null) {
      dataSource.setConfigurationLogicalUrn(pin.logicalUrn());
    }
    dataSource.setConfigurationUrn(pin.versionedUrn());
    return dataSourceRepository.save(dataSource);
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
            .email("user-" + seq + "@test.local");
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

  public Assignment assignment(Group group, Role role, DataPool scope) {
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
            .description("test dataset")
            .dataSetStatus(DataSetStatus.DRAFT)
            .openDataAccess(false);
    customizer.accept(builder);
    return dataSetRepository.save(builder.build());
  }

  /** Re-saves an existing dataset (e.g. after wiring child collections post-build). */
  public DataSet saveDataSet(DataSet dataSet) {
    return dataSetRepository.save(dataSet);
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
            .description("test data source")
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
            .description("test data structure")
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
            .dataStructureVersionStatus(DataStructureVersionStatus.DRAFT)
            .dataStructure(dataStructure);
    customizer.accept(builder);
    return dataStructureVersionRepository.save(builder.build());
  }

  /**
   * A minimal, well-formed JSON Schema document suitable for a data structure version's model. No
   * {@code $id} is set — Model Forge is the sole version authority and mints the CORE URN itself; a
   * caller-supplied {@code $id} that isn't a full, well-formed CORE URN gets stored verbatim and
   * breaks version extraction downstream.
   */
  public Map<String, Object> dataStructureVersionModel(String title) {
    Map<String, Object> schema = new HashMap<>();
    schema.put("title", title);
    schema.put("type", "object");
    return schema;
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
  // DataSink (required parents: DataSet, Pipeline)
  // ---------------------------------------------------------------------------

  public DataSink dataSink(DataSet dataSet, Pipeline pipeline) {
    return dataSink(dataSet, pipeline, sink -> {});
  }

  public DataSink dataSink(DataSet dataSet, Pipeline pipeline, Consumer<DataSink> customizer) {
    DataSink sink = new DataSink();
    sink.setDataSet(dataSet);
    sink.setPipeline(pipeline);
    sink.setDataSinkType(DataSinkType.FROST);
    customizer.accept(sink);
    return dataSinkRepository.save(sink);
  }

  public DataSink dataSink(DataSet dataSet) {
    DataSink sink = new DataSink();
    sink.setDataSet(dataSet);
    sink.setDataSinkType(DataSinkType.FROST);
    return dataSinkRepository.save(sink);
  }

  // ---------------------------------------------------------------------------
  // Style (required parent: DataSet)
  // ---------------------------------------------------------------------------

  public Style style(DataSet dataSet) {
    long seq = nextSeq();
    Style style = new Style();
    style.setDataSet(dataSet);
    style.setName("style-" + seq);
    style.setSldContent("<StyledLayerDescriptor/>");
    return styleRepository.save(style);
  }

  // ---------------------------------------------------------------------------
  // Layer (required parents: DataSet, DataSink)
  // ---------------------------------------------------------------------------

  public Layer layer(DataSet dataSet, DataSink dataSink) {
    long seq = nextSeq();
    Layer layer = new Layer();
    layer.setDataSet(dataSet);
    layer.setDataSink(dataSink);
    layer.setLayerName("layer-" + seq);
    return layerRepository.save(layer);
  }

  // ---------------------------------------------------------------------------
  // DataPool
  // ---------------------------------------------------------------------------

  public DataPool dataPool() {
    return dataPool(b -> {});
  }

  public DataPool dataPool(Consumer<DataPool.DataPoolBuilder<?, ?>> customizer) {
    var builder = DataPool.builder().name("datapool-" + nextSeq());
    customizer.accept(builder);
    return dataPoolRepository.save(builder.build());
  }

  // ---------------------------------------------------------------------------
  // cleanAll — deletes all entities in FK-safe order
  // ---------------------------------------------------------------------------

  /**
   * Deletes all test entities in the correct order (respecting FK and join-table constraints).
   *
   * <p>ManyToMany join tables are owned by: Pipeline (pipeline_data_sources), Group
   * (group_members), Role (role_permissions). The owning side must be deleted before the inverse
   * side.
   */
  public void cleanAll() {
    assignmentRepository.deleteAll();
    layerRepository.deleteAll();
    styleRepository.deleteAll();
    dataSinkRepository.deleteAll();
    pipelineRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataSourceRepository.deleteAll();
    dataStructureVersionRepository.deleteAll();
    dataStructureRepository.deleteAll();
    dataPoolRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
    userRepository.deleteAll();
  }
}

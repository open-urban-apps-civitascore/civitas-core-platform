package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTParser;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for which data sources a pipeline may reference.
 *
 * <p>The Use relationship decides it, not a permission the caller holds on the data source: a
 * source released for every datapool, or for the datapool of the pipeline's dataset, may be
 * referenced by anyone authorized to edit that dataset. The route's own permission is enforced by
 * OPA upstream; these tests hold no data source assignment at all, so what they exercise is the
 * relationship rule alone.
 */
@DisplayName("Pipeline DataSource Reference Integration Tests")
class PipelineDataSourceReferenceIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private DataPoolRepository dataPoolRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private PipelineRepository pipelineRepository;

  private String accessToken;

  @BeforeEach
  void setUp() throws Exception {
    accessToken = getValidAccessToken();
    String externalId = JWTParser.parse(accessToken).getJWTClaimsSet().getSubject();

    User user = new User();
    user.setFirstName("Test");
    user.setLastName("User");
    user.setEmail("pipeline-ref." + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    user.setExternalId(externalId);
    user = userRepository.save(user);

    Group group = new Group();
    group.setName("ref-group-" + UUID.randomUUID().toString().substring(0, 8));
    group.getMembers().add(user);
    groupRepository.save(group);
  }

  @AfterEach
  void cleanup() {
    // Pipelines hold FKs into data_sources; drop them before their referenced data sources.
    pipelineRepository.deleteAll();
    assignmentRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataSourceRepository.deleteAll();
    dataPoolRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
    userRepository.deleteAll();
  }

  private DataPool dataPool() {
    DataPool pool = new DataPool();
    pool.setName("ref-pool-" + UUID.randomUUID().toString().substring(0, 8));
    return dataPoolRepository.save(pool);
  }

  private DataSource dataSource(DatapoolScopeType scopeType, DataPool... scopedPools) {
    DataSource ds = new DataSource();
    ds.setName("ref-source-" + UUID.randomUUID().toString().substring(0, 8));
    ds.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    ds.setDatapoolScopeType(scopeType);
    ds.setScopedDataPools(new HashSet<>(Set.of(scopedPools)));
    return dataSourceRepository.save(ds);
  }

  private DataSet draftDataSet(DataPool dataPool) {
    DataSet ds = new DataSet();
    ds.setName("ref-dataset-" + UUID.randomUUID().toString().substring(0, 8));
    ds.setDataSetStatus(DataSetStatus.DRAFT);
    ds.setOpenDataAccess(false);
    ds.setDataPool(dataPool);
    return dataSetRepository.save(ds);
  }

  private ResponseEntity<String> createPipelineReferencing(UUID dataSetId, UUID dataSourceId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    // A non-wildcard scope header, so the request cannot pass by claiming tenant-wide access.
    headers.set(AllowedScopesFilter.HEADER_NAME, UUID.randomUUID().toString());

    Map<String, Object> body =
        Map.of(
            "name",
            "ref-pipeline-" + UUID.randomUUID().toString().substring(0, 8),
            "dataSourceIds",
            Set.of(dataSourceId.toString()));

    return restTemplate.exchange(
        "/datasets/" + dataSetId + "/pipelines",
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        String.class);
  }

  @Test
  @DisplayName("Accepts a source released for every datapool, with no data source assignment")
  void acceptsUnrestrictedSourceWithoutAssignment() {
    DataSource ds = dataSource(DatapoolScopeType.ALL);

    ResponseEntity<String> response =
        createPipelineReferencing(draftDataSet(dataPool()).getId(), ds.getId());

    assertThat(response.getStatusCode().value()).isEqualTo(201);
  }

  @Test
  @DisplayName("Accepts a source released for every datapool by a dataset in no datapool")
  void acceptsUnrestrictedSourceForPoolLessDataSet() {
    DataSource ds = dataSource(DatapoolScopeType.ALL);

    ResponseEntity<String> response =
        createPipelineReferencing(draftDataSet(null).getId(), ds.getId());

    assertThat(response.getStatusCode().value()).isEqualTo(201);
  }

  @Test
  @DisplayName("Accepts a source released for the dataset's own datapool")
  void acceptsSourceReleasedForTheDataSetsPool() {
    DataPool pool = dataPool();
    DataSource ds = dataSource(DatapoolScopeType.SPECIFIC, pool);

    ResponseEntity<String> response =
        createPipelineReferencing(draftDataSet(pool).getId(), ds.getId());

    assertThat(response.getStatusCode().value()).isEqualTo(201);
  }

  @Test
  @DisplayName("Rejects a source released only for another datapool")
  void rejectsSourceReleasedForAnotherPool() {
    DataSource ds = dataSource(DatapoolScopeType.SPECIFIC, dataPool());

    ResponseEntity<String> response =
        createPipelineReferencing(draftDataSet(dataPool()).getId(), ds.getId());

    assertThat(response.getStatusCode().value()).isEqualTo(422);
  }

  @Test
  @DisplayName("Rejects a pool-confined source for a dataset in no datapool")
  void rejectsConfinedSourceForPoolLessDataSet() {
    DataSource ds = dataSource(DatapoolScopeType.SPECIFIC, dataPool());

    ResponseEntity<String> response =
        createPipelineReferencing(draftDataSet(null).getId(), ds.getId());

    assertThat(response.getStatusCode().value()).isEqualTo(422);
  }

  @Test
  @DisplayName("Answers identically for a nonexistent, a DRAFT and an out-of-pool source")
  void rejectionsAreIndistinguishable() {
    DataPool pool = dataPool();
    UUID dataSetId = draftDataSet(pool).getId();

    DataSource draft = dataSource(DatapoolScopeType.ALL);
    draft.setDataSourceStatus(DataSourceStatus.DRAFT);
    dataSourceRepository.save(draft);
    DataSource otherPool = dataSource(DatapoolScopeType.SPECIFIC, dataPool());

    ResponseEntity<String> missing = createPipelineReferencing(dataSetId, UUID.randomUUID());
    ResponseEntity<String> draftRef = createPipelineReferencing(dataSetId, draft.getId());
    ResponseEntity<String> foreign = createPipelineReferencing(dataSetId, otherPool.getId());

    // Same status and same wording: a caller authorized only on the dataset must not learn whether
    // an id exists, nor what lifecycle status it has.
    assertThat(missing.getStatusCode().value()).isEqualTo(422);
    assertThat(draftRef.getStatusCode().value()).isEqualTo(422);
    assertThat(foreign.getStatusCode().value()).isEqualTo(422);
    assertThat(detailOf(draftRef)).isEqualTo(detailOf(missing));
    assertThat(detailOf(foreign)).isEqualTo(detailOf(missing));
  }

  /** The problem-detail message with the echoed ids stripped, so only the wording is compared. */
  private String detailOf(ResponseEntity<String> response) {
    return response.getBody().replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "<id>");
  }

  @Test
  @DisplayName("Denies referencing a data source when no scope header is present")
  void deniesWhenScopeHeaderAbsent() {
    DataSource ds = dataSource(DatapoolScopeType.ALL);
    UUID dataSetId = draftDataSet(dataPool()).getId();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    // No X-Allowed-Scope-Ids at all: the request never passed APISIX/OPA.
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/datasets/" + dataSetId + "/pipelines",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "name",
                    "ref-pipeline-" + UUID.randomUUID().toString().substring(0, 8),
                    "dataSourceIds",
                    Set.of(ds.getId().toString())),
                headers),
            String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  @DisplayName("Rejects a source released for no datapool at all")
  void rejectsSourceReleasedNowhere() {
    DataSource ds = dataSource(DatapoolScopeType.NONE);

    ResponseEntity<String> response =
        createPipelineReferencing(draftDataSet(dataPool()).getId(), ds.getId());

    assertThat(response.getStatusCode().value()).isEqualTo(422);
  }
}

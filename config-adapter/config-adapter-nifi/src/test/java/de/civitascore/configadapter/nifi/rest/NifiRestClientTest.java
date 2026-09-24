/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.nifi.auth.NifiTokenProvider;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttTruststoreConfig;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NifiRestClientTest {

  private WireMockServer server;
  private OkHttpClient httpClient;
  private FakeTokenProvider tokenProvider;
  private NifiRestClient client;

  /**
   * Revisions used by the delete tests. The process-group listing carries the revision as it stood
   * BEFORE the group was stopped; the read that observes it stopped carries a newer one. They must
   * differ, or the assertion on the delete's version parameter cannot tell which of the two the
   * client locked on — and the stale one is what produced the 409 this guards.
   */
  private static final int LISTING_REVISION = 2;

  private static final int STOPPED_REVISION = 7;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    httpClient = new OkHttpClient();
    tokenProvider = new FakeTokenProvider();
    client =
        new NifiRestClient(
            server.baseUrl(), tokenProvider, httpClient, MqttTruststoreConfig.nodeTruststore());
  }

  @AfterEach
  void tearDown() {
    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
    server.stop();
  }

  /**
   * Stands in for the OIDC token provider: hands out a bearer token and counts how often the client
   * asks for one and refreshes it (so the 401-replay path can be asserted without an HTTP token
   * endpoint). {@code refreshToken()} returns a DIFFERENT value than {@code getToken()} so a test
   * can prove the post-401 retry is actually sent with the refreshed token, not the stale one.
   */
  private static final class FakeTokenProvider implements NifiTokenProvider {
    private int getCount;
    private int refreshCount;

    @Override
    public String getToken() {
      getCount++;
      return "jwt-token";
    }

    @Override
    public String refreshToken() {
      refreshCount++;
      return "jwt-token-2";
    }
  }

  /** A single VALID, Running processor so the post-start processor health check passes. */
  private void stubRunningProcessors() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/processors"))
            .willReturn(
                json(
                    "{ \"processors\": [ { \"id\": \"proc-1\", \"component\": { \"name\":"
                        + " \"QueryDatabaseTableRecord\", \"validationStatus\": \"VALID\" },"
                        + " \"status\": { \"runStatus\": \"Running\" },"
                        + " \"revision\": { \"version\": 1 } } ] }")));
  }

  @Test
  void deployFlowRunsFullSequenceAndPushesSecretPostUpload() throws Exception {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .willReturn(json("{ \"id\": \"pg-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(
                json(
                    "{ \"controllerServices\": [ { \"id\": \"cs-1\","
                        + " \"component\": { \"name\": \"PostGISConnectionPool\","
                        + " \"type\": \"org.apache.nifi.dbcp.DBCPConnectionPool\","
                        + " \"state\": \"ENABLED\" },"
                        + " \"revision\": { \"version\": 3 } } ] }")));
    stubRunningProcessors();
    server.stubFor(put(urlEqualTo("/nifi-api/controller-services/cs-1")).willReturn(json("{}")));
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(put(urlEqualTo("/nifi-api/flow/process-groups/pg-1")).willReturn(json("{}")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x",
            "{ \"flowContents\": { \"name\": \"pipeline-x\" } }",
            Map.of("PostGISConnectionPool", Map.of("Password", "db-secret")));

    String pgId = client.deployFlow(plan);

    assertEquals("pg-1", pgId);
    // sensitive property pushed post-upload, with the revision echoed for optimistic locking
    server.verify(
        putRequestedFor(urlEqualTo("/nifi-api/controller-services/cs-1"))
            .withRequestBody(containing("db-secret"))
            .withRequestBody(containing("\"version\":3")));
  }

  @Test
  void provisionsRootPoliciesOnForbiddenThenRetries() throws Exception {
    // First root read is denied (fresh OIDC NiFi: the service account has global policies but not
    // the root canvas). The client must grant itself the four root policies, then re-read and
    // succeed.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .inScenario("provision")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(403).withBody("No applicable policies"))
            .willSetStateTo("granted"));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .inScenario("provision")
            .whenScenarioStateIs("granted")
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/current-user"))
            .willReturn(json("{ \"identity\": \"svc-account\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/tenants/users"))
            .willReturn(
                json(
                    "{ \"users\": [ { \"id\": \"user-1\", \"component\": { \"identity\":"
                        + " \"svc-account\" } } ] }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root"))
            .willReturn(json("{ \"processGroupFlow\": { \"id\": \"root-1\" } }")));
    // No policy exists yet for any of the four resource/action pairs → 404 → create.
    server.stubFor(
        get(urlMatching("/nifi-api/policies/(read|write)/(data/)?process-groups/root-1"))
            .willReturn(aResponse().withStatus(404)));
    server.stubFor(post(urlEqualTo("/nifi-api/policies")).willReturn(json("{ \"id\": \"pol\" }")));

    assertEquals("root-1", client.getRootProcessGroupId());

    // exactly the four root policies were created, each granting the resolved NiFi user id
    server.verify(4, postRequestedFor(urlEqualTo("/nifi-api/policies")));
    server.verify(
        postRequestedFor(urlEqualTo("/nifi-api/policies")).withRequestBody(containing("user-1")));
  }

  @Test
  void addsUserToAnExistingRootPolicyInsteadOfCreatingIt() throws Exception {
    // A NiFi that already carries the root policies (persistent state) must not error: the client
    // updates the existing policy to include its user rather than POSTing a duplicate.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .inScenario("provision")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(403))
            .willSetStateTo("granted"));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .inScenario("provision")
            .whenScenarioStateIs("granted")
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/current-user"))
            .willReturn(json("{ \"identity\": \"svc-account\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/tenants/users"))
            .willReturn(
                json(
                    "{ \"users\": [ { \"id\": \"user-1\", \"component\": { \"identity\":"
                        + " \"svc-account\" } } ] }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root"))
            .willReturn(json("{ \"processGroupFlow\": { \"id\": \"root-1\" } }")));
    // An existing policy that grants another user AND an admin group is returned; the client PUTs
    // it
    // back including its own user while preserving both the other user and the group.
    server.stubFor(
        get(urlMatching("/nifi-api/policies/(read|write)/(data/)?process-groups/root-1"))
            .willReturn(
                json(
                    "{ \"id\": \"pol-1\", \"revision\": { \"version\": 5 }, \"component\": { \"id\":"
                        + " \"pol-1\", \"resource\": \"/process-groups/root-1\", \"action\":"
                        + " \"read\", \"users\": [ { \"id\": \"other\" } ], \"userGroups\": [ {"
                        + " \"id\": \"admin-group\" } ] } }")));
    server.stubFor(put(urlMatching("/nifi-api/policies/pol-1")).willReturn(json("{}")));

    assertEquals("root-1", client.getRootProcessGroupId());

    // no new policy created; the existing one is updated to include the client's user while KEEPING
    // the other user and the admin group, echoing the optimistic-lock revision
    server.verify(0, postRequestedFor(urlEqualTo("/nifi-api/policies")));
    server.verify(
        putRequestedFor(urlEqualTo("/nifi-api/policies/pol-1"))
            .withRequestBody(containing("user-1"))
            .withRequestBody(containing("other"))
            .withRequestBody(containing("admin-group"))
            .withRequestBody(containing("\"version\":5")));
  }

  @Test
  void serverErrorIsRetryable() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(aResponse().withStatus(503).withBody("overloaded")));

    assertThrows(
        RetryableAdapterException.class,
        () -> {
          client.authenticate();
          client.getRootProcessGroupId();
        });
  }

  @Test
  void clientErrorIsFatal() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(aResponse().withStatus(400).withBody("bad request")));

    assertThrows(
        FatalAdapterException.class,
        () -> {
          client.authenticate();
          client.getRootProcessGroupId();
        });
  }

  @Test
  void conflictIsRetryable() {
    // 409 is a transient optimistic-lock/component-starting condition, not a permanent failure —
    // it must be retryable so a redelivery can complete the deploy rather than rolling it back.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(aResponse().withStatus(409).withBody("revision conflict")));

    assertThrows(
        RetryableAdapterException.class,
        () -> {
          client.authenticate();
          client.getRootProcessGroupId();
        });
  }

  @Test
  void tooManyRequestsIsRetryable() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(aResponse().withStatus(429).withBody("slow down")));

    assertThrows(
        RetryableAdapterException.class,
        () -> {
          client.authenticate();
          client.getRootProcessGroupId();
        });
  }

  @Test
  void invalidControllerServiceFailsFatallyInsteadOfPollingForever() {
    // A controller service whose configuration is INVALID will never reach ENABLED. The enable-wait
    // must fail FATALLY (naming the service) rather than time out as retryable and loop forever.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .willReturn(json("{ \"id\": \"pg-1\" }")));
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(
                json(
                    "{ \"controllerServices\": [ { \"id\": \"cs-1\", \"component\": { \"name\":"
                        + " \"PostGISConnectionPool\", \"state\": \"DISABLED\","
                        + " \"validationStatus\": \"INVALID\", \"validationErrors\": [ \"'Database"
                        + " Connection URL' is invalid\" ] } } ] }")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x", "{ \"flowContents\": { \"name\": \"pipeline-x\" } }", Map.of());

    FatalAdapterException ex =
        assertThrows(FatalAdapterException.class, () -> client.deployFlow(plan));
    assertTrue(
        ex.getMessage().contains("PostGISConnectionPool"),
        "the fatal error must name the INVALID controller service");
  }

  @Test
  void missingRevisionVersionIsFatal() {
    // A matching group whose revision.version field is ABSENT must not be silently coerced to 0
    // (which would be sent as a stale optimistic-lock version) — surface the malformed response.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(
                json(
                    "{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [ { \"id\":"
                        + " \"pg-old\", \"component\": { \"name\": \"pipeline-x\" } } ] } } }")));

    assertThrows(FatalAdapterException.class, () -> client.deleteFlowByName("pipeline-x"));
  }

  @Test
  void uploadReturningNoIdIsFatal() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    // 2xx but malformed body carrying no id — must not be reported as a successful deploy
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .willReturn(json("{ }")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x", "{ \"flowContents\": { \"name\": \"pipeline-x\" } }", Map.of());

    assertThrows(FatalAdapterException.class, () -> client.deployFlow(plan));
  }

  @Test
  void transientErrorTearingDownExistingGroupStaysRetryable() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    // an existing group with the same name triggers the stop-and-delete redeploy path
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(
                json(
                    "{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [ { \"id\":"
                        + " \"pg-old\", \"component\": { \"name\": \"pipeline-x\" }, \"revision\": {"
                        + " \"version\": 2 } } ] } } }")));
    // stopping the existing group fails transiently (5xx) — must surface as retryable, not as a
    // non-retryable IllegalStateException that the saga would route to manual intervention
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-old"))
            .willReturn(aResponse().withStatus(503).withBody("overloaded")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x", "{ \"flowContents\": { \"name\": \"pipeline-x\" } }", Map.of());

    assertThrows(RetryableAdapterException.class, () -> client.deployFlow(plan));
  }

  @Test
  void reauthenticatesAndRetriesOnceOn401() throws Exception {
    // first GET is rejected (expired token), then succeeds after re-authentication
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .inScenario("reauth")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("reauthed"));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .inScenario("reauth")
            .whenScenarioStateIs("reauthed")
            .willReturn(json("{ \"id\": \"root-1\" }")));

    client.authenticate();
    String rootId = client.getRootProcessGroupId(); // 401 → re-auth → retry → 200

    assertEquals("root-1", rootId);
    // token refreshed exactly once, in response to the 401 (the initial token came from
    // authenticate)
    assertEquals(1, tokenProvider.getCount);
    assertEquals(1, tokenProvider.refreshCount);
    // the FIRST attempt carried the initial token; the RETRY must carry the REFRESHED token, not a
    // replay of the stale one — this is the whole point of the 401 path
    server.verify(
        getRequestedFor(urlEqualTo("/nifi-api/process-groups/root"))
            .withHeader("Authorization", equalTo("Bearer jwt-token")));
    server.verify(
        getRequestedFor(urlEqualTo("/nifi-api/process-groups/root"))
            .withHeader("Authorization", equalTo("Bearer jwt-token-2")));
  }

  @Test
  void persistent401AfterReauthIsRetryable() throws Exception {
    // every call to root is rejected, even after re-authentication (e.g. NiFi restarting)
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root")).willReturn(aResponse().withStatus(401)));

    client.authenticate();
    // first 401 → re-auth → still 401 → must surface as retryable (transient), not fatal
    assertThrows(RetryableAdapterException.class, client::getRootProcessGroupId);
    // a token refresh was attempted in response to the 401
    assertEquals(1, tokenProvider.refreshCount);
  }

  @Test
  void uploadResendsSnapshotBodyAfterReauthOn401() throws Exception {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    // the upload is rejected once (token expired mid-upload), then accepted after re-auth
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .inScenario("upload-reauth")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("reauthed"));
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .inScenario("upload-reauth")
            .whenScenarioStateIs("reauthed")
            .willReturn(json("{ \"id\": \"pg-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(
                json(
                    "{ \"controllerServices\": [ { \"id\": \"cs-1\", \"component\": { \"name\":"
                        + " \"JsonTreeReader\", \"type\": \"t\", \"state\": \"ENABLED\" },"
                        + " \"revision\": { \"version\": 1 } } ] }")));
    stubRunningProcessors();
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(put(urlEqualTo("/nifi-api/flow/process-groups/pg-1")).willReturn(json("{}")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x", "{ \"flowContents\": { \"name\": \"pipeline-x\" } }", Map.of());

    assertEquals("pg-1", client.deployFlow(plan));
    // the REPLAYED upload (after re-auth) must carry the snapshot body, not an exhausted/empty
    // stream — the multipart is rebuilt per attempt
    server.verify(
        postRequestedFor(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .withRequestBody(containing("pipeline-x")));
  }

  @Test
  void patchesOnlyTheNameMatchedControllerService() throws Exception {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .willReturn(json("{ \"id\": \"pg-1\" }")));
    // two controller services of the SAME type; only the name-matched one must be patched
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(
                json(
                    "{ \"controllerServices\": ["
                        + " { \"id\": \"cs-1\", \"component\": { \"name\":"
                        + " \"PostGISConnectionPool\", \"type\":"
                        + " \"org.apache.nifi.dbcp.DBCPConnectionPool\", \"state\": \"ENABLED\" },"
                        + " \"revision\": { \"version\": 1 } },"
                        + " { \"id\": \"cs-2\", \"component\": { \"name\": \"OtherPool\","
                        + " \"type\": \"org.apache.nifi.dbcp.DBCPConnectionPool\", \"state\":"
                        + " \"ENABLED\" }, \"revision\": { \"version\": 1 } } ] }")));
    stubRunningProcessors();
    server.stubFor(put(urlEqualTo("/nifi-api/controller-services/cs-1")).willReturn(json("{}")));
    server.stubFor(put(urlEqualTo("/nifi-api/controller-services/cs-2")).willReturn(json("{}")));
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(put(urlEqualTo("/nifi-api/flow/process-groups/pg-1")).willReturn(json("{}")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x",
            "{ \"flowContents\": { \"name\": \"pipeline-x\" } }",
            Map.of("PostGISConnectionPool", Map.of("Password", "db-secret")));

    client.deployFlow(plan);

    server.verify(
        putRequestedFor(urlEqualTo("/nifi-api/controller-services/cs-1"))
            .withRequestBody(containing("db-secret")));
    // the wrong-named service of the same type is never patched
    server.verify(0, putRequestedFor(urlEqualTo("/nifi-api/controller-services/cs-2")));
  }

  @Test
  void patchesSensitivePropertyOnAProcessorNotJustControllerServices() throws Exception {
    // A ConsumeMQTT *processor* carries a sensitive Password — it is NOT a controller service, so
    // the post-upload secret push must also patch processors, else authenticated MQTT sources
    // deploy without a password.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .willReturn(json("{ \"id\": \"pg-1\" }")));
    // no controller service carries the secret (FROST sink: reader + writer only)
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(
                json(
                    "{ \"controllerServices\": [ { \"id\": \"cs-r\", \"component\": { \"name\":"
                        + " \"JsonTreeReader\", \"type\": \"t\", \"state\": \"ENABLED\" },"
                        + " \"revision\": { \"version\": 1 } } ] }")));
    // the ConsumeMQTT processor is where the Password lives; VALID + Running so the post-start
    // processor health check passes
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/processors"))
            .willReturn(
                json(
                    "{ \"processors\": [ { \"id\": \"proc-1\", \"component\": { \"name\":"
                        + " \"ConsumeMQTT\", \"validationStatus\": \"VALID\" },"
                        + " \"status\": { \"runStatus\": \"Running\" },"
                        + " \"revision\": { \"version\": 4 } } ] }")));
    server.stubFor(put(urlEqualTo("/nifi-api/processors/proc-1")).willReturn(json("{}")));
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(put(urlEqualTo("/nifi-api/flow/process-groups/pg-1")).willReturn(json("{}")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x",
            "{ \"flowContents\": { \"name\": \"pipeline-x\" } }",
            Map.of("ConsumeMQTT", Map.of("Password", "mqtt-secret")));

    client.deployFlow(plan);

    // the processor secret is pushed under component.config.properties, with the revision echoed
    server.verify(
        putRequestedFor(urlEqualTo("/nifi-api/processors/proc-1"))
            .withRequestBody(containing("mqtt-secret"))
            .withRequestBody(containing("\"version\":4")));
  }

  @Test
  void patchesSharedFrostCredentialOntoEveryInvokeHttpProcessor() throws Exception {
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{ \"controllerServices\": [] }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/processors"))
            .willReturn(
                json(
                    "{ \"processors\": ["
                        + " { \"id\": \"get-1\", \"component\": { \"name\":"
                        + " \"FrostPublish\" }, \"revision\": { \"version\": 2 } },"
                        + " { \"id\": \"post-1\", \"component\": { \"name\":"
                        + " \"FrostPublish\" }, \"revision\": { \"version\": 5 } } ] }")));
    server.stubFor(put(urlEqualTo("/nifi-api/processors/get-1")).willReturn(json("{}")));
    server.stubFor(put(urlEqualTo("/nifi-api/processors/post-1")).willReturn(json("{}")));

    client.patchSensitiveProperties(
        "pg-1", Map.of("FrostPublish", Map.of("X-API-Key", "frost-secret")));

    server.verify(
        putRequestedFor(urlEqualTo("/nifi-api/processors/get-1"))
            .withRequestBody(containing("frost-secret")));
    server.verify(
        putRequestedFor(urlEqualTo("/nifi-api/processors/post-1"))
            .withRequestBody(containing("frost-secret")));
  }

  @Test
  void unmatchedSensitivePropertyFailsLoudlyInsteadOfDroppingTheSecret() {
    // A secret whose target component exists in neither the controller services nor the processors
    // must fail the deploy, not silently vanish.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .willReturn(json("{ \"id\": \"pg-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{ \"controllerServices\": [] }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/processors"))
            .willReturn(json("{ \"processors\": [] }")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x",
            "{ \"flowContents\": { \"name\": \"pipeline-x\" } }",
            Map.of("NoSuchComponent", Map.of("Password", "orphan-secret")));

    assertThrows(FatalAdapterException.class, () -> client.deployFlow(plan));
  }

  @Test
  void invalidProcessorAfterStartFailsTheDeploy() {
    // starting the group returns only an HTTP status; a processor left INVALID (e.g. a bad cron)
    // would otherwise make the saga report success while the flow never runs. The post-start check
    // must fail the deploy with the processor's validation state.
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));
    server.stubFor(
        post(urlPathEqualTo("/nifi-api/process-groups/root-1/process-groups/upload"))
            .willReturn(json("{ \"id\": \"pg-1\" }")));
    // an already-ENABLED controller service so the enable step passes and the deploy reaches the
    // post-start processor health check
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(
                json(
                    "{ \"controllerServices\": [ { \"id\": \"cs-1\", \"component\": { \"name\":"
                        + " \"JsonTreeReader\", \"type\": \"t\", \"state\": \"ENABLED\" },"
                        + " \"revision\": { \"version\": 1 } } ] }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/processors"))
            .willReturn(
                json(
                    "{ \"processors\": [ { \"id\": \"proc-1\", \"component\": { \"name\":"
                        + " \"QueryDatabaseTableRecord\", \"validationStatus\": \"INVALID\","
                        + " \"validationErrors\": [ \"'Scheduling Period' is invalid\" ] },"
                        + " \"status\": { \"runStatus\": \"Stopped\" },"
                        + " \"revision\": { \"version\": 1 } } ] }")));
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(put(urlEqualTo("/nifi-api/flow/process-groups/pg-1")).willReturn(json("{}")));

    DeploymentPlan plan =
        new DeploymentPlan(
            "pipeline-x", "{ \"flowContents\": { \"name\": \"pipeline-x\" } }", Map.of());

    assertThrows(FatalAdapterException.class, () -> client.deployFlow(plan));
  }

  @Test
  void deleteOfMissingProcessGroupIsIdempotent() throws Exception {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(json("{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [] } } }")));

    // no matching group -> no-op, no exception
    client.deleteFlowByName("pipeline-absent");
  }

  @Test
  void deleteWaitsForProcessGroupToStopBeforeDeleting() throws Exception {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(
                json(
                    "{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [ { \"id\": \"pg-1\","
                        + " \"component\": { \"name\": \"pipeline-x\" }, \"revision\": { \"version\":"
                        + " "
                        + LISTING_REVISION
                        + " } } ] } } }")));
    server.stubFor(put(urlEqualTo("/nifi-api/flow/process-groups/pg-1")).willReturn(json("{}")));

    // Stopping is asynchronous: the first status poll still reports a running processor with an
    // active thread, the second reports the group fully stopped. The delete must not fire until
    // then, else NiFi answers 409 ("Processor is running").
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1"))
            .inScenario("stopping")
            .whenScenarioStateIs("Started")
            .willReturn(
                json(
                    "{ \"runningCount\": 1, \"revision\": { \"version\": "
                        + STOPPED_REVISION
                        + " }, \"status\": {"
                        + " \"aggregateSnapshot\": { \"activeThreadCount\": 1 } } }"))
            .willSetStateTo("stopped"));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1"))
            .inScenario("stopping")
            .whenScenarioStateIs("stopped")
            .willReturn(
                json(
                    "{ \"runningCount\": 0, \"revision\": { \"version\": "
                        + STOPPED_REVISION
                        + " }, \"status\": {"
                        + " \"aggregateSnapshot\": { \"activeThreadCount\": 0 } } }")));

    stubEmptyAllQueues();
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{ \"controllerServices\": [] }")));
    server.stubFor(delete(urlPathEqualTo("/nifi-api/process-groups/pg-1")).willReturn(json("{}")));

    client.deleteFlowByName("pipeline-x");

    // Polled until stopped (two status reads) before the single delete was issued.
    server.verify(2, getRequestedFor(urlPathEqualTo("/nifi-api/process-groups/pg-1")));
    server.verify(1, deleteRequestedFor(urlPathEqualTo("/nifi-api/process-groups/pg-1")));
    // The delete locks on the revision observed together with the stop, not the one the listing
    // carried before it.
    server.verify(
        1,
        deleteRequestedFor(urlPathEqualTo("/nifi-api/process-groups/pg-1"))
            .withQueryParam("version", equalTo(String.valueOf(STOPPED_REVISION))));
  }

  /**
   * The queues are emptied between "stopped" and "controller services disabled". NiFi answers the
   * drop asynchronously, so the delete must wait for the request to report finished — otherwise it
   * runs into the very HTTP 409 ("Queue not empty") the step exists to prevent.
   */
  @Test
  void deleteEmptiesQueuesAndWaitsForTheDropToFinish() throws Exception {
    stubStoppedProcessGroupForDelete();
    server.stubFor(
        post(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests"))
            .willReturn(json("{ \"dropRequest\": { \"id\": \"drop-1\", \"finished\": false } }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1"))
            .inScenario("dropping")
            .whenScenarioStateIs("Started")
            .willReturn(json("{ \"dropRequest\": { \"id\": \"drop-1\", \"finished\": false } }"))
            .willSetStateTo("dropped"));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1"))
            .inScenario("dropping")
            .whenScenarioStateIs("dropped")
            .willReturn(
                json(
                    "{ \"dropRequest\": { \"id\": \"drop-1\", \"finished\": true,"
                        + " \"droppedCount\": 30 } }")));
    server.stubFor(
        delete(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1"))
            .willReturn(json("{}")));

    client.deleteFlowByName("pipeline-x");

    server.verify(
        1,
        postRequestedFor(
            urlPathEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests")));
    // Polled until the drop reported finished (two reads) before the group was deleted.
    server.verify(
        2,
        getRequestedFor(
            urlPathEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1")));
    server.verify(1, deleteRequestedFor(urlPathEqualTo("/nifi-api/process-groups/pg-1")));
    // The finished request is released so it does not linger in NiFi.
    server.verify(
        1,
        deleteRequestedFor(
            urlPathEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1")));
  }

  /**
   * NiFi reports a failed drop in-band with HTTP 200 through {@code failureReason}. Deleting anyway
   * would hit the 409 and loop, so the teardown stops here — retryably, because a redelivery can
   * succeed once whatever held the queue is gone.
   */
  @Test
  void deleteFailsRetryablyWhenTheDropReportsAFailureReason() {
    stubStoppedProcessGroupForDelete();
    server.stubFor(
        post(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests"))
            .willReturn(json("{ \"dropRequest\": { \"id\": \"drop-1\", \"finished\": false } }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1"))
            .willReturn(
                json(
                    "{ \"dropRequest\": { \"id\": \"drop-1\", \"finished\": true,"
                        + " \"failureReason\": \"Cannot drop while a processor is running\" } }")));
    server.stubFor(
        delete(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1"))
            .willReturn(json("{}")));

    RetryableAdapterException thrown =
        assertThrows(RetryableAdapterException.class, () -> client.deleteFlowByName("pipeline-x"));

    assertTrue(thrown.getMessage().contains("Cannot drop while a processor is running"));
    server.verify(0, deleteRequestedFor(urlPathEqualTo("/nifi-api/process-groups/pg-1")));
    // Released even though the drop failed — the cleanup must not depend on the outcome.
    server.verify(
        1,
        deleteRequestedFor(
            urlPathEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1")));
  }

  /** Resolves pipeline-x to pg-1 and reports it already stopped, so a delete reaches the queues. */
  private void stubStoppedProcessGroupForDelete() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(
                json(
                    "{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [ { \"id\": \"pg-1\","
                        + " \"component\": { \"name\": \"pipeline-x\" }, \"revision\": {"
                        + " \"version\": "
                        + LISTING_REVISION
                        + " } } ] } } }")));
    server.stubFor(put(urlEqualTo("/nifi-api/flow/process-groups/pg-1")).willReturn(json("{}")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1"))
            .willReturn(
                json(
                    "{ \"runningCount\": 0, \"revision\": { \"version\": "
                        + STOPPED_REVISION
                        + " }, \"status\": {"
                        + " \"aggregateSnapshot\": { \"activeThreadCount\": 0 } } }")));
    server.stubFor(
        put(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{}")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/controller-services"))
            .willReturn(json("{ \"controllerServices\": [] }")));
    server.stubFor(delete(urlPathEqualTo("/nifi-api/process-groups/pg-1")).willReturn(json("{}")));
  }

  /** A drop request that finishes immediately, for tests that are not about the drop itself. */
  private void stubEmptyAllQueues() {
    server.stubFor(
        post(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests"))
            .willReturn(json("{ \"dropRequest\": { \"id\": \"drop-1\", \"finished\": false } }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1"))
            .willReturn(
                json(
                    "{ \"dropRequest\": { \"id\": \"drop-1\", \"finished\": true,"
                        + " \"droppedCount\": 0 } }")));
    server.stubFor(
        delete(urlEqualTo("/nifi-api/process-groups/pg-1/empty-all-connections-requests/drop-1"))
            .willReturn(json("{}")));
  }

  /** One VALID, Running processor so the inspection reports nothing and the bulletin decides. */
  private void stubHealthyProcessorsForBulletinTests() {
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/pg-1/processors"))
            .willReturn(
                json(
                    "{ \"processors\": [ { \"id\": \"proc-1\", \"component\": { \"name\":"
                        + " \"LogMessage\", \"validationStatus\": \"VALID\" },"
                        + " \"status\": { \"runStatus\": \"Running\" },"
                        + " \"revision\": { \"version\": 1 } } ] }")));
  }

  private static com.fasterxml.jackson.databind.JsonNode bulletin(String level, String message)
      throws Exception {
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(
            "[ { \"bulletin\": { \"groupId\": \"pg-1\", \"sourceId\": \"proc-1\","
                + " \"sourceName\": \"LogMessage\", \"level\": \""
                + level
                + "\", \"message\": \""
                + message
                + "\", \"timestamp\": \"\" } } ]");
  }

  @Test
  void recordsReachingTheErrorSinkMakeThePipelineUnhealthy() throws Exception {
    // NiFi raises the error-sink bulletin at its warn level, which it spells WARNING. Reporting the
    // pipeline healthy here is what let a flow discard every record while looking fine.
    stubHealthyProcessorsForBulletinTests();

    NifiRestClient.RuntimeStatus status =
        client.readRuntimeStatus(
            "pg-1", bulletin("WARNING", "Pipeline record dropped: httpStatus=400 exception="));

    assertEquals(false, status.healthy());
    assertTrue(status.message().contains("Pipeline record dropped"));
  }

  @Test
  void theShortWarnSpellingIsStillTreatedAsAFailure() throws Exception {
    stubHealthyProcessorsForBulletinTests();

    NifiRestClient.RuntimeStatus status =
        client.readRuntimeStatus("pg-1", bulletin("WARN", "connection refused"));

    assertEquals(false, status.healthy());
  }

  @Test
  void aWarningThatNamesNoFailureLeavesThePipelineHealthy() throws Exception {
    // The level alone must not condemn a pipeline — NiFi warns about plenty of benign things.
    stubHealthyProcessorsForBulletinTests();

    NifiRestClient.RuntimeStatus status =
        client.readRuntimeStatus("pg-1", bulletin("WARNING", "queue is 80 percent full"));

    assertEquals(true, status.healthy());
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
      String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(body);
  }
}

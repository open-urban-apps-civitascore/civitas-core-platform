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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NifiRestClientTest {

  private WireMockServer server;
  private Client httpClient;
  private NifiRestClient client;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    httpClient = ClientBuilder.newClient();
    client = new NifiRestClient(server.baseUrl(), "admin", "pw", httpClient);
  }

  @AfterEach
  void tearDown() {
    httpClient.close();
    server.stop();
  }

  private void stubAuth() {
    server.stubFor(
        post(urlEqualTo("/nifi-api/access/token"))
            .willReturn(aResponse().withStatus(201).withBody("jwt-token")));
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
    stubAuth();
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
  void serverErrorIsRetryable() {
    stubAuth();
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
    stubAuth();
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
    stubAuth();
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
    stubAuth();
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
    stubAuth();
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
    stubAuth();
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
    stubAuth();
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
    stubAuth();
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
    stubAuth(); // token endpoint always issues a token
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
    // token endpoint hit twice: the initial authenticate() + the re-auth triggered by the 401
    server.verify(2, postRequestedFor(urlEqualTo("/nifi-api/access/token")));
  }

  @Test
  void persistent401AfterReauthIsRetryable() throws Exception {
    stubAuth(); // token endpoint always issues a token
    // every call to root is rejected, even after re-authentication (e.g. NiFi restarting)
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root")).willReturn(aResponse().withStatus(401)));

    client.authenticate();
    // first 401 → re-auth → still 401 → must surface as retryable (transient), not fatal
    assertThrows(RetryableAdapterException.class, client::getRootProcessGroupId);
    // re-authentication was attempted: initial authenticate() + the one triggered by the 401
    server.verify(2, postRequestedFor(urlEqualTo("/nifi-api/access/token")));
  }

  @Test
  void uploadResendsSnapshotBodyAfterReauthOn401() throws Exception {
    stubAuth();
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
    stubAuth();
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
    stubAuth();
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
  void unmatchedSensitivePropertyFailsLoudlyInsteadOfDroppingTheSecret() throws Exception {
    // A secret whose target component exists in neither the controller services nor the processors
    // must fail the deploy, not silently vanish.
    stubAuth();
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
  void invalidProcessorAfterStartFailsTheDeploy() throws Exception {
    // starting the group returns only an HTTP status; a processor left INVALID (e.g. a bad cron)
    // would otherwise make the saga report success while the flow never runs. The post-start check
    // must fail the deploy with the processor's validation state.
    stubAuth();
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
    stubAuth();
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
    stubAuth();
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/root"))
            .willReturn(json("{ \"id\": \"root-1\" }")));
    server.stubFor(
        get(urlEqualTo("/nifi-api/flow/process-groups/root-1"))
            .willReturn(
                json(
                    "{ \"processGroupFlow\": { \"flow\": { \"processGroups\": [ { \"id\": \"pg-1\","
                        + " \"component\": { \"name\": \"pipeline-x\" }, \"revision\": { \"version\":"
                        + " 2 } } ] } } }")));
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
                    "{ \"runningCount\": 1, \"status\": { \"aggregateSnapshot\": {"
                        + " \"activeThreadCount\": 1 } } }"))
            .willSetStateTo("stopped"));
    server.stubFor(
        get(urlEqualTo("/nifi-api/process-groups/pg-1"))
            .inScenario("stopping")
            .whenScenarioStateIs("stopped")
            .willReturn(
                json(
                    "{ \"runningCount\": 0, \"status\": { \"aggregateSnapshot\": {"
                        + " \"activeThreadCount\": 0 } } }")));

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
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
      String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(body);
  }
}

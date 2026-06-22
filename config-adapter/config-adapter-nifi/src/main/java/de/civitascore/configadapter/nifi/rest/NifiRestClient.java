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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin client over the NiFi 2.x REST API implementing the verified deploy sequence (token → root PG
 * → upload snapshot → patch sensitive controller-service properties → enable controller services →
 * start process group) plus idempotent stop-and-delete. Network/5xx failures map to {@link
 * RetryableAdapterException}; 4xx map to {@link FatalAdapterException}; a 404 on delete is treated
 * as success (idempotent teardown).
 */
public class NifiRestClient implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(NifiRestClient.class);
  private static final String API = "/nifi-api";
  private static final String CLIENT_ID = "config-adapter-nifi";
  private static final int ENABLE_POLL_ATTEMPTS = 30;
  private static final long ENABLE_POLL_INTERVAL_MS = 1000L;

  private final String baseUrl;
  private final String username;
  private final String password;
  private final Client client;
  private final ObjectMapper mapper = new ObjectMapper();
  private volatile String token;

  /**
   * Creates a client.
   *
   * @param baseUrl the NiFi base URL (e.g. {@code https://nifi:8443})
   * @param username the single-user username
   * @param password the single-user password
   * @param client the JAX-RS client to use
   */
  public NifiRestClient(String baseUrl, String username, String password, Client client) {
    this.baseUrl = baseUrl;
    this.username = username;
    this.password = password;
    this.client = client;
  }

  /** A reference to a NiFi process group with its optimistic-locking revision. */
  public record ProcessGroupRef(String id, long version) {}

  /** A reference to a controller service with its type and revision. */
  public record ControllerServiceRef(String id, String type, long version) {}

  // ─── Public orchestration ──────────────────────────────────────────────────

  /**
   * Deploys a flow: upload → patch sensitive properties → enable controller services → start. If a
   * process group with the same name already exists it is stopped and deleted first (idempotent
   * redeploy).
   *
   * @param plan the deployment plan
   * @return the new process-group id
   * @throws FatalAdapterException on a non-retryable error
   * @throws RetryableAdapterException on a transient error
   */
  public String deployFlow(DeploymentPlan plan)
      throws FatalAdapterException, RetryableAdapterException {
    authenticate();
    String rootId = getRootProcessGroupId();
    Optional<ProcessGroupRef> existing = findProcessGroupByName(rootId, plan.processGroupName());
    if (existing.isPresent()) {
      stopAndDeleteProcessGroup(existing.get());
    }

    String pgId = uploadSnapshot(rootId, plan.processGroupName(), plan.snapshotJson());
    // The snapshot is uploaded but not yet live. If any subsequent step fails, the half-deployed
    // group (whose controller services may already hold patched datasource secrets) must not be
    // left orphaned in NiFi — best-effort delete it before propagating the original failure.
    try {
      patchSensitiveProperties(pgId, plan.sensitivePropsByComponent());
      enableControllerServices(pgId);
      awaitControllerServicesEnabled(pgId);
      startProcessGroup(pgId);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      bestEffortDelete(rootId, plan.processGroupName());
      throw e;
    }
    LOG.info(
        "Deployed NiFi flow {} as process group {}", Encode.forJava(plan.processGroupName()), pgId);
    return pgId;
  }

  /** Best-effort teardown of a partially-deployed group; never masks the original failure. */
  @SuppressWarnings(
      "PMD.AvoidCatchingGenericException") // deliberately swallow everything on cleanup
  private void bestEffortDelete(String rootId, String pgName) {
    try {
      Optional<ProcessGroupRef> orphan = findProcessGroupByName(rootId, pgName);
      if (orphan.isPresent()) {
        stopAndDeleteProcessGroup(orphan.get());
        LOG.info("Cleaned up partially-deployed process group {}", Encode.forJava(pgName));
      }
    } catch (FatalAdapterException | RetryableAdapterException | RuntimeException cleanup) {
      LOG.error(
          "Failed to clean up partially-deployed process group {}; manual removal may be needed",
          Encode.forJava(pgName),
          cleanup);
    }
  }

  /**
   * Stops and deletes the process group with the given name, if present. A missing group is a
   * no-op.
   *
   * @param name the process-group name
   * @throws FatalAdapterException on a non-retryable error
   * @throws RetryableAdapterException on a transient error
   */
  public void deleteFlowByName(String name)
      throws FatalAdapterException, RetryableAdapterException {
    authenticate();
    String rootId = getRootProcessGroupId();
    Optional<ProcessGroupRef> existing = findProcessGroupByName(rootId, name);
    if (existing.isPresent()) {
      stopAndDeleteProcessGroup(existing.get());
    }
  }

  // ─── Individual steps ──────────────────────────────────────────────────────

  String authenticate() throws FatalAdapterException, RetryableAdapterException {
    Form form = new Form().param("username", username).param("password", password);
    try (Response response =
        target(API + "/access/token").request(MediaType.TEXT_PLAIN).post(Entity.form(form))) {
      check(response, "authenticate");
      this.token = response.readEntity(String.class);
      return token;
    } catch (ProcessingException e) {
      throw network("authenticate", e);
    }
  }

  String getRootProcessGroupId() throws FatalAdapterException, RetryableAdapterException {
    JsonNode body = getJson(API + "/process-groups/root", "root process group");
    return requireId(body.path("id").asText(), "root process group");
  }

  Optional<ProcessGroupRef> findProcessGroupByName(String rootId, String name)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode body = getJson(API + "/flow/process-groups/" + rootId, "list process groups");
    for (JsonNode group : body.path("processGroupFlow").path("flow").path("processGroups")) {
      if (name.equals(group.path("component").path("name").asText())) {
        return Optional.of(
            new ProcessGroupRef(
                group.path("id").asText(), group.path("revision").path("version").asLong()));
      }
    }
    return Optional.empty();
  }

  String uploadSnapshot(String rootId, String pgName, String snapshotJson)
      throws FatalAdapterException, RetryableAdapterException {
    WebTarget target =
        target(API + "/process-groups/" + rootId + "/process-groups/upload")
            .register(MultiPartFeature.class);
    byte[] body = snapshotJson.getBytes(StandardCharsets.UTF_8);
    try (Response response =
        sendAuthorized(() -> postUploadMultipart(target, rootId, pgName, body))) {
      check(response, "upload snapshot");
      return requireId(
          mapper.readTree(response.readEntity(String.class)).path("id").asText(),
          "uploaded process group");
    } catch (ProcessingException e) {
      throw network("upload snapshot", e);
    } catch (IOException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_FLOW_ERROR, e, "upload snapshot");
    }
  }

  /**
   * Builds a FRESH multipart body for each call. The snapshot stream is single-use, so the {@link
   * #sendAuthorized} 401-replay must rebuild it — replaying the original, already-consumed stream
   * would upload an empty body.
   */
  private Response postUploadMultipart(
      WebTarget target, String rootId, String pgName, byte[] body) {
    try (FormDataMultiPart multipart = new FormDataMultiPart()) {
      multipart.field("id", rootId);
      multipart.field("groupName", pgName);
      multipart.field("positionX", "0");
      multipart.field("positionY", "0");
      multipart.field("clientId", CLIENT_ID);
      multipart.bodyPart(
          new StreamDataBodyPart(
              "file",
              new ByteArrayInputStream(body),
              pgName + ".json",
              MediaType.APPLICATION_JSON_TYPE));
      return authorized(target).post(Entity.entity(multipart, multipart.getMediaType()));
    } catch (IOException e) {
      // FormDataMultiPart.close() (in-memory body) — surface as a transient transport error.
      throw new ProcessingException("upload multipart", e);
    }
  }

  void patchSensitiveProperties(String pgId, Map<String, Map<String, String>> sensitiveByComponent)
      throws FatalAdapterException, RetryableAdapterException {
    if (sensitiveByComponent.isEmpty()) {
      return;
    }
    // Secrets may target controller services (e.g. a DBCP pool's Password) OR processors (e.g.
    // ConsumeMQTT's Password). Patch the controller services first, then — only if any secret is
    // still unplaced — the processors. A secret that matches neither must fail the deploy rather
    // than be dropped silently (a missing source password would otherwise surface much later as an
    // opaque connection failure).
    Set<String> remaining = new HashSet<>(sensitiveByComponent.keySet());
    patchControllerServiceSecrets(pgId, sensitiveByComponent, remaining);
    if (!remaining.isEmpty()) {
      patchProcessorSecrets(pgId, sensitiveByComponent, remaining);
    }
    if (!remaining.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR,
          "sensitive properties target no controller service or processor in the flow: "
              + remaining);
    }
  }

  private void patchControllerServiceSecrets(
      String pgId, Map<String, Map<String, String>> sensitiveByComponent, Set<String> remaining)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode services =
        getJson(API + "/flow/process-groups/" + pgId + "/controller-services", "list services")
            .path("controllerServices");
    for (JsonNode service : services) {
      // Match the controller service to its secret bundle by its friendly NAME (which the builder
      // stamps onto each CS), not by a fuzzy type substring — so a second service of the same type
      // (e.g. another DBCP pool) can never be force-matched to the wrong secrets.
      String name = service.path("component").path("name").asText();
      Map<String, String> props = sensitiveByComponent.get(name);
      if (props != null) {
        patchService(
            new ControllerServiceRef(
                service.path("id").asText(),
                service.path("component").path("type").asText(),
                service.path("revision").path("version").asLong()),
            props);
        remaining.remove(name);
      }
    }
  }

  private void patchProcessorSecrets(
      String pgId, Map<String, Map<String, String>> sensitiveByComponent, Set<String> remaining)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode processors =
        getJson(API + "/process-groups/" + pgId + "/processors", "list processors")
            .path("processors");
    for (JsonNode processor : processors) {
      String name = processor.path("component").path("name").asText();
      Map<String, String> props = sensitiveByComponent.get(name);
      if (props != null) {
        patchProcessor(
            processor.path("id").asText(),
            processor.path("revision").path("version").asLong(),
            props);
        remaining.remove(name);
      }
    }
  }

  private void patchProcessor(String id, long version, Map<String, String> sensitiveProps)
      throws FatalAdapterException, RetryableAdapterException {
    ObjectNode body = mapper.createObjectNode();
    ObjectNode revision = body.putObject("revision");
    revision.put("version", version);
    revision.put("clientId", CLIENT_ID);
    ObjectNode component = body.putObject("component");
    component.put("id", id);
    // A processor's properties live under component.config.properties (controller services put
    // them directly under component.properties).
    ObjectNode properties = component.putObject("config").putObject("properties");
    sensitiveProps.forEach(properties::put);
    put(API + "/processors/" + id, body, "patch processor");
  }

  private void patchService(ControllerServiceRef service, Map<String, String> sensitiveProps)
      throws FatalAdapterException, RetryableAdapterException {
    ObjectNode body = mapper.createObjectNode();
    ObjectNode revision = body.putObject("revision");
    revision.put("version", service.version());
    revision.put("clientId", CLIENT_ID);
    ObjectNode component = body.putObject("component");
    component.put("id", service.id());
    ObjectNode properties = component.putObject("properties");
    sensitiveProps.forEach(properties::put);
    put(API + "/controller-services/" + service.id(), body, "patch controller service");
  }

  void enableControllerServices(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    ObjectNode body = mapper.createObjectNode();
    body.put("id", pgId);
    body.put("state", "ENABLED");
    body.put("disconnectedNodeAcknowledged", false);
    put(API + "/flow/process-groups/" + pgId + "/controller-services", body, "enable services");
  }

  /**
   * Returns whether every controller service in the group is in {@code state}. {@code
   * emptyMeansReady} decides how an empty/absent service list is interpreted: when awaiting ENABLED
   * before start it must be {@code false} (an empty list means "not yet materialized", keep
   * polling); when awaiting DISABLED before delete it is {@code true} (no services left to
   * disable).
   */
  private boolean controllerServicesAllInState(String pgId, String state, boolean emptyMeansReady)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode services =
        getJson(API + "/flow/process-groups/" + pgId + "/controller-services", "list services")
            .path("controllerServices");
    if (services.isEmpty()) {
      return emptyMeansReady;
    }
    for (JsonNode service : services) {
      if (!state.equals(service.path("component").path("state").asText())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Bulk-disables all controller services in the group (required before the group can be deleted).
   *
   * @param pgId the process-group id
   * @throws FatalAdapterException on a non-retryable error
   * @throws RetryableAdapterException on a transient error
   */
  void disableControllerServices(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    ObjectNode body = mapper.createObjectNode();
    body.put("id", pgId);
    body.put("state", "DISABLED");
    body.put("disconnectedNodeAcknowledged", false);
    put(API + "/flow/process-groups/" + pgId + "/controller-services", body, "disable services");
  }

  /**
   * Blocks until all controller services in the group reach the given state (enabling/disabling is
   * asynchronous).
   *
   * @param pgId the process-group id
   * @param state the target state ({@code ENABLED} or {@code DISABLED})
   * @throws FatalAdapterException on a non-retryable error
   * @throws RetryableAdapterException if the services do not reach the state in time
   */
  private void awaitControllerServicesState(String pgId, String state, boolean emptyMeansReady)
      throws FatalAdapterException, RetryableAdapterException {
    for (int attempt = 0; attempt < ENABLE_POLL_ATTEMPTS; attempt++) {
      if (controllerServicesAllInState(pgId, state, emptyMeansReady)) {
        return;
      }
      try {
        Thread.sleep(ENABLE_POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RetryableAdapterException(
            AdapterErrorCode.NIFI_ERROR, e, "interrupted while waiting for controller services");
      }
    }
    throw new RetryableAdapterException(
        AdapterErrorCode.NIFI_ERROR,
        "controller services did not reach " + state + " state in time");
  }

  private void awaitControllerServicesEnabled(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    awaitControllerServicesState(pgId, "ENABLED", false);
  }

  void startProcessGroup(String pgId) throws FatalAdapterException, RetryableAdapterException {
    ObjectNode body = mapper.createObjectNode();
    body.put("id", pgId);
    body.put("state", "RUNNING");
    body.put("disconnectedNodeAcknowledged", false);
    put(API + "/flow/process-groups/" + pgId, body, "start process group");
  }

  private void stopAndDeleteProcessGroup(ProcessGroupRef group)
      throws FatalAdapterException, RetryableAdapterException {
    ObjectNode stop = mapper.createObjectNode();
    stop.put("id", group.id());
    stop.put("state", "STOPPED");
    stop.put("disconnectedNodeAcknowledged", false);
    put(API + "/flow/process-groups/" + group.id(), stop, "stop process group");

    // A process group cannot be deleted while its controller services are enabled.
    disableControllerServices(group.id());
    awaitControllerServicesState(group.id(), "DISABLED", true);

    try (Response response =
        sendAuthorized(
            () ->
                authorized(
                        target(API + "/process-groups/" + group.id())
                            .queryParam("version", group.version())
                            .queryParam("clientId", CLIENT_ID))
                    .delete())) {
      if (response.getStatus() != 404) {
        check(response, "delete process group");
      }
    } catch (ProcessingException e) {
      throw network("delete process group", e);
    }
  }

  // ─── HTTP helpers ──────────────────────────────────────────────────────────

  private JsonNode getJson(String path, String description)
      throws FatalAdapterException, RetryableAdapterException {
    try (Response response = sendAuthorized(() -> authorized(target(path)).get())) {
      check(response, description);
      return mapper.readTree(response.readEntity(String.class));
    } catch (ProcessingException e) {
      throw network(description, e);
    } catch (JsonProcessingException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_FLOW_ERROR, e, description);
    }
  }

  private void put(String path, JsonNode body, String description)
      throws FatalAdapterException, RetryableAdapterException {
    try (Response response =
        sendAuthorized(
            () ->
                authorized(target(path))
                    .put(Entity.entity(body.toString(), MediaType.APPLICATION_JSON)))) {
      check(response, description);
    } catch (ProcessingException e) {
      throw network(description, e);
    }
  }

  /**
   * Executes an authorized request and, if NiFi answers 401 (the bearer token expired mid-saga),
   * re-authenticates once and replays it — the replayed request rebuilds the {@code Authorization}
   * header from the refreshed {@link #token}.
   */
  private Response sendAuthorized(Supplier<Response> request)
      throws FatalAdapterException, RetryableAdapterException {
    Response response = request.get();
    if (response.getStatus() == 401) {
      response.close();
      LOG.info("NiFi returned 401 — re-authenticating and retrying once");
      authenticate();
      response = request.get();
      if (response.getStatus() == 401) {
        response.close();
        // Re-authentication did not clear the 401 (e.g. NiFi restarting, credentials momentarily
        // rejected). That is a transient condition — surface it as retryable so the saga layer can
        // back off and redeliver, rather than letting check() map the 4xx to a fatal DLQ error.
        throw new RetryableAdapterException(
            AdapterErrorCode.NIFI_ERROR, "re-authentication did not clear HTTP 401");
      }
    }
    return response;
  }

  private WebTarget target(String path) {
    return client.target(baseUrl + path);
  }

  private jakarta.ws.rs.client.Invocation.Builder authorized(WebTarget target) {
    return target.request(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
  }

  private void check(Response response, String description)
      throws FatalAdapterException, RetryableAdapterException {
    int status = response.getStatus();
    if (status >= 200 && status < 300) {
      return;
    }
    String body = safeBody(response);
    if (status >= 500) {
      throw new RetryableAdapterException(
          AdapterErrorCode.NIFI_ERROR, description + ": HTTP " + status + " — " + body);
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_FLOW_ERROR, description + ": HTTP " + status + " — " + body);
  }

  private static String safeBody(Response response) {
    try {
      return response.hasEntity() ? response.readEntity(String.class) : "";
    } catch (ProcessingException | IllegalStateException e) {
      LOG.warn("Could not read NiFi error response body: {}", e.getMessage());
      return "<unreadable response body>";
    }
  }

  private static String requireId(String id, String description) throws FatalAdapterException {
    if (id == null || id.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR, "NiFi returned no id for " + description);
    }
    return id;
  }

  private static RetryableAdapterException network(String description, ProcessingException e) {
    return new RetryableAdapterException(
        AdapterErrorCode.NETWORK_ERROR, "nifi", description + ": " + e.getMessage());
  }

  @Override
  public void close() {
    client.close();
  }
}

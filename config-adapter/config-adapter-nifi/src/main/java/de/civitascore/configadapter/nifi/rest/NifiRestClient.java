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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.auth.NifiTokenProvider;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttTruststoreConfig;
import de.civitascore.configadapter.util.OkHttpJson;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
  private static final int POLL_ATTEMPTS = 30;
  private static final long POLL_INTERVAL_MS = 1000L;

  private final String baseUrl;
  private final NifiTokenProvider tokenProvider;
  private final OkHttpClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * The bearer token most recently obtained from {@link #tokenProvider}, refreshed in place on a
   * 401. This single shared field is safe for the single-instance/concurrent-deploy contract
   * because every token is a valid credential for the same OIDC service-account identity: a
   * concurrent refresh can at worst replace it with another equally-valid token (a fresh JWT, not
   * necessarily byte-identical). {@code volatile} guarantees visibility of that replacement across
   * threads.
   */
  private volatile String token;

  /** Only its names are used, to point a validation failure at the right parameter context. */
  private final MqttTruststoreConfig mqttTruststore;

  /**
   * Creates a client.
   *
   * @param baseUrl the NiFi base URL (e.g. {@code https://nifi:8443})
   * @param tokenProvider supplies (and refreshes) the OIDC bearer token sent to NiFi
   * @param client the OkHttp client to use
   * @param mqttTruststore the configured MQTT trust anchor, named in truststore-password
   *     diagnostics
   */
  public NifiRestClient(
      String baseUrl,
      NifiTokenProvider tokenProvider,
      OkHttpClient client,
      MqttTruststoreConfig mqttTruststore) {
    this.baseUrl = baseUrl;
    this.tokenProvider = tokenProvider;
    this.client = client;
    this.mqttTruststore = mqttTruststore;
  }

  /** A reference to a NiFi process group with its optimistic-locking revision. */
  public record ProcessGroupRef(String id, long version) {}

  public record ManagedProcessGroup(String pipelineId, String processGroupId) {}

  public List<ManagedProcessGroup> listManagedProcessGroups()
      throws FatalAdapterException, RetryableAdapterException {
    if (token == null) {
      authenticate();
    }
    String rootId = getRootProcessGroupId();
    JsonNode groups =
        getJson(API + "/flow/process-groups/" + rootId, "list process groups")
            .path("processGroupFlow")
            .path("flow")
            .path("processGroups");
    List<ManagedProcessGroup> result = new ArrayList<>();
    for (JsonNode group : groups) {
      String name = group.path("component").path("name").asText();
      if (!name.startsWith("pipeline-")) {
        continue;
      }
      String pipelineId = name.substring("pipeline-".length());
      try {
        UUID.fromString(pipelineId);
        result.add(new ManagedProcessGroup(pipelineId, group.path("id").asText()));
      } catch (IllegalArgumentException ignored) {
        // Integration/test flows may use readable names; only managed UUID pipelines are tracked.
      }
    }
    return result;
  }

  /** Runtime information collected from processors and the NiFi bulletin board. */
  public record RuntimeStatus(
      boolean healthy, String message, String stacktrace, Instant occurredAt) {}

  private record ProcessorInspection(RuntimeStatus status, Set<String> processorIds) {}

  public RuntimeStatus readRuntimeStatus(String processGroupId)
      throws FatalAdapterException, RetryableAdapterException {
    return readRuntimeStatus(processGroupId, readBulletins());
  }

  public RuntimeStatus readRuntimeStatus(String processGroupId, JsonNode bulletins)
      throws FatalAdapterException, RetryableAdapterException {
    if (token == null) {
      authenticate();
    }
    ProcessorInspection inspection = readProcessorStatus(processGroupId);
    if (inspection.status() != null) {
      return inspection.status();
    }
    RuntimeStatus bulletinStatus =
        readBulletinStatus(processGroupId, inspection.processorIds(), bulletins);
    return bulletinStatus == null
        ? new RuntimeStatus(true, null, null, Instant.now())
        : bulletinStatus;
  }

  public JsonNode readBulletins() throws FatalAdapterException, RetryableAdapterException {
    if (token == null) {
      authenticate();
    }
    return getJson(API + "/flow/bulletin-board", "list bulletins")
        .path("bulletinBoard")
        .path("bulletins");
  }

  private ProcessorInspection readProcessorStatus(String processGroupId)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode processors = processorNodes(processGroupId);
    Set<String> processorIds = new HashSet<>();
    for (JsonNode processor : processors) {
      processorIds.add(processor.path("id").asText());
      JsonNode component = processor.path("component");
      String name = component.path("name").asText("processor");
      String validation = component.path("validationStatus").asText();
      String runStatus = processor.path("status").path("runStatus").asText();
      if ("INVALID".equals(validation)) {
        return new ProcessorInspection(
            new RuntimeStatus(
                false,
                "Processor '" + name + "' is invalid",
                validationErrors(component),
                Instant.now()),
            processorIds);
      }
      if ("Stopped".equals(runStatus) || "Disabled".equals(runStatus)) {
        return new ProcessorInspection(
            new RuntimeStatus(
                false,
                "Processor '" + name + "' is " + runStatus,
                name + " runStatus=" + runStatus,
                Instant.now()),
            processorIds);
      }
    }

    return new ProcessorInspection(null, processorIds);
  }

  private JsonNode processorNodes(String processGroupId)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      return getJson(
              API + "/flow/process-groups/" + processGroupId + "/processors", "list processors")
          .path("processors");
    } catch (FatalAdapterException e) {
      if (!e.getInternalMessage().contains("HTTP 404")) {
        throw e;
      }
      return getJson(API + "/flow/process-groups/" + processGroupId, "read process group")
          .path("processGroupFlow")
          .path("flow")
          .path("processors");
    }
  }

  private RuntimeStatus readBulletinStatus(
      String processGroupId, Set<String> processorIds, JsonNode bulletins) {
    for (JsonNode entry : bulletins) {
      JsonNode bulletin = entry.path("bulletin");
      if (!matchesPipeline(bulletin, processGroupId, processorIds)) {
        continue;
      }
      String level = bulletin.path("level").asText();
      String message = bulletin.path("message").asText();
      if (isRuntimeFailure(level, message)) {
        String timestamp = bulletin.path("timestamp").asText();
        Instant occurredAt;
        try {
          occurredAt = timestamp.isBlank() ? Instant.now() : Instant.parse(timestamp);
        } catch (DateTimeParseException ignored) {
          occurredAt = Instant.now();
        }
        return new RuntimeStatus(
            false,
            message.isBlank() ? "NiFi reported a pipeline error" : message,
            message,
            occurredAt);
      }
    }
    return null;
  }

  private static boolean matchesPipeline(
      JsonNode bulletin, String processGroupId, Set<String> processorIds) {
    return processGroupId.equals(bulletin.path("groupId").asText())
        || processorIds.contains(bulletin.path("sourceId").asText());
  }

  /**
   * Whether a bulletin means the pipeline is failing rather than merely noisy. NiFi reports the
   * warn level as {@code WARNING}, not {@code WARN}, so matching the short spelling alone never
   * fired — and a record reaching the error sink is the only thing this branch can catch, because
   * NiFi raises that bulletin at warn level. Both spellings are accepted so the check does not
   * depend on which one a NiFi version reports.
   */
  private static boolean isRuntimeFailure(String level, String message) {
    return "ERROR".equalsIgnoreCase(level)
        || (isWarnLevel(level)
            && message.matches(
                "(?is).*\\b(error|failed|exception|connection refused|unable to connect|yielding)\\b.*"));
  }

  private static boolean isWarnLevel(String level) {
    return "WARN".equalsIgnoreCase(level) || "WARNING".equalsIgnoreCase(level);
  }

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
      awaitProcessorsRunning(pgId);
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
    this.token = tokenProvider.getToken();
    return token;
  }

  /**
   * Returns the root process-group id, self-healing the one authorization gap that a fresh
   * OIDC-secured NiFi always has. NiFi's initial-admin seeding grants the config-adapter service
   * account the global policies (/flow, /controller, /policies, /tenants) but NOT read/write on the
   * root canvas, so the very first read returns 403. On that 403 we grant the missing root policies
   * — using the global rights the account already holds — and return the id resolved while doing
   * so. On a NiFi that already has the policies (persistent state, later deploys) the first read
   * succeeds and nothing is provisioned.
   */
  String getRootProcessGroupId() throws FatalAdapterException, RetryableAdapterException {
    try (Response response =
        sendAuthorized(() -> execute(authorized(API + "/process-groups/root").get().build()))) {
      if (response.code() != HttpURLConnection.HTTP_FORBIDDEN) {
        check(response, "root process group");
        return requireId(
            readTree(response, "root process group").path("id").asText(), "root process group");
      }
    } catch (IOException e) {
      throw network("root process group", e);
    }
    LOG.info("NiFi denied root process-group access (403) — provisioning service-account policies");
    return ensureRootAccess();
  }

  /**
   * Grants the authenticated service account read+write on the root process group and its data, and
   * returns the root process-group id. Mirrors the one-time canvas grant an operator would
   * otherwise perform by hand. Idempotent: re-running against a NiFi that already has the policies
   * re-adds an already-present user. {@code synchronized} so concurrent deploys hitting the initial
   * 403 provision once, not in a race. A failure to provision (e.g. the account lacks the global
   * /policies right) propagates from {@link #grantUserPolicy}.
   */
  private synchronized String ensureRootAccess()
      throws FatalAdapterException, RetryableAdapterException {
    String userId = currentUserId();
    String rootId = flowRootProcessGroupId();
    for (String resource : List.of("/process-groups/" + rootId, "/data/process-groups/" + rootId)) {
      for (String action : List.of("read", "write")) {
        LOG.debug("Granting {} on {} to the config-adapter service account", action, resource);
        grantUserPolicy(userId, resource, action);
      }
    }
    LOG.info("Ensured NiFi root process-group access for the config-adapter service account");
    return rootId;
  }

  /** Resolves the NiFi user id of the currently-authenticated identity. */
  private String currentUserId() throws FatalAdapterException, RetryableAdapterException {
    String identity = getJson(API + "/flow/current-user", "current user").path("identity").asText();
    if (identity.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_AUTH_ERROR,
          "NiFi returned no identity for the current token — check the OIDC token's claims");
    }
    for (JsonNode user : getJson(API + "/tenants/users", "list users").path("users")) {
      if (identity.equals(user.path("component").path("identity").asText())) {
        return requireId(user.path("id").asText(), "current user");
      }
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_AUTH_ERROR,
        "authenticated identity '"
            + identity
            + "' has no NiFi user — is INITIAL_ADMIN_IDENTITY set to this service account?");
  }

  /**
   * The root process-group id, read via the /flow endpoint (needs only the global /flow policy).
   */
  private String flowRootProcessGroupId() throws FatalAdapterException, RetryableAdapterException {
    JsonNode body = getJson(API + "/flow/process-groups/root", "root process group (flow)");
    return requireId(
        body.path("processGroupFlow").path("id").asText(), "root process group (flow)");
  }

  /** Ensures the given user has an access policy for {@code action} on {@code resource}. */
  private void grantUserPolicy(String userId, String resource, String action)
      throws FatalAdapterException, RetryableAdapterException {
    Optional<JsonNode> existing = getPolicy(action, resource);
    if (existing.isPresent()) {
      addUserToPolicy(existing.get(), userId);
    } else {
      createPolicyWithUser(resource, action, userId);
    }
  }

  /** Returns the access policy for {@code action}/{@code resource}, or empty if none exists yet. */
  private Optional<JsonNode> getPolicy(String action, String resource)
      throws FatalAdapterException, RetryableAdapterException {
    try (Response response =
        sendAuthorized(
            () -> execute(authorized(API + "/policies/" + action + resource).get().build()))) {
      if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return Optional.empty();
      }
      check(response, "read access policy");
      return Optional.of(readTree(response, "read access policy"));
    } catch (IOException e) {
      throw network("read access policy", e);
    }
  }

  /**
   * Adds the user to an existing policy (no-op if already present). PUT replaces the whole policy
   * component, so BOTH the existing users AND user groups are carried over verbatim — otherwise
   * adding the service-account user would silently drop an admin group already granted on the root
   * canvas.
   */
  private void addUserToPolicy(JsonNode policy, String userId)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode component = policy.path("component");
    ArrayNode users = mapper.createArrayNode();
    boolean present = false;
    for (JsonNode user : component.path("users")) {
      String id = user.path("id").asText();
      users.add(mapper.createObjectNode().put("id", id));
      present |= userId.equals(id);
    }
    if (present) {
      return;
    }
    users.add(mapper.createObjectNode().put("id", userId));
    String policyId = requireId(policy.path("id").asText(), "access policy");
    ObjectNode body = mapper.createObjectNode();
    ObjectNode revision = body.putObject("revision");
    revision.put("version", requireRevisionVersion(policy, "access policy"));
    revision.put("clientId", CLIENT_ID);
    ObjectNode newComponent = body.putObject("component");
    newComponent.put("id", policyId);
    newComponent.put("resource", component.path("resource").asText());
    newComponent.put("action", component.path("action").asText());
    newComponent.set("users", users);
    // Preserve any group grants unchanged — we only add a user, never touch groups.
    JsonNode userGroups = component.path("userGroups");
    if (userGroups.isArray()) {
      newComponent.set("userGroups", userGroups.deepCopy());
    }
    put(API + "/policies/" + policyId, body, "add user to access policy");
  }

  /** Creates a new access policy granting the given user {@code action} on {@code resource}. */
  private void createPolicyWithUser(String resource, String action, String userId)
      throws FatalAdapterException, RetryableAdapterException {
    ObjectNode body = mapper.createObjectNode();
    ObjectNode revision = body.putObject("revision");
    revision.put("version", 0);
    revision.put("clientId", CLIENT_ID);
    ObjectNode component = body.putObject("component");
    component.put("resource", resource);
    component.put("action", action);
    component.putArray("users").add(mapper.createObjectNode().put("id", userId));
    try (Response response =
        sendAuthorized(() -> execute(authorized(API + "/policies").post(jsonBody(body)).build()))) {
      check(response, "create access policy");
    } catch (IOException e) {
      throw network("create access policy", e);
    }
  }

  Optional<ProcessGroupRef> findProcessGroupByName(String rootId, String name)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode body = getJson(API + "/flow/process-groups/" + rootId, "list process groups");
    for (JsonNode group : body.path("processGroupFlow").path("flow").path("processGroups")) {
      if (name.equals(group.path("component").path("name").asText())) {
        return Optional.of(
            new ProcessGroupRef(
                group.path("id").asText(), requireRevisionVersion(group, "process group " + name)));
      }
    }
    return Optional.empty();
  }

  String uploadSnapshot(String rootId, String pgName, String snapshotJson)
      throws FatalAdapterException, RetryableAdapterException {
    byte[] body = snapshotJson.getBytes(StandardCharsets.UTF_8);
    try (Response response =
        sendAuthorized(() -> execute(uploadMultipartRequest(rootId, pgName, body)))) {
      check(response, "upload snapshot");
      return requireId(
          readTree(response, "uploaded process group").path("id").asText(),
          "uploaded process group");
    } catch (IOException e) {
      throw network("upload snapshot", e);
    }
  }

  /**
   * Builds a FRESH multipart body for each call, from the snapshot bytes rather than a stream — the
   * {@link #sendAuthorized} 401-replay rebuilds the request, so a body that could only be written
   * once would upload empty on retry.
   */
  private Request uploadMultipartRequest(String rootId, String pgName, byte[] body) {
    RequestBody multipart =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("id", rootId)
            .addFormDataPart("groupName", pgName)
            .addFormDataPart("positionX", "0")
            .addFormDataPart("positionY", "0")
            .addFormDataPart("clientId", CLIENT_ID)
            .addFormDataPart("file", pgName + ".json", RequestBody.create(body, OkHttpJson.JSON))
            .build();
    return authorized(API + "/process-groups/" + rootId + "/process-groups/upload")
        .post(multipart)
        .build();
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
    boolean awaitingEnabled = "ENABLED".equals(state);
    boolean allInState = true;
    for (JsonNode service : services) {
      JsonNode component = service.path("component");
      // A service whose configuration is INVALID will never reach ENABLED, so polling for it is
      // pointless: fail fast and FATALLY (a retryable timeout would loop forever under redelivery).
      if (awaitingEnabled && "INVALID".equals(component.path("validationStatus").asText())) {
        String name = component.path("name").asText();
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_FLOW_ERROR,
            "controller service '"
                + name
                + "' is INVALID and will never enable: "
                + validationErrors(component)
                + provisioningHint(name));
      }
      if (!state.equals(component.path("state").asText())) {
        allInState = false;
      }
    }
    return allInState;
  }

  /**
   * Joins a controller service's NiFi validation error messages into a single diagnostic string.
   */
  private static String validationErrors(JsonNode component) {
    JsonNode errors = component.path("validationErrors");
    if (!errors.isArray() || errors.isEmpty()) {
      return "no validation detail reported";
    }
    StringBuilder joined = new StringBuilder();
    for (JsonNode error : errors) {
      if (joined.length() > 0) {
        joined.append("; ");
      }
      joined.append(error.asText());
    }
    return joined.toString();
  }

  /**
   * Points at the deployment-owned parameter context behind the MQTT SSL Context Service. Where the
   * deployment has not populated it, the context exists but holds no password and NiFi reports an
   * unrelated-looking truststore-password error.
   */
  private String provisioningHint(String serviceName) {
    if (!MqttSourceStage.MQTT_SSL_CONTEXT_SERVICE.equals(serviceName)
        || !mqttTruststore.hasPasswordParameter()) {
      return "";
    }
    return " — check that parameter context '"
        + mqttTruststore.parameterContext()
        + "' provides a value for the sensitive parameter '"
        + mqttTruststore.passwordParameter()
        + "'";
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
    for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
      if (controllerServicesAllInState(pgId, state, emptyMeansReady)) {
        return;
      }
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RetryableAdapterException(
            AdapterErrorCode.NIFI_ERROR, e, "interrupted while waiting for controller services");
      }
    }
    throw new RetryableAdapterException(
        AdapterErrorCode.NIFI_ERROR,
        "controller services did not reach "
            + state
            + " state in time; current states: "
            + describeServiceStates(pgId));
  }

  /** Lists each controller service as {@code name=state(validationStatus)} for diagnostics. */
  private String describeServiceStates(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode services =
        getJson(API + "/flow/process-groups/" + pgId + "/controller-services", "list services")
            .path("controllerServices");
    StringBuilder description = new StringBuilder();
    for (JsonNode service : services) {
      JsonNode component = service.path("component");
      if (description.length() > 0) {
        description.append(", ");
      }
      description
          .append(component.path("name").asText())
          .append('=')
          .append(component.path("state").asText())
          .append('(')
          .append(component.path("validationStatus").asText())
          .append(')');
    }
    return description.toString();
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

  /**
   * Blocks until every processor in the group is RUNNING, failing fast if any is INVALID. Starting
   * the group only returns the REST status of the bulk request — it does not confirm the processors
   * actually reached a valid, running state. Without this a processor left INVALID (e.g. a cron
   * that passed the field-count check but is syntactically wrong) would make the saga report
   * success while the flow never runs. Mirrors {@link #awaitControllerServicesState}.
   */
  private void awaitProcessorsRunning(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
      if (processorsAllRunning(pgId)) {
        return;
      }
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RetryableAdapterException(
            AdapterErrorCode.NIFI_ERROR, e, "interrupted while waiting for processors to start");
      }
    }
    throw new RetryableAdapterException(
        AdapterErrorCode.NIFI_ERROR,
        "processors did not reach RUNNING state in time; current states: "
            + describeProcessorStates(pgId));
  }

  /**
   * Whether every processor in the group is RUNNING. An INVALID processor will never run, so
   * polling for it is pointless: fail fast and FATALLY (a retryable timeout would loop forever
   * under redelivery) with its NiFi validation errors. An empty processor list means the flow has
   * not materialized yet — keep polling.
   */
  private boolean processorsAllRunning(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode processors =
        getJson(API + "/process-groups/" + pgId + "/processors", "list processors")
            .path("processors");
    if (processors.isEmpty()) {
      return false;
    }
    boolean allRunning = true;
    for (JsonNode processor : processors) {
      JsonNode component = processor.path("component");
      if ("INVALID".equals(component.path("validationStatus").asText())) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_FLOW_ERROR,
            "processor '"
                + component.path("name").asText()
                + "' is INVALID and will never run: "
                + validationErrors(component));
      }
      if (!"Running".equals(processor.path("status").path("runStatus").asText())) {
        allRunning = false;
      }
    }
    return allRunning;
  }

  /** Lists each processor as {@code name=runStatus(validationStatus)} for diagnostics. */
  private String describeProcessorStates(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    JsonNode processors =
        getJson(API + "/process-groups/" + pgId + "/processors", "list processors")
            .path("processors");
    StringBuilder description = new StringBuilder();
    for (JsonNode processor : processors) {
      if (description.length() > 0) {
        description.append(", ");
      }
      description
          .append(processor.path("component").path("name").asText())
          .append('=')
          .append(processor.path("status").path("runStatus").asText())
          .append('(')
          .append(processor.path("component").path("validationStatus").asText())
          .append(')');
    }
    return description.toString();
  }

  private void stopAndDeleteProcessGroup(ProcessGroupRef group)
      throws FatalAdapterException, RetryableAdapterException {
    ObjectNode stop = mapper.createObjectNode();
    stop.put("id", group.id());
    stop.put("state", "STOPPED");
    stop.put("disconnectedNodeAcknowledged", false);
    put(API + "/flow/process-groups/" + group.id(), stop, "stop process group");

    // Stopping is asynchronous: NiFi rejects the delete below with HTTP 409 ("Processor is
    // running")
    // while any processor is still scheduled or draining a thread (e.g. ConsumeMQTT closing its
    // broker connection). Wait until the group is fully stopped before deleting.
    awaitProcessGroupStopped(group.id());

    // A process group cannot be deleted while its controller services are enabled.
    disableControllerServices(group.id());
    awaitControllerServicesState(group.id(), "DISABLED", true);

    HttpUrl deleteUrl =
        url(API + "/process-groups/" + group.id())
            .newBuilder()
            .addQueryParameter("version", String.valueOf(group.version()))
            .addQueryParameter("clientId", CLIENT_ID)
            .build();
    try (Response response =
        sendAuthorized(() -> execute(authorized(deleteUrl).delete().build()))) {
      if (response.code() != HttpURLConnection.HTTP_NOT_FOUND) {
        check(response, "delete process group");
      }
    } catch (IOException e) {
      throw network("delete process group", e);
    }
  }

  /**
   * Blocks until the process group is fully stopped — no scheduled processors ({@code runningCount}
   * == 0) and no active threads. Stopping a group is asynchronous, and NiFi refuses to delete it
   * while a processor is still running or draining (HTTP 409), so this must complete before the
   * delete in {@link #stopAndDeleteProcessGroup}.
   *
   * @param pgId the process-group id
   * @throws FatalAdapterException on a non-retryable error
   * @throws RetryableAdapterException if the group does not stop in time
   */
  private void awaitProcessGroupStopped(String pgId)
      throws FatalAdapterException, RetryableAdapterException {
    for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
      JsonNode group = getJson(API + "/process-groups/" + pgId, "read process group state");
      int running = group.path("runningCount").asInt(0);
      int activeThreads =
          group.path("status").path("aggregateSnapshot").path("activeThreadCount").asInt(0);
      if (running == 0 && activeThreads == 0) {
        return;
      }
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RetryableAdapterException(
            AdapterErrorCode.NIFI_ERROR, e, "interrupted while waiting for process group to stop");
      }
    }
    throw new RetryableAdapterException(
        AdapterErrorCode.NIFI_ERROR, "process group " + pgId + " did not stop in time");
  }

  // ─── HTTP helpers ──────────────────────────────────────────────────────────

  private JsonNode getJson(String path, String description)
      throws FatalAdapterException, RetryableAdapterException {
    try (Response response = sendAuthorized(() -> execute(authorized(path).get().build()))) {
      check(response, description);
      return readTree(response, description);
    } catch (IOException e) {
      throw network(description, e);
    }
  }

  /**
   * Parses a JSON response body, mapping malformed JSON to a fatal error. An empty body yields an
   * empty object (not {@code null}), so callers' {@code path(...)} chains degrade to a clean
   * "returned no id" error instead of a {@link NullPointerException}. A non-JSON {@link
   * IOException} (e.g. the connection dropping mid-read) is left undeclared-here, to propagate as
   * the network error the caller's outer catch already classifies it as.
   */
  private JsonNode readTree(Response response, String description)
      throws FatalAdapterException, IOException {
    try {
      JsonNode node = mapper.readTree(response.body().string());
      return node == null ? mapper.createObjectNode() : node;
    } catch (JsonProcessingException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_FLOW_ERROR, e, description);
    }
  }

  private void put(String path, JsonNode body, String description)
      throws FatalAdapterException, RetryableAdapterException {
    try (Response response =
        sendAuthorized(() -> execute(authorized(path).put(jsonBody(body)).build()))) {
      check(response, description);
    } catch (IOException e) {
      throw network(description, e);
    }
  }

  /** Serializes a JSON node to a request body. */
  private static RequestBody jsonBody(JsonNode body) {
    return RequestBody.create(body.toString(), OkHttpJson.JSON);
  }

  /** Builds the request. Declares {@link IOException} so the checked network failure propagates. */
  @FunctionalInterface
  private interface RequestSupplier {
    Response get() throws IOException;
  }

  /**
   * Executes an authorized request and, if NiFi answers 401 (the bearer token expired mid-saga),
   * refreshes the token once and replays it — the replayed request rebuilds the {@code
   * Authorization} header from the refreshed {@link #token}.
   */
  private Response sendAuthorized(RequestSupplier request)
      throws FatalAdapterException, RetryableAdapterException, IOException {
    Response response = request.get();
    if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
      response.close();
      LOG.info("NiFi returned 401 — refreshing token and retrying once");
      this.token = tokenProvider.refreshToken();
      response = request.get();
      if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        response.close();
        // A freshly-refreshed Keycloak token is still rejected. This is transient while NiFi's OIDC
        // filter is still initialising at boot (kept retryable so a redelivery succeeds), but if it
        // persists it is an OIDC misconfiguration NiFi cannot accept — NIFI_SECURITY_USER_OIDC
        // audience/issuer not matching the token, or a revoked client. The message names that so a
        // looping deploy is diagnosable rather than an opaque "HTTP 401".
        throw new RetryableAdapterException(
            AdapterErrorCode.NIFI_ERROR,
            "NiFi rejected a freshly-refreshed OIDC token (HTTP 401) — transient during NiFi's OIDC"
                + " startup; if persistent, check NiFi's oidc audience/issuer match the Keycloak"
                + " token and the client is not revoked");
      }
    }
    return response;
  }

  private HttpUrl url(String path) {
    return HttpUrl.get(baseUrl + path);
  }

  private Request.Builder authorized(HttpUrl url) {
    return new Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + token);
  }

  private Request.Builder authorized(String path) {
    return authorized(url(path));
  }

  /** Executes an HTTP request, letting OkHttp's checked {@link IOException} propagate. */
  private Response execute(Request request) throws IOException {
    return client.newCall(request).execute();
  }

  private void check(Response response, String description)
      throws FatalAdapterException, RetryableAdapterException {
    int status = response.code();
    if (status >= 200 && status < 300) {
      return;
    }
    String body = safeBody(response);
    // 5xx is transient; so are 408 (request timeout), 409 (a component still starting, or a
    // concurrent edit that moved the optimistic-lock revision) and 429 (rate limited). These are
    // retryable — a permanent FatalAdapterException here would abort and roll back a deploy that a
    // redelivery could complete.
    if (status >= 500 || status == 408 || status == 409 || status == 429) {
      throw new RetryableAdapterException(
          AdapterErrorCode.NIFI_ERROR, description + ": HTTP " + status + " — " + body);
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_FLOW_ERROR, description + ": HTTP " + status + " — " + body);
  }

  private static String safeBody(Response response) {
    try {
      return response.body().string();
    } catch (IOException e) {
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

  /**
   * Returns the optimistic-locking revision version from a component entity, requiring the field to
   * be present. {@code asLong()} alone yields {@code 0} for an absent field — indistinguishable
   * from a legitimate version {@code 0} — which would later be sent as a stale lock version and
   * rejected with a 409. A malformed-but-200 response is surfaced here instead.
   */
  private static long requireRevisionVersion(JsonNode entity, String description)
      throws FatalAdapterException {
    JsonNode version = entity.path("revision").path("version");
    if (!version.isNumber()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR, "NiFi returned no revision version for " + description);
    }
    return version.asLong();
  }

  private static RetryableAdapterException network(String description, IOException e) {
    return new RetryableAdapterException(
        AdapterErrorCode.NETWORK_ERROR, "nifi", description + ": " + e.getMessage());
  }

  @Override
  public void close() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
  }
}

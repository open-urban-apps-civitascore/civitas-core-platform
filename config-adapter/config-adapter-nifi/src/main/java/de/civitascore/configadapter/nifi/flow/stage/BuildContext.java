/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The mutable state of one flow build plus the only primitives that can mint components into it.
 * Handed to each stage's build half; the {@code loadProcessor}/{@code addControllerService} pair is
 * the sole path to the classpath fragment loader, so only {@link Fragment} constants can ever
 * become flow components.
 *
 * <p>All component ids are name-based UUIDs over stable seeds — they are the redeploy-idempotency
 * key against a live NiFi. Array append order and property-put order are byte-significant: the
 * snapshot serializes insertion-ordered.
 */
public final class BuildContext {

  private static final String CS_TOKEN_PREFIX = "${CS:";

  private final ObjectMapper mapper;
  private final String pgId;
  private final FlowBuildSpec spec;
  private final ArrayNode processors;
  private final ArrayNode controllerServices;
  private final ArrayNode connections;
  private final Map<String, String> csIdByName = new LinkedHashMap<>();

  public BuildContext(
      ObjectMapper mapper,
      String pgId,
      FlowBuildSpec spec,
      ArrayNode processors,
      ArrayNode controllerServices,
      ArrayNode connections) {
    this.mapper = mapper;
    this.pgId = pgId;
    this.spec = spec;
    this.processors = processors;
    this.controllerServices = controllerServices;
    this.connections = connections;
  }

  public String pgId() {
    return pgId;
  }

  public FlowBuildSpec spec() {
    return spec;
  }

  // ─── Component minting ──────────────────────────────────────────────────────

  public Processor loadProcessor(Fragment fragment, String outRelationship)
      throws FatalAdapterException {
    return loadProcessor(fragment, outRelationship, "");
  }

  /**
   * Loads a processor fragment with a deterministic id. The {@code discriminator} keeps ids unique
   * when the same fragment is instantiated more than once (e.g. one UpdateRecord per strategy).
   */
  public Processor loadProcessor(Fragment fragment, String outRelationship, String discriminator)
      throws FatalAdapterException {
    ObjectNode node = loadFragment(fragment);
    String seed =
        pgId
            + ":proc:"
            + fragment.resource()
            + (discriminator.isEmpty() ? "" : ":" + discriminator);
    String id = deterministicId(seed);
    node.put("identifier", id);
    node.put("groupIdentifier", pgId);
    resolveControllerServiceReferences(node);
    return new Processor(node, id, outRelationship);
  }

  /**
   * Loads a controller-service fragment, registers it under its friendly name (the post-upload
   * match key for sensitive properties), applies its non-sensitive properties from the spec, and
   * appends it to the flow.
   */
  public String addControllerService(Fragment fragment, String friendlyName)
      throws FatalAdapterException {
    ObjectNode node = loadFragment(fragment);
    node.put("identifier", deterministicId(pgId + ":cs:" + friendlyName));
    node.put("groupIdentifier", pgId);
    // stamp the friendly name so the REST client can match sensitive properties by name post-upload
    node.put("name", friendlyName);
    ObjectNode props = (ObjectNode) node.get("properties");
    spec.controllerServiceProperties().getOrDefault(friendlyName, Map.of()).forEach(props::put);
    controllerServices.add(node);
    csIdByName.put(friendlyName, node.get("identifier").asText());
    return node.get("identifier").asText();
  }

  // ─── Graph emission ─────────────────────────────────────────────────────────

  public void addProcessor(Processor processor) {
    processors.add(processor.node());
  }

  /** Connects two chain processors over the source's own out relationship. */
  public void addChainConnection(Processor from, Processor to) {
    connections.add(connection(from, to, from.outRelationship()));
  }

  public void addConnection(Processor from, Processor to, String relationship) {
    connections.add(connection(from, to, relationship));
  }

  /**
   * Routes a processor's single {@code failure} relationship to the error sink (no silent drop).
   */
  public void routeFailure(Processor processor, Processor errorSink) {
    removeAutoTerminated(processor, "failure");
    addConnection(processor, errorSink, "failure");
  }

  /**
   * Marks a relationship as auto-terminated (idempotent). A terminal processor's unconnected
   * relationship would otherwise leave the processor invalid — NiFi silently skips invalid
   * processors on process-group start, so FlowFiles queue in front of them forever.
   */
  public static void addAutoTerminated(Processor processor, String relationship) {
    ArrayNode terminated = processor.node().withArray("autoTerminatedRelationships");
    for (JsonNode existing : terminated) {
      if (relationship.equals(existing.asText())) {
        return;
      }
    }
    terminated.add(relationship);
  }

  /**
   * Removes a relationship from a processor's {@code autoTerminatedRelationships}, if present —
   * NiFi forbids a relationship being both auto-terminated and connected.
   */
  public static void removeAutoTerminated(Processor processor, String relationship) {
    JsonNode auto = processor.node().get("autoTerminatedRelationships");
    if (auto instanceof ArrayNode array) {
      for (int i = array.size() - 1; i >= 0; i--) {
        if (relationship.equals(array.get(i).asText())) {
          array.remove(i);
        }
      }
    }
  }

  public static void setProp(Processor processor, String key, String value) {
    ((ObjectNode) processor.node().get("properties")).put(key, value);
  }

  /**
   * Switches the entry processor to cron-driven scheduling. A {@code null} cron leaves the source
   * fragment's built-in schedule. Only the source's schedule drives the flow — downstream
   * processors stay timer-driven and run when FlowFiles arrive in their queues.
   */
  public static void applySchedule(Processor source, String cron) {
    if (cron == null) {
      return;
    }
    source.node().put("schedulingStrategy", "CRON_DRIVEN");
    source.node().put("schedulingPeriod", cron);
  }

  // ─── Internals ──────────────────────────────────────────────────────────────

  private ObjectNode connection(Processor source, Processor destination, String relationship) {
    ObjectNode connection = mapper.createObjectNode();
    connection.put(
        "identifier",
        deterministicId(
            pgId + ":conn:" + source.id() + ":" + relationship + "->" + destination.id()));
    ObjectNode src = connection.putObject("source");
    src.put("id", source.id());
    src.put("type", "PROCESSOR");
    src.put("groupId", pgId);
    ObjectNode dst = connection.putObject("destination");
    dst.put("id", destination.id());
    dst.put("type", "PROCESSOR");
    dst.put("groupId", pgId);
    connection.put("groupIdentifier", pgId);
    ArrayNode rels = connection.putArray("selectedRelationships");
    rels.add(relationship);
    connection.put("backPressureObjectThreshold", 10_000);
    connection.put("backPressureDataSizeThreshold", "1 GB");
    connection.put("flowFileExpiration", "0 sec");
    connection.put("loadBalanceStrategy", "DO_NOT_LOAD_BALANCE");
    connection.put("loadBalanceCompression", "DO_NOT_COMPRESS");
    connection.put("labelIndex", 0);
    connection.put("zIndex", 0);
    connection.putArray("prioritizers");
    connection.putArray("bends");
    connection.put("componentType", "CONNECTION");
    return connection;
  }

  /** Replaces {@code ${CS:Name}} property tokens with the assigned controller-service id. */
  private void resolveControllerServiceReferences(ObjectNode component)
      throws FatalAdapterException {
    JsonNode properties = component.get("properties");
    if (!(properties instanceof ObjectNode props)) {
      return;
    }
    var fields = props.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode value = entry.getValue();
      if (value.isTextual() && value.asText().startsWith(CS_TOKEN_PREFIX)) {
        String name =
            value.asText().substring(CS_TOKEN_PREFIX.length(), value.asText().length() - 1);
        String id = csIdByName.get(name);
        if (id == null) {
          throw new FatalAdapterException(
              AdapterErrorCode.NIFI_FLOW_ERROR, "unresolved controller-service reference: " + name);
        }
        props.put(entry.getKey(), id);
      }
    }
  }

  private ObjectNode loadFragment(Fragment fragment) throws FatalAdapterException {
    String resource = "fragments/" + fragment.resource() + ".json";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_FLOW_ERROR, "missing NiFi component fragment: " + resource);
      }
      return (ObjectNode) mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_FLOW_ERROR, e, "reading " + resource);
    }
  }

  private static String deterministicId(String seed) {
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
  }
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.nifi.frost.batch.BatchDocument;
import de.civitascore.nifi.frost.batch.BatchResponse;
import de.civitascore.nifi.frost.batch.RecordOutcome;
import de.civitascore.nifi.frost.batch.RecordPlan;
import de.civitascore.nifi.frost.batch.ResponseDivision;
import de.civitascore.nifi.frost.client.FrostBatchClient;
import de.civitascore.nifi.frost.port.PortPlanner;
import de.civitascore.nifi.frost.port.RecordRejectedException;
import de.civitascore.nifi.frost.port.SinkPort;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.web.client.provider.api.WebClientServiceProvider;

/**
 * Writes records into a FROST SensorThings server with one JSON batch request per invocation.
 *
 * <p>One FlowFile is one record and one atomicity group. The lookup and the write of a record live
 * in that group, so a defective record stops only itself, and the find-or-create decision is made
 * by the server through the {@code if} condition of the batch extension rather than by a graph of
 * processors.
 *
 * <p>The processor sends the batch itself. It holds the FlowFiles and needs the answer to route
 * them, which a downstream request processor could not give back.
 */
@Tags({"FROST", "SensorThings", "STA", "IoT", "batch", "civitas"})
@CapabilityDescription(
    "Writes records into a FROST SensorThings server. One record becomes one atomicity group of a"
        + " JSON batch request; the port decides which entities the group writes and which"
        + " references it resolves.")
@InputRequirement(Requirement.INPUT_REQUIRED)
@WritesAttributes({
  @WritesAttribute(
      attribute = PutFrostRecord.ERROR_ENTITY,
      description = "The SensorThings entity the record failed on."),
  @WritesAttribute(
      attribute = PutFrostRecord.ERROR_STATUS,
      description =
          "The HTTP status the failing sub-request answered, or 0 when no request was sent."),
  @WritesAttribute(
      attribute = PutFrostRecord.ERROR_MESSAGE,
      description = "The reason FROST gave, or the reason the processor derived.")
})
public class PutFrostRecord extends AbstractProcessor {

  public static final String ERROR_ENTITY = "frost.error.entity";
  public static final String ERROR_STATUS = "frost.error.status";
  public static final String ERROR_MESSAGE = "frost.error.message";

  /**
   * The largest number of records one request may carry. The SensorThings specification names no
   * limit; what limits it is the request size the server accepts and the time one transaction may
   * take. The cap exists so that a collection window cannot grow a request without a bound.
   */
  private static final int MAXIMUM_RECORDS_PER_REQUEST = 100;

  static final PropertyDescriptor PORT =
      new PropertyDescriptor.Builder()
          .name("Port")
          .displayName("Port")
          .description(
              "The logic the sink applies. The port defines the expected data model, the write"
                  + " operation and the reference condition. There is no default: a sink without a"
                  + " port is not configured.")
          .required(true)
          .allowableValues(labels())
          .build();

  static final PropertyDescriptor BASE_URL =
      new PropertyDescriptor.Builder()
          .name("FROST Base URL")
          .displayName("FROST Base URL")
          .description("The SensorThings service root, without the batch path.")
          .required(true)
          .addValidator(StandardValidators.URL_VALIDATOR)
          .build();

  static final PropertyDescriptor PROJECT_ID =
      new PropertyDescriptor.Builder()
          .name("FROST Project Id")
          .displayName("FROST Project Id")
          .description(
              "The Dataset's FROST project. It scopes the lookups and the writes, so that a"
                  + " reference of one Dataset cannot resolve to the entity of another.")
          .required(true)
          .addValidator(StandardValidators.POSITIVE_LONG_VALIDATOR)
          .build();

  static final PropertyDescriptor WEB_CLIENT_SERVICE =
      new PropertyDescriptor.Builder()
          .name("Web Client Service Provider")
          .displayName("Web Client Service Provider")
          .description(
              "The HTTP client the batch request uses. Its properties carry the TLS material, the"
                  + " proxy and the timeouts.")
          .required(true)
          .identifiesControllerService(WebClientServiceProvider.class)
          .build();

  static final PropertyDescriptor USERNAME =
      new PropertyDescriptor.Builder()
          .name("Basic Auth Username")
          .displayName("Basic Auth Username")
          .description("The user FROST knows. Leave empty where FROST needs no authentication.")
          .required(false)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  static final PropertyDescriptor PASSWORD =
      new PropertyDescriptor.Builder()
          .name("Basic Auth Password")
          .displayName("Basic Auth Password")
          .description("The password of that user.")
          .required(false)
          .sensitive(true)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  static final PropertyDescriptor RECORDS_PER_REQUEST =
      new PropertyDescriptor.Builder()
          .name("Records per Request")
          .displayName("Records per Request")
          .description(
              "How many records one batch request may carry. One record per request sends every"
                  + " measurement as it arrives; a higher number trades that promptness for fewer"
                  + " requests.")
          .required(true)
          .defaultValue("1")
          .addValidator(
              StandardValidators.createLongValidator(1, MAXIMUM_RECORDS_PER_REQUEST, true))
          .build();

  static final Relationship SUCCESS =
      new Relationship.Builder().name("success").description("The record reached FROST.").build();

  static final Relationship FAILURE =
      new Relationship.Builder()
          .name("failure")
          .description(
              "The record was rejected. Another attempt with the same data would be rejected"
                  + " again, so the cause is in the record or in the data it refers to.")
          .build();

  static final Relationship RETRY =
      new Relationship.Builder()
          .name("retry")
          .description(
              "The request did not complete, or FROST answered that it could not answer now. The"
                  + " record is unchanged and can be sent again.")
          .build();

  private static final List<PropertyDescriptor> PROPERTIES =
      List.of(
          PORT, BASE_URL, PROJECT_ID, WEB_CLIENT_SERVICE, USERNAME, PASSWORD, RECORDS_PER_REQUEST);

  private static final Set<Relationship> RELATIONSHIPS = Set.of(SUCCESS, FAILURE, RETRY);

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return PROPERTIES;
  }

  @Override
  public Set<Relationship> getRelationships() {
    return RELATIONSHIPS;
  }

  @Override
  protected Collection<ValidationResult> customValidate(ValidationContext context) {
    List<ValidationResult> results = new ArrayList<>();
    boolean user = context.getProperty(USERNAME).isSet();
    boolean secret = context.getProperty(PASSWORD).isSet();
    if (user != secret) {
      // Half a credential authenticates nothing and fails at the first request, with a status that
      // says the server rejected the user rather than that the flow is configured wrong.
      results.add(
          new ValidationResult.Builder()
              .subject(USERNAME.getDisplayName())
              .valid(false)
              .explanation("give both the user and the password, or neither")
              .build());
    }
    return results;
  }

  @Override
  public void onTrigger(ProcessContext context, ProcessSession session) {
    List<FlowFile> flowFiles = session.get(context.getProperty(RECORDS_PER_REQUEST).asInteger());
    if (flowFiles.isEmpty()) {
      return;
    }
    Planned planned = plan(context, session, flowFiles);
    if (planned.document().plans().isEmpty()) {
      return;
    }
    send(context, session, planned);
  }

  /**
   * Plans every record, and routes the ones that cannot be planned. A record that reaches no plan
   * never reaches FROST either: a request built from it would be answered with a status that
   * describes the request, not the defect in the record.
   */
  private Planned plan(ProcessContext context, ProcessSession session, List<FlowFile> flowFiles) {
    SinkPort port = SinkPort.of(context.getProperty(PORT).getValue());
    String projectId = context.getProperty(PROJECT_ID).getValue();
    PortPlanner planner = port.planner();

    List<RecordPlan> plans = new ArrayList<>(flowFiles.size());
    Map<Integer, FlowFile> pending = new LinkedHashMap<>();
    for (int index = 0; index < flowFiles.size(); index++) {
      FlowFile flowFile = flowFiles.get(index);
      RecordPlan plan = new RecordPlan(index);
      try {
        planner.plan(readRecord(session, flowFile), plan, projectId);
      } catch (RecordRejectedException e) {
        session.transfer(fail(session, flowFile, e.entity(), 0, e.getMessage()), FAILURE);
        continue;
      } catch (IOException e) {
        session.transfer(
            fail(session, flowFile, "Record", 0, "the record is not a JSON object: " + e), FAILURE);
        continue;
      }
      plans.add(plan);
      pending.put(index, flowFile);
    }
    return new Planned(new BatchDocument(plans), pending);
  }

  /** Sends the document and routes every record by what the answer says happened to it. */
  private void send(ProcessContext context, ProcessSession session, Planned planned) {
    FrostBatchClient client = client(context);
    FrostBatchClient.BatchExchange exchange;
    try {
      exchange = client.send(mapper.writeValueAsBytes(planned.document().toJson(mapper)));
    } catch (IOException e) {
      getLogger().warn("The FROST batch request to {} did not complete", client.batchUri(), e);
      transferAll(session, planned.pending().values(), RETRY, true);
      return;
    }
    if (!exchange.successful()) {
      transferBatchRejection(session, planned.pending().values(), exchange);
      return;
    }

    Map<Integer, RecordOutcome> outcomes;
    try {
      outcomes = ResponseDivision.divide(planned.document(), BatchResponse.of(exchange.body()));
    } catch (IllegalArgumentException e) {
      // A successful status that is not a batch response means the endpoint is not a batch
      // endpoint. Reporting the records as written here would lose every one of them.
      getLogger().error("The answer of {} is not a batch response", client.batchUri(), e);
      transferAll(session, planned.pending().values(), FAILURE, false);
      return;
    }
    route(session, planned.pending(), outcomes);
  }

  private void route(
      ProcessSession session,
      Map<Integer, FlowFile> pending,
      Map<Integer, RecordOutcome> outcomes) {
    for (Map.Entry<Integer, FlowFile> entry : pending.entrySet()) {
      RecordOutcome outcome = outcomes.get(entry.getKey());
      if (outcome == null || outcome.successful()) {
        session.transfer(entry.getValue(), SUCCESS);
      } else {
        session.transfer(
            fail(session, entry.getValue(), outcome.entity(), outcome.status(), outcome.message()),
            FAILURE);
      }
    }
  }

  /** The document to send, and the record each atomicity group belongs to. */
  private record Planned(BatchDocument document, Map<Integer, FlowFile> pending) {}

  private FrostBatchClient client(ProcessContext context) {
    WebClientServiceProvider provider =
        context.getProperty(WEB_CLIENT_SERVICE).asControllerService(WebClientServiceProvider.class);
    return new FrostBatchClient(
        provider.getWebClientService(),
        mapper,
        context.getProperty(BASE_URL).getValue(),
        context.getProperty(USERNAME).getValue(),
        context.getProperty(PASSWORD).getValue());
  }

  private ObjectNode readRecord(ProcessSession session, FlowFile flowFile) throws IOException {
    JsonNode parsed;
    try (InputStream content = session.read(flowFile)) {
      parsed = mapper.readTree(content);
    }
    if (!(parsed instanceof ObjectNode record)) {
      throw new IOException("the content is not a JSON object");
    }
    return record;
  }

  /** Routes every record of a batch the server rejected as a whole. */
  private void transferBatchRejection(
      ProcessSession session,
      Collection<FlowFile> flowFiles,
      FrostBatchClient.BatchExchange exchange) {
    if (exchange.retryable()) {
      getLogger().warn("FROST answered the batch request with {}", exchange.status());
      transferAll(session, flowFiles, RETRY, true);
      return;
    }
    String reason = exchange.body() == null ? "" : exchange.body().toString();
    for (FlowFile flowFile : List.copyOf(flowFiles)) {
      session.transfer(fail(session, flowFile, "Batch", exchange.status(), reason), FAILURE);
    }
  }

  private void transferAll(
      ProcessSession session,
      Collection<FlowFile> flowFiles,
      Relationship relationship,
      boolean penalize) {
    for (FlowFile flowFile : List.copyOf(flowFiles)) {
      session.transfer(penalize ? session.penalize(flowFile) : flowFile, relationship);
    }
  }

  private FlowFile fail(
      ProcessSession session, FlowFile flowFile, String entity, int status, String message) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(ERROR_ENTITY, entity == null ? "" : entity);
    attributes.put(ERROR_STATUS, Integer.toString(status));
    attributes.put(ERROR_MESSAGE, message == null ? "" : message);
    return session.putAllAttributes(flowFile, attributes);
  }

  private static String[] labels() {
    return Arrays.stream(SinkPort.values()).map(SinkPort::label).toArray(String[]::new);
  }
}

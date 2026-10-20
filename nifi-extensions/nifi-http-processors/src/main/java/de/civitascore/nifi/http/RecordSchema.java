/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.DisallowSchemaLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Data structure of an HTTP/REST Data source, which each record of a response must agree with.
 *
 * <p>The validator settings are those of Model Forge, so that the processor accepts what Model
 * Forge accepts as data of the Data structure.
 */
final class RecordSchema {

  static final List<String> NON_VALIDATING_KEYWORDS =
      List.of("x-core-ref", "x-xsd-source", "x-ui-position");

  private static final int REPORTED_VIOLATIONS = 3;

  private static final String NOT_JSON = "the response is not JSON";

  private static final ObjectMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

  private final JsonSchema schema;

  private RecordSchema(JsonSchema schema) {
    this.schema = schema;
  }

  /**
   * Compiles the schema and resolves each of its references.
   *
   * @throws InvalidRecordSchemaException when the text is not JSON, the validator cannot compile
   *     it, or a reference leads to a document that is not vendored
   */
  static RecordSchema compile(String text) throws InvalidRecordSchemaException {
    JsonNode node;
    try {
      node = MAPPER.readTree(text);
    } catch (JacksonException e) {
      throw new InvalidRecordSchemaException("Record Schema is not JSON", e);
    }
    try {
      JsonSchema schema = factory().getSchema(node);
      schema.initializeValidators();
      return new RecordSchema(schema);
    } catch (JsonSchemaException | IllegalArgumentException | StackOverflowError e) {
      throw new InvalidRecordSchemaException(
          "Record Schema cannot be compiled, or refers to a document other"
              + " than a GeoJSON geometry of https://geojson.org/schema/: "
              + e.getClass().getSimpleName(),
          e);
    }
  }

  /**
   * Why the response does not agree, or empty when it does. A JSON array holds one record in each
   * element, a JSON object is one record. The reason names no value of the response: the message of
   * the validator is not used, because some messages quote the value.
   */
  Optional<String> rejection(byte[] body) {
    JsonNode response;
    try {
      response = MAPPER.readTree(body);
    } catch (IOException e) {
      return Optional.of(NOT_JSON);
    }
    if (response == null || response.isMissingNode()) {
      return Optional.of(NOT_JSON);
    }
    if (!response.isArray() && !response.isObject()) {
      return Optional.of(
          "the response is a JSON " + scalarType(response) + ", not an object or an array");
    }
    List<JsonNode> records = new ArrayList<>();
    if (response.isArray()) {
      response.forEach(records::add);
    } else {
      records.add(response);
    }
    return violations(records);
  }

  private Optional<String> violations(List<JsonNode> records) {
    int invalid = 0;
    List<String> reported = new ArrayList<>();
    for (int index = 0; index < records.size(); index++) {
      List<String> recordViolations = recordViolations(records.get(index));
      if (recordViolations.isEmpty()) {
        continue;
      }
      invalid++;
      for (String violation : recordViolations) {
        if (reported.size() < REPORTED_VIOLATIONS) {
          reported.add("record " + index + ": " + violation);
        }
      }
    }
    if (invalid == 0) {
      return Optional.empty();
    }
    return Optional.of(
        invalid
            + " of "
            + records.size()
            + " records violate the Data structure: "
            + String.join("; ", reported));
  }

  private List<String> recordViolations(JsonNode record) {
    if (!record.isObject()) {
      return List.of("type");
    }
    List<String> violations = new ArrayList<>();
    for (ValidationMessage message : schema.validate(record)) {
      violations.add(describe(message));
    }
    return violations;
  }

  private static String scalarType(JsonNode scalar) {
    if (scalar.isTextual()) {
      return "string";
    }
    if (scalar.isNumber()) {
      return "number";
    }
    if (scalar.isBoolean()) {
      return "boolean";
    }
    return "null";
  }

  /** The pointer inside the record and the keyword; for {@code required} the property too. */
  private static String describe(ValidationMessage message) {
    StringBuilder text = new StringBuilder();
    String pointer = pointer(message.getInstanceLocation());
    if (!pointer.isEmpty()) {
      text.append(pointer).append(' ');
    }
    text.append(message.getType());
    if ("required".equals(message.getType()) && message.getProperty() != null) {
      text.append(' ').append(message.getProperty());
    }
    return text.toString();
  }

  /** The JSON Pointer of RFC 6901 for the location inside the record. */
  private static String pointer(JsonNodePath location) {
    StringBuilder pointer = new StringBuilder();
    for (int i = 0; i < location.getNameCount(); i++) {
      String token = String.valueOf(location.getElement(i));
      pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
    }
    return pointer.toString();
  }

  /**
   * A factory of its own for each schema: a factory caches the documents it resolves by their
   * {@code $id}, and the Pipelines of all tenants share the JVM of NiFi.
   */
  private static JsonSchemaFactory factory() {
    JsonMetaSchema.Builder metaSchemaBuilder = JsonMetaSchema.builder(JsonMetaSchema.getV202012());
    NON_VALIDATING_KEYWORDS.forEach(
        keyword -> metaSchemaBuilder.keyword(new NonValidationKeyword(keyword)));
    JsonMetaSchema metaSchema = metaSchemaBuilder.build();
    return JsonSchemaFactory.getInstance(
        SpecVersion.VersionFlag.V202012,
        builder ->
            builder
                .metaSchema(metaSchema)
                .defaultMetaSchemaIri(metaSchema.getIri())
                .schemaLoaders(
                    loaders ->
                        loaders
                            .add(new GeoJsonSchemaLoader())
                            .add(DisallowSchemaLoader.getInstance())));
  }
}

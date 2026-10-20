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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

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

  /** A record ends inside the response, so the tokens after it are no error of the record. */
  private static final ObjectReader RECORD_READER =
      MAPPER.readerFor(JsonNode.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  /** Keywords whose subschema applies to a member of the instance that the schema does not name. */
  private static final Set<String> UNNAMED_MEMBER_KEYWORDS =
      Set.of(
          "additionalProperties",
          "unevaluatedProperties",
          "items",
          "additionalItems",
          "unevaluatedItems",
          "contains");

  /** Keywords followed by a pattern or an index whose subschema applies to one member. */
  private static final Set<String> MATCHED_MEMBER_KEYWORDS =
      Set.of("patternProperties", "prefixItems");

  /** Keywords followed by a property name whose subschema applies to the instance itself. */
  private static final Set<String> NAMED_SELF_KEYWORDS = Set.of("dependentSchemas", "dependencies");

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

  /** The verdict on the response in the content of a FlowFile. */
  Verdict verdict(ProcessSession session, FlowFile response, boolean arrayRequired) {
    AtomicReference<Verdict> verdict = new AtomicReference<>();
    session.read(response, content -> verdict.set(verdict(content, arrayRequired)));
    return verdict.get();
  }

  /**
   * Reads the response one record at a time: a JSON array holds one record in each element, a JSON
   * object is one record unless {@code arrayRequired}. The type comes before the records, so a
   * response of the wrong type gets no record violation. The message of the validator is not used,
   * because some messages quote the value.
   */
  Verdict verdict(InputStream response, boolean arrayRequired) {
    try (JsonParser parser = RECORD_READER.createParser(response)) {
      JsonToken first = parser.nextToken();
      if (first == null) {
        return new Verdict.Blank();
      }
      Verdict verdict =
          switch (first) {
            case START_ARRAY -> elements(parser);
            case START_OBJECT -> {
              if (arrayRequired) {
                parser.skipChildren();
                yield new Verdict.WrongType("object", "an array");
              }
              Violations violations = new Violations();
              violations.add(recordViolations(parser, first));
              yield violations.verdict();
            }
            default ->
                new Verdict.WrongType(
                    scalarType(first), arrayRequired ? "an array" : "an object or an array");
          };
      return parser.nextToken() == null ? verdict : new Verdict.Rejected(NOT_JSON);
    } catch (IOException e) {
      // Also CharConversionException, which Jackson throws for bytes that look like UTF-32. NiFi
      // throws a failed read of the content unchecked, so it does not end up here.
      return new Verdict.Rejected(NOT_JSON);
    }
  }

  private Verdict elements(JsonParser parser) throws IOException {
    Violations violations = new Violations();
    for (JsonToken token = parser.nextToken();
        token != JsonToken.END_ARRAY;
        token = parser.nextToken()) {
      violations.add(recordViolations(parser, token));
    }
    return violations.verdict();
  }

  private List<String> recordViolations(JsonParser parser, JsonToken token) throws IOException {
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren();
      return List.of("type");
    }
    JsonNode record = RECORD_READER.readTree(parser);
    List<String> violations = new ArrayList<>();
    for (ValidationMessage message : schema.validate(record)) {
      violations.add(describe(message));
    }
    return violations;
  }

  private static String scalarType(JsonToken scalar) {
    return switch (scalar) {
      case VALUE_STRING -> "string";
      case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> "number";
      case VALUE_TRUE, VALUE_FALSE -> "boolean";
      default -> "null";
    };
  }

  /** The pointer inside the record and the keyword; for {@code required} the property too. */
  private static String describe(ValidationMessage message) {
    StringBuilder text = new StringBuilder();
    String pointer = pointer(message.getInstanceLocation(), message.getEvaluationPath());
    if (!pointer.isEmpty()) {
      text.append(pointer).append(' ');
    }
    text.append(message.getType());
    if ("required".equals(message.getType()) && message.getProperty() != null) {
      text.append(' ').append(message.getProperty());
    }
    return text.toString();
  }

  /**
   * The JSON Pointer of RFC 6901 for the location inside the record. A property name that no {@code
   * properties} of the schema declares is {@code *}, because it comes from the response.
   */
  private static String pointer(JsonNodePath location, JsonNodePath evaluationPath) {
    boolean[] declared = declaredProperties(location, evaluationPath);
    StringBuilder pointer = new StringBuilder();
    for (int i = 0; i < declared.length; i++) {
      Object token = location.getElement(i);
      pointer.append('/');
      if (declared[i] || token instanceof Integer) {
        pointer.append(String.valueOf(token).replace("~", "~0").replace("/", "~1"));
      } else {
        pointer.append('*');
      }
    }
    return pointer.toString();
  }

  /** Walks the evaluation path along the location, one member of the instance at a time. */
  private static boolean[] declaredProperties(JsonNodePath location, JsonNodePath evaluationPath) {
    boolean[] declared = new boolean[location.getNameCount()];
    int member = 0;
    int step = 0;
    while (step < evaluationPath.getNameCount() && member < declared.length) {
      String keyword = String.valueOf(evaluationPath.getElement(step));
      if ("properties".equals(keyword) && step + 1 < evaluationPath.getNameCount()) {
        step++;
        declared[member] = evaluationPath.getElement(step).equals(location.getElement(member));
        member++;
      } else if (MATCHED_MEMBER_KEYWORDS.contains(keyword)) {
        step++;
        member++;
      } else if (UNNAMED_MEMBER_KEYWORDS.contains(keyword)) {
        member++;
      } else if (NAMED_SELF_KEYWORDS.contains(keyword)) {
        step++;
      }
      step++;
    }
    return declared;
  }

  /** What the records of one response gave. */
  sealed interface Verdict {

    /** The response holds only JSON whitespace. */
    record Blank() implements Verdict {}

    /**
     * @param records the number of records
     */
    record Agrees(int records) implements Verdict {}

    /**
     * @param reason names no value of the response
     */
    record Rejected(String reason) implements Verdict {}

    /**
     * The response is JSON of a type that holds no records.
     *
     * @param found for example {@code object} or {@code number}
     * @param expected for example {@code an array}
     */
    record WrongType(String found, String expected) implements Verdict {

      String reason(String subject) {
        return subject + " is a JSON " + found + ", not " + expected;
      }
    }
  }

  private static final class Violations {

    private int records;
    private int invalid;
    private final List<String> reported = new ArrayList<>();

    void add(List<String> recordViolations) {
      if (!recordViolations.isEmpty()) {
        invalid++;
        for (String violation : recordViolations) {
          if (reported.size() < REPORTED_VIOLATIONS) {
            reported.add("record " + records + ": " + violation);
          }
        }
      }
      records++;
    }

    Verdict verdict() {
      if (invalid == 0) {
        return new Verdict.Agrees(records);
      }
      return new Verdict.Rejected(
          invalid
              + " of "
              + records
              + " records violate the Data structure: "
              + String.join("; ", reported));
    }
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

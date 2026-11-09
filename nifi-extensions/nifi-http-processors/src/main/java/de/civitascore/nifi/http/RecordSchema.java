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
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

/**
 * The Data structure of an HTTP/REST Data source, which each record of a response must agree with.
 */
final class RecordSchema {

  private static final int REPORTED_VIOLATIONS = 3;

  private static final String NOT_JSON = "the response is not JSON";

  private static final ObjectMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

  /** A record ends inside the response, so the tokens after it are no error of the record. */
  private static final ObjectReader RECORD_READER =
      MAPPER.readerFor(JsonNode.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

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
      JsonSchema schema = ModelForgeSchemaFactory.create().getSchema(node);
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
  Verdict verdict(ProcessSession session, FlowFile response, ResponseShape shape) {
    AtomicReference<Verdict> verdict = new AtomicReference<>();
    session.read(response, content -> verdict.set(verdict(content, shape)));
    return verdict.get();
  }

  /**
   * Reads the response one record at a time: a JSON array holds one record in each element, a JSON
   * object is one record. The type comes before the records, so a response of the wrong type for
   * the shape gets no record violation. The values at the paths of the shape come from the same
   * read.
   */
  Verdict verdict(InputStream response, ResponseShape shape) {
    try (JsonParser parser = RECORD_READER.createParser(response)) {
      JsonToken first = parser.nextToken();
      if (first == null) {
        return new Verdict.Empty();
      }
      Verdict verdict =
          shape instanceof ResponseShape.EntriesAt entriesAt && first != JsonToken.VALUE_NULL
              ? new EntriesReader(parser, entriesAt).read()
              : switch (first) {
                case START_ARRAY -> {
                  if (shape instanceof ResponseShape.RecordObject) {
                    parser.skipChildren();
                    yield new Verdict.WrongType("array", shape.expected());
                  }
                  yield elements(parser);
                }
                case START_OBJECT -> {
                  if (shape instanceof ResponseShape.RecordArray) {
                    parser.skipChildren();
                    yield new Verdict.WrongType("object", shape.expected());
                  }
                  yield object(parser, shape);
                }
                case VALUE_NULL -> new Verdict.Empty();
                default -> new Verdict.WrongType(scalarType(first), shape.expected());
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
    return violations.verdict(Map.of(), Map.of(), Optional.empty());
  }

  /** A response that is one record, whose tree is in memory for the validation anyway. */
  private Verdict object(JsonParser parser, ResponseShape shape) throws IOException {
    NumberTextParser numbers = new NumberTextParser(parser, shape.paths());
    JsonNode record = RECORD_READER.readTree(numbers);
    Violations violations = new Violations();
    violations.add(violations(record));
    return violations.verdict(shape.valuesIn(record), numbers.texts(), Optional.empty());
  }

  private List<String> recordViolations(JsonParser parser, JsonToken token) throws IOException {
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren();
      return List.of("type");
    }
    return violations(RECORD_READER.readTree(parser));
  }

  private List<String> violations(JsonNode record) {
    List<String> violations = new ArrayList<>();
    for (ValidationMessage message : schema.validate(record)) {
      violations.add(ViolationText.describe(message));
    }
    return violations;
  }

  /**
   * Reads a response whose records lie at the path to entries. It reads a tree only for one entry
   * at a time and for the value at a path of the pagination, and skips the rest of the response.
   */
  private final class EntriesReader {

    private final NumberTextParser parser;
    private final JsonPath entriesPath;
    private final List<JsonPath> paths;
    private final Map<JsonPath, JsonNode> values = new HashMap<>();
    private final Violations violations = new Violations();

    EntriesReader(JsonParser parser, ResponseShape.EntriesAt shape) {
      this.parser = new NumberTextParser(parser, shape.paths());
      this.entriesPath = shape.entriesPath();
      this.paths = shape.paths();
      for (JsonPath path : paths) {
        values.put(path, MissingNode.getInstance());
      }
    }

    /** Leaves the parser at the last token of the response. */
    Verdict read() throws IOException {
      boolean found = false;
      Optional<PageContent.Span> entries = Optional.empty();
      for (JsonToken token = parser.currentToken(); ; token = parser.nextToken()) {
        if (token != JsonToken.FIELD_NAME && !token.isStructEnd()) {
          JsonStreamContext location = location(token);
          Optional<String> rejection = rejection(token, location, found);
          if (rejection.isPresent()) {
            skipToTheEnd();
            return new Verdict.Rejected(rejection.get());
          }
          if (entriesPath.isAt(location)) {
            found = true;
            entries = entriesAt(token, location);
          } else {
            value(token, location);
          }
        }
        if (parser.getParsingContext().inRoot()) {
          break;
        }
      }
      if (!found) {
        return new Verdict.Rejected("the response has no value at " + entriesPath);
      }
      return violations.verdict(values, parser.texts(), entries);
    }

    /**
     * @param found whether the response had a value at the path to entries before {@code token}
     */
    private Optional<String> rejection(JsonToken token, JsonStreamContext location, boolean found) {
      Optional<JsonPath> holder = paths.stream().filter(path -> path.isAt(location)).findFirst();
      if (token.isStructStart() && holdsEntries(location) && holder.isPresent()) {
        return Optional.of(
            "the value at " + holder.get() + " must not hold the path to entries " + entriesPath);
      }
      if (!entriesPath.isAt(location)) {
        return Optional.empty();
      }
      if (found) {
        return Optional.of("the response holds more than one value at " + entriesPath);
      }
      if (token == JsonToken.VALUE_NULL) {
        return Optional.empty();
      }
      if (!token.isStructStart()) {
        return Optional.of(
            "the value at "
                + entriesPath
                + " is a JSON "
                + scalarType(token)
                + ", not an object or an array");
      }
      if (parser.currentTokenLocation().getByteOffset() < 0) {
        return Optional.of("the response is not UTF-8");
      }
      return Optional.empty();
    }

    /**
     * Reads the value that starts at {@code token}, or only its first token when the parser must
     * descend into it to reach a path.
     */
    private void value(JsonToken token, JsonStreamContext location) throws IOException {
      if (paths.stream().anyMatch(path -> path.isAt(location))) {
        capture(location);
      } else if (token.isStructStart()
          && !holdsEntries(location)
          && paths.stream().noneMatch(path -> path.isInside(location))) {
        parser.skipChildren();
      }
    }

    /**
     * @return where the value lies in the response; empty for {@code null}
     */
    private Optional<PageContent.Span> entriesAt(JsonToken token, JsonStreamContext location)
        throws IOException {
      if (token == JsonToken.VALUE_NULL) {
        capture(location);
        return Optional.empty();
      }
      long start = parser.currentTokenLocation().getByteOffset();
      if (token == JsonToken.START_ARRAY) {
        for (JsonToken element = parser.nextToken();
            element != JsonToken.END_ARRAY;
            element = parser.nextToken()) {
          JsonNode entry = capture(location(element));
          violations.add(entry.isObject() ? violations(entry) : List.of("type"));
        }
      } else {
        violations.add(violations(capture(location)));
      }
      return Optional.of(
          new PageContent.Span(start, parser.currentLocation().getByteOffset() - start));
    }

    private boolean holdsEntries(JsonStreamContext location) {
      return entriesPath.isAt(location) || entriesPath.isInside(location);
    }

    /**
     * Reads the value at {@code location} as a tree, and takes from it the value of each path that
     * starts at the location.
     */
    private JsonNode capture(JsonStreamContext location) throws IOException {
      int depth = JsonPath.depth(location);
      List<JsonPath> within =
          paths.stream().filter(path -> path.isAt(location) || path.isInside(location)).toList();
      JsonNode value = RECORD_READER.readTree(parser);
      JsonNode tree = value == null ? NullNode.getInstance() : value;
      for (JsonPath path : within) {
        values.put(path, path.valueIn(tree, depth));
      }
      return tree;
    }

    /** Skips to the last token of the response, so that a JSON error still comes first. */
    private void skipToTheEnd() throws IOException {
      while (!parser.getParsingContext().inRoot()) {
        parser.nextToken();
      }
    }

    /** The context of the current value; Jackson opens a context of its own for a container. */
    private JsonStreamContext location(JsonToken token) {
      JsonStreamContext context = parser.getParsingContext();
      return token.isStructStart() ? context.getParent() : context;
    }
  }

  private static String scalarType(JsonToken scalar) {
    return switch (scalar) {
      case VALUE_STRING -> "string";
      case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> "number";
      case VALUE_TRUE, VALUE_FALSE -> "boolean";
      default -> throw new IllegalArgumentException("not a JSON scalar: " + scalar);
    };
  }

  /** What the records of one response gave. */
  sealed interface Verdict {

    /**
     * The response holds only JSON whitespace, or only {@code null}, which some APIs send for an
     * empty result.
     */
    record Empty() implements Verdict {}

    record Agrees(PageContent content) implements Verdict {}

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

    Verdict verdict(
        Map<JsonPath, JsonNode> values,
        Map<JsonPath, String> numberTexts,
        Optional<PageContent.Span> entries) {
      if (invalid == 0) {
        return new Verdict.Agrees(new PageContent(records, values, numberTexts, entries));
      }
      return new Verdict.Rejected(
          invalid
              + " of "
              + records
              + " records violate the Data structure: "
              + String.join("; ", reported));
    }
  }
}
